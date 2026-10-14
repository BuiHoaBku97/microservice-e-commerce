param([string]$BaseUrl = "http://localhost:8080")
$ErrorActionPreference = "Stop"
$rows = docker compose ps --format json | ConvertFrom-Json
if (($rows | Where-Object { $_.State -ne "running" }).Count -gt 0) {
  throw "One or more Compose services are not running"
}
$search = Invoke-WebRequest -UseBasicParsing "$BaseUrl/api/v1/products/search?page=0&size=1"
if ($search.StatusCode -ne 200) { throw "Elasticsearch search smoke failed" }
Write-Host "Compose infrastructure and public search smoke passed."
