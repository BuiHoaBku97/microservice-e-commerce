# Requirement audit — 2026-10-13

Đối chiếu với `BUSINESS_STATE_DESIGN.md`, `DATABASE_DESIGN.md`,
`KAFKA_EVENT_CONTRACT.md`, `REST_API_CONTRACT.md` và `BUSINESS_TEST_CHECKLIST.md`.

## Đã hoàn thiện trong đợt audit

- Identity: access/refresh token tách biệt, refresh token chỉ lưu hash, rotation có khóa dòng,
  chống replay, logout/revoke, kiểm tra tài khoản ACTIVE; profile và API quản trị khóa/mở khóa user.
- Catalog: API quản trị Category/Product/SKU, optimistic version, unique variant, trạng thái bán,
  ảnh mặc định, lịch sử giá và cache Product bằng Redis với TTL/fallback PostgreSQL.
- Order: trạng thái `PENDING_PAYMENT -> CONFIRMED -> SHIPPING -> COMPLETED`, nhánh
  `CANCELLED/PAYMENT_REVIEW`, hết hạn tại đúng deadline, history, admin list/detail/ship/complete/report.
- Payment: giao dịch khởi tạo PENDING, callback HMAC trên raw body, cửa sổ timestamp,
  callback idempotency, callback race, tách funds/order acceptance, history và report quản trị.
- Notification: ghi PENDING theo event, worker giao demo, retry metadata, delivery attempts,
  các event confirmed/rejected/shipping/completed và API quản trị.
- Kafka/outbox: event ID bền vững, key theo orderId, consumer idempotency ở luồng nghiệp vụ,
  publisher claim batch bằng `FOR UPDATE SKIP LOCKED` để nhiều instance không đọc cùng batch.
- Gateway: route mới, callback public riêng, ADMIN authorization và Redis rate limit theo user/IP;
  public GET fallback khi Redis lỗi, write/login/checkout fail closed.

## Còn mở so với toàn bộ thiết kế

- Catalog Elasticsearch: mapping/analyzer tiếng Việt/không dấu, durable Catalog outbox/indexer,
  filter/sort/page, external product version và reindex job bền vững chưa được triển khai.
- Inventory: chưa có reservation/reservation_items và stock_movements riêng; API inventory write
  chưa dùng Idempotency-Key và chưa có list/movement history theo contract.
- Thứ tự khóa payment/cancel/expiry cần tách orchestration để luôn lấy Redis SKU lock trước khóa
  order/reservation trong cùng transaction và chỉ unlock sau commit/rollback.
- Internal authentication vẫn dùng shared service token M2; thiết kế đích yêu cầu service JWT có
  audience/scope riêng và end-user context đã xác minh.
- Identity chưa lưu account_status_history; kiểm tra ACTIVE tức thời trên mọi protected request ở
  các service vẫn cần cơ chế introspection/cache có chính sách rõ ràng.
- Payment demo simulator endpoint chưa có; report hiện là tổng toàn kỳ, chưa nhận from/to.
- OpenAPI, Postman collection, load test 100 khách, chaos test từng dependency và smoke toàn bộ
  Docker Compose vẫn cần hoàn thiện trước nghiệm thu cuối.

## Verification đã chạy

- `./gradlew test --continue --rerun-tasks`
- `./gradlew :event-flow-tests:integrationTest --rerun-tasks`
- `docker compose config --quiet` với hai secret bắt buộc được cấp qua environment.

Không tự động commit các thay đổi của đợt audit.
