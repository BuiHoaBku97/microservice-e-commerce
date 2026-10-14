# Service JWT operations

Internal calls use `X-Service-Authorization: Bearer <JWT>`. Tokens are RS256 signed, last 60 seconds,
and contain `iss`, service `sub`, `aud`, `scope`, `iat`, `exp`, `jti`, and `kid`. Order issues
`identity.checkout` for audience `identity-service` and `catalog.checkout` for audience
`catalog-service`. The end-user bearer token remains in `Authorization` and is verified separately.

Keys must come from `SERVICE_JWT_PRIVATE_KEY_BASE64` and `SERVICE_JWT_PUBLIC_KEY_BASE64`; do not
store them in source. During rotation, resource services accept a semicolon-separated key ring in
`SERVICE_JWT_PUBLIC_KEYS`, for example `service-key-2=<new>;service-key-1=<old>`. Deploy the key ring,
switch callers to the new private key and `SERVICE_JWT_KEY_ID`, wait at least the 60-second token TTL,
then remove the old entry. Logs may include service subject and `jti`; they must never include JWTs.
