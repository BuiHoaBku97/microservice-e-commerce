"""Generate the checked-in OpenAPI contract from the implemented REST operation inventory."""

from pathlib import Path
import yaml


ROOT = Path(__file__).resolve().parents[1]


def op(operation_id, summary, tag, *, security=None, params=None, body=None,
       success="200", success_description="Successful response"):
    value = {
        "operationId": operation_id,
        "summary": summary,
        "tags": [tag],
        "responses": {
            success: {
                "description": success_description,
                **({} if success == "204" else {
                    "content": {"application/json": {"schema": {"$ref": "#/components/schemas/ApiResponse"}}}
                }),
            },
            "400": {"$ref": "#/components/responses/BadRequest"},
            "401": {"$ref": "#/components/responses/Unauthenticated"},
            "403": {"$ref": "#/components/responses/Forbidden"},
            "404": {"$ref": "#/components/responses/NotFound"},
            "409": {"$ref": "#/components/responses/Conflict"},
        },
    }
    if security is not None:
        value["security"] = security
    if params:
        value["parameters"] = params
    if body:
        value["requestBody"] = {
            "required": True,
            "content": {"application/json": {"schema": {"$ref": f"#/components/schemas/{body}"}}},
        }
    if success == "204":
        value["responses"].pop("400", None)
    return value


USER = [{"userJwt": []}]
SERVICE = [{"serviceJwt": []}]
SERVICE_AND_USER = [{"serviceJwt": [], "userJwt": []}]
PUBLIC = []


def path_param(name):
    return {"name": name, "in": "path", "required": True,
            "schema": {"type": "integer", "format": "int64", "minimum": 1}}


def query(name, kind="string", **schema):
    return {"name": name, "in": "query", "required": False,
            "schema": {"type": kind, **schema}}


PAGE = [query("page", "integer", minimum=0, default=0),
        query("size", "integer", minimum=1, maximum=100, default=20)]
IDEMPOTENCY = {"$ref": "#/components/parameters/IdempotencyKey"}

paths = {}


def add(path, method, *args, **kwargs):
    paths.setdefault(path, {})[method] = op(*args, **kwargs)


# Identity
add("/api/v1/auth/register", "post", "register", "Register customer", "Identity",
    security=PUBLIC, body="RegisterRequest", success="201", success_description="Customer created")
add("/api/v1/auth/login", "post", "login", "Login", "Identity", security=PUBLIC, body="LoginRequest")
add("/api/v1/auth/refresh", "post", "refreshToken", "Rotate refresh token", "Identity",
    security=PUBLIC, body="RefreshTokenRequest")
add("/api/v1/auth/logout", "post", "logout", "Revoke refresh session", "Identity",
    security=PUBLIC, body="RefreshTokenRequest", success="204", success_description="Session revoked")
add("/api/v1/users/me", "get", "getCurrentUser", "Get current profile", "Identity", security=USER)
add("/api/v1/users/me", "patch", "updateCurrentUser", "Update current profile", "Identity",
    security=USER, body="UpdateProfileRequest")
add("/api/v1/users/me/addresses", "get", "listAddresses", "List owned addresses", "Identity", security=USER)
add("/api/v1/users/me/addresses", "post", "createAddress", "Create address", "Identity",
    security=USER, body="AddressRequest", success="201", success_description="Address created")
add("/api/v1/users/me/addresses/{addressId}", "patch", "updateAddress", "Update owned address", "Identity",
    security=USER, params=[path_param("addressId")], body="AddressRequest")
add("/api/v1/users/me/addresses/{addressId}", "delete", "deleteAddress", "Delete owned address", "Identity",
    security=USER, params=[path_param("addressId")], success="204", success_description="Address deleted")
add("/api/v1/users/me/addresses/{addressId}/default", "put", "setDefaultAddress", "Set default address", "Identity",
    security=USER, params=[path_param("addressId")])
add("/api/v1/admin/users", "get", "listUsers", "Filter users", "Identity", security=USER,
    params=[query("status"), query("keyword"), *PAGE])
add("/api/v1/admin/users/{id}", "get", "getUser", "Get user", "Identity", security=USER,
    params=[path_param("id")])
add("/api/v1/admin/users/{id}/lock", "post", "lockUser", "Lock user", "Identity", security=USER,
    params=[path_param("id")], body="ChangeUserStatusRequest")
add("/api/v1/admin/users/{id}/unlock", "post", "unlockUser", "Unlock user", "Identity", security=USER,
    params=[path_param("id")], body="ChangeUserStatusRequest")

# Catalog
add("/api/v1/categories", "get", "listCategories", "List active categories", "Catalog", security=PUBLIC,
    params=PAGE)
add("/api/v1/products/search", "get", "searchProducts", "Search sellable products", "Catalog", security=PUBLIC,
    params=[query("q"), query("categoryId", "integer", format="int64"),
            query("minPrice", "integer", format="int64", minimum=0),
            query("maxPrice", "integer", format="int64", minimum=0),
            query("sort", enum=["relevance", "priceAsc", "priceDesc", "newest"]), *PAGE])
add("/api/v1/products/{id}", "get", "getProduct", "Get active product", "Catalog", security=PUBLIC,
    params=[path_param("id")])
add("/api/v1/admin/categories", "get", "listAdminCategories", "List all categories", "Catalog", security=USER)
add("/api/v1/admin/categories", "post", "createCategory", "Create category", "Catalog", security=USER,
    body="GenericObject", success="201", success_description="Category created")
add("/api/v1/admin/categories/{id}", "patch", "updateCategory", "Update category", "Catalog", security=USER,
    params=[path_param("id")], body="GenericObject")
add("/api/v1/admin/products", "get", "listAdminProducts", "List all products", "Catalog", security=USER)
add("/api/v1/admin/products", "post", "createProduct", "Create draft product", "Catalog", security=USER,
    body="GenericObject", success="201", success_description="Product created")
add("/api/v1/admin/products/{id}", "get", "getAdminProduct", "Get product for administration", "Catalog",
    security=USER, params=[path_param("id")])
add("/api/v1/admin/products/{id}", "patch", "updateProduct", "Update product", "Catalog",
    security=USER, params=[path_param("id")], body="GenericObject")
add("/api/v1/admin/products/{id}/status", "put", "changeProductStatus", "Change product status", "Catalog",
    security=USER, params=[path_param("id")], body="VersionedStatusRequest")
add("/api/v1/admin/products/{productId}/skus", "post", "createSku", "Create SKU", "Catalog",
    security=USER, params=[path_param("productId")], body="GenericObject", success="201", success_description="SKU created")
add("/api/v1/admin/skus/{id}", "patch", "updateSku", "Update SKU", "Catalog", security=USER,
    params=[path_param("id")], body="GenericObject")
add("/api/v1/admin/skus/{id}/status", "put", "changeSkuStatus", "Change SKU status", "Catalog",
    security=USER, params=[path_param("id")], body="VersionedStatusRequest")
add("/api/v1/admin/skus/{id}/price-history", "get", "getSkuPriceHistory", "Get SKU price history", "Catalog",
    security=USER, params=[path_param("id"), *PAGE])
add("/api/v1/admin/search/reindex", "post", "startCatalogReindex", "Start durable search reindex", "Catalog",
    security=USER, success="202", success_description="Reindex accepted")
add("/api/v1/admin/search/reindex/{id}", "get", "getCatalogReindex", "Get reindex progress", "Catalog",
    security=USER, params=[path_param("id")])

# Cart, orders, inventory
add("/api/v1/cart", "get", "getCart", "Get current cart", "Order", security=USER)
add("/api/v1/cart", "delete", "clearCart", "Clear cart", "Order", security=USER,
    params=[query("version", "integer", format="int64")], success="204", success_description="Cart cleared")
add("/api/v1/cart/items", "post", "addCartItem", "Add cart item", "Order", security=USER,
    params=[query("version", "integer", format="int64")], body="CartItemRequest")
add("/api/v1/cart/items/{skuId}", "patch", "updateCartItem", "Update cart item", "Order", security=USER,
    params=[path_param("skuId"), query("version", "integer", format="int64")], body="CartItemRequest")
add("/api/v1/cart/items/{skuId}", "delete", "removeCartItem", "Remove cart item", "Order", security=USER,
    params=[path_param("skuId"), query("version", "integer", format="int64")])
add("/api/v1/orders/checkout", "post", "checkout", "Create order from cart", "Order", security=USER,
    params=[IDEMPOTENCY], body="CheckoutRequest", success="201", success_description="Order created")
add("/api/v1/orders", "get", "listOrders", "Filter owned orders", "Order", security=USER,
    params=[query("status"), query("from", format="date-time"), query("to", format="date-time"), *PAGE])
add("/api/v1/orders/{orderId}", "get", "getOrder", "Get owned order", "Order", security=USER,
    params=[path_param("orderId")])
add("/api/v1/orders/{orderId}/history", "get", "getOrderHistory", "Get owned order history", "Order",
    security=USER, params=[path_param("orderId")])
add("/api/v1/orders/{orderId}/cancel", "post", "cancelOrder", "Cancel pending order", "Order",
    security=USER, params=[path_param("orderId")])
add("/api/v1/admin/orders", "get", "listAdminOrders", "Filter all orders", "Order", security=USER,
    params=[query("userId", "integer", format="int64"), query("status"),
            query("from", format="date-time"), query("to", format="date-time"), *PAGE])
add("/api/v1/admin/orders/{orderId}", "get", "getAdminOrder", "Get any order", "Order", security=USER,
    params=[path_param("orderId")])
add("/api/v1/admin/orders/{orderId}/history", "get", "getAdminOrderHistory", "Get order history", "Order",
    security=USER, params=[path_param("orderId")])
add("/api/v1/admin/orders/{orderId}/ship", "post", "shipOrder", "Move order to shipping", "Order",
    security=USER, params=[path_param("orderId")], body="VersionRequest")
add("/api/v1/admin/orders/{orderId}/complete", "post", "completeOrder", "Complete shipping order", "Order",
    security=USER, params=[path_param("orderId")], body="VersionRequest")
add("/api/v1/admin/reports/orders", "get", "getOrderReport", "Get order report", "Order", security=USER,
    params=[query("from", format="date-time"), query("to", format="date-time")])
add("/api/v1/inventory/availability", "get", "getAvailability", "Get indicative availability", "Inventory",
    security=PUBLIC, params=[{"name": "skuIds", "in": "query", "required": True,
                              "schema": {"type": "array", "maxItems": 100,
                                         "items": {"type": "integer", "format": "int64"}}}])
add("/api/v1/admin/inventory", "get", "listInventory", "Filter inventory", "Inventory", security=USER,
    params=[query("skuId", "integer", format="int64"), *PAGE])
add("/api/v1/admin/inventory/{skuId}/initialize", "post", "initializeInventory", "Initialize inventory at zero", "Inventory",
    security=USER, params=[path_param("skuId"), IDEMPOTENCY])
add("/api/v1/admin/inventory/{skuId}/receipts", "post", "receiveInventory", "Receive stock", "Inventory",
    security=USER, params=[path_param("skuId"), IDEMPOTENCY], body="InventoryReceiptRequest")
add("/api/v1/admin/inventory/{skuId}/adjustments", "post", "adjustInventory", "Adjust stock", "Inventory",
    security=USER, params=[path_param("skuId"), IDEMPOTENCY], body="InventoryAdjustmentRequest")
add("/api/v1/admin/inventory/{skuId}/movements", "get", "listStockMovements", "Get stock movement history", "Inventory",
    security=USER, params=[path_param("skuId"), *PAGE])
add("/api/v1/admin/orders/outbox", "get", "listOrderOutbox", "List failed order outbox events", "Operations",
    security=USER, params=[query("status")])
add("/api/v1/admin/orders/outbox/{id}/replay", "post", "replayOrderOutbox", "Replay order outbox event", "Operations",
    security=USER, params=[path_param("id")])

# Payment
add("/api/v1/orders/{orderId}/payment", "get", "getOrderPayment", "Get payment for owned order", "Payment",
    security=USER, params=[path_param("orderId")])
add("/api/v1/admin/payments", "get", "listPayments", "Filter payments", "Payment", security=USER,
    params=[query("status"), query("orderId", "integer", format="int64"),
            query("userId", "integer", format="int64"), query("from", format="date-time"),
            query("to", format="date-time"), *PAGE])
add("/api/v1/admin/payments/{id}", "get", "getPayment", "Get payment", "Payment", security=USER,
    params=[path_param("id")])
add("/api/v1/admin/payments/{id}/history", "get", "getPaymentHistory", "Get payment history", "Payment",
    security=USER, params=[path_param("id"), *PAGE])
add("/api/v1/admin/reports/payments", "get", "getPaymentReport", "Get payment report", "Payment", security=USER)
add("/api/v1/admin/demo/payments/{id}/simulate", "post", "simulatePayment", "Simulate provider callback in demo", "Payment",
    security=USER, params=[path_param("id")], body="PaymentSimulationRequest")
add("/api/v1/payments/mock/callback", "post", "mockPaymentCallback", "Receive signed mock provider callback", "Payment",
    security=PUBLIC, params=[{"name": "X-Mock-Timestamp", "in": "header", "required": True, "schema": {"type": "string"}},
                             {"name": "X-Mock-Signature", "in": "header", "required": True, "schema": {"type": "string"}}],
    body="PaymentCallbackRequest")
add("/api/v1/admin/payments/outbox", "get", "listPaymentOutbox", "List failed payment outbox events", "Operations",
    security=USER, params=[query("status")])
add("/api/v1/admin/payments/outbox/{id}/replay", "post", "replayPaymentOutbox", "Replay payment outbox event", "Operations",
    security=USER, params=[path_param("id")])

# Notifications
add("/api/v1/notifications", "get", "listNotifications", "List owned notifications", "Notification",
    security=USER, params=PAGE)
add("/api/v1/notifications/{notificationId}/read", "patch", "markNotificationRead", "Mark notification read", "Notification",
    security=USER, params=[path_param("notificationId")])
add("/api/v1/admin/notifications", "get", "listAdminNotifications", "Filter notifications", "Notification",
    security=USER, params=[query("status"), query("orderId", "integer", format="int64"), *PAGE])
add("/api/v1/admin/notifications/{id}/attempts", "get", "listNotificationAttempts", "Get delivery attempts", "Notification",
    security=USER, params=[path_param("id")])

# Internal service APIs. Catalog calls require only serviceJwt by design.
add("/internal/v1/users/current/checkout-context", "post", "getCheckoutContext", "Resolve active customer and address", "Internal",
    security=SERVICE_AND_USER, body="CheckoutContextRequest")
add("/internal/v1/users/current/status", "get", "getCurrentUserStatus", "Get current end-user status", "Internal",
    security=SERVICE_AND_USER)
add("/internal/v1/users/{userId}/status", "get", "getUserStatus", "Get minimal user status", "Internal",
    security=SERVICE, params=[path_param("userId")])
add("/internal/v1/skus/checkout-items", "post", "getCheckoutItems", "Resolve authoritative checkout SKU snapshots", "Internal",
    security=SERVICE, body="CheckoutItemsRequest")
add("/internal/v1/skus/{skuId}", "get", "getInternalSku", "Resolve SKU sellability for cart", "Internal",
    security=SERVICE, params=[path_param("skuId")])


schemas = {
    "ApiResponse": {"type": "object", "required": ["code", "message", "data", "requestId"],
                    "properties": {"code": {"type": "string", "const": "SUCCESS"},
                                   "message": {"type": "string"}, "data": {},
                                   "requestId": {"type": "string"}}},
    "ApiError": {"type": "object", "required": ["code", "message", "errors", "requestId"],
                 "properties": {"code": {"type": "string"}, "message": {"type": "string"},
                                "data": {}, "errors": {"type": "array", "items": {"type": "string"}},
                                "requestId": {"type": "string"}}},
    "PageData": {"type": "object", "required": ["items", "page", "size", "totalElements", "totalPages"],
                 "properties": {"items": {"type": "array", "items": {}}, "page": {"type": "integer"},
                                "size": {"type": "integer"}, "totalElements": {"type": "integer", "format": "int64"},
                                "totalPages": {"type": "integer"}}},
    "GenericObject": {"type": "object", "additionalProperties": True},
    "RegisterRequest": {"type": "object", "required": ["email", "password", "fullName"],
                        "properties": {"email": {"type": "string", "format": "email", "maxLength": 254},
                                       "password": {"type": "string", "minLength": 8},
                                       "fullName": {"type": "string", "maxLength": 150}}},
    "LoginRequest": {"type": "object", "required": ["email", "password"],
                     "properties": {"email": {"type": "string", "format": "email"}, "password": {"type": "string"}}},
    "RefreshTokenRequest": {"type": "object", "required": ["refreshToken"],
                            "properties": {"refreshToken": {"type": "string"}}},
    "UpdateProfileRequest": {"type": "object", "required": ["version"],
                             "properties": {"fullName": {"type": "string", "maxLength": 150},
                                            "phone": {"type": "string", "maxLength": 30},
                                            "version": {"type": "integer", "format": "int64"}}},
    "AddressRequest": {"type": "object", "required": ["recipientName", "recipientPhone", "addressLine", "ward", "province"],
                       "properties": {"recipientName": {"type": "string"}, "recipientPhone": {"type": "string"},
                                      "addressLine": {"type": "string"}, "ward": {"type": "string"},
                                      "district": {"type": "string"}, "province": {"type": "string"},
                                      "defaultAddress": {"type": "boolean"}}},
    "ChangeUserStatusRequest": {"type": "object", "required": ["version"],
                                "properties": {"reason": {"type": "string"}, "version": {"type": "integer", "format": "int64"}}},
    "VersionRequest": {"type": "object", "required": ["version"],
                       "properties": {"version": {"type": "integer", "format": "int64"}}},
    "VersionedStatusRequest": {"type": "object", "required": ["status", "version"],
                               "properties": {"status": {"type": "string"}, "version": {"type": "integer", "format": "int64"}}},
    "CartItemRequest": {"type": "object", "required": ["skuId", "quantity"],
                        "properties": {"skuId": {"type": "integer", "format": "int64"},
                                       "quantity": {"type": "integer", "minimum": 1, "maximum": 10},
                                       "displayedUnitPrice": {"type": "integer", "format": "int64"}}},
    "CheckoutRequest": {"type": "object", "required": ["cartVersion", "addressId", "paymentMethod", "expectedItems"],
                        "properties": {"cartVersion": {"type": "integer", "format": "int64"},
                                       "addressId": {"type": "integer", "format": "int64"},
                                       "paymentMethod": {"type": "string", "enum": ["MOCK_ONLINE"]},
                                       "customerNote": {"type": "string", "maxLength": 1000},
                                       "expectedItems": {"type": "array", "minItems": 1, "items": {"$ref": "#/components/schemas/ExpectedItem"}}}},
    "ExpectedItem": {"type": "object", "required": ["skuId", "quantity", "expectedUnitPrice"],
                     "properties": {"skuId": {"type": "integer", "format": "int64"},
                                    "quantity": {"type": "integer", "minimum": 1, "maximum": 10},
                                    "expectedUnitPrice": {"type": "integer", "format": "int64", "minimum": 1}}},
    "InventoryReceiptRequest": {"type": "object", "required": ["quantity", "reason"],
                                "properties": {"quantity": {"type": "integer", "minimum": 1},
                                               "reason": {"type": "string", "maxLength": 200}}},
    "InventoryAdjustmentRequest": {"type": "object", "required": ["quantity", "reason", "version"],
                                   "properties": {"quantity": {"type": "integer", "not": {"const": 0}},
                                                  "reason": {"type": "string", "maxLength": 200},
                                                  "version": {"type": "integer", "format": "int64"}}},
    "PaymentSimulationRequest": {"type": "object", "required": ["result"],
                                 "properties": {"result": {"type": "string", "enum": ["SUCCESS", "FAILED"]}}},
    "PaymentCallbackRequest": {"type": "object", "required": ["providerEventId", "providerTransactionId", "paymentId", "orderId", "result", "amount", "currency"],
                               "properties": {"providerEventId": {"type": "string"}, "providerTransactionId": {"type": "string"},
                                              "paymentId": {"type": "integer", "format": "int64"}, "orderId": {"type": "integer", "format": "int64"},
                                              "result": {"type": "string", "enum": ["SUCCESS", "FAILED"]},
                                              "amount": {"type": "integer", "format": "int64"}, "currency": {"type": "string"},
                                              "paidAt": {"type": "string", "format": "date-time"}}},
    "CheckoutContextRequest": {"type": "object", "required": ["addressId"],
                               "properties": {"addressId": {"type": "integer", "format": "int64"}}},
    "CheckoutItemsRequest": {"type": "object", "required": ["skuIds"],
                             "properties": {"skuIds": {"type": "array", "minItems": 1, "maxItems": 100,
                                                        "uniqueItems": True, "items": {"type": "integer", "format": "int64"}}}},
}

error_content = {"application/json": {"schema": {"$ref": "#/components/schemas/ApiError"}}}
components = {
    "securitySchemes": {
        "userJwt": {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"},
        "serviceJwt": {"type": "apiKey", "in": "header", "name": "X-Service-Authorization"},
    },
    "parameters": {"IdempotencyKey": {"name": "Idempotency-Key", "in": "header", "required": True,
                                            "schema": {"type": "string", "minLength": 1, "maxLength": 100}}},
    "responses": {
        "BadRequest": {"description": "Validation or malformed request", "content": error_content},
        "Unauthenticated": {"description": "Authentication failed", "content": error_content},
        "Forbidden": {"description": "Caller is not allowed", "content": error_content},
        "NotFound": {"description": "Resource not found or not owned by caller", "content": error_content},
        "Conflict": {"description": "Version, state, stock, price, or idempotency conflict", "content": error_content},
    },
    "schemas": schemas,
}

document = {
    "openapi": "3.1.0",
    "info": {"title": "E-commerce microservices API", "version": "1.0.0",
             "description": "Public gateway REST API and private service-to-service contracts."},
    "servers": [{"url": "http://localhost:8080", "description": "Local gateway (public APIs only)"}],
    "components": components,
    "paths": paths,
}

target = ROOT / "docs" / "openapi.yaml"
target.write_text(yaml.safe_dump(document, sort_keys=False, allow_unicode=True), encoding="utf-8")
print(f"Wrote {target} with {sum(len(item) for item in paths.values())} operations")
