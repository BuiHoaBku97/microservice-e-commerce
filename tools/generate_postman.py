"""Generate the local Postman acceptance collection and environment.

The request sequence mirrors tests/smoke/compose-smoke.ps1 so identifiers and
optimistic-lock versions are captured from real responses instead of being
hard-coded.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"


def script(lines: list[str]) -> dict:
    return {"type": "text/javascript", "exec": lines}


def event(kind: str, lines: list[str]) -> dict:
    return {"listen": kind, "script": script(lines)}


def request(
    name: str,
    method: str,
    path: str,
    *,
    body: str | None = None,
    auth: str | None = None,
    headers: list[dict] | None = None,
    tests: list[str] | None = None,
    pre_request: list[str] | None = None,
    description: str | None = None,
) -> dict:
    request_headers = list(headers or [])
    if auth:
        request_headers.insert(0, {"key": "Authorization", "value": f"Bearer {{{{{auth}}}}}", "type": "text"})
    if body is not None:
        request_headers.append({"key": "Content-Type", "value": "application/json", "type": "text"})

    value = {
        "name": name,
        "request": {
            "method": method,
            "header": request_headers,
            # Keep this as a raw URL string. Postman can drop path segments when
            # an object URL has `host` but an incomplete `path` array.
            "url": "{{baseUrl}}" + path,
        },
        "response": [],
    }
    if body is not None:
        value["request"]["body"] = {"mode": "raw", "raw": body, "options": {"raw": {"language": "json"}}}
    if description:
        value["request"]["description"] = description

    events = []
    if pre_request:
        events.append(event("prerequest", pre_request))
    if tests:
        events.append(event("test", tests))
    if events:
        value["event"] = events
    return value


def ok(status: int, *extra: str) -> list[str]:
    return [
        f"pm.test('HTTP {status}', () => pm.response.to.have.status({status}));",
        *extra,
    ]


new_run = [
    "const runId = Date.now() + '-' + Math.floor(Math.random() * 10000);",
    "pm.environment.set('runId', runId);",
    "if (!pm.environment.get('customerEmail')) {",
    "  pm.environment.set('customerEmail', `postman-${runId}@example.test`);",
    "}",
    "pm.environment.set('categoryCode', `PM-${runId}`);",
    "pm.environment.set('categorySlug', `postman-${runId}`);",
    "pm.environment.set('skuCode', `PM-SKU-${runId}`);",
    "pm.environment.set('inventoryInitKey', `postman-${runId}-init`);",
    "pm.environment.set('inventoryReceiptKey', `postman-${runId}-receipt`);",
    "pm.environment.set('checkoutKey', `postman-${runId}-checkout`);",
]


collection = {
    "info": {
        "name": "E-commerce - Local acceptance flow",
        "description": (
            "Dữ liệu test chạy tuần tự cho API trong docs/openapi.yaml. "
            "Chọn environment 'E-commerce local', chạy collection từ trên xuống bằng Collection Runner. "
            "Collection tự tạo email/code duy nhất và lưu token, ID, version từ response. "
            "Postman gọi Gateway tại localhost:8080; service ID identity-service chỉ là định danh Eureka nội bộ "
            "và không thêm vào URL."
        ),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "event": [
        event("prerequest", [
            "if (!pm.environment.get('runId')) {",
            *[f"  {line}" for line in new_run],
            "}",
        ]),
    ],
    "item": [
        {
            "name": "00 - Authentication",
            "description": "Tạo customer mới và lấy JWT cho customer lẫn admin.",
            "item": [
                request(
                    "Register unique customer",
                    "POST",
                    "/api/v1/auth/register",
                    body='''{\n  "email": "{{customerEmail}}",\n  "password": "{{customerPassword}}",\n  "fullName": "Postman Test Customer"\n}''',
                    tests=ok(201,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('customerId', data.id);",
                        "pm.environment.set('userVersion', data.version);"),
                ),
                request(
                    "Login customer",
                    "POST",
                    "/api/v1/auth/login",
                    body='''{\n  "email": "{{customerEmail}}",\n  "password": "{{customerPassword}}"\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('customerToken', data.accessToken);",
                        "pm.environment.set('refreshToken', data.refreshToken);"),
                ),
                request(
                    "Refresh customer token",
                    "POST",
                    "/api/v1/auth/refresh",
                    body='''{\n  "refreshToken": "{{refreshToken}}"\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('customerToken', data.accessToken);",
                        "pm.environment.set('refreshToken', data.refreshToken);"),
                ),
                request(
                    "Login admin",
                    "POST",
                    "/api/v1/auth/login",
                    body='''{\n  "email": "{{adminEmail}}",\n  "password": "{{adminPassword}}"\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('adminToken', data.accessToken);"),
                    description="adminEmail/adminPassword phải trùng ADMIN_EMAIL/ADMIN_PASSWORD trong file .env của Docker Compose.",
                ),
            ],
        },
        {
            "name": "01 - Customer and address",
            "item": [
                request(
                    "Get current customer",
                    "GET",
                    "/api/v1/users/me",
                    auth="customerToken",
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('customerId', data.id);",
                        "pm.environment.set('userVersion', data.version);"),
                ),
                request(
                    "Create shipping address",
                    "POST",
                    "/api/v1/users/me/addresses",
                    auth="customerToken",
                    body='''{\n  "recipientName": "Nguyen Van Postman",\n  "recipientPhone": "0900000000",\n  "addressLine": "1 Nguyen Hue",\n  "ward": "Ben Nghe",\n  "district": "District 1",\n  "province": "Ho Chi Minh City",\n  "defaultAddress": true\n}''',
                    tests=ok(201,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('addressId', data.id);"),
                ),
            ],
        },
        {
            "name": "02 - Admin catalog and inventory",
            "description": "Tạo riêng category, product và SKU cho mỗi lần chạy để không phụ thuộc dữ liệu cũ.",
            "item": [
                request(
                    "Create category",
                    "POST",
                    "/api/v1/admin/categories",
                    auth="adminToken",
                    body='''{\n  "code": "{{categoryCode}}",\n  "name": "Postman Category {{runId}}",\n  "slug": "{{categorySlug}}"\n}''',
                    tests=ok(201,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('categoryId', data.id);"),
                ),
                request(
                    "Create product",
                    "POST",
                    "/api/v1/admin/products",
                    auth="adminToken",
                    body='''{\n  "categoryId": {{categoryId}},\n  "name": "Postman Shirt {{runId}}",\n  "description": "Product generated by the Postman acceptance flow",\n  "imageUrls": []\n}''',
                    tests=ok(201,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('productId', data.id);",
                        "pm.environment.set('productVersion', data.version);"),
                ),
                request(
                    "Create SKU",
                    "POST",
                    "/api/v1/admin/products/{{productId}}/skus",
                    auth="adminToken",
                    body='''{\n  "code": "{{skuCode}}",\n  "color": "black",\n  "size": "M",\n  "price": {{unitPrice}}\n}''',
                    tests=ok(201,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('skuId', data.id);"),
                ),
                request(
                    "Get product version",
                    "GET",
                    "/api/v1/admin/products/{{productId}}",
                    auth="adminToken",
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('productVersion', data.version);"),
                ),
                request(
                    "Activate product",
                    "PUT",
                    "/api/v1/admin/products/{{productId}}/status",
                    auth="adminToken",
                    body='''{\n  "status": "ACTIVE",\n  "version": {{productVersion}}\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('productVersion', data.version);"),
                ),
                request(
                    "Initialize inventory",
                    "POST",
                    "/api/v1/admin/inventory/{{skuId}}/initialize",
                    auth="adminToken",
                    headers=[{"key": "Idempotency-Key", "value": "{{inventoryInitKey}}", "type": "text"}],
                    tests=ok(200),
                ),
                request(
                    "Receive inventory",
                    "POST",
                    "/api/v1/admin/inventory/{{skuId}}/receipts",
                    auth="adminToken",
                    headers=[{"key": "Idempotency-Key", "value": "{{inventoryReceiptKey}}", "type": "text"}],
                    body='''{\n  "quantity": {{stockQuantity}},\n  "reason": "Postman test stock"\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('inventoryVersion', data.version);",
                        "pm.environment.set('inventoryOnHand', data.onHand);"),
                ),
                request(
                    "Replay same receipt (idempotency test)",
                    "POST",
                    "/api/v1/admin/inventory/{{skuId}}/receipts",
                    auth="adminToken",
                    headers=[{"key": "Idempotency-Key", "value": "{{inventoryReceiptKey}}", "type": "text"}],
                    body='''{\n  "quantity": {{stockQuantity}},\n  "reason": "Postman test stock"\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.test('Stock was not added twice', () => pm.expect(String(data.onHand)).to.eql(pm.environment.get('inventoryOnHand')));"),
                ),
                request(
                    "Check public availability",
                    "GET",
                    "/api/v1/inventory/availability?skuIds={{skuId}}",
                    tests=ok(200,
                        "const rows = pm.response.json().data;",
                        "pm.test('SKU is available', () => pm.expect(rows[0].available).to.be.above(0));"),
                ),
            ],
        },
        {
            "name": "03 - Cart, checkout and payment",
            "item": [
                request(
                    "Get empty cart",
                    "GET",
                    "/api/v1/cart",
                    auth="customerToken",
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('cartVersion', data.version);"),
                ),
                request(
                    "Add SKU to cart",
                    "POST",
                    "/api/v1/cart/items?version={{cartVersion}}",
                    auth="customerToken",
                    body='''{\n  "skuId": {{skuId}},\n  "quantity": 1,\n  "displayedUnitPrice": {{unitPrice}}\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('cartVersion', data.version);"),
                ),
                request(
                    "Checkout",
                    "POST",
                    "/api/v1/orders/checkout",
                    auth="customerToken",
                    headers=[{"key": "Idempotency-Key", "value": "{{checkoutKey}}", "type": "text"}],
                    body='''{\n  "cartVersion": {{cartVersion}},\n  "addressId": {{addressId}},\n  "paymentMethod": "MOCK_ONLINE",\n  "customerNote": "Created from Postman",\n  "expectedItems": [\n    {\n      "skuId": {{skuId}},\n      "quantity": 1,\n      "expectedUnitPrice": {{unitPrice}}\n    }\n  ]\n}''',
                    tests=ok(201,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('orderId', data.id);",
                        "pm.environment.set('orderVersion', data.version);",
                        "pm.test('Order total is correct', () => pm.expect(data.totalAmount).to.eql(data.subtotal + data.shippingFee));"),
                ),
                request(
                    "Get payment (wait for async creation)",
                    "GET",
                    "/api/v1/orders/{{orderId}}/payment",
                    auth="customerToken",
                    tests=[
                        "const retry = Number(pm.environment.get('paymentRetry') || 0);",
                        "if (pm.response.code === 200) {",
                        "  const data = pm.response.json().data;",
                        "  pm.environment.set('paymentId', data.id);",
                        "  pm.environment.unset('paymentRetry');",
                        "  pm.test('Payment was created', () => pm.expect(data.id).to.exist);",
                        "} else if (retry < 10) {",
                        "  pm.environment.set('paymentRetry', retry + 1);",
                        "  pm.execution.setNextRequest('Get payment (wait for async creation)');",
                        "  setTimeout(() => {}, 1000);",
                        "} else {",
                        "  pm.test('Payment was created within 10 retries', () => pm.expect.fail(`Last HTTP status: ${pm.response.code}`));",
                        "}",
                    ],
                    description="Trong Collection Runner, request tự thử lại tối đa 10 lần vì payment được tạo bất đồng bộ qua Kafka.",
                ),
                request(
                    "Simulate successful payment",
                    "POST",
                    "/api/v1/admin/demo/payments/{{paymentId}}/simulate",
                    auth="adminToken",
                    body='''{\n  "result": "SUCCESS"\n}''',
                    tests=ok(200),
                ),
                request(
                    "Get confirmed order (wait for async update)",
                    "GET",
                    "/api/v1/orders/{{orderId}}",
                    auth="customerToken",
                    tests=[
                        "pm.test('HTTP 200', () => pm.response.to.have.status(200));",
                        "const data = pm.response.json().data;",
                        "const retry = Number(pm.environment.get('orderRetry') || 0);",
                        "if (data.status === 'CONFIRMED') {",
                        "  pm.environment.set('orderVersion', data.version);",
                        "  pm.environment.unset('orderRetry');",
                        "  pm.test('Order is confirmed', () => pm.expect(data.status).to.eql('CONFIRMED'));",
                        "} else if (retry < 10) {",
                        "  pm.environment.set('orderRetry', retry + 1);",
                        "  pm.execution.setNextRequest('Get confirmed order (wait for async update)');",
                        "  setTimeout(() => {}, 1000);",
                        "} else {",
                        "  pm.test('Order confirmed within 10 retries', () => pm.expect.fail(`Last status: ${data.status}`));",
                        "}",
                    ],
                ),
                request(
                    "Ship order",
                    "POST",
                    "/api/v1/admin/orders/{{orderId}}/ship",
                    auth="adminToken",
                    body='''{\n  "version": {{orderVersion}}\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('orderVersion', data.version);",
                        "pm.test('Order is shipping', () => pm.expect(data.status).to.eql('SHIPPING'));"),
                ),
                request(
                    "Complete order",
                    "POST",
                    "/api/v1/admin/orders/{{orderId}}/complete",
                    auth="adminToken",
                    body='''{\n  "version": {{orderVersion}}\n}''',
                    tests=ok(200,
                        "const data = pm.response.json().data;",
                        "pm.environment.set('orderVersion', data.version);",
                        "pm.test('Order is completed', () => pm.expect(data.status).to.eql('COMPLETED'));"),
                ),
            ],
        },
        {
            "name": "04 - Verification",
            "item": [
                request("Get order history", "GET", "/api/v1/orders/{{orderId}}/history", auth="customerToken", tests=ok(200)),
                request("List customer notifications", "GET", "/api/v1/notifications?page=0&size=20", auth="customerToken", tests=ok(200)),
                request("Get stock movements", "GET", "/api/v1/admin/inventory/{{skuId}}/movements?page=0&size=20", auth="adminToken", tests=ok(200)),
                request("Get payment history", "GET", "/api/v1/admin/payments/{{paymentId}}/history?page=0&size=20", auth="adminToken", tests=ok(200)),
            ],
        },
    ],
}


def variable(key: str, value: str, *, secret: bool = False) -> dict:
    result = {"key": key, "value": value, "enabled": True}
    if secret:
        result["type"] = "secret"
    return result


environment = {
    "name": "E-commerce local",
    "values": [
        variable("baseUrl", "http://localhost:8080"),
        variable("adminEmail", "admin@example.test"),
        variable("adminPassword", "ChangeMe123!", secret=True),
        variable("customerEmail", ""),
        variable("customerPassword", "PostmanPass!42", secret=True),
        variable("unitPrice", "125000"),
        variable("stockQuantity", "10"),
        *[variable(key, "") for key in (
            "runId", "customerToken", "adminToken", "refreshToken", "customerId", "userVersion",
            "addressId", "categoryCode", "categorySlug", "categoryId", "productId", "productVersion",
            "skuCode", "skuId", "inventoryInitKey", "inventoryReceiptKey", "inventoryVersion",
            "inventoryOnHand", "cartVersion", "checkoutKey", "orderId", "orderVersion", "paymentId",
        )],
    ],
    "_postman_variable_scope": "environment",
    "_postman_exported_using": "tools/generate_postman.py",
}


outputs = {
    DOCS / "e-commerce.postman_collection.json": collection,
    DOCS / "e-commerce.postman_environment.json": environment,
}
for target, content in outputs.items():
    target.write_text(json.dumps(content, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {target.relative_to(ROOT)}")
