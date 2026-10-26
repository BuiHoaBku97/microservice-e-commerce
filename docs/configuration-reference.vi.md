# Tham chiếu cấu hình

## 1. Thứ tự và phạm vi cấu hình

Spring đọc biến môi trường được tham chiếu trong từng `application.yml`. Docker Compose đọc `.env` để nội suy `docker-compose.yml`, nhưng một biến chỉ vào container khi được khai báo dưới `environment` của service. Vì vậy thêm `RATE_LIMIT_PUBLIC=500` vào `.env` chưa đủ nếu Compose chưa chuyển biến đó cho Gateway; cần bổ sung mapping hoặc dùng file override.

Tạo key/secret local bằng:

```powershell
powershell -File scripts/generate-dev-env.ps1
```

Không commit `.env`, private key, callback secret, VNPay hash secret hoặc token. `.env.example` chỉ chứa placeholder và giá trị phát triển.

## 2. Port và URL

| Thành phần | Port trong container/app | Port host mặc định | Ghi chú |
|---|---:|---:|---|
| Gateway | 8080 | 8080 | Public API |
| Identity | 8081 | không publish | Internal Compose |
| Catalog | 8082 | không publish | Internal Compose |
| Order | 8083 | không publish | Internal Compose |
| Payment | 8084 | không publish | Internal Compose |
| Notification | 8085 | không publish | Internal Compose |
| Eureka | 8761 | 8761 | Registry/UI |
| PostgreSQL | 5432 | 5433 | `POSTGRES_PORT` |
| Kafka | 9092 | 9092 | `KAFKA_PORT` |
| Redis | 6379 | 6379 | `REDIS_PORT` |
| Elasticsearch | 9200 | 9200 | `ELASTICSEARCH_PORT` |
| Grafana | 3000 | 3000 | `GRAFANA_PORT` |
| Prometheus | 9090 | 9090 | `PROMETHEUS_PORT` |
| Loki | 3100 | 3100 | `LOKI_PORT` |
| Jaeger UI | 16686 | 16686 | `JAEGER_UI_PORT` |
| OTLP gRPC/HTTP | 4317/4318 | 4317/4318 | `OTEL_GRPC_PORT`, `OTEL_HTTP_PORT` |

`SERVER_PORT` được Compose đặt cho từng app. Khi chạy trực tiếp từ IDE, port mặc định trong `application.yml` được dùng.

## 3. Key và security

| Biến | Bắt buộc | Mặc định | Ý nghĩa |
|---|---|---|---|
| `JWT_PRIVATE_KEY_BASE64` | Có | — | PKCS#8 DER base64; Identity ký user JWT |
| `JWT_PUBLIC_KEY_BASE64` | Có | — | X.509 DER base64; Gateway/service xác thực user JWT |
| `ORDER_SERVICE_JWT_PRIVATE_KEY_BASE64` | Compose | — | Order ký service JWT |
| `ORDER_SERVICE_JWT_PUBLIC_KEY_BASE64` | Compose | — | Callee xác thực Order |
| `CATALOG_SERVICE_JWT_PRIVATE_KEY_BASE64` | Compose | — | Catalog ký service JWT |
| `CATALOG_SERVICE_JWT_PUBLIC_KEY_BASE64` | Compose | — | Identity xác thực Catalog |
| `PAYMENT_SERVICE_JWT_PRIVATE_KEY_BASE64` | Compose | — | Payment ký service JWT |
| `PAYMENT_SERVICE_JWT_PUBLIC_KEY_BASE64` | Compose | — | Identity xác thực Payment |
| `NOTIFICATION_SERVICE_JWT_PRIVATE_KEY_BASE64` | Compose | — | Notification ký service JWT |
| `NOTIFICATION_SERVICE_JWT_PUBLIC_KEY_BASE64` | Compose | — | Identity xác thực Notification |
| `JWT_ACCESS_TOKEN_SECONDS` | Không | 900 | Tuổi access token |
| `JWT_REFRESH_TOKEN_SECONDS` | Không | 2592000 | Tuổi refresh session |
| `SERVICE_JWT_ISSUER` | Không | `ecommerce-internal` | Issuer chung |
| `SERVICE_JWT_TTL_SECONDS` | Không | 60 | Tuổi service token do Order phát |
| `SERVICE_JWT_KEY_ID` | Theo service | `<service>-key-1` | `kid` trong JWT; Compose ánh xạ biến riêng |
| `SERVICE_JWT_PUBLIC_KEYS` | Callee | — | Danh sách `kid=publicKey` phân tách `;` |
| `SERVICE_JWT_KEY_OWNERS` | Callee | — | Danh sách `kid=subject` phân tách `;` |
| `PAYMENT_CALLBACK_SECRET` | Có | — | HMAC secret cho mock callback |

Compose hỗ trợ override `IDENTITY_SERVICE_JWT_PUBLIC_KEYS`, `IDENTITY_SERVICE_JWT_KEY_OWNERS`, `CATALOG_SERVICE_JWT_PUBLIC_KEYS`, `CATALOG_SERVICE_JWT_KEY_OWNERS` và key ID riêng của từng service. Khi rotate key, triển khai public key mới ở callee trước, sau đó đổi private key/key ID ở caller, giữ key cũ trong thời gian lớn hơn TTL rồi mới gỡ.

## 4. Admin bootstrap

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `ADMIN_BOOTSTRAP_ENABLED` | `false` ở app; `.env.example` đặt `true` | Cho phép tạo/cập nhật admin lúc startup |
| `ADMIN_EMAIL` | rỗng | Email admin bootstrap |
| `ADMIN_PASSWORD` | rỗng | Mật khẩu; phải đổi giá trị mẫu |
| `ADMIN_FULL_NAME` | `System Administrator` | Tên hiển thị |

Chỉ bật bootstrap có kiểm soát. Production nên cấp secret qua secret manager và có quy trình rotate, không giữ plaintext trong file deployment.

## 5. Database

| Service | URL mặc định khi chạy host | Username/password phát triển |
|---|---|---|
| Identity | `jdbc:postgresql://localhost:5433/identity_db` | `identity_user` / `identity_password` |
| Catalog | `jdbc:postgresql://localhost:5433/catalog_db` | `catalog_user` / `catalog_password` |
| Order | `jdbc:postgresql://localhost:5433/order_db` | `order_user` / `order_password` |
| Payment | `jdbc:postgresql://localhost:5433/payment_db` | `payment_user` / `payment_password` |
| Notification | `jdbc:postgresql://localhost:5433/notification_db` | `notification_user` / `notification_password` |

Biến có mẫu `<SERVICE>_DB_URL`, `<SERVICE>_DB_USERNAME`, `<SERVICE>_DB_PASSWORD`. Compose đổi host thành `postgres:5432`. Flyway bật, location `classpath:db/migration`; Hibernate chỉ `validate`; Open Session in View tắt.

## 6. Discovery, Redis, Kafka và Elasticsearch

| Biến | Mặc định host local | Ý nghĩa |
|---|---|---|
| `EUREKA_URI` | `http://localhost:8761/eureka/` | Service registry |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker list |
| `SPRING_ELASTICSEARCH_URIS` | Spring default/local | Elasticsearch URI; Compose đặt `http://elasticsearch:9200` |
| `ORDER_KAFKA_GROUP_ID` | `order-service` | Consumer group Order |
| `PAYMENT_KAFKA_GROUP_ID` | `payment-service` | Consumer group Payment |
| `NOTIFICATION_KAFKA_GROUP_ID` | `notification-service` | Consumer group Notification |
| `ORDER_EVENTS_TOPIC` | `ecommerce.order.events` | Topic order |
| `PAYMENT_EVENTS_TOPIC` | `ecommerce.payment.events` | Topic payment |

Producer dùng `acks=all` và idempotence. Consumer dùng `enable-auto-commit=false`, `auto-offset-reset=earliest`, ack `manual_immediate`. Không đổi topic/group trên một service riêng lẻ nếu các producer/consumer còn lại chưa được đổi đồng bộ.

## 7. Gateway và rate limit

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `RATE_LIMIT_WINDOW_SECONDS` | 60 | Kích thước cửa sổ |
| `RATE_LIMIT_PUBLIC` | 120 | Request/cửa sổ cho public client |
| `RATE_LIMIT_AUTHENTICATED` | 240 | Request/cửa sổ cho principal đăng nhập |
| `RATE_LIMIT_LOGIN` | 1050 | Giới hạn route login |
| `RATE_LIMIT_CHECKOUT` | 30 | Giới hạn checkout |

Gateway dùng Redis để chia sẻ bộ đếm. Compose chuyển các biến `RATE_LIMIT_*` vào Gateway; giá trị mặc định vẫn như bảng trên. JMeter có thể gặp 429 nếu tải vượt các giá trị này; đó là hành vi bảo vệ, không phải lỗi service. Khi benchmark trên môi trường local cô lập, có thể override biến trong PowerShell rồi recreate Gateway; phải khôi phục cấu hình sau test và ghi limit đã dùng trong báo cáo.

## 8. Order và inventory

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `ORDER_SHIPPING_FEE` | 30000 | Phí giao hàng VND snapshot vào order |
| `PAYMENT_EXPIRY_SCAN_DELAY_MS` | 30000 | Chu kỳ quét đơn hết hạn |
| `INVENTORY_LOCK_WAIT_MS` | 2000 | Thời gian chờ distributed lock |
| `CATALOG_CONNECT_TIMEOUT_MS` | 2000 | Timeout kết nối Catalog |
| `CATALOG_READ_TIMEOUT_MS` | 3000 | Timeout đọc Catalog |
| `ACTIVE_ACCOUNT_CHECK_ENABLED` | `true` | Bật kiểm tra account ở Order |
| `OUTBOX_PUBLISH_DELAY_MS` | 5000 | Chu kỳ publisher Order/Payment |

Giảm delay giúp test nhanh hơn nhưng tăng truy vấn nền. Tăng lock wait có thể giảm conflict giả khi tải ngắn, đồng thời làm latency tail cao hơn.

## 9. Catalog

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `CATALOG_DEFAULT_IMAGE_URL` | Cloudinary placeholder | Ảnh khi product không có ảnh |
| `CATALOG_CACHE_TTL_SECONDS` | 300 | TTL Redis cache |
| `CATALOG_INDEX_DELAY_MS` | 3000 | Chu kỳ cập nhật search index |
| `CATALOG_REINDEX_DELAY_MS` | 2000 | Chu kỳ xử lý reindex job |

Giảm TTL/delay tăng độ mới nhưng tăng tải. Elasticsearch vẫn là read model; checkout đọc catalog nguồn.

## 10. Payment và VNPay

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `PAYMENT_CALLBACK_WINDOW_SECONDS` | 300 | Độ lệch thời gian cho mock callback |
| `VNPAY_ENABLED` | `false` | Bật tích hợp VNPay |
| `VNPAY_PAY_URL` | Sandbox URL | Cổng tạo thanh toán |
| `VNPAY_TMN_CODE` | rỗng | Mã terminal Sandbox |
| `VNPAY_HASH_SECRET` | rỗng | Secret xác thực query VNPay |
| `VNPAY_RETURN_URL` | rỗng | Backend return URL public |
| `VNPAY_IPN_URL` | rỗng | Backend IPN HTTPS public |
| `VNPAY_FRONTEND_RESULT_URL` | rỗng | Trang kết quả của frontend |
| `VNPAY_EXPIRE_MINUTES` | 15 | Thời hạn giao dịch |

Khi `VNPAY_ENABLED=true`, các giá trị credential và URL phải đầy đủ. IPN phải truy cập được từ Internet qua HTTPS; localhost chỉ kiểm tra được bước tạo URL. Xem [VNPay Sandbox](vnpay-sandbox.md).

## 11. Notification

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `NOTIFICATION_MAX_ATTEMPTS` | 3 | Số lần delivery |
| `NOTIFICATION_RETRY_SECONDS` | 30 | Khoảng retry |
| `NOTIFICATION_DELIVERY_DELAY_MS` | 5000 | Chu kỳ worker |

Hiện delivery là demo log. `delivery_attempts` vẫn lưu bằng chứng mỗi lần thử để mở rộng sang email/SMS/push.

## 12. Logging, metrics và tracing

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `logstash` | JSON structured console log |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | 1.0 | Tỷ lệ trace từ 0 đến 1 |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | `http://otel-collector:4318/v1/traces` | OTLP HTTP endpoint |
| `GRAFANA_ADMIN_USER` | `admin` | User Grafana local |
| `GRAFANA_ADMIN_PASSWORD` | `admin` | Password Grafana local; phải đổi ngoài máy cá nhân |

Production thường giảm sampling và áp retention/storage riêng. Health và Prometheus được expose; không mở thêm actuator endpoint nhạy cảm nếu chưa có bảo vệ.

## 13. Kiểm tra cấu hình trước khi chạy

```powershell
docker compose config --quiet
docker compose config > build/resolved-compose.yml
docker compose up -d --wait
docker compose ps
```

File resolved có thể chứa secret đã nội suy; không commit và xóa theo quy trình bảo mật. Với lỗi startup, kiểm tra theo thứ tự: biến bắt buộc, health dependency, database migration, Eureka registration, rồi route Gateway.
