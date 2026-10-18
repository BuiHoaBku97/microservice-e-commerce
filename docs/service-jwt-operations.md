# Service JWT operations

Internal calls use `X-Service-Authorization: Bearer <JWT>`. Tokens are RS256 signed, last 60 seconds,
and contain `iss`, service `sub`, `aud`, `scope`, `iat`, `exp`, `jti`, and `kid`. Order issues
`identity.checkout` for audience `identity-service` and `catalog.checkout` for audience
`catalog-service`. Identity checkout and `current/status` receive the end-user bearer token in
`Authorization` and verify it separately. Catalog checkout receives only the service JWT because
Order has already verified the customer and address; Catalog trusts the restricted service scope
and reads authoritative SKU data without calling Identity again.

Each caller has its own RSA key pair. Compose receives the private keys through
`ORDER_SERVICE_JWT_PRIVATE_KEY_BASE64`, `CATALOG_SERVICE_JWT_PRIVATE_KEY_BASE64`,
`PAYMENT_SERVICE_JWT_PRIVATE_KEY_BASE64`, and `NOTIFICATION_SERVICE_JWT_PRIVATE_KEY_BASE64`;
their matching public keys use the same prefixes. Do not store any private key in source.

Resource services accept two semicolon-separated maps. `SERVICE_JWT_PUBLIC_KEYS` maps `kid` to a
public key, while `SERVICE_JWT_KEY_OWNERS` binds the same `kid` to the service that owns it. For
example:

```text
SERVICE_JWT_PUBLIC_KEYS=order-service-key-2=<new>;order-service-key-1=<old>
SERVICE_JWT_KEY_OWNERS=order-service-key-2=order-service;order-service-key-1=order-service
```

The resource verifies that the JWT `sub` matches the owner of the signing key before applying the
audience, operation scope, and caller allow-list. During rotation, deploy both map entries, switch
the caller to the new private key and key ID, wait at least the 60-second token TTL, then remove the
old entries. Logs may include service subject and `jti`; they must never include JWTs.
