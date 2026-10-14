# Service JWT operations

Internal calls use `X-Service-Authorization: Bearer <JWT>`. Tokens are RS256 signed, last 60 seconds,
and contain `iss`, service `sub`, `aud`, `scope`, `iat`, `exp`, `jti`, and `kid`. Order issues
`identity.checkout` for audience `identity-service` and `catalog.checkout` for audience
`catalog-service`. The end-user bearer token remains in `Authorization` and is verified separately.

Keys must come from `SERVICE_JWT_PRIVATE_KEY_BASE64` and `SERVICE_JWT_PUBLIC_KEY_BASE64`; do not
store them in source. Rotate by publishing the new public key under a new `kid`, deploying resource
services, switching callers to the new private key, waiting at least the maximum token TTL, then
removing the old public key. Logs may include service subject and `jti`; they must never include JWTs.
