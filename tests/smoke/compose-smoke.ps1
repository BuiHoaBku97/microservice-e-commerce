param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$AdminToken = $env:ADMIN_TOKEN,
  [string]$AdminEmail = $env:ADMIN_EMAIL,
  [string]$AdminPassword = $env:ADMIN_PASSWORD
)
$ErrorActionPreference = "Stop"

function Invoke-Json([string]$Method, [string]$Path, $Body = $null,
    [string]$Token = "", [hashtable]$Headers = @{}) {
  if ($Token) { $Headers.Authorization = "Bearer $Token" }
  $arguments = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $Headers }
  if ($null -ne $Body) {
    $arguments.ContentType = "application/json"
    $arguments.Body = $Body | ConvertTo-Json -Depth 10 -Compress
  }
  Invoke-RestMethod @arguments
}

function Wait-Until([scriptblock]$Probe, [string]$Description, [int]$Seconds = 45) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  do {
    try { $result = & $Probe; if ($result) { return $result } } catch { }
    Start-Sleep -Seconds 1
  } while ((Get-Date) -lt $deadline)
  throw "Timed out waiting for $Description"
}

$rows = docker compose ps --format json | ConvertFrom-Json
if (!$rows -or ($rows | Where-Object { $_.State -ne "running" })) {
  throw "Every Compose service must be running before the smoke test"
}
if (!$AdminToken) {
  if (!$AdminEmail -or !$AdminPassword) { throw "Set ADMIN_TOKEN or ADMIN_EMAIL and ADMIN_PASSWORD" }
  $AdminToken = (Invoke-Json POST "/api/v1/auth/login" @{email=$AdminEmail;password=$AdminPassword}).data.accessToken
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "smoke-$suffix@example.test"
$password = "SmokePass!42"
$registered = Invoke-Json POST "/api/v1/auth/register" @{email=$email;password=$password;fullName="Smoke User"}
$customerId = $registered.data.id
$tokens = (Invoke-Json POST "/api/v1/auth/login" @{email=$email;password=$password}).data
$customerToken = $tokens.accessToken
$rotated = (Invoke-Json POST "/api/v1/auth/refresh" @{refreshToken=$tokens.refreshToken}).data

$address = Invoke-Json POST "/api/v1/users/me/addresses" @{
  recipientName="Smoke User";recipientPhone="0900000000";addressLine="1 Test Street"
  ward="Ward 1";district="District 1";province="Ho Chi Minh";defaultAddress=$true
} $customerToken
$category = (Invoke-Json POST "/api/v1/admin/categories" @{
  code="SMOKE-$suffix";name="Smoke $suffix";slug="smoke-$suffix"
} $AdminToken).data
$product = (Invoke-Json POST "/api/v1/admin/products" @{
  categoryId=$category.id;name="Áo smoke $suffix";description="Compose acceptance";imageUrls=@()
} $AdminToken).data
$sku = (Invoke-Json POST "/api/v1/admin/products/$($product.id)/skus" @{
  code="SMOKE-SKU-$suffix";color="black";size="M";price=125000
} $AdminToken).data
Invoke-Json PUT "/api/v1/admin/products/$($product.id)/status" @{
  status="ACTIVE";version=$product.version
} $AdminToken | Out-Null

$key = "smoke-$suffix"
Invoke-Json POST "/api/v1/admin/inventory/$($sku.id)/initialize" $null $AdminToken @{
  "Idempotency-Key"="$key-init"
} | Out-Null
$receipt = Invoke-Json POST "/api/v1/admin/inventory/$($sku.id)/receipts" @{
  quantity=10;reason="compose-smoke"
} $AdminToken @{"Idempotency-Key"="$key-receipt"}
$replayed = Invoke-Json POST "/api/v1/admin/inventory/$($sku.id)/receipts" @{
  quantity=10;reason="compose-smoke"
} $AdminToken @{"Idempotency-Key"="$key-receipt"}
if ($replayed.onHand -ne $receipt.onHand) { throw "Inventory idempotency replay changed stock" }

Wait-Until { $page=Invoke-Json GET "/api/v1/products/search?q=ao%20smoke%20$suffix"; if ($page.totalElements -ge 1) { $page } } "Elasticsearch indexing" | Out-Null
$cart = Invoke-Json GET "/api/v1/cart" $null $customerToken
$cart = Invoke-Json POST "/api/v1/cart/items?version=$($cart.version)" @{
  skuId=$sku.id;quantity=1;displayedUnitPrice=125000
} $customerToken
$order = Invoke-Json POST "/api/v1/orders/checkout" @{
  cartVersion=$cart.version;addressId=$address.id
  paymentMethod="MOCK_ONLINE";customerNote="Compose smoke"
  expectedItems=@(@{skuId=$sku.id;quantity=1;expectedUnitPrice=125000})
} $customerToken @{"Idempotency-Key"="$key-checkout"}
$payment = Wait-Until {
  $candidate=(Invoke-Json GET "/api/v1/orders/$($order.id)/payment" $null $customerToken).data
  if ($candidate.id) { $candidate }
} "payment creation"
Invoke-Json POST "/api/v1/admin/demo/payments/$($payment.id)/simulate" @{result="SUCCESS"} $AdminToken | Out-Null
Wait-Until {
  $candidate=Invoke-Json GET "/api/v1/orders/$($order.id)" $null $customerToken
  if ($candidate.status -eq "CONFIRMED") { $candidate }
} "order confirmation" | Out-Null
Wait-Until {
  $page=Invoke-Json GET "/api/v1/admin/notifications?userId=$customerId&page=0&size=20" $null $AdminToken
  if ($page.data.totalElements -ge 1) { $page }
} "notification creation" | Out-Null

$inventory = Invoke-Json GET "/api/v1/admin/inventory?skuId=$($sku.id)" $null $AdminToken
$row = $inventory.content[0]
if ($row.onHand -lt 0 -or $row.reserved -lt 0 -or $row.reserved -gt $row.onHand) {
  throw "Inventory invariant failed"
}
Invoke-Json POST "/api/v1/auth/logout" @{refreshToken=$rotated.refreshToken} | Out-Null
Write-Host "Full Compose smoke passed for order $($order.id), payment $($payment.id), SKU $($sku.id)."
