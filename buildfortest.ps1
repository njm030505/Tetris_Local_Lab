# Compatibility entry point for the maintained test runner.
param([switch]$RequireNetwork)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'test-local.ps1') -VerifyCoverage -RequireNetwork:$RequireNetwork
