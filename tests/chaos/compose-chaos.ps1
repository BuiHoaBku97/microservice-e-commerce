param(
  [Parameter(Mandatory=$true)]
  [ValidateSet("kafka", "elasticsearch", "redis", "identity-service")]
  [string]$Dependency,
  [scriptblock]$DuringFailureProbe,
  [scriptblock]$AfterRecoveryProbe,
  [int]$RecoveryTimeoutSeconds = 90
)
$ErrorActionPreference = "Stop"

$running = docker compose ps --services --filter status=running
if ($running -notcontains $Dependency) { throw "$Dependency is not running in the Compose project" }

try {
  docker compose stop $Dependency
  if ($LASTEXITCODE -ne 0) { throw "Could not stop $Dependency" }
  if ($DuringFailureProbe) { & $DuringFailureProbe }
} finally {
  docker compose start $Dependency
  if ($LASTEXITCODE -ne 0) { throw "Could not restart $Dependency" }
}

$deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
do {
  $state = docker compose ps $Dependency --format json | ConvertFrom-Json
  if ($state.State -eq "running" -and (!$AfterRecoveryProbe -or (& $AfterRecoveryProbe))) {
    Write-Host "$Dependency recovered and the recovery probe passed."
    exit 0
  }
  Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
throw "$Dependency did not recover within $RecoveryTimeoutSeconds seconds"
