# Xác minh giá khi checkout

Order service không dùng `displayedUnitPrice` trong Cart làm giá cuối cùng.
Trước khi giữ hàng, Order gọi Catalog service qua:

```http
POST /internal/v1/skus/checkout-items
Authorization: Bearer <JWT khách hàng>
```

Body:

```json
{"skuIds":[1,2]}
```

Catalog chỉ trả SKU khi Product và SKU đều `ACTIVE`, cùng giá hiện tại,
currency và `productVersion`. Endpoint nội bộ không được route qua Gateway.

Checkout public nhận `cartVersion` và `expectedItems`. Order kiểm tra:

- `cartVersion` vẫn khớp Cart hiện tại.
- Tập SKU và số lượng trong `expectedItems` khớp toàn bộ Cart.
- Giá Catalog khớp giá khách xác nhận và giá đang lưu trong Cart.
- Tất cả SKU đều bán được.

Order chỉ gọi `InventoryService.reserve` sau khi tất cả kiểm tra thành công.
Giá từ Catalog được dùng để tạo `OrderItem.unitPrice`, tính `lineTotal` và
`Order.totalAmount`.

| Trường hợp | HTTP | Mã lỗi |
|---|---:|---|
| Giá đã thay đổi | 409 | `PRICE_CHANGED` |
| Cart version hoặc nội dung Cart thay đổi | 409 | `VERSION_CONFLICT`/`CART_CHANGED` |
| SKU/Product không còn ACTIVE | 409 | `SKU_NOT_SELLABLE` |
| Catalog không phản hồi | 503 | `CATALOG_UNAVAILABLE` |

Các lỗi trên xảy ra trước reserve nên không giữ hàng và không tạo Order.
OpenFeign tắt retry tự động, timeout mặc định 2 giây kết nối và 3 giây đọc.

Identity checkout-context sử dụng `POST /internal/v1/users/current/checkout-context`.
Order truyền `addressId` và bearer token của user; Identity kiểm tra user ACTIVE,
quyền sở hữu địa chỉ rồi trả snapshot. Order lưu snapshot vào Order trước khi
phát `OrderCreated`, nên thay đổi địa chỉ sau checkout không thay đổi đơn cũ.

## Idempotency checkout

Checkout public dùng `POST /api/v1/orders/checkout` và bắt buộc header
`Idempotency-Key`. Order lưu hash payload cùng order. Retry cùng key và cùng
payload trả order đã tạo kèm header `Idempotency-Replayed: true`; cùng key
nhưng payload khác trả `409 IDEMPOTENCY_KEY_REUSED`.

Các request checkout đồng thời cùng user được serialize bằng pessimistic lock
trên Cart và unique index `(user_id, idempotency_key)`. Request chạy sau kiểm tra
lại idempotency sau khi nhận lock để replay order đã commit.
