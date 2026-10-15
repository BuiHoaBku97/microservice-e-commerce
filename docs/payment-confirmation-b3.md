# B3 — xác nhận thanh toán và chốt tồn kho

Payment service phát `PaymentSucceeded` hoặc `PaymentFailed` với `eventId`, `paymentId`,
`orderId`, `amount`, `currency` và `paymentDueAt`. Order service kiểm tra toàn bộ các trường
này trước khi xử lý.

Callback hợp lệ khóa order và các SKU theo thứ tự ổn định, giảm `reserved` và `onHand`, chuyển
order sang `CONFIRMED`, lưu `payment_event_history` và phát `OrderConfirmed`. Callback mismatch
hoặc quá hạn giải phóng reservation, chuyển order sang `CANCELLED`, phát `OrderCancelled` và
`OrderConfirmationRejected`, không trừ `onHand`. Payment đã nhận tiền chuyển `REQUIRES_REVIEW`.

`eventId` và `paymentId` có unique constraint để replay hoặc callback đồng thời không chốt kho
hai lần. Mục service token riêng cho internal API vẫn thuộc M2 và chưa nằm trong B3.
