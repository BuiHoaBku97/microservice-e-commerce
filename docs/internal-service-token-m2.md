# M2 — service token cho internal API

Các endpoint `/internal/**` của Catalog và Identity yêu cầu header:

```text
X-Internal-Service-Token: <INTERNAL_SERVICE_TOKEN>
```

Order service tự thêm header này cho Feign calls tới Catalog và Identity. JWT của user vẫn
được truyền qua Identity để endpoint checkout-context xác định user; service token chỉ chứng
minh request đi từ service nội bộ.

Token không có giá trị mặc định trong Docker Compose. Khởi động stack bằng cách đặt biến môi
trường `INTERNAL_SERVICE_TOKEN` trước khi chạy compose. Request thiếu token hoặc dùng token sai
sẽ không được cấp `SCOPE_INTERNAL_SERVICE`.
