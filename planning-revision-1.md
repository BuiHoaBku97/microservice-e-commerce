# Planning revision 1

## 1. Xử lý callback thanh toán thành công đến muộn

### Vấn đề

Một Payment có thể phát sinh nhiều sự kiện hợp lệ trong suốt vòng đời. Ví dụ:

```text
PaymentFailed(eventId=E1, paymentId=P)
→ Order chuyển sang CANCELLED và giải phóng reservation

PaymentSucceeded(eventId=E2, paymentId=P) đến muộn
→ Order phải giữ CANCELLED
→ Order phát OrderConfirmationRejected
→ Payment chuyển sang REQUIRES_REVIEW
```

Hiện tại Order service coi sự kiện thứ hai là duplicate vì:

- Migration `order-service/src/main/resources/db/migration/V7__create_payment_event_history.sql` đặt `payment_event_history.payment_id` là `UNIQUE`.
- `OrderService.isDuplicate()` kiểm tra cả `eventId` và `paymentId`.

`paymentId` định danh một giao dịch thanh toán, còn `eventId` định danh một sự kiện cụ thể. Hai event khác nhau như `PaymentFailed` và `PaymentSucceeded` có thể dùng chung một `paymentId`, nhưng bắt buộc có `eventId` khác nhau. Chỉ việc nhận lại cùng một `eventId` mới là event replay cần bỏ qua.

Do bị bỏ qua theo `paymentId`, `PaymentSucceeded` đến muộn không đi qua `rejectPayment()`. Order không phát `OrderConfirmationRejected`, khiến Payment có thể dừng ở `SUCCESS` với `orderAcceptance=PENDING` thay vì được đưa vào đối soát.

Payment service còn có một khoảng trạng thái không an toàn: `Payment.receive()` hiện chỉ chuyển sang `REQUIRES_REVIEW` khi `orderAcceptance` đã là `REJECTED`. Nếu Payment đang `FAILED`, `EXPIRED` hoặc `CANCELLED` nhưng event từ Order chưa đến, callback thành công sẽ tạm đặt Payment thành `SUCCESS/PENDING`.

### Kết quả mong muốn

- Event replay được xác định bằng `eventId`, không phải `paymentId`.
- `payment_event_history` cho phép nhiều bản ghi có cùng `paymentId` và vẫn bắt buộc `eventId` duy nhất.
- Callback thành công sau `FAILED`, `EXPIRED` hoặc `CANCELLED` luôn ghi nhận `fundsStatus=RECEIVED` và chuyển Payment sang `REQUIRES_REVIEW`.
- Order đã `CANCELLED` không được mở lại và không commit kho khi nhận `PaymentSucceeded` đến muộn.
- Order ghi nhận event thành công đến muộn và phát `OrderConfirmationRejected` để hoàn tất `orderAcceptance=REJECTED` phía Payment.
- Replay cùng một event không tạo thêm history, outbox hoặc tác động kho.

### Phương án sửa

#### 1. Sửa schema của Order service

Không chỉnh sửa V7 nếu migration này đã được áp dụng. Tạo migration kế tiếp, dự kiến `V15__allow_multiple_events_per_payment.sql`:

```sql
ALTER TABLE payment_event_history
    DROP CONSTRAINT IF EXISTS payment_event_history_payment_id_key;

CREATE INDEX IF NOT EXISTS idx_payment_event_history_payment_id
    ON payment_event_history (payment_id);
```

Unique constraint trên `event_id` được giữ nguyên. Index không unique trên `payment_id` hỗ trợ truy vấn lịch sử theo Payment.

Trong `PaymentEventHistory`, bỏ thuộc tính `unique = true` khỏi cột `paymentId`:

```java
@Column(nullable = false)
private Long paymentId;
```

#### 2. Sửa idempotency trong Order service

Đổi `OrderService.isDuplicate()` để chỉ kiểm tra `eventId`:

```java
private boolean isDuplicate(PaymentEvent event) {
  return event.eventId() != null
      && paymentEventHistoryRepository.existsByEventId(event.eventId());
}
```

Xóa `existsByPaymentId()` và `findByPaymentId()` khỏi `PaymentEventHistoryRepository` nếu không còn nơi sử dụng.

Consumer tiếp tục claim event bằng unique key `(consumer_name, event_id)` trong `processed_events`. Đây là lớp idempotency nguyên tử cho Kafka replay; kiểm tra lịch sử trong `OrderService` được giữ như lớp bảo vệ khi service được gọi trực tiếp.

Sau thay đổi, `PaymentSucceeded` có `eventId` mới sẽ tiếp tục vào `validatePayment()`. Nếu Order đã `CANCELLED`, event bị từ chối, được ghi vào `payment_event_history` với kết quả `REVIEW`, và `rejectPayment()` phát `OrderConfirmationRejected`. Order không release hoặc commit kho lần nữa.

#### 3. Sửa chuyển trạng thái trong Payment service

Trong `Payment.receive()`, xác định việc cần đối soát từ trạng thái trước khi ghi nhận tiền:

```java
boolean requiresReview =
    status == PaymentStatus.FAILED
        || status == PaymentStatus.EXPIRED
        || status == PaymentStatus.CANCELLED
        || orderAcceptance == OrderAcceptance.REJECTED;

fundsStatus = FundsStatus.RECEIVED;
this.providerTransactionId = providerTransactionId;
this.providerReference = providerTransactionId;
this.paidAt = paidAt == null ? Instant.now() : paidAt;
status = requiresReview ? PaymentStatus.REQUIRES_REVIEW : PaymentStatus.SUCCESS;
failureReason = null;
```

Tên `requiresReview` phản ánh quyết định nghiệp vụ rõ hơn `lateReceipt`: Payment cần đối soát khi tiền đến sau trạng thái đóng hoặc khi Order đã từ chối, kể cả khi nguyên nhân không chỉ là thời gian.

`PaymentService.callback()` vẫn phải phát `PaymentSucceeded` khi lần đầu xác nhận `fundsStatus=RECEIVED`, kể cả Payment vừa chuyển sang `REQUIRES_REVIEW`. Event này thông báo sự thật rằng tiền đã được nhận để Order phản hồi bằng `OrderConfirmed` hoặc `OrderConfirmationRejected`.

### Kiểm thử cần bổ sung

#### Order service

1. Xử lý `PaymentFailed(E1, P)` rồi `PaymentSucceeded(E2, P)` trên cùng Order:
   - Order giữ trạng thái `CANCELLED`.
   - Reservation chỉ được release một lần.
   - Không commit inventory.
   - Có hai history cùng `paymentId=P`, lần lượt mang `eventId=E1` và `eventId=E2`.
   - History của E2 có kết quả `REVIEW`.
   - Có `OrderConfirmationRejected` với đúng `paymentId` và lý do phù hợp.
2. Replay lại E2:
   - Không thêm history hoặc outbox.
   - Không tác động inventory.
3. Test migration/repository:
   - Cho phép hai hàng khác `eventId` nhưng cùng `paymentId`.
   - Từ chối hai hàng có cùng `eventId`.

#### Payment service

1. Callback `SUCCESS` sau mỗi trạng thái `FAILED`, `EXPIRED` và `CANCELLED`:
   - `fundsStatus=RECEIVED`.
   - `status=REQUIRES_REVIEW`.
   - Vẫn tạo `PaymentSucceeded` đúng một lần.
2. Callback `SUCCESS` khi Payment còn `PENDING` và Order chưa từ chối:
   - `status=SUCCESS`.
   - `orderAcceptance=PENDING` cho đến khi nhận phản hồi Order.
3. Nhận `OrderConfirmationRejected` sau callback thành công muộn:
   - `orderAcceptance=REJECTED`.
   - `status` tiếp tục là `REQUIRES_REVIEW`.
4. Replay cùng `providerEventId`:
   - Không tạo thêm outbox hoặc lịch sử trạng thái.

### Tiêu chí hoàn thành

Luồng `PaymentFailed → Order CANCELLED → callback SUCCESS` phải kết thúc ổn định ở:

```text
Order.status               = CANCELLED
Payment.fundsStatus        = RECEIVED
Payment.orderAcceptance    = REJECTED
Payment.status             = REQUIRES_REVIEW
```

Không có lần commit hoặc release kho thứ hai, và mọi replay cùng `eventId` đều không tạo hiệu ứng mới.

## 2. Giữ Payment ở FAILED khi nhận OrderCancelled(PAYMENT_FAILED)

### Vấn đề

Sau khi Payment nhận callback thất bại, luồng sự kiện bình thường diễn ra như sau:

```text
Payment chuyển sang FAILED
→ Payment phát PaymentFailed
→ Order chuyển sang CANCELLED với cancellationReason=PAYMENT_FAILED
→ Order phát OrderCancelled(PAYMENT_FAILED)
→ Payment nhận lại sự kiện đóng Order
```

`Payment.cancelFromOrder()` hiện chỉ phân biệt `PAYMENT_EXPIRED`. Khi chưa nhận tiền, mọi lý do khác đều bị ánh xạ thành `PaymentStatus.CANCELLED`:

```java
status = "PAYMENT_EXPIRED".equals(reason)
    ? PaymentStatus.EXPIRED
    : PaymentStatus.CANCELLED;
```

Vì vậy, Payment đã ở `FAILED` bị đổi thành `CANCELLED` khi nhận phản hồi `OrderCancelled(PAYMENT_FAILED)`. Trạng thái sau cùng không còn phản ánh nguyên nhân thanh toán thất bại.

Hành vi này trái với quy tắc trạng thái và Kafka contract:

- `PAYMENT_FAILED` ánh xạ thành `FAILED`.
- `PAYMENT_EXPIRED` ánh xạ thành `EXPIRED`.
- `CUSTOMER_CANCELLED` ánh xạ thành `CANCELLED`.
- Nếu `fundsStatus=RECEIVED`, Order không chấp nhận Payment thì trạng thái phải là `REQUIRES_REVIEW`, không phụ thuộc lý do đóng Order.

### Kết quả mong muốn

- Payment đang `FAILED` vẫn giữ `FAILED` khi nhận `OrderCancelled(PAYMENT_FAILED)`.
- Payment chưa nhận tiền được đóng đúng trạng thái theo `reason` của Order.
- Payment đã nhận tiền chuyển sang `REQUIRES_REVIEW` và giữ thông tin giao dịch đã nhận.
- Reason ngoài Kafka contract không bị âm thầm quy thành `CANCELLED`.
- Việc xử lý lại cùng kết quả không tạo lịch sử trạng thái sai hoặc phát thêm Payment event.

### Phương án sửa

Không cần thay đổi `PaymentStatus`. Sửa `Payment.cancelFromOrder()` để ánh xạ rõ từng cancellation reason:

```java
public void cancelFromOrder(String reason) {
  orderAcceptance = OrderAcceptance.REJECTED;

  if (fundsStatus == FundsStatus.RECEIVED) {
    status = PaymentStatus.REQUIRES_REVIEW;
    reviewReason = reason;
    return;
  }

  status = switch (reason) {
    case "PAYMENT_FAILED" -> PaymentStatus.FAILED;
    case "PAYMENT_EXPIRED" -> PaymentStatus.EXPIRED;
    case "CUSTOMER_CANCELLED" -> PaymentStatus.CANCELLED;
    default -> throw new IllegalArgumentException("ORDER_CANCELLATION_REASON_INVALID");
  };
}
```

`PaymentService.handleOrderCancelled()` tiếp tục khóa Payment theo `orderId`, gọi `cancelFromOrder(reason)` và dùng `recordIfChanged()` để chỉ ghi history khi trạng thái tổng hợp thực sự thay đổi. Không phát `PaymentFailed` mới từ `OrderCancelled`, tránh tạo event loop.

Reason không thuộc contract phải làm consumer thất bại để đi qua cơ chế retry/DLT hiện có, thay vì ghi một trạng thái nghiệp vụ sai.

### Kiểm thử cần bổ sung

1. Payment chưa nhận tiền nhận `OrderCancelled(PAYMENT_FAILED)`:
   - `status=FAILED`.
   - `orderAcceptance=REJECTED`.
2. Payment chưa nhận tiền nhận `OrderCancelled(PAYMENT_EXPIRED)`:
   - `status=EXPIRED`.
   - `orderAcceptance=REJECTED`.
3. Payment chưa nhận tiền nhận `OrderCancelled(CUSTOMER_CANCELLED)`:
   - `status=CANCELLED`.
   - `orderAcceptance=REJECTED`.
4. Payment có `fundsStatus=RECEIVED` nhận từng reason hợp lệ:
   - `status=REQUIRES_REVIEW`.
   - `orderAcceptance=REJECTED`.
   - `reviewReason` giữ đúng reason.
5. Reason không thuộc contract:
   - Ném `ORDER_CANCELLATION_REASON_INVALID`.
   - Consumer không acknowledge thành công và sự kiện đi qua retry/DLT.
6. Luồng đầy đủ `PaymentFailed → OrderCancelled(PAYMENT_FAILED)`:
   - Payment kết thúc ở `FAILED`, không bị đổi thành `CANCELLED`.
   - Không phát thêm `PaymentFailed` khi xử lý `OrderCancelled`.

### Tiêu chí hoàn thành

Luồng thất bại thanh toán phải kết thúc ổn định ở:

```text
Order.status               = CANCELLED
Order.cancellationReason   = PAYMENT_FAILED
Payment.fundsStatus        = NOT_RECEIVED
Payment.orderAcceptance    = REJECTED
Payment.status             = FAILED
```

Các reason hợp lệ còn lại được ánh xạ đúng theo contract; Payment đã nhận tiền luôn được đưa vào `REQUIRES_REVIEW`.

## 3. Lưu Order cancellation context khi `OrderCancelled` đến trước `OrderCreated`

### Quyết định kiến trúc

Giữ cơ chế Outbox retry non-blocking hiện tại. Không đổi sang retry blocking bằng Resilience4j và không bắt event sau phải chờ event trước của cùng Order publish thành công.

Quyết định này phù hợp với `KAFKA_EVENT_CONTRACT.md`: retry/DLT hoặc multi-publisher có thể làm event sau vượt event lỗi, nên consumer phải chịu được event sai thứ tự bằng aggregate state/context. Resilience4j có thể retry ngắn trong memory nhưng không thay thế trạng thái Outbox bền vững sau restart và cũng không tự bảo đảm thứ tự giữa nhiều publisher.

Phạm vi triển khai của mục này là hoàn thiện khả năng chịu sai thứ tự tại Payment service. Không thay đổi truy vấn claim Outbox, retry backoff, DLT hoặc Kafka partition key.

### Vấn đề

Luồng hiện tại xử lý `OrderCancelled` bằng:

```java
paymentRepository.findByOrderIdForUpdate(orderId)
    .ifPresent(payment -> payment.cancelFromOrder(reason));
```

Nếu `OrderCancelled` đến khi chưa có Payment, `Optional` rỗng và transaction không lưu bất kỳ trạng thái nghiệp vụ nào. `processed_events` vẫn claim event và consumer vẫn acknowledge, nên replay cùng `eventId` không thể khôi phục cancellation đã bị bỏ qua.

Khi `OrderCreated` đến sau, `PaymentService.processOrderPayment()` chỉ kiểm tra `payments`. Vì không tìm thấy Payment, service tạo bản ghi mới với các giá trị mặc định:

```text
status          = PENDING
fundsStatus     = NOT_RECEIVED
orderAcceptance = PENDING
```

Kết quả là Order đã `CANCELLED` nhưng Payment lại mở một giao dịch chờ thanh toán. Payment này có thể nhận callback và thu tiền cho một Order đã đóng. Hành vi đó trái với `BUSINESS_STATE_DESIGN.md` và `KAFKA_EVENT_CONTRACT.md`: cancellation đến trước phải được lưu context, và `OrderCreated` đến sau không được mở Payment `PENDING`.

### Kết quả mong muốn

- `OrderCancelled` luôn được lưu bền vững, kể cả khi chưa có Payment.
- `OrderCreated` đến sau tạo Payment trực tiếp ở trạng thái đóng phù hợp; không có thời điểm nào Payment của Order đã đóng ở `PENDING`.
- Cancellation reason được ánh xạ thống nhất với mục 2:
  - `PAYMENT_FAILED` → `FAILED`.
  - `PAYMENT_EXPIRED` → `EXPIRED`.
  - `CUSTOMER_CANCELLED` → `CANCELLED`.
- Payment được tạo sau cancellation có `fundsStatus=NOT_RECEIVED` và `orderAcceptance=REJECTED`.
- Nếu callback `SUCCESS` hợp lệ đến Payment đã đóng, vẫn ghi `fundsStatus=RECEIVED`, chuyển `REQUIRES_REVIEW` và phát `PaymentSucceeded` đúng một lần theo mục 1.
- Replay cùng event không tạo thêm context, Payment, history hoặc outbox.
- `OrderCreated` và `OrderCancelled` chạy đồng thời phải hội tụ về cùng một kết quả đóng, không phụ thuộc transaction nào lấy khóa trước.

### Phương án sửa

#### 1. Thêm cancellation context bền vững trong Payment service

Tạo migration kế tiếp, dự kiến `payment-service/src/main/resources/db/migration/V9__create_order_payment_contexts.sql`, và entity `OrderPaymentContext`.

Context tối thiểu gồm:

```text
order_id                    PK
closed                      NOT NULL
cancellation_reason         nullable khi chưa đóng
cancellation_event_id       nullable, unique khi có giá trị
cancellation_occurred_at    nullable
cancellation_version        nullable
created_at                  NOT NULL
updated_at                  NOT NULL
version                     NOT NULL
```

`order_id` là khóa đồng bộ theo aggregate. `cancellation_event_id`, `occurredAt` và `aggregateVersion` được giữ để audit và không phụ thuộc riêng vào bảng `processed_events` khi kiểm tra trạng thái nghiệp vụ.

Không tạo một context chỉ tồn tại trong cache hoặc memory vì context phải còn sau restart. Không dùng chính bản ghi `Payment` làm tombstone khi nhận cancellation trước, do event này chưa chắc có đủ dữ liệu bắt buộc để tạo Payment hoàn chỉnh như `amount` và `currency`.

#### 2. Chuẩn hóa payload xử lý `OrderCancelled`

Tạo `OrderCancelledEvent` hoặc một command tương đương chứa tối thiểu:

```text
eventId
orderId
reason
occurredAt
aggregateVersion
```

`OrderEventConsumer` parse và validate các trường rồi truyền cả command vào `PaymentService`, thay vì chỉ truyền `orderId` và `reason`. Reason không thuộc contract tiếp tục làm transaction rollback và đi qua retry/DLT; không được lưu context đóng với reason không hợp lệ.

#### 3. Serialize `OrderCreated` và `OrderCancelled` theo `orderId`

Cả `processOrderPayment()` và `handleOrderCancelled()` phải gọi một thao tác `ensureAndLockContext(orderId)` trong cùng transaction:

1. `INSERT ... ON CONFLICT DO NOTHING` để bảo đảm row context tồn tại.
2. `SELECT ... FOR UPDATE` context theo `orderId`.
3. Chỉ sau khi giữ khóa mới đọc hoặc tạo/cập nhật Payment.

Cách này xử lý cả trường hợp hai event chạy đồng thời khi context chưa tồn tại. Chỉ gọi `findByOrderIdForUpdate()` trên bảng `payments` là chưa đủ vì không có row nào để khóa trước khi Payment được tạo.

Kết quả tuần tự hóa phải là:

```text
OrderCreated giữ khóa trước:
  tạo Payment PENDING
  → OrderCancelled giữ khóa sau
  → đóng context và đóng Payment

OrderCancelled giữ khóa trước:
  đóng context
  → OrderCreated giữ khóa sau
  → tạo Payment trực tiếp ở trạng thái đóng
```

Cả hai nhánh đều kết thúc bằng cùng trạng thái terminal.

#### 4. Xử lý `OrderCancelled`

Sau khi khóa context:

1. Validate reason theo mapping của mục 2.
2. Đánh dấu context `closed=true` và lưu metadata cancellation.
3. Nếu Payment đã tồn tại, gọi `cancelFromOrder(reason)` và `recordIfChanged()` như hiện tại.
4. Nếu chưa có Payment, commit context; không coi đây là no-op.

Việc lưu context và claim `processed_events` phải nằm trong cùng transaction hiện có. Nếu lưu context thất bại thì claim event rollback và consumer không acknowledge thành công.

Cancellation là trạng thái terminal của Order. Event cancellation hợp lệ đến lại với cùng kết quả là idempotent. Nếu xuất hiện cancellation khác reason/version cho cùng Order, không được âm thầm ghi đè chứng cứ cũ; phải áp dụng aggregate version hoặc báo conflict để retry/DLT và phục vụ điều tra.

#### 5. Xử lý `OrderCreated` khi context đã đóng

Sau khi validate event và khóa context:

- Nếu đã có Payment, trả Payment hiện tại như idempotency hiện có.
- Nếu context chưa đóng, tạo Payment `PENDING` như hiện tại.
- Nếu context đã đóng, tạo Payment bằng factory/constructor nghiệp vụ riêng, ví dụ `Payment.closedFromOrder(...)`, với:

```text
fundsStatus     = NOT_RECEIVED
orderAcceptance = REJECTED
status          = FAILED | EXPIRED | CANCELLED theo cancellationReason
reviewReason    = null
failureReason   = PAYMENT_FAILED khi phù hợp, nếu model cần giữ nguyên nhân thất bại
```

Không tạo Payment `PENDING` rồi gọi tiếp `cancelFromOrder()` trong hai bước có thể quan sát riêng. Entity phải được persist lần đầu ở trạng thái terminal để invariant luôn đúng trong transaction và history đầu tiên phản ánh đúng kết quả.

Ghi một history duy nhất với source rõ nghĩa, dự kiến `ORDER_CREATED_AFTER_CANCELLATION`, và reason lấy từ context. Không phát `PaymentFailed` hoặc payment result event từ thao tác materialize này; `OrderCancelled` là phản hồi từ Order, phát thêm `PaymentFailed` sẽ tạo event loop.

#### 6. Không thay đổi Outbox retry trong batch này

Giữ nguyên các thuộc tính `status`, `attempts`, `next_attempt_at`, `claimed_until` và manual replay của Outbox. Không thêm Resilience4j vào `OutboxPublisher` trong lần sửa này.

Lý do:

- Retry trong database phục hồi được sau process restart.
- Retry blocking khi `publishPendingEvents()` đang giữ transaction có thể kéo dài row lock và chặn cả batch.
- Retry hữu hạn vẫn có thể hết attempt và cho event sau vượt lên; retry vô hạn có thể chặn mọi aggregate vì một poison event.
- Chính sách đã chọn là cho pipeline tiếp tục và làm consumer an toàn trước event sai thứ tự.

### Kiểm thử cần bổ sung

#### Payment service unit/repository tests

1. `OrderCancelled(CUSTOMER_CANCELLED)` khi chưa có Payment:
   - Context được tạo với `closed=true` và đúng event metadata.
   - Không tạo Payment hoặc history tại thời điểm cancellation.
2. Sau test 1, xử lý `OrderCreated`:
   - Tạo đúng một Payment.
   - Payment có `CANCELLED/NOT_RECEIVED/REJECTED`, chưa từng được persist ở `PENDING`.
   - Có đúng một history `ORDER_CREATED_AFTER_CANCELLATION`.
3. Lặp lại tương ứng với `PAYMENT_EXPIRED` và `PAYMENT_FAILED`:
   - Trạng thái cuối lần lượt là `EXPIRED` và `FAILED`.
4. `OrderCreated` trước rồi `OrderCancelled`:
   - Payment hiện có được đóng đúng mapping.
   - Context cũng được đánh dấu đóng.
5. Replay cùng `OrderCancelled.eventId` và cùng `OrderCreated.eventId`:
   - Không thêm context, Payment hoặc history.
6. Cancellation reason không hợp lệ:
   - Không lưu context đóng.
   - `processed_events` rollback và consumer không acknowledge thành công.
7. Callback `SUCCESS` sau khi Payment được materialize ở trạng thái đóng:
   - `fundsStatus=RECEIVED`.
   - `status=REQUIRES_REVIEW`.
   - `orderAcceptance=REJECTED`.
   - Phát `PaymentSucceeded` đúng một lần.
8. Repository test PostgreSQL chạy hai transaction đồng thời cho cùng `orderId`, một `OrderCreated` và một `OrderCancelled`:
   - Chỉ có một context và một Payment.
   - Trạng thái cuối luôn đóng.
   - Không có unique violation rò ra ngoài hoặc deadlock.

#### Event-flow integration test

Publish thủ công vào Order topic với cùng key nhưng thứ tự Kafka là:

```text
OrderCancelled(E-cancel, orderId=O)
OrderCreated(E-create, orderId=O)
```

Xác nhận:

- Payment consumer commit được cả hai offset.
- Context đóng được lưu sau event đầu.
- Event thứ hai không mở Payment `PENDING`.
- Payment cuối có trạng thái terminal đúng reason.
- Restart Payment context rồi replay hai event không tạo thêm dữ liệu.

Thêm một test concurrency gọi hai handler bằng transaction thật để chứng minh khóa context, thay vì chỉ dựa vào test tuần tự qua một Kafka partition.

### Tiêu chí hoàn thành

Hai thứ tự sau phải hội tụ về cùng kết quả:

```text
OrderCreated → OrderCancelled
OrderCancelled → OrderCreated
```

Với `CUSTOMER_CANCELLED`, trạng thái cuối là:

```text
OrderPaymentContext.closed    = true
Payment.status                = CANCELLED
Payment.fundsStatus           = NOT_RECEIVED
Payment.orderAcceptance       = REJECTED
```

`PAYMENT_EXPIRED` và `PAYMENT_FAILED` phải kết thúc tương ứng ở `EXPIRED` và `FAILED`. Không có Payment `PENDING` cho Order đã đóng, không phát payment result event do materialize context, và replay/race không tạo thêm bản ghi hay lịch sử.

## 4. Dùng database clock cho quyết định hết hạn Order

### Quyết định kiến trúc

Giữ nguyên chính sách demo trong `BUSINESS_STATE_DESIGN.md`: Order quyết định chấp nhận hay từ chối Payment theo thời điểm Order thực sự xử lý event, không hồi tố theo `paidAt`. Nếu Payment đã nhận tiền trước hạn nhưng event đến Order sau hạn, Order vẫn từ chối xác nhận và Payment đi vào `REQUIRES_REVIEW`.

Thay đổi trong mục này chỉ chuẩn hóa nguồn thời gian và thứ tự lấy thời gian:

```text
Lấy Redis lock theo SKU
→ bắt đầu database transaction
→ khóa Order bằng SELECT ... FOR UPDATE
→ đọc PostgreSQL clock_timestamp()
→ quyết định chuyển trạng thái
→ cập nhật Order, reservation, history và outbox
→ commit
```

PostgreSQL là nguồn thời gian có thẩm quyền cho các quyết định liên quan đến `paymentDueAt`. Không dùng `Instant.now()` của JVM và không dùng timestamp được tạo trước khi chờ khóa cho quyết định này.

Phải dùng `clock_timestamp()`, không dùng `now()`, `CURRENT_TIMESTAMP` hoặc `transaction_timestamp()`. Các hàm sau trả thời điểm bắt đầu transaction nên có thể đã cũ nếu transaction phải chờ khóa; `clock_timestamp()` trả thời gian thực tại lúc câu lệnh được thực thi.

Phạm vi này không thay đổi chính sách late payment, không thêm grace period và không mở lại Order đã `CANCELLED`. Các timestamp không tham gia quyết định hết hạn như `shippingAt`, `completedAt` và timestamp phục vụ audit có thể được chuẩn hóa trong một thay đổi riêng.

### Vấn đề

Hiện tại có ba nguồn sai lệch:

1. `OrderService.validatePayment()` dùng `Instant.now()` sau khi khóa Order. Thứ tự khóa đúng nhưng nguồn thời gian là clock của JVM đang xử lý event, nên các instance có thể đưa ra kết quả khác nhau ở sát deadline.
2. `OrderService.cancel()` cũng dùng `Instant.now()` để chọn `CUSTOMER_CANCELLED` hoặc `PAYMENT_EXPIRED`, làm cancellation reason phụ thuộc clock của instance.
3. `PaymentExpiryScheduler` tạo một `Instant.now()` trước khi gọi service. `expirePendingPayments(now)` dùng lại timestamp này để chọn candidate và kiểm tra sau `getForUpdate()`. Khi batch lớn hoặc phải chờ Redis/database lock, giá trị `now` không còn phản ánh thời điểm transaction có quyền quyết định.

Ngoài ra, checkout đang tạo `paymentDueAt` bằng:

```java
Instant.now().plusSeconds(paymentTimeoutSeconds)
```

Do đó thời hạn giữ hàng ban đầu cũng phụ thuộc clock của JVM. Constructor mặc định trong `Order` tiếp tục gắn cứng 900 giây bằng `Instant.now()`, dễ tạo thêm đường khởi tạo không tuân theo cấu hình và nguồn thời gian chuẩn.

### Kết quả mong muốn

- Mọi quyết định `decisionTime < paymentDueAt` hoặc `decisionTime >= paymentDueAt` dùng thời gian PostgreSQL.
- `decisionTime` chỉ được đọc sau khi transaction đã lấy được pessimistic lock của Order.
- Một quyết định chỉ dùng một giá trị `decisionTime`; không đọc clock lại giữa validation và mutation.
- Tại đúng `paymentDueAt`, Order được coi là hết hạn.
- Scheduler không truyền `now` từ bên ngoài và vẫn kiểm tra lại trạng thái/deadline sau khi khóa từng Order.
- `paymentDueAt` của Order mới được tính từ database clock trong transaction checkout.
- `Order` chứa quy tắc so sánh deadline nhưng không phụ thuộc `JdbcTemplate`, `EntityManager` hoặc Spring bean.
- Payment callback, customer cancellation và expiry scheduler cạnh tranh trên cùng Order phải hội tụ theo thứ tự khóa và trạng thái đã commit.

### Phương án sửa

#### 1. Tạo adapter đọc database clock

Tạo một component, dự kiến `order-service/src/main/java/com/ecommerce/order/services/DatabaseClock.java`, dùng `JdbcTemplate` hoặc `EntityManager` để đọc PostgreSQL:

```java
@Component
public class DatabaseClock {
  private final JdbcTemplate jdbcTemplate;

  public DatabaseClock(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Instant now() {
    return jdbcTemplate.queryForObject(
        "select clock_timestamp()",
        (rs, rowNum) -> rs.getTimestamp(1).toInstant());
  }

  public Instant deadlineAfter(long timeoutSeconds) {
    return jdbcTemplate.queryForObject(
        "select clock_timestamp() + (? * interval '1 second')",
        (rs, rowNum) -> rs.getTimestamp(1).toInstant(),
        timeoutSeconds);
  }
}
```

`DatabaseClock.now()` dùng cho quyết định sau khóa. `deadlineAfter()` dùng khi tạo Order để database tính trực tiếp deadline từ cùng nguồn clock, tránh lấy DB time rồi cộng sau bằng một clock khác.

Không inject `DatabaseClock` vào entity `Order`. Entity phải độc lập với persistence và chỉ nhận `Instant` do application service truyền vào.

#### 2. Chuẩn hóa quy tắc deadline trong `Order`

Thêm một phương thức vào `Order`:

```java
public boolean isPaymentExpiredAt(Instant decisionTime) {
  Objects.requireNonNull(decisionTime, "decisionTime");
  return !decisionTime.isBefore(paymentDueAt);
}
```

Quy tắc duy nhất là:

```text
decisionTime <  paymentDueAt → còn hạn
decisionTime >= paymentDueAt → hết hạn
```

Xóa constructor tự tạo deadline bằng `Instant.now().plusSeconds(900)` nếu không còn nơi sử dụng production:

```java
public Order(Long userId, Long totalAmount, String currency)
```

Mọi nơi tạo Order phải truyền `paymentDueAt` rõ ràng. Test có thể truyền một `Instant` cố định để kết quả không phụ thuộc clock thực.

Không đổi `confirm()`, `markPaymentExpired()` thành nơi tự đọc thời gian. `OrderService` chịu trách nhiệm lấy một `decisionTime` đúng thứ tự; `Order` chịu trách nhiệm áp dụng invariant.

#### 3. Sửa callback Payment trong Order service

Trong cả `handlePaymentSucceeded()` và `handlePaymentFailed()`, thao tác bên trong `executeInventoryCommand()` phải theo thứ tự:

```java
Order order = getForUpdate(event.orderId());
Instant decisionTime = databaseClock.now();

if (isDuplicate(event)) {
  return order;
}

String invalidReason = validatePayment(event, order, decisionTime);
```

Đổi chữ ký validation:

```java
private String validatePayment(
    PaymentEvent event,
    Order order,
    Instant decisionTime)
```

Và thay `Instant.now()` bằng:

```java
if (order.isPaymentExpiredAt(decisionTime)) {
  return "PAYMENT_EXPIRED";
}
```

Cùng một `decisionTime` phải được dùng cho toàn bộ quyết định của event. Không đọc database clock lần hai trước khi gọi `confirm()`, vì một event đã được phân loại hợp lệ tại một thời điểm sau khóa phải hoàn tất theo cùng snapshot quyết định dù commit xảy ra sau deadline.

Kiểm tra duplicate có thể chạy trước khi đọc clock để giảm một query, nhưng chỉ sau khi đã khóa Order. Nếu giữ thứ tự như ví dụ trên để đơn giản hóa flow, kết quả duplicate vẫn phải là no-op và không phụ thuộc deadline.

#### 4. Sửa customer cancellation

Sau `getForUpdate()`, đọc database clock và dùng `Order.isPaymentExpiredAt()`:

```java
Order order = getForUpdate(owned.getId());
Instant decisionTime = databaseClock.now();

String cancellationReason =
    order.isPaymentExpiredAt(decisionTime)
        ? "PAYMENT_EXPIRED"
        : "CUSTOMER_CANCELLED";
```

Việc release reservation, cập nhật Order, status history và outbox tiếp tục nằm trong cùng transaction đang giữ Order lock. Reason ghi vào tất cả các bản ghi/event phải lấy từ cùng kết quả quyết định.

#### 5. Sửa expiry scheduler và candidate query

Đổi scheduler thành:

```java
public void expirePendingPayments() {
  orderService.expirePendingPayments();
}
```

Không truyền `Instant.now()` qua ranh giới scheduler/service.

Đổi query tìm candidate để PostgreSQL tự so sánh với clock hiện tại, dự kiến dùng native query:

```sql
SELECT *
FROM orders
WHERE status = 'PENDING_PAYMENT'
  AND payment_due_at <= clock_timestamp()
ORDER BY payment_due_at
```

Candidate query chỉ là tối ưu để tìm bản ghi có khả năng hết hạn; nó không phải quyết định cuối cùng. Với từng candidate, transaction vẫn phải:

```java
Order order = getForUpdate(candidateOrder.getId());
Instant decisionTime = databaseClock.now();

if (order.getStatus() != OrderStatus.PENDING_PAYMENT
    || !order.isPaymentExpiredAt(decisionTime)) {
  return false;
}
```

Khi tối ưu batch sau này, có thể bổ sung giới hạn số lượng và `FOR UPDATE SKIP LOCKED`. Không đưa tối ưu đó vào thay đổi đầu tiên nếu nó làm thay đổi ranh giới transaction hoặc thứ tự Redis lock hiện tại.

#### 6. Tạo deadline checkout từ database clock

Di chuyển việc tạo `Order` vào transaction lambda của `executeInventoryCommand()`. Sau khi đã lấy Redis lock và transaction bắt đầu, lấy deadline:

```java
Instant paymentDueAt = databaseClock.deadlineAfter(paymentTimeoutSeconds);
Order order = new Order(userId, totalAmount, "VND", paymentDueAt);
```

Sau đó mới gán idempotency, shipping snapshot, items, lưu Order và tạo reservation. `Order.paymentDueAt` và `Reservation.expiresAt` phải dùng đúng cùng một giá trị `paymentDueAt`, không gọi clock riêng cho reservation.

Cần giữ nguyên hành vi idempotency checkout: request replay phải trả Order đã tồn tại và deadline cũ, không tính deadline mới.

#### 7. Giữ transaction boundary rõ ràng

Mọi lời gọi `DatabaseClock` dùng cho quyết định phải nằm bên trong transaction do `InventoryService.executeCommand()`/`TransactionTemplate` mở. Không được lấy clock ở `PaymentExpiryScheduler`, consumer hoặc trước `executeInventoryCommand()` rồi truyền vào lambda.

Nếu refactor `executeInventoryCommand()`, phải loại bỏ khả năng chạy lại `action.get()` ngoài transaction khi kết quả `null`. Các command trong phạm vi này nên trả kết quả không-null rõ ràng; không dùng fallback thực thi mutation lần thứ hai.

### Kiểm thử cần bổ sung

#### Unit test `Order`

1. `decisionTime = paymentDueAt - 1 ns`:
   - `isPaymentExpiredAt()` trả `false`.
2. `decisionTime = paymentDueAt`:
   - `isPaymentExpiredAt()` trả `true`.
3. `decisionTime = paymentDueAt + 1 ns`:
   - `isPaymentExpiredAt()` trả `true`.
4. `decisionTime=null`:
   - từ chối rõ ràng, không âm thầm dùng JVM clock.

#### Order service unit test

1. `PaymentSucceeded` dùng giá trị do `DatabaseClock` trả về, không phụ thuộc `Instant.now()` của test runner.
2. Callback tại đúng deadline bị từ chối với `PAYMENT_EXPIRED`, release reservation và không commit inventory.
3. Callback trước deadline được xác nhận và commit inventory.
4. Customer cancellation trước deadline ghi `CUSTOMER_CANCELLED`; tại/sau deadline ghi `PAYMENT_EXPIRED`.
5. Scheduler không nhận tham số thời gian; mỗi candidate được kiểm tra lại bằng database clock sau `findByIdForUpdate()`.
6. Checkout truyền cùng `paymentDueAt` từ database cho Order và reservation.
7. Replay checkout không gọi tạo deadline mới.

Unit test phải mock `DatabaseClock` bằng các `Instant` cố định. Không dùng `Instant.now().plusSeconds(...)` trong assertion biên vì test sẽ không ổn định và không chứng minh đúng nguồn clock.

#### PostgreSQL integration test

Tạo test concurrency bằng database thật:

1. Transaction A lấy `FOR UPDATE` trên một Order `PENDING_PAYMENT` có deadline sắp đến.
2. Transaction B bắt đầu xử lý `PaymentSucceeded` và bị chặn khi lấy cùng Order lock.
3. Chờ deadline đi qua rồi cho transaction A commit mà không đổi trạng thái.
4. Transaction B lấy được khóa, sau đó đọc `clock_timestamp()`.
5. B phải kết luận `PAYMENT_EXPIRED`, không confirm Order và không commit reservation.

Thêm test cạnh tranh giữa expiry và payment callback:

- Nếu callback lấy khóa và quyết định hợp lệ trước hạn, callback confirm; expiry chờ rồi thấy `CONFIRMED` và không hủy.
- Nếu expiry lấy khóa sau hạn và commit trước, callback chờ rồi thấy `CANCELLED`, ghi nhận/reject theo contract và không mở lại Order.

Test integration phải chứng minh thứ tự lock → clock bằng transaction thật; Mockito `InOrder` chỉ hỗ trợ kiểm tra lời gọi và không chứng minh hành vi chờ khóa của PostgreSQL.

### Tiêu chí hoàn thành

- Không còn `Instant.now()` trong bất kỳ nhánh quyết định `PAYMENT_EXPIRED`, `CUSTOMER_CANCELLED` hay chấp nhận Payment của Order service.
- `PaymentExpiryScheduler` không tạo hoặc truyền `now`.
- Candidate query và quyết định sau khóa đều dựa trên PostgreSQL `clock_timestamp()`.
- `paymentDueAt` của Order mới và `expiresAt` của reservation được tạo từ cùng một database deadline.
- Quy tắc tại biên được giữ chính xác: bằng deadline là hết hạn.
- Payment callback, customer cancellation và scheduler đều đọc clock sau khi khóa Order và dùng một `decisionTime` duy nhất cho mỗi quyết định.
- Integration test chứng minh transaction chờ qua deadline không thể xác nhận Order quá hạn.
- Chính sách demo về event đến muộn vẫn giữ nguyên: không hồi tố theo `paidAt`, Order đã đóng không được mở lại và Payment đã nhận tiền đi vào `REQUIRES_REVIEW`.

## 5. Loại bỏ lần gọi Identity trùng lặp trong checkout

### Vấn đề

`ActiveAccountFilter` của Order service hiện chạy cho mọi request đã xác thực. Vì vậy một checkout mới đi qua hai lần gọi đồng bộ đến Identity:

```text
POST /api/v1/orders/checkout
→ ActiveAccountFilter gọi GET /internal/v1/users/{userId}/status
→ OrderService.checkout() gọi POST /internal/v1/users/current/checkout-context
```

Lần gọi `checkout-context` đã bao gồm cả hai kiểm tra cần thiết. `AddressServiceImpl.checkoutContext()` gọi `ensureActive(userId)` trước khi kiểm tra địa chỉ thuộc user và trả snapshot địa chỉ. Do đó lần gọi `/status` trước đó không bổ sung invariant nghiệp vụ cho checkout mới, nhưng làm tăng latency, tải lên Identity và thêm một điểm có thể timeout hoặc trả `503`.

Luồng hiện tại còn có một lần kiểm tra Identity gián tiếp khi Order gọi Catalog. `OrderService.checkout()` chuyển tiếp customer `Authorization` vào `CatalogClient.getCheckoutItems()`, đồng thời Feign interceptor thêm `X-Service-Authorization`. Tại Catalog, `JwtAuthenticationFilter` xác thực customer JWT và `ActiveAccountFilter` gọi Identity `/status` trước khi `InternalServiceTokenFilter` thay authentication bằng service identity:

```text
OrderService.checkout()
→ Catalog POST /internal/v1/skus/checkout-items
  → JwtAuthenticationFilter nhận customer JWT
  → Catalog ActiveAccountFilter gọi Identity /status
  → InternalServiceTokenFilter kiểm tra service JWT
```

Internal Catalog lookup chỉ cần service JWT có audience `catalog-service` và scope `catalog.checkout`. Order đã xác minh account cùng địa chỉ qua `checkout-context`, còn Catalog chỉ đọc dữ liệu SKU authoritative. Việc chuyển tiếp customer JWT làm Catalog kiểm tra Identity lần nữa, trái với internal REST contract và khiến checkout mới vẫn có thể gọi Identity hai lần ngay cả sau khi bỏ lần `/status` tại Order filter.

Không được chỉ cho `ActiveAccountFilter` bỏ qua checkout rồi giữ nguyên toàn bộ flow hiện tại. `OrderService.checkout()` đang tìm Order theo `(userId, idempotencyKey)` và trả replay trước khi gọi `checkout-context`. Nếu filter bị bỏ qua mà không bổ sung kiểm tra trong nhánh này, replay sẽ không kiểm tra trạng thái ACTIVE.

### Kết quả mong muốn

- Mỗi request checkout gọi Identity tối đa một lần.
- Checkout mới dùng `checkout-context` để kiểm tra ACTIVE, quyền sở hữu địa chỉ và lấy snapshot trong một lần gọi.
- Replay dùng `/status` để kiểm tra ACTIVE rồi trả Order đã tồn tại.
- Replay không kiểm tra lại địa chỉ hiện tại vì Order đã lưu shipping snapshot tại lần checkout đầu tiên.
- Việc sửa hoặc xóa địa chỉ sau khi Order được tạo không làm replay thất bại.
- Account đã bị khóa không thể checkout mới hoặc replay checkout.
- Các request Order/Cart đã xác thực khác vẫn được `ActiveAccountFilter` kiểm tra ACTIVE như hiện tại.
- Internal Catalog checkout chỉ nhận service JWT; không chuyển tiếp customer JWT và không gọi Identity.
- Customer JWT đơn lẻ không thể gọi `/internal/v1/skus/checkout-items`.
- OpenAPI mô tả đúng internal Catalog checkout chỉ yêu cầu `serviceJwt`, không khai báo `serviceJwt` và `userJwt` thành hai lựa chọn thay thế.

### Phương án sửa

#### 1. Bỏ qua đúng endpoint checkout trong `ActiveAccountFilter`

Thêm `shouldNotFilter()` vào `order-service/.../configs/ActiveAccountFilter.java` và chỉ bỏ qua đúng method/path của checkout:

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
  return "POST".equals(request.getMethod())
      && "/api/v1/orders/checkout".equals(request.getServletPath());
}
```

Không bỏ qua toàn bộ `/api/v1/orders/**` hoặc `/api/v1/cart/**`. Các endpoint xem đơn, hủy đơn và thao tác giỏ hàng vẫn cần policy ACTIVE chung của filter.

#### 2. Tách hai nhánh kiểm tra Identity trong `OrderService.checkout()`

Giữ bước tìm Order theo idempotency key trước khi lấy checkout context:

```text
Chuẩn hóa Idempotency-Key và tính request hash
→ tìm Order theo userId + Idempotency-Key
  → đã tồn tại: gọi /status một lần, kiểm tra request hash và trả Order cũ
  → chưa tồn tại: gọi checkout-context một lần rồi tiếp tục tạo Order
```

Flow dự kiến:

```java
Order existing =
    orderRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey).orElse(null);

if (existing != null) {
  identityStatusClient.requireActive(userId);
  return replayOrReject(existing, requestHash);
}

IdentityCheckoutContext checkoutContext =
    identityClient.getCheckoutContext(
        new IdentityCheckoutContextRequest(checkoutRequest.addressId()),
        authorization);
```

Sau khi lấy Cart, lần kiểm tra thứ hai đối với cùng idempotency key vẫn phải được giữ để xử lý hai request checkout chạy đồng thời. Nếu Order xuất hiện ở lần kiểm tra thứ hai thì request hiện tại đã gọi `checkout-context` đúng một lần và có thể trả replay; không gọi thêm `/status`.

#### 3. Tạo client kiểm tra status với đúng service scope

Client gọi `/status` phải phát service JWT với scope `identity.status`. Không dùng cấu hình hiện tại của `IdentityClient`, vì interceptor của client đó phát scope `identity.checkout`.

Có thể tạo một Feign client riêng với `contextId` riêng, ví dụ:

```java
@FeignClient(
    name = "identity-service",
    contextId = "identityStatusClient",
    configuration = IdentityStatusClientConfiguration.class)
public interface IdentityStatusClient {
  @GetMapping("/internal/v1/users/{userId}/status")
  String getStatus(@PathVariable Long userId);
}
```

`IdentityStatusClientConfiguration` dùng cùng timeout và `Retryer.NEVER_RETRY`, nhưng phát `X-Service-Authorization` với audience `identity-service` và scope `identity.status`.

`OrderService` ánh xạ kết quả như sau:

- HTTP 200 và body `ACTIVE`: tiếp tục trả replay.
- HTTP 200 nhưng status khác `ACTIVE`, hoặc Identity trả 403: `ACCOUNT_NOT_ACTIVE`.
- Timeout, lỗi kết nối hoặc lỗi Identity không thuộc trường hợp account bị khóa: `IDENTITY_UNAVAILABLE`.

Không cần sửa `AddressServiceImpl.checkoutContext()`. Phương thức này đã thực hiện đúng contract gộp ACTIVE và địa chỉ cho checkout mới:

```java
User user = ensureActive(userId);
Address address = getOwned(userId, addressId);
```

#### 4. Loại bỏ customer JWT khỏi internal Catalog lookup

Sửa `CatalogClient.getCheckoutItems()` để không còn nhận hoặc phát header customer `Authorization`:

```java
@PostMapping("/internal/v1/skus/checkout-items")
List<CatalogCheckoutItem> getCheckoutItems(
    @RequestBody CatalogCheckoutRequest request);
```

`OrderService.checkout()` chỉ truyền request SKU. `CatalogClientConfiguration` tiếp tục phát `X-Service-Authorization` với audience `catalog-service` và scope `catalog.checkout`; đây là credential duy nhất của internal lookup.

Cho `catalog-service/.../configs/ActiveAccountFilter.java` bỏ qua internal endpoint để một customer JWT thừa hoặc header không liên quan không thể kích hoạt Identity lookup:

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
  return "POST".equals(request.getMethod())
      && "/internal/v1/skus/checkout-items".equals(request.getServletPath());
}
```

`InternalServiceTokenFilter` và `SecurityConfig` vẫn bắt buộc service JWT hợp lệ cùng `SCOPE_catalog.checkout`. Không mở quyền cho user JWT trên `/internal/**` và không đổi endpoint này thành public API.

Sửa OpenAPI của endpoint thành một security requirement duy nhất:

```yaml
security: [{serviceJwt: []}]
```

Không dùng `security: [{serviceJwt: []}, {userJwt: []}]`, vì hai object trong mảng mang nghĩa OR và sẽ mô tả sai rằng user JWT đơn lẻ được chấp nhận.

#### 5. Giữ nguyên snapshot và semantics của idempotency replay

Replay phải trả shipping snapshot đã lưu trong Order, không đọc lại địa chỉ nguồn. Điều này bảo đảm response của cùng một idempotency key không thay đổi khi user sửa hoặc xóa địa chỉ sau lần checkout thành công.

Request dùng lại cùng idempotency key nhưng có request hash khác vẫn trả `IDEMPOTENCY_KEY_CONFLICT` và không tạo thêm Order, reservation hoặc outbox event. Việc kiểm tra ACTIVE vẫn diễn ra một lần cho request replay theo policy áp dụng cho protected request.

### Kiểm thử cần bổ sung

#### `ActiveAccountFilterTest`

1. `POST /api/v1/orders/checkout` được bỏ qua và không gọi `/status`.
2. Một endpoint đã xác thực khác, ví dụ `GET /api/v1/orders/{id}`, vẫn gọi `/status`.
3. Endpoint khác vẫn fail closed với `IDENTITY_UNAVAILABLE` khi Identity không phản hồi.

#### `OrderServiceTest`

1. Checkout mới:
   - Gọi `checkout-context` đúng một lần.
   - Không gọi status client.
   - Lưu đúng shipping snapshot vào Order.
2. Checkout mới với account `LOCKED`:
   - Trả `ACCOUNT_NOT_ACTIVE`.
   - Không đọc Cart để checkout, không reserve inventory và không tạo Order.
3. Replay hợp lệ:
   - Gọi `/status` đúng một lần.
   - Không gọi `checkout-context`.
   - Không reserve inventory, không xóa Cart và không tạo outbox mới.
   - Trả Order cũ với replay flag.
4. Replay sau khi địa chỉ nguồn đã bị sửa hoặc xóa:
   - Vẫn trả Order cũ và shipping snapshot cũ.
   - Không truy vấn lại địa chỉ.
5. Replay khi account đã bị khóa:
   - Trả `ACCOUNT_NOT_ACTIVE`.
   - Không trả dữ liệu Order.
6. Cùng idempotency key nhưng request hash khác:
   - Trả `IDEMPOTENCY_KEY_CONFLICT`.
   - Không tạo hiệu ứng nghiệp vụ mới.
7. Hai request checkout đồng thời cùng idempotency key:
   - Chỉ một Order và một reservation được tạo.
   - Request thua race tìm thấy Order ở lần kiểm tra thứ hai và trả replay.
   - Mỗi request gọi Identity tối đa một lần.
8. Identity timeout hoặc unavailable ở cả nhánh checkout mới và replay:
   - Trả `IDENTITY_UNAVAILABLE`.
   - Không tạo thêm Order hoặc tác động inventory.

#### Catalog client và security test

1. Order gọi `CatalogClient.getCheckoutItems()` mà không chuyển tiếp customer `Authorization`.
2. Feign interceptor vẫn phát `X-Service-Authorization` đúng audience `catalog-service` và scope `catalog.checkout`.
3. `POST /internal/v1/skus/checkout-items` với service JWT hợp lệ được xử lý mà không gọi Identity.
4. Customer JWT đơn lẻ bị từ chối; không thể thay thế service JWT.
5. Service JWT thiếu scope, sai audience hoặc sai caller bị từ chối theo policy hiện tại.
6. Nếu request internal chứa thêm customer `Authorization`, `ActiveAccountFilter` vẫn bỏ qua endpoint và không gọi Identity; quyền cuối cùng vẫn do service JWT quyết định.

#### OpenAPI contract test

1. Internal Catalog checkout chỉ khai báo `serviceJwt`.
2. Không có security alternative cho `userJwt` tại endpoint này.

### Tiêu chí hoàn thành

- Checkout mới chỉ phát một request Identity đến `checkout-context`; không phát `/status` từ filter.
- Replay chỉ phát một request Identity đến `/status`; không phát `checkout-context`.
- Internal Catalog lookup chỉ dùng service JWT và không phát request Identity.
- Checkout mới và replay đều từ chối account không còn ACTIVE.
- Replay tiếp tục trả Order đã tồn tại khi địa chỉ nguồn đã được sửa hoặc xóa.
- Mọi endpoint Order/Cart đã xác thực ngoài checkout vẫn giữ policy ACTIVE fail-closed hiện tại.
- Test chứng minh toàn bộ call chain của mỗi nhánh gọi Identity tối đa một lần và không tạo thêm Order, reservation hoặc outbox khi replay.

## 6. Chuẩn hóa Kafka event contract và correlation ID

### Quyết định kiến trúc

Batch này sửa implementation về đúng Kafka event contract v1 trước khi hệ thống được triển khai. Không giữ định dạng event flat hiện tại và không thêm nhánh tương thích event cũ, vì chưa có message production hoặc Outbox production cần replay.

Các định danh có trách nhiệm riêng:

- `correlationId` truy vết một request đầu vào và chuỗi xử lý trực tiếp phát sinh từ request đó.
- `orderId` liên kết toàn bộ vòng đời nghiệp vụ của một Order và tiếp tục là Kafka partition key cho Order/Payment topic.
- `eventId` định danh một lần phát event và là khóa chống xử lý trùng.
- `aggregateId` định danh aggregate phát event: Order dùng `orderId`, Payment dùng `paymentId`; không dùng máy móc làm Kafka key.

Ví dụ checkout và callback thanh toán là hai request độc lập:

```text
Checkout correlation=A
  → OrderCreated(A)
  → Payment PENDING(A)
  → Notification(A)

Payment callback correlation=B
  → PaymentSucceeded(B)
  → OrderConfirmed(B)
  → Notification(B)
```

Hai chuỗi có correlation khác nhau nhưng cùng `orderId`. Không thêm một workflow ID khác trong batch này.

Centralized logging, Grafana, Loki, Alloy/Promtail và tracing backend không thuộc phạm vi. Ứng dụng chỉ chuẩn hóa correlation context và log console để hạ tầng quan sát có thể thu thập sau này.

### Vấn đề

`OutboxEventService.payload()` và `PaymentEventService.enqueuePaymentResult()` hiện trộn envelope với dữ liệu nghiệp vụ tại JSON root. Ví dụ Order event đang có dạng:

```json
{
  "eventId": "...",
  "eventType": "OrderCreated",
  "aggregateType": "Order",
  "orderId": 10,
  "userId": 5,
  "totalAmount": 100000
}
```

Contract v1 yêu cầu envelope cố định và business payload nằm trong object `payload`:

```json
{
  "eventId": "...",
  "eventType": "OrderCreated",
  "schemaVersion": 1,
  "aggregateType": "ORDER",
  "aggregateId": 10,
  "aggregateVersion": 1,
  "occurredAt": "2026-10-17T10:00:00Z",
  "correlationId": "...",
  "payload": {
    "orderId": 10,
    "orderNumber": "ORD-000010",
    "userId": 5,
    "totalAmount": 100000,
    "currency": "VND",
    "paymentDueAt": "2026-10-17T10:15:00Z"
  }
}
```

Payload chung của Order còn thiếu các field bắt buộc:

- `OrderCreated.orderNumber`;
- `OrderConfirmed.confirmedAt`;
- `OrderCancelled.cancelledAt`;
- `OrderShipping.shippingAt`;
- `OrderCompleted.completedAt`.

`shippingAt` và `completedAt` đã tồn tại trong `Order` nhưng chưa được serialize. `orderNumber`, `confirmedAt` và `cancelledAt` chưa tồn tại trong entity/schema nên không thể sửa đúng chỉ bằng cách thêm entry vào Map.

Payload chung hiện còn phát dữ liệu không thuộc từng event như `items`, `paymentId=0` và `reason=""`. Điều này che giấu field thiếu, làm consumer phải hiểu một superset không được contract quy định và khiến giá trị giả có thể bị diễn giải thành dữ liệu nghiệp vụ.

Payment event cũng chưa đúng contract:

- business field nằm ở root;
- `aggregateType` dùng `Payment` thay vì `PAYMENT`;
- `PaymentFailed` dùng `failureReason` thay vì `reason`;
- `PaymentFailed` chưa có `failedAt`;
- Payment publisher lấy `orderId` ở root để tạo partition key.

HTTP hiện không có correlation propagation. Gateway chỉ dùng request ID cục bộ cho một số error response; các service, Feign client, Java HTTP client, Outbox và Kafka consumer không chia sẻ cùng một correlation ID.

### Kết quả mong muốn

- Mọi Order/Payment event có một envelope v1 duy nhất và business payload nằm trong `payload`.
- Producer chỉ phát field được contract quy định cho từng event; không phát items/địa chỉ hoặc giá trị giả.
- Order lưu bền vững đầy đủ `orderNumber`, `confirmedAt`, `cancelledAt`, `shippingAt` và `completedAt`.
- Correlation ID được tạo ở HTTP entry point, truyền qua HTTP nội bộ, lưu cùng Outbox và tiếp tục qua chuỗi Kafka do request đó gây ra.
- Publisher retry giữ nguyên `eventId`, `correlationId` và serialized event.
- Kafka key của cả Order và Payment event vẫn là chuỗi `orderId`.
- Consumer validate thiếu/sai kiểu như contract error; không biến field thiếu thành `0`, chuỗi rỗng hoặc nghiệp vụ hợp lệ.
- Không có code dual-read flat/nested, backfill JSON Outbox cũ hoặc schema version 2.

### Phương án sửa

#### 1. Tạo kiểu envelope và payload rõ ràng

Tạo typed envelope dùng chung về cấu trúc trong Order và Payment service:

```java
public record EventEnvelope<T>(
    String eventId,
    String eventType,
    int schemaVersion,
    String aggregateType,
    Long aggregateId,
    Long aggregateVersion,
    Instant occurredAt,
    String correlationId,
    T payload) {}
```

Không dùng `Map<String, Object>` cho business event mới. Tạo payload record riêng:

- `OrderCreatedPayload`;
- `OrderConfirmedPayload`;
- `OrderCancelledPayload`;
- `OrderShippingPayload`;
- `OrderCompletedPayload`;
- `OrderConfirmationRejectedPayload`;
- `PaymentSucceededPayload`;
- `PaymentFailedPayload`.

`outbox_events.payload` tiếp tục lưu toàn bộ serialized `EventEnvelope`. Tên cột này không đồng nghĩa với field `payload` bên trong JSON; có thể đổi tên biến Java cục bộ thành `eventJson` để tránh nhầm nhưng không cần đổi tên cột.

Envelope bắt buộc:

- `eventId`: UUID không rỗng;
- `eventType`: loại được hỗ trợ;
- `schemaVersion=1`;
- `aggregateType=ORDER|PAYMENT`;
- `aggregateId > 0`;
- `aggregateVersion >= 0`;
- `occurredAt`: ISO-8601 Instant hợp lệ;
- `correlationId`: không rỗng, tối đa 128 ký tự;
- `payload`: object không null.

#### 2. Bổ sung Order number và lifecycle timestamps

Thêm migration và mapping entity cho:

- `orders.order_number VARCHAR(40) UNIQUE NOT NULL`;
- `orders.confirmed_at TIMESTAMPTZ`;
- `orders.cancelled_at TIMESTAMPTZ`.

`orderNumber` được sinh từ ID do database cấp, ví dụ `ORD-000001`, không dùng `MAX(id)+1`. Dùng cơ chế database-generated value/trigger dựa trên identity ID và đọc lại giá trị vào entity sau insert để vẫn giữ unique + not-null ngay tại database.

Các state transition nhận cùng một decision time đã được chuẩn hóa ở mục 4:

- `confirm(paymentId, decisionTime)` gán `confirmedAt` đúng một lần;
- mọi đường chuyển sang CANCELLED gọi chung một thao tác gán `cancelledAt` và `cancellationReason`;
- `ship(decisionTime)` gán `shippingAt`;
- `complete(decisionTime)` gán `completedAt`.

Timestamp payload lấy từ state đã lưu, không gọi `Instant.now()` lần nữa khi serialize. `occurredAt` là thời điểm tạo event và không thay thế lifecycle timestamp.

Bổ sung `orderNumber` vào REST `OrderResponse` để đồng bộ với `REST_API_CONTRACT.md`.

#### 3. Phát đúng payload cho từng Order event

Payload bắt buộc:

| Event | Payload |
|---|---|
| OrderCreated | orderId, userId, totalAmount, currency, paymentDueAt, orderNumber |
| OrderConfirmed | orderId, userId, paymentId, totalAmount, currency, confirmedAt |
| OrderCancelled | orderId, userId, reason, cancelledAt, paymentDueAt |
| OrderShipping | orderId, userId, shippingAt |
| OrderCompleted | orderId, userId, completedAt, totalAmount, currency |
| OrderConfirmationRejected | orderId, paymentId, reason, currentOrderStatus, rejectedAt |

`OutboxEventService` tạo đúng payload record theo method enqueue tương ứng. Xóa payload superset và không đưa `items` hoặc shipping address vào Kafka Order event.

#### 4. Phát đúng payload cho từng Payment event

Payload bắt buộc:

| Event | Payload |
|---|---|
| PaymentSucceeded | paymentId, orderId, userId, amount, currency, provider, providerTransactionId, paidAt |
| PaymentFailed | paymentId, orderId, userId, amount, currency, failedAt, reason |

`PaymentEventService` tách hai đường tạo payload thay vì dùng một Map chung. Không dùng `paidAt` cho PaymentFailed và không phát `failureReason` thay cho field `reason` của contract.

#### 5. Tạo và truyền correlation ID qua HTTP

Chuẩn hóa header `X-Correlation-ID`.

Gateway thêm `CorrelationIdGlobalFilter` chạy trước authentication:

1. Nhận header hợp lệ hoặc tạo UUID mới.
2. Gắn header vào request route xuống service.
3. Gắn cùng header vào response.
4. Đưa correlation vào logging context phù hợp với WebFlux.

Identity, Catalog, Order, Payment và Notification thêm `CorrelationIdFilter`:

1. Đọc header hoặc tạo UUID fallback khi service được gọi trực tiếp.
2. Lưu vào request attribute và MDC.
3. Trả cùng ID trong response header.
4. Xóa MDC trong `finally`.

Các error/success response đang dùng request ID cục bộ phải dùng correlation ID để client có thể cung cấp đúng mã khi báo lỗi mà không đổi tên field response nếu REST contract đang gọi nó là `traceId`.

Feign interceptor Order → Identity, Order → Catalog và mọi Java `HttpClient` nội bộ phải truyền `X-Correlation-ID` cùng service token. Không đọc header client trực tiếp ở domain service; dùng một `CorrelationContext` có API thống nhất để lấy ID hiện tại.

#### 6. Lưu correlation ID cùng Outbox

Thêm `correlation_id VARCHAR(128) NOT NULL` vào `outbox_events` của Order và Payment service. Cập nhật cả hai `OutboxEvent` entity và constructor.

Factory tạo event phải dùng một correlation ID cho cả:

- cột `outbox_events.correlation_id`;
- field `correlationId` trong serialized envelope.

Correlation được chụp tại thời điểm tạo Outbox trong transaction nghiệp vụ. Không lấy correlation từ MDC tại `OutboxPublisher`, vì publisher chạy sau trên scheduler/thread khác.

Không thêm correlation vào `orders`, `payments` hoặc `processed_events`. Những bảng này đã có business/event identity riêng; correlation thuộc lần thực thi tạo event.

Vì chưa có dữ liệu production, migration không backfill hoặc chuyển đổi JSON payload cũ. Database phát triển được reset trước khi chạy đầy đủ migration mới.

#### 7. Khôi phục correlation trong Kafka consumer

Mỗi consumer:

1. Parse root envelope.
2. Validate envelope trước khi diễn giải nghiệp vụ.
3. Đọc business data từ `root.path("payload")`.
4. Đặt `correlationId` vào MDC/`CorrelationContext`.
5. Xử lý `processed_events` và nghiệp vụ.
6. Event phát sinh trong transaction lấy cùng correlation ID.
7. Xóa context trong `finally`, kể cả khi ném lỗi để retry/DLT.

Không dùng fallback đọc business field ở root. Message thiếu `payload` hoặc `correlationId` là contract error.

#### 8. Giữ Kafka partition key theo Order

Order publisher tiếp tục gửi key `String.valueOf(orderId)`.

Payment publisher phải đọc key từ `envelope.payload.orderId`, không đọc `orderId` ở root và không dùng `aggregateId=paymentId` làm fallback cho một Payment event hợp lệ.

`correlationId` không tham gia chọn partition.

#### 9. Xử lý các nguồn không có HTTP request cha

- Payment callback là HTTP entry point mới và có correlation riêng của callback.
- Expiry scheduler tạo correlation mới cho từng Order được xử lý để một lần hủy và các event trực tiếp sau đó dùng cùng ID.
- Kafka consumer dùng correlation của event đầu vào.
- Outbox publisher dùng correlation đã lưu trong row/envelope để log kết quả publish.

#### 10. Cập nhật tài liệu

Cập nhật `KAFKA_EVENT_CONTRACT.md`:

- topic Order là `ecommerce.order.events`;
- định nghĩa rõ correlation theo request/causal execution;
- Kafka key vẫn là `orderId`;
- envelope chứa nested `payload`;
- liệt kê validation và field bắt buộc theo event.

Cập nhật `DATABASE_DESIGN.md` để correlation column và lifecycle fields thống nhất với migration thực tế.

Xóa các quyết định “không bổ sung correlationId” tại dòng nguyên tắc, mục 2.2 và Batch P3-A của `docs/remaining-implementation-plan.md`.

### Kiểm thử cần bổ sung

#### Contract test producer

1. Mỗi Order/Payment event serialize đủ envelope và `payload` là object.
2. Không có business field như `orderId`, `paymentId`, `items` ở root.
3. `aggregateType` đúng `ORDER` hoặc `PAYMENT`.
4. Mỗi payload có đúng field bắt buộc và không có giá trị giả.
5. Timestamp lifecycle trong payload bằng timestamp đã lưu trong aggregate.
6. `PaymentFailed` có `failedAt` và `reason`, không có `paidAt` hoặc `failureReason`.

#### Order model/repository test

1. Order number duy nhất, đúng định dạng và được sinh từ ID đã cấp.
2. Confirm gán `confirmedAt` đúng một lần.
3. Mọi reason hủy hợp lệ đều gán `cancelledAt`.
4. Ship/complete giữ timestamp khi gọi idempotent.
5. Constraint trạng thái và lifecycle timestamp không cho lưu tổ hợp sai.

#### HTTP correlation test

1. Gateway tạo ID khi header thiếu và trả cùng ID trong response.
2. Gateway giữ header hợp lệ do caller gửi.
3. Service direct-call tạo fallback.
4. Feign/HTTP client truyền đúng ID sang Identity/Catalog.
5. MDC/context được xóa sau request thành công và lỗi.

#### Outbox/Kafka test

1. Outbox column và envelope lưu cùng correlation ID.
2. Publisher retry giữ nguyên event ID, correlation ID và serialized event.
3. Consumer đọc business data chỉ từ nested payload.
4. PaymentSucceeded(B) tạo OrderConfirmed(B).
5. Kafka key của Order và Payment event cùng là `orderId`.
6. Thiếu correlation, payload hoặc field bắt buộc bị phân loại contract error và không tạo mutation nghiệp vụ.
7. Context được xóa sau consumer thành công và lỗi.

#### Event-flow integration test

1. Checkout correlation A đi qua Gateway/Order, OrderCreated, Payment PENDING và notification tạo đơn.
2. Callback correlation B đi qua PaymentSucceeded, OrderConfirmed và notification xác nhận.
3. Cả hai flow có correlation khác nhau nhưng cùng `orderId`.
4. Replay Outbox giữ nguyên identity và không tạo hiệu ứng nghiệp vụ lần hai.

### Tiêu chí hoàn thành

- Tất cả Order/Payment producer và consumer chỉ dùng envelope v1 có nested payload.
- Không còn producer tạo Kafka business event bằng payload Map superset.
- Các field bắt buộc của Order và Payment event khớp `KAFKA_EVENT_CONTRACT.md`.
- Order lưu đầy đủ order number và lifecycle timestamps; REST trả order number.
- Correlation ID đi xuyên HTTP nội bộ, Outbox và chuỗi Kafka trực tiếp; request khác có correlation riêng.
- Order ID tiếp tục là khóa truy vết toàn vòng đời và Kafka partition key.
- Không có dual-format compatibility hoặc migration JSON event cũ.
- Test service và event-flow liên quan đều pass.
- Không triển khai centralized logging hoặc tracing backend trong batch này.
