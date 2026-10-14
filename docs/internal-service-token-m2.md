# M2 internal authentication

The former shared `X-Internal-Service-Token` mechanism has been retired. Internal endpoints now
require a short-lived asymmetric service JWT with a per-resource audience and per-operation scope.
Configuration, claims, and rotation order are documented in [service-jwt-operations.md](service-jwt-operations.md).
