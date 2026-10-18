# VNPay Sandbox

## Luồng triển khai

Checkout lưu `paymentMethod` là `MOCK_ONLINE` hoặc `VNPAY`. `OrderCreated` truyền method qua Kafka; payment service tạo payment tương ứng. Client gọi:

```http
POST /api/v1/orders/{orderId}/payment/initialize
```

API trả `nextAction=SIMULATE` cho mock hoặc `nextAction=REDIRECT` cùng `paymentUrl` cho VNPay. Client redirect đến URL Sandbox khi nhận `REDIRECT`.

VNPay gọi `GET /api/v1/payments/vnpay/ipn` để cập nhật PostgreSQL. Endpoint kiểm tra HMAC-SHA512, `vnp_TmnCode`, `vnp_TxnRef`, số tiền và transaction state trước khi cập nhật payment, ghi history và phát outbox event. `GET /api/v1/payments/vnpay/return` chỉ kiểm tra chữ ký và redirect trình duyệt về frontend; client phải đọc lại payment vì Return có thể đến trước IPN.

## Cấu hình

Copy các biến sau vào `.env` thật và điền thông tin VNPay cấp:

```dotenv
VNPAY_ENABLED=true
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_TMN_CODE=<tmn-code>
VNPAY_HASH_SECRET=<hash-secret>
VNPAY_RETURN_URL=https://<public-host>/api/v1/payments/vnpay/return
VNPAY_IPN_URL=https://<public-host>/api/v1/payments/vnpay/ipn
VNPAY_FRONTEND_RESULT_URL=https://<frontend-host>/payment/result
```

`VNPAY_IPN_URL` phải truy cập được từ Internet. Khi chạy local, dùng HTTPS tunnel và cấu hình đúng URL tunnel trong Sandbox merchant profile/merchant IPN configuration; chỉ đặt biến `.env` không làm VNPay tự biết địa chỉ callback. URL này không cần xuất hiện trong payment URL, vì VNPay lấy IPN endpoint từ cấu hình merchant.

## Database

- Order migration `V20__allow_vnpay_payment_method.sql` mở rộng constraint payment method.
- Payment migration `V12__add_payment_method.sql` lưu method trên payment.
- Payment migration `V13__create_payment_transactions.sql` lưu từng `txnRef`, trạng thái, mã phản hồi, mã giao dịch ngân hàng và thời gian thanh toán.

## Correlation và đối soát

HTTP request có `X-Correlation-ID` do filter hiện tại tạo hoặc truyền tiếp. IPN không cần JWT và thường không có header này nên filter tự sinh ID. Log IPN ghi thêm `orderId`, `paymentId`, `txnRef`, `providerTransactionId`, `responseCode` và `transactionStatus`. Khi truy vết xuyên Kafka, dùng `paymentId`/`txnRef` cùng `eventId` vì mỗi HTTP callback và Kafka consumer có thể có correlation ID riêng.

## Kiểm thử

```powershell
.\gradlew.bat :payment-service:test :order-service:test :gateway:test
```

Các test cần giữ: URL ký đúng, amount nhân 100, IPN sai chữ ký không ghi DB, IPN retry không phát event lần hai, payment thất bại không xác nhận order và Return không tự đánh dấu thanh toán thành công.

### Trạng thái đã chạy

- `.\gradlew.bat test`: PASS, gồm các test hồi quy của catalog, identity, notification, order, payment, gateway và event-flow test thường.
- `.\gradlew.bat :payment-service:test :order-service:test :gateway:test`: PASS.
- `.\gradlew.bat :event-flow-tests:integrationTest --tests com.ecommerce.integrations.EventFlowIntegrationTest.vnpayIpn_shouldUpdatePostgresAndEnqueuePaymentEvent`: PASS với PostgreSQL/Kafka/Redis Testcontainers. Kịch bản xác nhận IPN ký đúng, payment chuyển `SUCCESS`, transaction chuyển `SUCCESS` và payment outbox được tạo.
- Test unit `VnpayServiceTest`: PASS, kiểm tra amount nhân 100 và verify HMAC-SHA512.
- Smoke Compose `tests/smoke/compose-smoke.ps1`: PASS với `MOCK_ONLINE`, bao gồm checkout, gọi `payment/initialize`, simulate, confirm, ship, complete, notification và inventory.
- Smoke Compose `tests/smoke/compose-smoke.ps1 -PaymentMethod VNPAY`: PASS tạo URL redirect với credential Sandbox đã cấu hình trong `.env`; chưa nghiệm thu IPN thật vì `VNPAY_IPN_URL` hiện vẫn là localhost và chưa có HTTPS public tunnel.

Smoke chuẩn bị VNPay dùng:

```powershell
.\tests\smoke\compose-smoke.ps1 -PaymentMethod VNPAY
```

Lệnh này cần `VNPAY_ENABLED`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, payment URL và các callback URL trong `.env`. Khi cấu hình hợp lệ, smoke sẽ kiểm tra `initialize` trả `nextAction=REDIRECT` và in `paymentUrl` để tiếp tục giao dịch Sandbox trên trình duyệt. Hiện workspace chưa có các giá trị merchant/public HTTPS nên chưa chạy được nhánh redirect VNPay thật.

### Phần chưa thể nghiệm thu với Sandbox thật

Đã có `TMN_CODE` và `HASH_SECRET` Sandbox trong `.env`, nhưng chưa chạy hoàn chỉnh giao dịch thật với callback IPN vì cần đổi `VNPAY_IPN_URL` sang HTTPS public được khai báo trong merchant Sandbox. Khi có tunnel, cần kiểm thử thêm redirect trình duyệt, Return đến trước IPN, IPN retry, giao dịch thất bại và callback amount/signature sai.

Lệnh integration đầy đủ hiện còn hai test event-flow cũ thất bại độc lập với VNPay: callback thành công với hai `eventId` khác nhau đang tạo hai payment history, và một scenario replay không materialize payment đúng kỳ vọng. Đây là regression tồn tại trong workflow order/payment hiện tại; test VNPay mới chạy riêng đã PASS và không bị hai lỗi này che khuất.
