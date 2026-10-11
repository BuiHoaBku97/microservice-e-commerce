# Kafka retry và DLT

## Cấu hình hiện tại

Áp dụng cho Order, Payment và Notification:

- Consumer tắt auto commit, dùng MANUAL_IMMEDIATE. Listener acknowledge sau khi service trả thành công; service nghiệp vụ hiện có transaction.
- Lỗi tạm thời retry tối đa 3 lần, mặc định cách 2 giây (1 lần ban đầu + 3 lần thử lại). Có thể cấu hình app.kafka.retry.interval-ms và app.kafka.retry.max-retries.
- IllegalArgumentException và JsonProcessingException, kể cả được bọc bởi listener, được xem là lỗi không thể khắc phục bằng retry: chuyển thẳng DLT.
- Recoverer chờ broker xác nhận gửi DLT. Lỗi gửi hoặc timeout làm recovery thất bại; handler seek về record chưa xử lý, không commit vượt record đó.
- commitRecovered=true với MANUAL_IMMEDIATE: sau khi DLT được xác nhận mới commit offset nguồn + 1. ackAfterHandle=false tránh container acknowledge thêm ngoài cơ chế này.
- Producer dùng StringSerializer, acks=all, enable.idempotence=true. Producer idempotence không bảo đảm nghiệp vụ database exactly-once.
- Partition DLT giữ nguyên partition nguồn; DLT phải có cùng số partition. Không tự chọn partition khác nếu cấu hình thiếu.
- Spring Kafka 3.3.16 vốn đã mặc định failIfSendResultIsError=true; code đặt rõ và test để bảo vệ hành vi này.

## Topic

| Topic nguồn | Consumer group | DLT |
|---|---|---|
| ecommerce.order.events | payment-service | ecommerce.order.events.payment-service.dlt |
| ecommerce.order.events | notification-service | ecommerce.order.events.notification-service.dlt |
| ecommerce.payment.events | order-service | ecommerce.payment.events.order-service.dlt |
| ecommerce.payment.events | notification-service | ecommerce.payment.events.notification-service.dlt |

Compose tạo mỗi topic với 3 partition, replication factor 1. kafka-init dừng lỗi nếu tạo topic thất bại; cả ba consumer service đợi kafka-init hoàn tất.

Tên đích được tính từ topic nguồn và spring.kafka.consumer.group-id. Nếu đổi topic/group phải tạo DLT tương ứng trước khi chạy consumer. Các topic .DLT cũ không bị xóa; dữ liệu cũ cần kiểm tra riêng.

## Thông tin của record DLT

Key và payload được giữ nguyên; không sinh eventId mới khi chuyển DLT.

Spring Kafka cung cấp header gốc về topic, partition, offset và exception. Header bổ sung:

- x-dlt-consumer-group
- x-dlt-error-code: CONSUMER_PROCESSING_FAILED
- x-dlt-error-summary: thông báo chung tiếng Việt
- x-dlt-failed-at: thời gian UTC

failed-at được tạo lại nếu thử publish DLT lại. eventId vẫn nằm trong payload nếu event nguồn có trường này. Event hiện tại chưa có envelope/eventId đầy đủ; phần đó còn ở C1, không tự giả lập đã có chống trùng theo eventId.

## Kiểm tra topic và message

Chạy từ repo khi Docker đã hoạt động:

```powershell
docker compose up -d kafka
docker compose run --rm kafka-init
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic ecommerce.order.events.payment-service.dlt --from-beginning --max-messages 1 --timeout-ms 10000 --property print.headers=true --property print.key=true --property print.partition=true --property print.offset=true
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group payment-service
```

Lệnh đọc dùng console consumer riêng, không thay offset của group payment-service. Không có record trong thời gian chờ thì console có thể kết thúc bằng timeout.

## Gửi lại record đã sửa nguyên nhân lỗi

1. Ghi nhận DLT topic/partition/offset, key, payload và eventId nếu có. Sửa handler hoặc nguyên nhân lỗi trước khi gửi lại.
2. Kiểm tra dữ liệu nghiệp vụ: hiện mới có dedup theo orderId/trạng thái/deduplicationKey; chưa có processed_events. Không mặc định mọi replay đều an toàn.
3. Gửi lại đúng một record đã chọn lên topic nguồn, giữ nguyên key, payload và eventId. Không tạo ID mới, không thay amount/status để vượt kiểm tra.
4. Ghi nhận lần redrive, kiểm tra dữ liệu DB, offset, DLT và các consumer liên quan. Publish vào topic nguồn làm mọi consumer group của topic có thể nhận lại record.
5. Không reset toàn bộ group hoặc xóa topic DLT để xử lý một lỗi đơn lẻ.

Ví dụ thao tác thủ công trong shell của container, sau khi xác định record cần gửi lại ở DLT partition 1, offset 0:

```sh
docker compose exec kafka /bin/sh
/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic ecommerce.order.events.payment-service.dlt --partition 1 --offset 0 --max-messages 1 --timeout-ms 10000 --property print.key=true --property 'key.separator=|' > /tmp/order-dlt-replay.txt
cat /tmp/order-dlt-replay.txt
# Chỉ chạy lệnh producer sau khi kiểm tra key|payload đúng record cần gửi lại.
/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic ecommerce.order.events --producer-property acks=all --producer-property enable.idempotence=true --property parse.key=true --property 'key.separator=|' < /tmp/order-dlt-replay.txt
exit
```

Ví dụ dành cho key ID số và payload JSON một dòng hiện tại; không sao chép phần hiển thị header vào payload. Các header DLT dùng để tra cứu, không phải payload nghiệp vụ.

## Bằng chứng và giới hạn

KafkaErrorHandlerTest ở cả ba service sử dụng DefaultErrorHandler và DeadLetterPublishingRecoverer thật, với KafkaTemplate/Consumer được mock. Test kiểm tra:

- Ba lần retry trước recovery cho lỗi tạm thời.
- Topic theo group, partition/key/payload/header được giữ đúng.
- Chỉ commit offset nguồn + 1 sau send thành công.
- Không commit khi send lỗi đồng bộ, future thất bại hoặc future chưa hoàn tất.
- Recovery lại cùng record khi DLT gửi được.
- Lỗi vĩnh viễn đi thẳng DLT.

Cần nghiệm thu thêm với broker/DB thật: restart consumer, broker ngừng/phục hồi, offset sau restart và replay không tác động nghiệp vụ hai lần. Docker daemon chưa chạy trong lần triển khai A4; các ví dụ vận hành chưa được thực thi.

