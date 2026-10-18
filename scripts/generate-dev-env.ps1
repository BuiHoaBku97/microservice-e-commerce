param(
  [string]$OutputPath = (Join-Path $PSScriptRoot "..\.env"),
  [switch]$Force
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  throw "JDK 21 is required and java must be on PATH."
}
$generator = Join-Path $PSScriptRoot "GenerateDevEnv.java"
& java $generator ([IO.Path]::GetFullPath($OutputPath)) $Force.IsPresent
if ($LASTEXITCODE -ne 0) {
  throw "Environment generation failed with exit code $LASTEXITCODE"
}
