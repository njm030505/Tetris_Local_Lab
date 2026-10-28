$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'build-local.ps1')
& java "-Dtetris.dataDir=$PSScriptRoot\data" -jar (Join-Path $PSScriptRoot 'build\TFSE-Phase3-Local.jar')
