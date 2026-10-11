# Tra cứu thanh toán theo đơn hàng — B1

## API khách hàng

`GET /api/v1/orders/{orderId}/payment`, qua Gateway cổng 8080.
Gửi `Authorization: Bearer <accessToken>` của khách có role `CUSTOMER`.
Danh tính lấy từ JWT đã xác minh; không lấy `userId` từ query hoặc header tự đặt.

| Trường hợp | HTTP | Kết quả |
|---|---|---|
| Có payment thuộc khách | 200 | `data` là PaymentResponse, không trả entity |
| Payment chưa được Payment service xử lý hoặc thuộc khách khác | 404 | `RESOURCE_NOT_FOUND` |
| Thiếu/sai/hết hạn JWT | 401 | `UNAUTHENTICATED` |
| Thiếu role CUSTOMER | 403 | `FORBIDDEN` |
| orderId không hợp lệ | 400 | `VALIDATION_ERROR` |

Response thành công có `code`, `message`, `data`, `requestId`. Response lỗi thêm `errors`.
GET chỉ đọc, không tạo payment và không ghi outbox. Payment chỉ được tạo từ Kafka.
Nếu `OrderCreated` chưa được consumer xử lý, API trả 404; client có thể đọc lại sau.
API cũ `/api/v1/payments/orders/{orderId}` đã bỏ; không còn trả dữ liệu.
ADMIN không được bỏ qua quyền sở hữu bằng API khách.

## Hạn thanh toán và migration

Order lưu `paymentDueAt` khi tạo đơn, mặc định 900 giây;
cấu hình `app.order.payment-timeout-seconds` để đổi cho đơn mới.
Migration Order V4 thêm `payment_due_at`, backfill đơn cũ bằng `created_at + 15 phút`.
Không cập nhật hạn khi tra cứu; B1 chưa tự hủy đơn hết hạn.

## Kiểm chứng và phần còn lại

Test gồm service ownership, MockMvc với JWT/SecurityFilterChain thật, route đọc từ YAML
với predicate Gateway thật và hạn thanh toán ổn định. Repository trong service test được mock.

Chạy bằng JDK 21:

```powershell
.\gradlew.bat :payment-service:test :order-service:test :gateway:test :payment-service:bootJar :order-service:bootJar
```

Chưa nghiệm thu PostgreSQL migration và luồng xuyên container vì Docker daemon chưa
hoạt động. Kiểm tra tài khoản ACTIVE còn thuộc B5; chuẩn hóa Order/Payment state và
vòng đời thanh toán còn thuộc B3/B4/C3. B1 chỉ hoàn thành chức năng query/ownership;
chưa thể coi toàn bộ payment contract đã nghiệm thu.

Kết quả kiểm tra B1: **82 test PASS** (Payment 36, Order 31, Gateway 15;
30 test mới), cả hai task bootJar thành công. Không có failure/error.
