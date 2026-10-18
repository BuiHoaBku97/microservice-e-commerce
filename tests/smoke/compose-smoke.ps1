param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$AdminToken = $env:ADMIN_TOKEN,
  [string]$AdminEmail = $env:ADMIN_EMAIL,
  [string]$AdminPassword = $env:ADMIN_PASSWORD,
  [ValidateSet("MOCK_ONLINE", "VNPAY")]
  [string]$PaymentMethod = "MOCK_ONLINE"
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
  try {
    Invoke-RestMethod @arguments
  } catch {
    $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { "no status" }
    $details = $_.ErrorDetails.Message
    if (!$details) { $details = $_.Exception.Message }
    throw "$Method $Path failed ($status): $details"
  }
}

function Wait-Until([scriptblock]$Probe, [string]$Description, [int]$Seconds = 45) {
  $deadline = (Get-Date).AddSeconds($Seconds)
  do {
    try { $result = & $Probe; if ($result) { return $result } } catch { }
    Start-Sleep -Seconds 1
  } while ((Get-Date) -lt $deadline)
  throw "Timed out waiting for $Description"
}

$expectedServices = @(
  "postgres", "kafka", "redis", "elasticsearch", "eureka-server", "identity-service",
  "catalog-service", "order-service", "payment-service", "notification-service", "gateway"
)
$runningServices = @(docker compose ps --services --status running)
$missingServices = @($expectedServices | Where-Object { $_ -notin $runningServices })
if ($missingServices.Count -gt 0) {
  throw "Compose services are not running: $($missingServices -join ', ')"
}
$rows = docker compose ps --format json | ConvertFrom-Json
$unhealthy = @($rows | Where-Object { $_.Health -and $_.Health -ne "healthy" })
if ($unhealthy.Count -gt 0) {
  throw "Compose services are not healthy: $(($unhealthy.Service -join ', '))"
}
$kafkaInit = docker compose ps -a kafka-init --format json | ConvertFrom-Json
if (!$kafkaInit -or $kafkaInit.State -ne "exited" -or $kafkaInit.ExitCode -ne 0) {
  throw "kafka-init did not complete successfully"
}
if (!$AdminToken) {
  if (!$AdminEmail -or !$AdminPassword) { throw "Set ADMIN_TOKEN or ADMIN_EMAIL and ADMIN_PASSWORD" }
  $AdminToken = (Invoke-Json POST "/api/v1/auth/login" @{email=$AdminEmail;password=$AdminPassword}).data.accessToken
}
Wait-Until {
  Invoke-Json GET "/api/v1/categories?page=0&size=1"
} "Catalog route discovery" | Out-Null
Wait-Until {
  Invoke-Json GET "/api/v1/admin/inventory?page=0&size=1" $null $AdminToken
} "Order route discovery" | Out-Null

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$email = "smoke-$suffix@example.test"
$password = "SmokePass!42"
$registered = Invoke-Json POST "/api/v1/auth/register" @{email=$email;password=$password;fullName="Smoke User"}
$customerId = $registered.data.id
$tokens = (Invoke-Json POST "/api/v1/auth/login" @{email=$email;password=$password}).data
$customerToken = $tokens.accessToken
$rotated = (Invoke-Json POST "/api/v1/auth/refresh" @{refreshToken=$tokens.refreshToken}).data

$address = (Invoke-Json POST "/api/v1/users/me/addresses" @{
  recipientName="Smoke User";recipientPhone="0900000000";addressLine="1 Test Street"
  ward="Ward 1";district="District 1";province="Ho Chi Minh";defaultAddress=$true
} $customerToken).data
$category = (Invoke-Json POST "/api/v1/admin/categories" @{
  code="SMOKE-$suffix";name="Smoke $suffix";slug="smoke-$suffix"
} $AdminToken).data
$product = (Invoke-Json POST "/api/v1/admin/products" @{
  categoryId=$category.id;name="Ao smoke $suffix";description="Compose acceptance";imageUrls=@()
} $AdminToken).data
$sku = (Invoke-Json POST "/api/v1/admin/products/$($product.id)/skus" @{
  code="SMOKE-SKU-$suffix";color="black";size="M";price=125000
} $AdminToken).data
$product = (Invoke-Json GET "/api/v1/admin/products/$($product.id)" $null $AdminToken).data
Invoke-Json PUT "/api/v1/admin/products/$($product.id)/status" @{
  status="ACTIVE";version=$product.version
} $AdminToken | Out-Null

$key = "smoke-$suffix"
Invoke-Json POST "/api/v1/admin/inventory/$($sku.id)/initialize" $null $AdminToken @{
  "Idempotency-Key"="$key-init"
} | Out-Null
$receipt = (Invoke-Json POST "/api/v1/admin/inventory/$($sku.id)/receipts" @{
  quantity=10;reason="compose-smoke"
} $AdminToken @{"Idempotency-Key"="$key-receipt"}).data
$replayed = (Invoke-Json POST "/api/v1/admin/inventory/$($sku.id)/receipts" @{
  quantity=10;reason="compose-smoke"
} $AdminToken @{"Idempotency-Key"="$key-receipt"}).data
if ($replayed.onHand -ne $receipt.onHand) { throw "Inventory idempotency replay changed stock" }

Wait-Until { $page=(Invoke-Json GET "/api/v1/products/search?q=ao%20smoke%20$suffix").data; if ($page.totalElements -ge 1) { $page } } "Elasticsearch indexing" | Out-Null
$cart = (Invoke-Json GET "/api/v1/cart" $null $customerToken).data
$cart = (Invoke-Json POST "/api/v1/cart/items?version=$($cart.version)" @{
  skuId=$sku.id;quantity=1;displayedUnitPrice=125000
} $customerToken).data
$order = (Invoke-Json POST "/api/v1/orders/checkout" @{
  cartVersion=$cart.version;addressId=$address.id
  paymentMethod=$PaymentMethod;customerNote="Compose smoke"
  expectedItems=@(@{skuId=$sku.id;quantity=1;expectedUnitPrice=125000})
} $customerToken @{"Idempotency-Key"="$key-checkout"}).data
$payment = Wait-Until {
  $candidate=(Invoke-Json GET "/api/v1/orders/$($order.id)/payment" $null $customerToken).data
  if ($candidate.id) { $candidate }
} "payment creation"
$initialized = (Invoke-Json POST "/api/v1/orders/$($order.id)/payment/initialize" $null $customerToken).data
if ($initialized.paymentMethod -ne $PaymentMethod) {
  throw "Payment initialize returned $($initialized.paymentMethod), expected $PaymentMethod"
}
if ($PaymentMethod -eq "MOCK_ONLINE") {
  if ($initialized.nextAction -ne "SIMULATE") { throw "Mock initialize did not return SIMULATE" }
  Invoke-Json POST "/api/v1/admin/demo/payments/$($payment.id)/simulate" @{result="SUCCESS"} $AdminToken | Out-Null
} else {
  if ($initialized.nextAction -ne "REDIRECT" -or !$initialized.paymentUrl) {
    throw "VNPay initialize did not return REDIRECT with paymentUrl"
  }
  Write-Host "VNPay initialize smoke passed for order $($order.id), payment $($payment.id)."
  Write-Host "Redirect URL: $($initialized.paymentUrl)"
  Invoke-Json POST "/api/v1/auth/logout" @{refreshToken=$rotated.refreshToken} | Out-Null
  exit 0
}
$confirmedOrder = Wait-Until {
  $candidate=(Invoke-Json GET "/api/v1/orders/$($order.id)" $null $customerToken).data
  if ($candidate.status -eq "CONFIRMED") { $candidate }
} "order confirmation"
$shippingOrder = (Invoke-Json POST "/api/v1/admin/orders/$($order.id)/ship" @{
  version=$confirmedOrder.version
} $AdminToken).data
$completedOrder = (Invoke-Json POST "/api/v1/admin/orders/$($order.id)/complete" @{
  version=$shippingOrder.version
} $AdminToken).data
if ($completedOrder.status -ne "COMPLETED") { throw "Order did not complete" }
Wait-Until {
  $page=Invoke-Json GET "/api/v1/admin/notifications?page=0&size=100" $null $AdminToken
  $owned=@($page.data.items | Where-Object { $_.userId -eq $customerId })
  if ($owned.Count -ge 1) { $page }
} "notification creation" | Out-Null

$inventory = Invoke-Json GET "/api/v1/admin/inventory?skuId=$($sku.id)" $null $AdminToken
$row = $inventory.data.items[0]
if ($row.onHand -lt 0 -or $row.reserved -lt 0 -or $row.reserved -gt $row.onHand) {
  throw "Inventory invariant failed"
}
Invoke-Json POST "/api/v1/auth/logout" @{refreshToken=$rotated.refreshToken} | Out-Null
Write-Host "Full Compose smoke passed for $PaymentMethod order $($order.id), payment $($payment.id), SKU $($sku.id)."
