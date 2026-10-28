$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$jdk=Split-Path (Get-Command javac).Source
$jar=Join-Path $root 'build\TFSE-Phase3-Local.jar'
if(!(Test-Path $jar)){ & (Join-Path $root 'build-local.ps1') }
$inputDir=Join-Path $root 'build\package-input'
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item $jar $inputDir -Force
$dest=Join-Path $root 'build'
$app=Join-Path $dest 'TFSE-Phase3-Local'
if(Test-Path $app){throw 'Package already exists; rename or move it before rebuilding.'}
& "$jdk\jpackage.exe" --type app-image --name TFSE-Phase3-Local --app-version 0.3.0 --vendor 'team4-local' --input $inputDir --main-jar TFSE-Phase3-Local.jar --main-class phase3.ui.LocalMain --dest $dest --icon "$root\packaging\TFSE.ico" --java-options '-Dtetris.prototype=true'
if($LASTEXITCODE -ne 0){throw 'jpackage failed'}
Write-Output "Executable: $app\TFSE-Phase3-Local.exe"
