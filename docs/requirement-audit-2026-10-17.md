# Requirement, test và Docker audit — cập nhật 18/10/2026

## Phạm vi và kết luận

Audit đối chiếu repository với các tài liệu gốc tại
`D:\Learn\Backend-Java\Microservices\Thesis`: `Omnichannel_ECommerce.md`,
`BUSINESS_STATE_DESIGN.md`, `DATABASE_DESIGN.md`, `KAFKA_EVENT_CONTRACT.md`,
`REST_API_CONTRACT.md` và `BUSINESS_TEST_CHECKLIST.md`.

Chín sai lệch chức năng được xác nhận trong lần audit ngày 17/10 đã được triển khai. Unit/component
suite hiện pass hoàn toàn và OpenAPI bao phủ mọi controller operation. Repository vẫn chưa đủ bằng
chứng để tuyên bố nghiệm thu toàn hệ thống vì Docker daemon không hoạt động, nên Testcontainers,
Compose runtime, migration trên database thật, HTTP qua Gateway, Newman, k6 và chaos chưa chạy được.

## Các sai lệch đã sửa

| Hạng mục | Trạng thái hiện tại | Commit chính |
|---|---|---|
| Checkout thiếu `paymentMethod`, `customerNote`; Order thiếu `salesChannel`, `subtotal`, `shippingFee` | Đã thêm request validation, persistence, migration, response và shipping fee cấu hình được | `29bcc57` |
| OrderItem thiếu snapshot sản phẩm/SKU/variant/currency | Đã lưu `productId`, `skuCode`, `productName`, `color`, `size`, `currency` | `29bcc57` |
| `GET /orders` thiếu filter/page; ship/complete thiếu version | Đã có status/from/to/page/size, sort ổn định và optimistic version với retry idempotent | `fdba256` |
| Xóa địa chỉ mặc định tự chọn địa chỉ khác, trái ADR-07 | Đã bỏ tự động promote và thêm test hồi quy | `76301e1` |
| Cart nhận SKU không tồn tại/ngừng bán, trái CART-04 | Cart gọi Catalog bằng scope `catalog.read`; giá hiển thị lấy từ Catalog, không tin giá client | `406b1be`, `22ac377` |
| Response/error không thống nhất | Public API, Security entry point và filter dùng success/error envelope; list dùng `items/page/size/totalElements/totalPages` | `46f8586` |
| Outbox/processed event thiếu cột chuẩn | Đã thêm aggregate/schema/topic/partition/occurred/claim/lease và payload hash/aggregate ID cùng migration | `737c9c5` |
| REST path/internal auth lệch contract | Reindex dùng `/admin/search/reindex`; có internal SKU read và Identity `current/status`; Catalog checkout chỉ cần service JWT | `46f8586`, `cdbfaff` |
| OpenAPI chỉ có 35 operation | Hiện có 74 operation và tool tự động kiểm tra đủ 74/74 mapping, operationId duy nhất | `222a415`, `cdbfaff` |

Các list được ghi rõ là phân trang trong contract đã dùng page envelope, gồm category public, admin
category/product, customer/admin order, inventory, payment/history và notification. Admin product hỗ
trợ thêm `status` và `categoryId`.

## Bằng chứng kiểm thử đã chạy

| Kiểm tra | Kết quả |
|---|---|
| `.\gradlew.bat test --rerun-tasks --no-daemon` | PASS: 186 test, 0 failure, 0 error, 0 skipped; 31 task thực thi lại |
| 5 service test sau thay đổi REST | PASS |
| `python tools/check_openapi_coverage.py` | PASS: 74/74 controller operation được mô tả |
| Sinh `.env` bằng `scripts/generate-dev-env.ps1` | PASS; sinh 5 cặp RSA PKCS#8/X.509 và callback secret |
| `docker compose --env-file build/dev-env-test config --quiet` | PASS |
| Parse PowerShell setup/smoke | PASS cú pháp |
| `:event-flow-tests:integrationTest --rerun-tasks` | BLOCKED: 2 test initialization error do không kết nối được Docker daemon |
| `docker info` | BLOCKED: không có `dockerDesktopLinuxEngine` |

Unit suite tốt hơn lần audit đầu: có test ADR-07, Cart/Catalog sellability, giá Cart authoritative,
version ship/complete, event schema/evidence, response envelope và Notification ownership/page. Tuy
vậy số lượng test không đồng nghĩa toàn bộ 151 business case đã được nghiệm thu.

## Khoảng trống kiểm thử còn lại

### Unit và controller test

- Catalog CRUD mới kiểm tra một phần category và checkout lookup; còn thiếu ma trận image URL,
  variant uniqueness, price history, trạng thái Product/SKU và các filter admin.
- Address còn thiếu controller test đầy đủ cho create/update/default/ownership và race đặt default.
- InventoryService mới có unit test availability; receipt, adjustment, operation idempotency, ledger,
  rollback nhiều SKU và tranh chấp cần test trực tiếp hơn.
- Order controller chưa có ma trận HTTP cho list filter/date boundary, history, cancel, ship, complete
  và report; logic service chính đã có test.
- Payment có controller test query theo order, nhưng callback HTTP, admin list/history/report và demo
  profile chưa có controller test tương xứng.
- Notification đã có customer page/ownership test; admin list/attempts và security matrix chưa đầy đủ.
- Eureka không có test riêng. Repository chưa cấu hình JaCoCo hoặc coverage gate.

### Integration, API và business scenario

- Hai Testcontainers suite tồn tại nhưng chưa chạy được trong lần audit này. Vì vậy migration V18/V19,
  event replay, lease, payload conflict, Kafka DLT và DB race chưa có bằng chứng runtime mới.
- Compose smoke hiện tự dựng luồng register → address → catalog → inventory → cart → checkout → payment
  → confirm → ship → complete → notification và kiểm tra inventory. Script đã kiểm tra đủ service,
  health và `kafka-init`, nhưng chưa thể chạy do Docker daemon.
- Postman collection vẫn chỉ có 6 request và 2 request có script; nó là bộ hỗ trợ thủ công, chưa phải
  acceptance suite độc lập. Newman chưa được cài/chạy.
- k6 chưa dựng customer/cart độc lập cho từng VU và chưa chạy. Chaos scripts chưa tạo bằng chứng về
  recovery/invariant trong lần này.
- `BUSINESS_TEST_CHECKLIST.md` có 151 case và vẫn 151 mục `[ ]`. Không đánh dấu pass khi chưa có kết quả
  runtime/traceability cho từng ID.
- Chưa có test Elasticsearch thật cho analyzer tiếng Việt không dấu, sort/filter và alias reindex.

## Danh mục Docker setup

| Danh mục | Hiện trạng sau sửa | Mức xác minh |
|---|---|---|
| Dockerfile | 7 service dùng Gradle/JRE 21, cài curl cho healthcheck, expose đúng port | Static complete; chưa build image |
| Build context | Mỗi service có `.dockerignore`; Dockerfile root gây nhầm đã bỏ | Đã kiểm tra file |
| Infrastructure | PostgreSQL 16.4, Kafka 3.9.1 KRaft, Redis 7.4.1, Elasticsearch 8.15.3 | Compose render pass |
| Application | Eureka, Identity, Catalog, Order, Payment, Notification, Gateway | Compose render pass |
| Database bootstrap | 5 database/user và Flyway từng service | Chưa chạy trên PostgreSQL sạch |
| Kafka topics | 2 topic chính và 4 DLT qua `kafka-init` | Chưa kiểm tra broker thật |
| Persistence | Volume cho PostgreSQL, Kafka, Redis, Elasticsearch | Chưa test restart |
| Healthcheck | Có cho PostgreSQL, Kafka, Redis, Elasticsearch, Eureka và toàn bộ app | Compose render pass |
| Readiness | App chờ DB/Kafka/Eureka/Identity cần thiết; Gateway chờ mọi backend healthy | Compose render pass |
| Secrets | `.env.example` và script sinh user JWT + 4 service JWT key pair, callback secret | Generator chạy pass |
| Admin seed | `.env` sinh sẵn bootstrap local và README yêu cầu đổi password | Static complete |
| Hướng dẫn | README có build/up `--wait`, test, integration test, smoke, ports và shutdown | Static complete |
| Smoke | Tự dựng dữ liệu và kiểm tra full business flow đến COMPLETED | Chưa chạy runtime |

Port host mặc định: Gateway `8080`, Eureka `8761`, PostgreSQL `5433`, Kafka `9092`, Redis `6379`,
Elasticsearch `9200`. Chỉ Gateway là public API; internal endpoint không được route qua Gateway.

## Việc còn cần môi trường runtime

1. Bật Docker Desktop/Linux engine.
2. Chạy `docker compose build` và `docker compose up -d --wait` trên volume sạch.
3. Chạy Testcontainers integration test, Compose smoke, Newman, k6 và chaos; lưu report theo lần chạy.
4. Kiểm tra Flyway trên cả database mới và database nâng cấp, Eureka registration, Kafka topics/DLT,
   Elasticsearch analyzer/reindex, restart persistence và outbox recovery.
5. Lập traceability từ 151 business ID đến automated/manual test rồi chỉ đánh dấu các case có bằng chứng.

Cho đến khi năm bước trên hoàn tất, kết luận đúng là **implementation của các sai lệch đã đầy đủ ở mức
code và test cô lập, nhưng nghiệm thu end-to-end vẫn bị chặn bởi Docker runtime**.
