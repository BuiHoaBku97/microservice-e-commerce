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

## Ma trận sau triển khai remaining plan

| Phase | Trạng thái | Bằng chứng chính |
|---|---|---|
| P1 | DONE | Reservation bền vững; Redis lock bao ngoài `TransactionTemplate`; checkout/payment/cancel/expiry ghi order, reservation, inventory và outbox trong cùng transaction. |
| P2 | DONE | `stock_movements`, idempotency record, API inventory list/movement và bắt buộc `Idempotency-Key`. |
| P3 | DONE | Processed-event registry theo consumer, outbox lease/reclaim/backoff/FAILED và API inspect/replay. |
| P4 | DONE | Elasticsearch analyzer tiếng Việt, filter/sort/page, durable catalog outbox, version guard, checkpoint và atomic alias switch khi reindex. |
| P5 | DONE | Service JWT RS256 có audience/scope/jti/kid, key ring phục vụ grace period và không còn shared static token. |
| P6 | DONE | Refresh token có `session_id`, rotation/revoke theo session, account status history và protected service fail closed khi Identity unavailable/không ACTIVE. |
| P7 | DONE | Demo simulator chỉ ở profile local/demo và đi qua callback HMAC; admin list/history/report dùng query phân trang và totals tách biệt. |
| P8 | ACCEPTANCE | OpenAPI/Postman/k6/chaos runbook và full-flow Compose smoke đã có; cần chạy trên Docker daemon và lưu báo cáo đo thực tế. |

Các mục payment attempts và vô hiệu access token ngay sau logout vẫn nằm ngoài baseline vì contract
nguồn chưa định nghĩa. Report payment tiếp tục là tổng toàn kỳ đúng REST contract hiện tại.

## Verification đã chạy

- `./gradlew test --continue --rerun-tasks`
- `./gradlew :event-flow-tests:integrationTest --rerun-tasks`
- `docker compose config --quiet` với hai secret bắt buộc được cấp qua environment.

Verification của đợt triển khai remaining plan:

- `./gradlew test --continue` — xanh toàn bộ 31 task.
- `docker compose config --quiet` — hợp lệ với các biến khóa bắt buộc.
- Compose smoke/load/chaos runtime — chưa chạy vì Docker daemon không hoạt động trên máy hiện tại;
  giữ trạng thái `ACCEPTANCE`, không đánh dấu `DONE`.

Không tự động commit các thay đổi của đợt audit.
