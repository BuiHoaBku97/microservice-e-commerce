# Tài liệu dự án E-commerce Microservices

## 1. Giới thiệu hệ thống

Đây là hệ thống thương mại điện tử triển khai theo kiến trúc microservices trên Spring Boot. Client truy cập qua http://localhost:8080/api/v1. Gateway xác thực JWT, giới hạn tốc độ và định tuyến đến các service đã đăng ký với Eureka.

Các domain nghiệp vụ chính:

- Identity: đăng ký, đăng nhập, refresh token, hồ sơ và địa chỉ.
- Catalog: danh mục, sản phẩm, SKU, giá và tìm kiếm.
- Order: giỏ hàng, checkout, đơn hàng và tồn kho.
- Payment: khởi tạo thanh toán, mock payment và VNPay.
- Notification: thông báo trạng thái đơn hàng và thanh toán.

Hệ thống dùng PostgreSQL với database riêng cho từng domain, Redis cho dữ liệu ngắn hạn và rate limit, Elasticsearch cho chỉ mục catalog/search, Kafka cho event bất đồng bộ và OpenTelemetry/Grafana/Loki/Jaeger cho quan sát vận hành.

## 2. Kiến trúc và luồng giao tiếp

    Client / Frontend
            |
            v
    Gateway :8080  -- JWT, rate limit, correlation ID
            |
            +--> Identity :8081  --> identity_db
            +--> Catalog  :8082  --> catalog_db --> Elasticsearch
            |                         |
            |                         +----------> Redis
            +--> Order    :8083  --> order_db ----> Redis
            |                         |
            |                         +----------> Kafka: order.events
            +--> Payment  :8084  --> payment_db --> Kafka: payment.events
            +--> Notification :8085 --> notification_db

    Eureka :8761: service discovery cho Gateway và các service
    Kafka: OrderCreated, PaymentSucceeded/Failed và các DLT
    Observability: Prometheus, Grafana, Loki, Alloy, OTel Collector, Jaeger

### Giao tiếp đồng bộ

- Gateway gọi các public API của từng domain qua Eureka và load balancing.
- Order gọi Identity để lấy checkout context và gọi Catalog để chốt thông tin SKU, giá, tên sản phẩm.
- Các internal API nằm dưới /internal/**, không đi qua Gateway và yêu cầu service JWT với scope riêng.

### Giao tiếp bất đồng bộ

- Order ghi outbox trong cùng transaction với thay đổi đơn hàng, sau đó publish lên ecommerce.order.events.
- Payment consume event order, xử lý payment context và publish lên ecommerce.payment.events.
- Order consume payment event để cập nhật trạng thái đơn hàng; Notification consume cả order event và payment event để tạo thông báo.
- DLT theo consumer: ecommerce.order.events.payment-service.dlt, ecommerce.order.events.notification-service.dlt, ecommerce.payment.events.order-service.dlt, ecommerce.payment.events.notification-service.dlt.
- Consumer lưu processed event và dùng event id/idempotency để chống xử lý lặp.

## 3. Domain và dịch vụ con

| Domain | Service | Database / hạ tầng | Chức năng chính |
|---|---|---|---|
| Identity | identity-service | identity_db, JWT RSA, refresh sessions | tài khoản, vai trò, trạng thái tài khoản, địa chỉ |
| Catalog | catalog-service | catalog_db, Elasticsearch, Redis | category, product, SKU, price history, search index |
| Order | order-service | order_db, Redis, Kafka, outbox | cart, checkout, order lifecycle, inventory/reservation |
| Payment | payment-service | payment_db, Kafka, outbox | payment context, mock payment, VNPay, transaction history |
| Notification | notification-service | notification_db, Kafka | inbox notification, read state, delivery attempts |
| Platform | Gateway + Eureka | Redis + Eureka | routing, auth edge, service discovery, rate limit |
| Observability | Prometheus, Grafana, Loki, Alloy, OTel, Jaeger | volume riêng | metrics, logs, traces, dashboard |

## 4. Route và role

Quy ước role: PUBLIC là không cần JWT; CUSTOMER là người mua; ADMIN là quản trị viên. Các route callback public vẫn phải kiểm tra chữ ký hoặc secret ở tầng nghiệp vụ.

### Identity

| Method | Route | Role | Mục đích |
|---|---|---|---|
| POST | /api/v1/auth/register | PUBLIC | đăng ký |
| POST | /api/v1/auth/login | PUBLIC | đăng nhập, cấp access/refresh token |
| POST | /api/v1/auth/refresh | PUBLIC | cấp access token mới |
| POST | /api/v1/auth/logout | CUSTOMER/ADMIN | thu hồi refresh session |
| GET/PATCH | /api/v1/users/me | CUSTOMER/ADMIN | xem/cập nhật hồ sơ |
| GET/POST/PATCH/DELETE/PUT | /api/v1/users/me/addresses... | CUSTOMER/ADMIN | quản lý địa chỉ |
| GET/POST | /api/v1/admin/users... | ADMIN | xem, khóa, mở khóa tài khoản |

### Catalog

| Method | Route | Role | Mục đích |
|---|---|---|---|
| GET | /api/v1/categories | PUBLIC | danh sách danh mục |
| GET | /api/v1/products/search | PUBLIC | tìm kiếm, lọc và phân trang |
| GET | /api/v1/products/{id} | PUBLIC | chi tiết sản phẩm và SKU |
| POST/PATCH/GET | /api/v1/admin/categories... | ADMIN | quản lý danh mục |
| POST/PATCH/GET/PUT | /api/v1/admin/products... | ADMIN | quản lý sản phẩm, trạng thái |
| POST/PATCH/PUT/GET | /api/v1/admin/products/{productId}/skus... và /api/v1/admin/skus... | ADMIN | quản lý SKU, giá |
| POST/GET | /api/v1/admin/search/reindex... | ADMIN | reindex Elasticsearch |

### Order và inventory

| Method | Route | Role | Mục đích |
|---|---|---|---|
| GET/POST/PATCH/DELETE | /api/v1/cart... | CUSTOMER | quản lý giỏ hàng |
| POST | /api/v1/orders/checkout | CUSTOMER | tạo đơn từ cart |
| GET | /api/v1/orders | CUSTOMER | lịch sử đơn hàng |
| GET | /api/v1/orders/{orderId} | CUSTOMER | chi tiết đơn |
| GET | /api/v1/orders/{orderId}/history | CUSTOMER | lịch sử trạng thái |
| POST | /api/v1/orders/{orderId}/cancel | CUSTOMER | hủy đơn |
| GET | /api/v1/inventory/availability | PUBLIC | kiểm tra khả dụng SKU |
| POST | /api/v1/admin/orders/{orderId}/ship | ADMIN | chuyển sang shipping |
| POST | /api/v1/admin/orders/{orderId}/complete | ADMIN | hoàn tất đơn |
| GET | /api/v1/admin/orders... | ADMIN | quản trị đơn |
| GET | /api/v1/admin/reports/orders | ADMIN | báo cáo đơn |
| POST/GET | /api/v1/admin/inventory... | ADMIN | quản trị tồn kho |
| GET/POST | /api/v1/admin/orders/outbox... | ADMIN/ops | xem và replay outbox |

### Payment

| Method | Route | Role | Mục đích |
|---|---|---|---|
| GET | /api/v1/orders/{orderId}/payment | CUSTOMER | xem payment |
| POST | /api/v1/orders/{orderId}/payment/initialize | CUSTOMER | lấy SIMULATE hoặc REDIRECT |
| POST | /api/v1/payments/mock/callback | PUBLIC + mock signature | callback giả lập |
| GET | /api/v1/payments/vnpay/ipn | PUBLIC + HMAC | VNPay cập nhật payment |
| GET | /api/v1/payments/vnpay/return | PUBLIC + signature | redirect về frontend |
| GET | /api/v1/admin/payments... | ADMIN | payment, history, báo cáo |
| GET/POST | /api/v1/admin/payments/outbox... | ADMIN/ops | replay payment event |
| POST | /api/v1/admin/demo/payments/{id}/simulate | ADMIN | mô phỏng payment |

### Notification

| Method | Route | Role | Mục đích |
|---|---|---|---|
| GET | /api/v1/notifications | CUSTOMER/ADMIN | danh sách thông báo |
| PATCH | /api/v1/notifications/{notificationId}/read | CUSTOMER/ADMIN | đánh dấu đã đọc |
| GET | /api/v1/admin/notifications | ADMIN | tra cứu notification |
| GET | /api/v1/admin/notifications/{id}/attempts | ADMIN | delivery attempts |

### Internal API

| Service | Route | Scope | Mục đích |
|---|---|---|---|
| Identity | POST /internal/v1/users/current/checkout-context | identity.checkout | checkout context |
| Identity | GET /internal/v1/users/{userId}/status | identity.status | trạng thái tài khoản |
| Catalog | POST /internal/v1/skus/checkout-items | catalog.checkout | chốt dữ liệu SKU |
| Catalog | GET /internal/v1/skus/{skuId} | catalog.read | đọc SKU nội bộ |

## 5. Flow chính

### Flow đặt hàng và thanh toán

1. Customer đăng nhập và thêm SKU vào /api/v1/cart/items.
2. Client gọi POST /api/v1/orders/checkout với Idempotency-Key.
3. Order gọi Identity lấy checkout context, gọi Catalog lấy snapshot SKU/giá, kiểm tra tồn kho và tạo order chờ thanh toán.
4. Order ghi OrderCreated vào outbox và publish Kafka. Payment tạo payment tương ứng.
5. Client gọi /api/v1/orders/{orderId}/payment/initialize.
6. Với MOCK_ONLINE, client dùng simulate/callback. Với VNPAY, client redirect đến paymentUrl.
7. Payment cập nhật payment và transaction history, ghi outbox PaymentSucceeded hoặc PaymentFailed.
8. Order cập nhật trạng thái đơn; Notification tạo thông báo.

### Flow VNPay Sandbox

- Checkout truyền paymentMethod=VNPAY qua OrderCreated.
- Initialize trả nextAction=REDIRECT và paymentUrl.
- VNPay gọi GET /api/v1/payments/vnpay/ipn. Service kiểm tra HMAC-SHA512, merchant code, transaction ref, amount và transaction state.
- IPN cập nhật payment, transaction history và phát outbox event. Retry cùng callback không phát event lần hai.
- Return chỉ kiểm tra chữ ký rồi redirect frontend. Frontend phải đọc lại payment vì Return có thể đến trước IPN.
- IPN cần public HTTPS URL. Cấu hình chi tiết nằm ở docs/vnpay-sandbox.md.

### Flow truy vấn lịch sử đơn hàng

1. Customer gọi GET /api/v1/orders với bộ lọc/phân trang.
2. Gateway xác thực JWT và chuyển đến Order.
3. Order đọc database theo user hiện tại.
4. Client gọi /api/v1/orders/{orderId} để xem snapshot hàng hóa, địa chỉ, tổng tiền và trạng thái.
5. Client gọi /api/v1/orders/{orderId}/history để xem các lần chuyển trạng thái.

### Flow danh mục và tìm kiếm

1. Admin tạo hoặc cập nhật category, product và SKU qua /api/v1/admin/....
2. Catalog lưu PostgreSQL và cập nhật chỉ mục Elasticsearch.
3. Customer gọi GET /api/v1/products/search để tìm kiếm và lọc.
4. Admin gọi /api/v1/admin/search/reindex khi cần rebuild index.
5. Khi checkout, Order gọi internal Catalog để chốt lại SKU, giá và thông tin sản phẩm.

## 6. Bảo mật, tin cậy và quan sát

- JWT RSA do Identity phát hành; Gateway và service xác thực bằng public key. Role nằm trong scope CUSTOMER và ADMIN.
- Internal service dùng service JWT và scope riêng. Service không công khai internal route qua Gateway.
- X-Correlation-ID được tạo hoặc truyền tiếp qua HTTP. Log gắn orderId, paymentId, txnRef và eventId để truy vết qua Kafka.
- Outbox, processed-event record và idempotency key bảo vệ checkout, payment và consumer retry.
- Prometheus thu metrics, Grafana hiển thị dashboard, Alloy thu stdout vào Loki, OTel Collector chuyển trace sang Jaeger.

## 7. Cách chạy và kiểm tra

    powershell -File scripts/generate-dev-env.ps1
    docker compose config --quiet
    docker compose up -d --wait
    .\gradlew.bat test --rerun-tasks --no-daemon
    .\gradlew.bat :event-flow-tests:integrationTest --rerun-tasks --no-daemon

Các cổng mặc định: Gateway 8080, Eureka 8761, PostgreSQL 5433, Kafka 9092, Redis 6379, Elasticsearch 9200, Grafana 3000, Prometheus 9090, Jaeger 16686.

### Kiểm thử API bằng Postman

Import hai file sau vào Postman:

- `docs/e-commerce.postman_collection.json`
- `docs/e-commerce.postman_environment.json`

Chọn environment `E-commerce local`, đặt `adminEmail` và `adminPassword` đúng với tài khoản admin trong `.env`, sau đó chạy Collection Runner theo thứ tự. Collection tự tạo customer, catalog, tồn kho, cart và checkout; các biến ID/token/version được lưu tự động từ response.

Luồng `MOCK_ONLINE` mặc định:

1. Chạy các request từ `00 - Authentication`, `01 - Address`, `02 - Catalog and inventory`.
2. Trong `03 - Cart, checkout and payment`, chạy `Get empty cart`, `Add SKU to cart`, `Checkout`, `Get payment (wait for async creation)`, `Initialize payment`.
3. Xác nhận response `Initialize payment` có `nextAction=SIMULATE`, rồi chạy `Simulate successful payment (MOCK_ONLINE)`.
4. Chạy các request chờ order `CONFIRMED`, sau đó tiếp tục ship/complete và kiểm tra notification/inventory.

Luồng VNPay Sandbox:

1. Trong environment, đổi `paymentMethod` thành `VNPAY`.
2. Trước khi chạy `Initialize payment`, phải cấu hình VNPay trong `.env` rồi restart `payment-service`:

   ```dotenv
   VNPAY_ENABLED=true
   VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
   VNPAY_TMN_CODE=<TMN_CODE-do-VNPay-cap>
   VNPAY_HASH_SECRET=<HASH_SECRET-do-VNPay-cap>
   VNPAY_RETURN_URL=https://<public-host>/api/v1/payments/vnpay/return
   VNPAY_IPN_URL=https://<public-host>/api/v1/payments/vnpay/ipn
   VNPAY_FRONTEND_RESULT_URL=http://localhost:3000/payment/result
   ```

   Sau đó chạy `docker compose up -d --force-recreate payment-service` hoặc restart toàn bộ Compose. `VNPAY_IPN_URL` phải là HTTPS public; `localhost` chỉ đủ để kiểm tra tạo URL, không đủ để VNPay gọi IPN.
3. Chạy các request đến `Initialize payment`. Response phải có `nextAction=REDIRECT` và `paymentUrl`.
4. Mở giá trị `{{paymentUrl}}` trên trình duyệt để thanh toán Sandbox. Không chạy request `Simulate successful payment (MOCK_ONLINE)`; collection tự bỏ qua request này khi chọn `VNPAY`.
5. VNPay gọi `GET /api/v1/payments/vnpay/ipn` để cập nhật PostgreSQL. Return chỉ redirect về frontend; sau khi thanh toán, dùng `Get payment` và `Get order` để kiểm tra trạng thái thực tế.

Nếu Postman trả `409 VNPAY_NOT_CONFIGURED`, kiểm tra đủ sáu biến `VNPAY_ENABLED`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_RETURN_URL`, `VNPAY_IPN_URL` và restart `payment-service`. Đây là lỗi cấu hình runtime, không phải lỗi URL hoặc Authorization của Postman.

Các request cần truyền `Authorization: Bearer {{customerToken}}` hoặc `Bearer {{adminToken}}` theo collection. Có thể đặt `X-Correlation-ID` ở request cần truy vết; nếu không truyền, service tự sinh correlation ID và log thêm `orderId`, `paymentId`, `txnRef`, `eventId`.
