# Kế hoạch hoàn thiện hệ thống sau đợt audit

Ngày lập: 2026-10-13
Phạm vi đối chiếu: requirement và tài liệu thiết kế tại thư mục `Thesis`, mã nguồn hiện tại, test tự động và `docs/requirement-audit-2026-10-13.md`.

## 1. Mục tiêu và nguyên tắc triển khai

Kế hoạch này chỉ bao gồm các hạng mục còn thiếu, chưa khép kín hoặc chưa có đủ bằng chứng nghiệm thu. Những phần đã có như payment callback B3, order lifecycle, notification retry, catalog cache, gateway rate limit và outbox `SKIP LOCKED` vẫn phải được kiểm tra hồi quy khi thay đổi các luồng liên quan.

Nguyên tắc thực hiện:

1. PostgreSQL là nguồn dữ liệu chuẩn; Redis và Elasticsearch chỉ là hạ tầng hỗ trợ.
2. Mọi thay đổi tồn kho phải có ledger, idempotency và transaction rõ ràng.
3. Cùng một order dùng `orderId` làm Kafka partition key; `eventId` dùng nhận diện lần phát cụ thể và chống xử lý trùng.
4. Không bổ sung lại `correlationId` khi chưa có use case và consumer thực tế.
5. Migration phải tương thích dữ liệu đang có; tránh migration phá hủy hoặc yêu cầu xóa database.
6. Mỗi batch phải chạy test của service bị sửa và test integration liên quan trước khi chuyển batch.
7. Không tự động commit. Các mốc commit bên dưới chỉ là ranh giới đề xuất để người dùng duyệt và yêu cầu commit.

## 2. Thứ tự ưu tiên và phụ thuộc

| Thứ tự | Mã | Hạng mục | Mức độ | Phụ thuộc |
|---|---|---|---|---|
| 0 | P0 | Chốt baseline và truy vết requirement | Bắt buộc | Không |
| 1 | P1 | Sửa transaction, lock order và reservation model | Critical | P0 |
| 2 | P2 | Inventory ledger, idempotency và API quản trị | Critical | P1 |
| 3 | P3 | Idempotent consumer và outbox recovery | Cao | P1 |
| 4 | P4 | Elasticsearch, catalog indexing và reindex bền vững | Cao | P3 |
| 5 | P5 | Service JWT cho internal API | Cao / M2 | P0; nên xong trước acceptance |
| 6 | P6 | Kiểm tra trạng thái ACTIVE theo thời gian thực | Cao | P5 hoặc cơ chế introspection tương đương |
| 7 | P7 | Hoàn thiện payment admin/report/attempt | Trung bình | P1-P3 |
| 8 | P8 | OpenAPI, Postman, load, chaos và Compose smoke | Nghiệm thu | P1-P7 |

Không nên làm P4 trước P3 vì catalog indexer cần cơ chế xử lý event trùng và outbox recovery. Không nên làm load/chaos test trước khi P1 hoàn tất vì kết quả concurrency khi đó không đại diện cho thiết kế cuối.

## 3. P0 — Chốt baseline và ma trận truy vết

### Mục tiêu

Tạo một điểm xuất phát có thể tái lập trước khi sửa các cấu trúc transaction và schema lớn. Worktree hiện có nhiều thay đổi chưa commit từ đợt audit, nên cần xác nhận toàn bộ thay đổi đó là một baseline hợp lệ.

### Công việc

1. Đối chiếu từng file thay đổi với `docs/requirement-audit-2026-10-13.md`; loại file sinh ra, log và artifact tạm khỏi phạm vi source.
2. Chạy toàn bộ unit test của từng service và integration test hiện có.
3. Chạy `docker compose config` để phát hiện biến môi trường hoặc service dependency thiếu.
4. Tạo ma trận requirement gồm các trạng thái:
   - `DONE`: có code và test chứng minh;
   - `PARTIAL`: có code nhưng thiếu một nhánh hoặc test;
   - `MISSING`: chưa có code;
   - `ACCEPTANCE`: cần môi trường chạy để nghiệm thu.
5. Gắn requirement ID vào tên test hoặc `@DisplayName` ở các batch kế tiếp để truy vết trực tiếp.

### Kiểm thử và điều kiện hoàn tất

- Toàn bộ test hiện có xanh trên baseline.
- Không có secret, token hoặc file môi trường thật nằm trong diff.
- Mỗi mục còn thiếu trong audit được ánh xạ đúng một phase bên dưới.
- Có danh sách migration hiện tại theo thứ tự của từng service để tránh trùng version.

### Ranh giới commit đề xuất

`chore: establish audited implementation baseline`

Chỉ commit khi người dùng yêu cầu.

## 4. P1 — Transaction boundary, lock order và reservation model

### Vấn đề cần giải quyết

Luồng hiện tại có khả năng khóa row của order/inventory trong transaction trước rồi mới lấy Redis lock SKU. `InventoryService` có thể nhả Redis lock trước khi outer transaction của `OrderService` commit vì các method cùng tham gia một transaction. Điều này trái với thứ tự khóa trong thiết kế và có thể tạo race khi checkout, payment hoặc expiry chạy đồng thời.

### Thiết kế đích

Thứ tự bắt buộc cho mọi lệnh tác động tồn kho:

1. Validate dữ liệu không cần lock và hoàn thành các network call bắt buộc.
2. Chuẩn hóa danh sách SKU, sắp xếp tăng dần và loại trùng.
3. Lấy Redis distributed lock theo thứ tự SKU.
4. Mở database transaction mới.
5. Khóa các row cần thiết bằng cùng một thứ tự ổn định.
6. Kiểm tra lại trạng thái bên trong transaction.
7. Ghi order, reservation, inventory, history và outbox trong cùng transaction.
8. Commit hoặc rollback transaction.
9. Chỉ sau đó mới nhả Redis lock.

### Batch P1-A — Bổ sung mô hình reservation

#### Schema

Tạo migration inventory/order tùy ownership cuối cùng, ưu tiên inventory service sở hữu reservation:

- `reservations`
  - `id` UUID/ULID;
  - `order_id` unique;
  - `status`: `ACTIVE`, `CONFIRMED`, `RELEASED`, `EXPIRED`;
  - `expires_at`;
  - `created_at`, `updated_at`, `version`.
- `reservation_items`
  - `reservation_id`, `sku_id`;
  - `quantity` dương;
  - unique `(reservation_id, sku_id)`.
- Index `(status, expires_at)` phục vụ worker expiry.

Backfill dữ liệu order `PENDING_PAYMENT` hiện có nếu môi trường có dữ liệu; script phải idempotent và phải báo các order không thể suy ra reservation.

#### Code

- Tạo aggregate/repository cho reservation.
- Thay các cập nhật `reserved` rời rạc bằng các operation:
  - `reserve(orderId, items, expiresAt)`;
  - `confirm(orderId, paymentEventId)`;
  - `release(orderId, reason, commandId)`;
  - `expire(orderId, commandId)`.
- Mỗi operation kiểm tra state transition hợp lệ và trả kết quả idempotent nếu state đích đã đạt.
- Không cho reservation `RELEASED` hoặc `EXPIRED` chuyển thành `CONFIRMED`.

#### Test

- Reserve mới, reserve lặp cùng order, thiếu tồn kho, nhiều SKU.
- Confirm làm `reserved -= quantity` và `on_hand -= quantity` đúng một lần.
- Release/expire chỉ giảm `reserved`.
- Payment đến sau expiry không giảm `on_hand`.
- Hai request reserve cùng SKU không gây oversell.

### Batch P1-B — Tách lock orchestration khỏi transaction

#### Code

- Tạo một application-level executor, ví dụ `InventoryCommandExecutor`, chịu trách nhiệm lấy/nhả distributed lock.
- Đưa phần database mutation sang bean riêng, ví dụ `InventoryTransactionalOperations`, với `@Transactional` tại method public.
- Tránh self-invocation làm mất proxy transaction.
- Nếu cần đảm bảo transaction đã kết thúc trước unlock, dùng `TransactionTemplate.execute(...)`; lock bao ngoài lời gọi `execute`.
- Cấu hình lock wait time và lease time; ghi metric khi timeout hoặc mất lock.
- Không gọi HTTP/Kafka trong vùng đang giữ database row lock.

#### Luồng phải chuyển đổi

1. Checkout/reserve.
2. Hủy order/release.
3. Worker expiry/release.
4. Payment success/confirm stock.
5. Payment failure/reject hoặc review mà không chốt stock.

#### Test concurrency

- Checkout và checkout cùng SKU.
- Payment success và cancel cùng order.
- Payment success và expiry cùng order.
- Hai payment success khác `eventId` cho cùng payment/order.
- Redis lock timeout: không mở DB transaction ghi dữ liệu.
- Exception giữa transaction: rollback đầy đủ rồi lock mới được nhả.

### Batch P1-C — Chốt state machine xuyên service

Định nghĩa rõ bảng transition dùng chung trong code/test:

| Trạng thái nguồn | Sự kiện | Trạng thái đích | Tồn kho |
|---|---|---|---|
| `PENDING_PAYMENT` | Payment hợp lệ, đúng hạn | `CONFIRMED` | Confirm reservation |
| `PENDING_PAYMENT` | Payment sai dữ liệu | `PAYMENT_REVIEW` hoặc `REJECTED` theo policy | Không chốt |
| `PENDING_PAYMENT` | Payment quá hạn | `PAYMENT_REVIEW`/`REJECTED` | Không chốt; reservation đã/được release |
| `PENDING_PAYMENT` | Cancel | `CANCELLED` | Release reservation |
| `CONFIRMED` | Ship | `SHIPPING` | Không đổi |
| `SHIPPING` | Complete | `COMPLETED` | Không đổi |

Order history, payment history, reservation state và outbox event phải được ghi atomically trong service sở hữu chúng. Cross-service consistency dùng event và operation idempotent, không cố tạo distributed transaction.

### Điều kiện hoàn tất P1

- Code không còn đường đi lấy DB lock trước Redis SKU lock.
- Unlock luôn xảy ra sau commit/rollback.
- Có reservation persisted cho mọi order đang chờ thanh toán.
- Các test race quan trọng chạy lặp nhiều lần không oversell và không âm `reserved`/`onHand`.

### Ranh giới commit đề xuất

1. `feat(inventory): persist reservation lifecycle`
2. `fix(inventory): enforce lock-before-transaction ordering`
3. `test(order): cover payment cancel and expiry races`

## 5. P2 — Inventory ledger, idempotency và API quản trị

### Batch P2-A — Stock movement ledger

#### Schema

Tạo `stock_movements`:

- `id`, `sku_id`, `type`;
- `quantity_delta`, `on_hand_before`, `on_hand_after`;
- `reserved_before`, `reserved_after`;
- `reference_type`, `reference_id`;
- `idempotency_key` hoặc `command_id`;
- `reason`, `actor_type`, `actor_id`;
- `created_at`;
- unique constraint phù hợp, ví dụ `(operation, idempotency_key)` hoặc `(reference_type, reference_id, type, sku_id)`.

Movement type tối thiểu: `INITIALIZE`, `RECEIPT`, `ADJUSTMENT`, `RESERVE`, `RELEASE`, `CONFIRM`, `EXPIRE`.

#### Quy tắc

- Mỗi mutation tồn kho bắt buộc sinh movement trong cùng transaction.
- Ledger là append-only; sửa sai bằng movement bù.
- Lưu before/after để điều tra nhưng invariant vẫn dựa trên inventory row hiện tại.
- Ràng buộc: `on_hand >= 0`, `reserved >= 0`, `reserved <= on_hand` nếu mô hình hiện tại định nghĩa reserved nằm trong on hand.

### Batch P2-B — Idempotency-Key cho write API

Áp dụng cho initialize, receipt và adjustment:

- Header `Idempotency-Key` bắt buộc.
- Persist request fingerprint, response status/body và trạng thái xử lý.
- Cùng key + cùng payload trả response đã lưu.
- Cùng key + payload khác trả `409 IDEMPOTENCY_KEY_REUSED`.
- Concurrent requests cùng key chỉ một request được mutation; request còn lại chờ hoặc nhận kết quả đã hoàn thành.
- Quy định retention và cleanup job; không xóa key khi movement còn cần audit.

### Batch P2-C — API inventory admin

Hoàn thiện theo contract:

- Danh sách inventory có phân trang và filter SKU/status.
- Initialize stock.
- Goods receipt.
- Adjustment với reason bắt buộc.
- Danh sách stock movements theo SKU/type/date/reference.
- Response không lộ internal exception hoặc secret.

### Test

- Permission admin và unauthorized/forbidden.
- Idempotency replay và conflicting payload.
- Concurrent receipt/adjustment.
- Invalid negative stock và reserved invariant.
- Pagination/filter/date boundary.
- Movement ghi đúng cho reserve/confirm/release từ P1.

### Điều kiện hoàn tất P2

- Không có đường mutation inventory nào bỏ qua ledger.
- Tất cả admin write endpoint có idempotency.
- Có thể tái dựng và giải thích mỗi thay đổi tồn kho bằng movement history.

## 6. P3 — Idempotent consumer và outbox recovery

### Batch P3-A — Chuẩn hóa event envelope tối thiểu

Envelope giữ các trường thực sự được dùng:

- `eventId`;
- `eventType`;
- `aggregateType`;
- `aggregateId`;
- `occurredAt`;
- `schemaVersion`;
- `payload`.

Quy ước:

- `aggregateId = orderId` cho chuỗi order/payment/notification liên quan order.
- Kafka key = `orderId` để cùng order vào cùng partition.
- `eventId` duy nhất cho một lần phát nghiệp vụ; retry publisher giữ nguyên `eventId`.
- Không thêm `correlationId` vào lúc này.

### Batch P3-B — Processed event registry

Mỗi consumer có bảng `processed_events`:

- `consumer_name`, `event_id`, `event_type`, `processed_at`;
- unique `(consumer_name, event_id)`.

Xử lý message trong một database transaction:

1. Insert marker hoặc claim event.
2. Nếu unique conflict, acknowledge như duplicate.
3. Thực hiện mutation nghiệp vụ và ghi outbox.
4. Commit marker cùng mutation.
5. Chỉ acknowledge Kafka sau commit.

Không đánh dấu processed trước mutation ở transaction khác. Business uniqueness như `paymentId` vẫn giữ vì bảo vệ một invariant khác với duplicate `eventId`.

### Batch P3-C — Outbox claim, retry và dead-letter policy

`SKIP LOCKED` hiện đã có; cần khép kín worker lifecycle:

- Claim theo batch và trạng thái `PENDING/PROCESSING/PUBLISHED/FAILED` hoặc lease timestamp.
- Retry giữ nguyên event ID và partition key.
- Exponential backoff có giới hạn.
- Thu hồi record `PROCESSING` khi worker chết/lease hết hạn.
- Sau số lần tối đa, chuyển `FAILED` và có API/command quản trị để inspect/replay.
- Metric: pending count, oldest pending age, retry count, failed count, publish latency.
- Không đánh dấu `PUBLISHED` trước khi broker xác nhận.

### Test

- Cùng event giao hai lần tuần tự và đồng thời.
- Consumer crash trước commit và sau commit trước acknowledge.
- Hai outbox workers không publish cùng row trong một claim hợp lệ.
- Worker chết sau claim; record được reclaim.
- Retry giữ nguyên `eventId` và key.
- Event cũ đến sau event mới không đảo ngược state.

### Điều kiện hoàn tất P3

- Replay không tạo side effect lần hai ở tất cả consumer quan trọng.
- Outbox record không bị kẹt vĩnh viễn do worker chết.
- Có cách quan sát và replay record failed có kiểm soát.

## 7. P4 — Elasticsearch và catalog indexing bền vững

### Batch P4-A — Index schema và search query

#### Mapping

- Document ID = `productId`.
- Trường: name, normalized name, categoryId, imageUrl, minPrice, currency, productVersion, status.
- Analyzer hỗ trợ tiếng Việt và bỏ dấu theo thiết kế; kiểm thử các cặp có dấu/không dấu.
- Index alias để migration mapping không làm gián đoạn search.

#### Search API

Hỗ trợ:

- `q`, `categoryId`, `minPrice`, `maxPrice`;
- sort theo relevance/price/newest theo contract;
- `page`, `size` với giới hạn size;
- chỉ trả product searchable/active;
- response gồm `id`, `name`, `categoryId`, `imageUrl`, `minPrice`, `currency`, `productVersion`.

Xác định rõ policy khi Elasticsearch down: trả lỗi service unavailable ổn định hoặc fallback được requirement cho phép; không trả dữ liệu giả như search thành công.

### Batch P4-B — Catalog outbox indexer

- Catalog write và outbox event cùng DB transaction.
- Indexer đọc event và ghi Elasticsearch với external version = `productVersion`.
- Event cũ không được ghi đè document mới.
- Delete/deactivate phải loại sản phẩm khỏi kết quả tìm kiếm theo policy.
- Consumer dùng `processed_events` từ P3.
- Có retry/backoff/metrics cho indexing failure.

### Batch P4-C — Durable reindex job

#### Schema

Tạo `catalog_reindex_jobs` với:

- `job_id`, `status`, `requested_by`;
- `target_index`, `alias_name`;
- total/processed/failed counts;
- checkpoint/cursor;
- error summary;
- timestamps và version.

#### API và worker

- `POST` trả `202` và `jobId`.
- `GET` trả trạng thái/progress/error.
- Worker đọc PostgreSQL theo page ổn định, bulk index, lưu checkpoint.
- Job restart tiếp tục từ checkpoint.
- Chỉ swap alias sau khi hoàn thành và kiểm tra count.
- Chặn hoặc xếp hàng khi có hai reindex job cùng catalog.

### Test

- SEARCH-01..10 trong checklist.
- Tìm kiếm có dấu/không dấu, filter giá/category, sort/page.
- Create/update/deactivate phản ánh qua indexer.
- Event version cũ không overwrite.
- Elasticsearch down rồi hồi phục.
- Reindex worker restart và tiếp tục.
- Alias chỉ đổi khi job thành công.

### Điều kiện hoàn tất P4

- Search dùng Elasticsearch thật và đúng contract.
- PostgreSQL write không phụ thuộc Elasticsearch đang sống.
- Reindex có trạng thái durable, progress và khả năng resume.

## 8. P5 — Service JWT cho internal API (M2)

### Phạm vi bảo vệ

Chỉ áp dụng cho `/internal/**` và các service-to-service call. Public/business endpoint vẫn dùng JWT người dùng bình thường theo role/permission. Việc user có JWT không đồng nghĩa được gọi internal endpoint.

### Batch P5-A — Token model và key management

- Chọn asymmetric signing để service nhận token chỉ cần public key.
- Claim tối thiểu: `iss`, `sub` là service name, `aud`, `scope`, `iat`, `exp`, `jti`.
- TTL ngắn; hỗ trợ `kid` để rotation.
- Tách key/credential khỏi source và compose mặc định; inject qua secret/env trong môi trường.
- Định nghĩa audience và scope cụ thể cho từng internal operation.

### Batch P5-B — Issuance và validation

- Service caller lấy hoặc tự phát service JWT theo cơ chế đã chọn.
- Resource service kiểm tra chữ ký, issuer, audience, expiry và scope.
- Thay shared `InternalServiceTokenFilter` tĩnh bằng filter/authentication provider cho service JWT.
- Chuẩn hóa error `401` cho token không hợp lệ và `403` cho thiếu scope.
- Log service subject và `jti`, không log token.

### Batch P5-C — End-user context

Nếu internal operation cần biết customer:

- Forward token người dùng để service đích tự verify, hoặc dùng token exchange có claim đã ký.
- Không tin `userId` thuần từ header/body của caller.
- Kiểm tra actor/service và end-user riêng trong audit trail.

### Test

- User JWT không gọi được `/internal/**`.
- Service JWT sai audience/scope/issuer/expiry bị từ chối.
- Key rotation chấp nhận key mới và grace period cho key cũ theo policy.
- Public endpoint vẫn dùng JWT người dùng bình thường.
- Không có token/secret trong log và response.

### Điều kiện hoàn tất P5

- Không còn shared static token là cơ chế bảo vệ chính.
- Mọi internal call có audience/scope tối thiểu cần thiết.
- Có tài liệu rotation và cấu hình môi trường.

## 9. P6 — Trạng thái tài khoản ACTIVE theo thời gian thực

### Batch P6-A — Account status history

Tạo `account_status_history`:

- user ID;
- from/to status;
- reason;
- actor;
- timestamp.

Mọi lock/unlock/disable ghi user state và history trong cùng transaction.

### Batch P6-B — Enforce current status

JWT còn hạn không được tiếp tục truy cập nếu tài khoản đã bị khóa. Có hai phương án triển khai, chọn một và ghi rõ policy:

1. Introspection qua identity service với cache TTL ngắn.
2. Identity phát `UserStatusChanged`; gateway/service duy trì cache trạng thái có version và có fallback khi cache miss.

Thiết kế cần xác định:

- TTL và mức trễ khóa tối đa;
- fail-open hay fail-closed theo nhóm endpoint;
- invalidation khi status thay đổi;
- cách xử lý Identity/Redis down;
- logout all sessions khi lock nếu requirement yêu cầu.

### Test

- Token phát khi ACTIVE, sau đó lock: request mới bị chặn trong SLA đã định.
- Unlock khôi phục truy cập.
- Event status giao trùng hoặc đảo thứ tự không rollback version.
- Identity/Redis down tuân thủ failure policy.
- History đầy đủ actor và reason.

### Điều kiện hoàn tất P6

- Tất cả protected request áp dụng cùng một policy ACTIVE.
- Có bằng chứng test cho trạng thái thay đổi sau khi JWT đã phát.

## 10. P7 — Hoàn thiện payment admin, report và attempt

### Batch P7-A — Demo payment simulator

- `POST /admin/demo/payments/{id}/simulate` theo contract.
- Chỉ admin/demo profile được phép dùng; production profile vô hiệu hóa endpoint.
- Simulator đi qua cùng callback validation/idempotency pipeline như provider thật.
- Cho phép mô phỏng success, failed, wrong amount, wrong currency và delayed callback.
- Không sửa trực tiếp order/inventory để né pipeline.

### Batch P7-B — Report filter

- Bổ sung `from`, `to`, status và phân trang cho payment report.
- Chuẩn hóa timezone và khoảng ngày `[from, to)`.
- Query có index phù hợp và không N+1.
- Đối chiếu tổng accepted/rejected/review với payment history.

### Batch P7-C — Payment attempts và retry policy

Xác nhận contract cuối, sau đó:

- Persist attempt number, provider request/reference, status, failure code và timestamps.
- Retry tạo attempt mới nhưng không tạo payment nghiệp vụ mới nếu cùng order/payment intent.
- Callback cũ không overwrite attempt/payment state mới.
- Không retry lỗi permanent như invalid amount/currency.

### Test

- Simulator bị chặn ngoài demo profile.
- Wrong amount/currency/late callback không chốt kho.
- Hai callback đồng thời chỉ một callback được accepted.
- Report date boundary/timezone.
- Retry và callback đến đảo thứ tự.

### Điều kiện hoàn tất P7

- Toàn bộ API payment trong contract có implementation và security phù hợp.
- Báo cáo có filter đúng và reconciliation được với history.

## 11. P8 — Tài liệu API và nghiệm thu vận hành

### Batch P8-A — OpenAPI và Postman

- Cập nhật OpenAPI cho tất cả endpoint, header idempotency, error codes, auth schemes và pagination.
- Thêm ví dụ service JWT và user JWT nhưng không chứa secret thật.
- Tạo/cập nhật Postman collection và environment template.
- Thêm luồng tuần tự: login → catalog → checkout → payment → confirm → ship → complete.
- Thêm negative flows: insufficient stock, wrong payment, late callback, duplicate callback, locked account.

### Batch P8-B — Integration và Compose smoke

Smoke test tự động phải khởi động stack và xác minh:

1. Infrastructure healthy.
2. User đăng ký/đăng nhập/refresh/logout.
3. Admin tạo catalog và inventory.
4. Search thấy dữ liệu qua Elasticsearch.
5. Checkout tạo reservation.
6. Payment callback confirm order và stock.
7. Notification được tạo/gửi hoặc retry theo stub.
8. Replay event không tạo side effect.
9. Restart worker không mất outbox/reindex job.

### Batch P8-C — Load test tối thiểu 100 users

Kịch bản:

- Browse/search mix.
- Checkout cùng SKU nóng để kiểm tra contention.
- Payment callback song song.
- Admin report read.

Thu thập:

- throughput, p50/p95/p99, error rate;
- Redis lock wait/timeout;
- DB pool/lock wait;
- Kafka lag và outbox age;
- ES latency/error;
- invariant cuối: không oversell, không stock âm, không duplicate confirmation.

Pass/fail threshold phải lấy từ requirement nếu đã có; nếu tài liệu chưa đặt ngưỡng, ghi baseline thực đo và chốt ngưỡng trước nghiệm thu chính thức.

### Batch P8-D — Chaos và recovery

Thực hiện lần lượt, không tắt nhiều dependency cùng lúc ở vòng đầu:

- Kafka down trong khi tạo order/catalog update; kiểm tra outbox recover.
- Elasticsearch down; PostgreSQL write vẫn thành công và index bắt kịp sau recovery.
- Redis down/lock unavailable; inventory write fail an toàn, không bypass lock.
- Notification provider down; retry/dead-letter đúng policy.
- Identity down; protected API tuân thủ ACTIVE failure policy.
- Kill outbox/reindex worker giữa batch; lease/checkpoint recovery.

### Điều kiện hoàn tất P8

- OpenAPI và Postman khớp implementation.
- Full Compose smoke xanh từ môi trường sạch.
- Có báo cáo load và chaos kèm invariant sau test.
- Không có lỗi critical/high chưa xử lý; lỗi thấp còn lại có ticket và phạm vi rõ.

## 12. Ma trận kiểm thử bắt buộc

| Nhóm | Unit | Repository/DB | Integration | Concurrency/Failure |
|---|---|---|---|---|
| Reservation | State transition | Constraint/query lock | Checkout/payment/expiry | Same SKU, payment vs cancel |
| Inventory | Quantity/idempotency | Ledger atomicity | Admin APIs | Duplicate key, lock timeout |
| Event/outbox | Envelope/version | Claim/processed unique | Kafka replay | Crash/reclaim/order inversion |
| Catalog search | Query builder | Job/checkpoint | PostgreSQL → ES | ES down, old version |
| Internal auth | Claim validation | Key config | Caller → resource | Rotation, expired token |
| Identity ACTIVE | Policy/version | Status history | Gateway/service enforcement | Status race, dependency down |
| Payment | Callback validation | Accepted/history/attempt | Payment → order → inventory | Late/wrong/duplicate/concurrent |
| Notification | Template/retry | Attempt/history | OrderConfirmed → delivery | Provider down/replay |

## 13. Quy tắc triển khai từng batch

Mỗi batch thực hiện theo cùng một checklist:

1. Đọc lại requirement ID và API/database design liên quan.
2. Chụp baseline test của service bị ảnh hưởng.
3. Viết migration có rollback strategy hoặc forward-fix strategy.
4. Viết test cho invariant/race trước hoặc cùng lúc với code.
5. Thực hiện code nhỏ nhất khép kín batch.
6. Chạy unit test, repository test và integration test trực tiếp liên quan.
7. Chạy regression ở order/payment/inventory nếu batch chạm một trong ba service này.
8. Cập nhật OpenAPI, Postman và audit matrix ngay trong cùng batch.
9. Kiểm tra diff, secret scan và migration ordering.
10. Dừng ở trạng thái reviewable; chỉ commit khi người dùng yêu cầu.

## 14. Definition of Done toàn bộ kế hoạch

Hệ thống chỉ được coi là hoàn thiện coding và sẵn sàng nghiệm thu khi thỏa tất cả điều kiện:

- Mọi mục requirement ở trạng thái `DONE` hoặc `ACCEPTANCE`, không còn `MISSING/PARTIAL` không có lý do.
- Lock order đúng thiết kế và test concurrency chứng minh không oversell.
- Reservation và stock movement lưu bền vững, mọi write API inventory idempotent.
- Consumer replay an toàn; outbox/reindex có recovery khi worker chết.
- Search chạy qua Elasticsearch với external version và reindex durable.
- Internal API dùng service JWT audience/scope; user JWT không vượt qua internal boundary.
- Account bị khóa bị chặn dù JWT cũ còn hạn.
- Payment wrong/late/duplicate/concurrent không chốt kho sai.
- Full test suite, Compose smoke, load 100 users và chaos/recovery đạt tiêu chí.
- OpenAPI, Postman, schema và tài liệu vận hành phản ánh đúng code thực tế.

## 15. Thứ tự thực thi đề xuất theo phiên làm việc

1. P0 baseline và ma trận requirement.
2. P1-A reservation schema/model.
3. P1-B lock/transaction orchestration.
4. P1-C state machine và concurrency tests.
5. P2-A ledger rồi P2-B idempotency, sau đó P2-C admin API.
6. P3-A envelope consistency, P3-B processed events, P3-C outbox recovery.
7. P4-A search, P4-B indexer, P4-C reindex jobs.
8. P5 service JWT.
9. P6 ACTIVE enforcement.
10. P7 payment gaps.
11. P8 tài liệu và acceptance.

Sau mỗi phase, cập nhật `docs/requirement-audit-2026-10-13.md` để trạng thái luôn phản ánh code và test hiện tại.
