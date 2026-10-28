param([switch]$Package)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$classes=Join-Path $root 'build\classes\java\main'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' }
$argsFile=Join-Path $root 'build\main-sources.txt'
[IO.File]::WriteAllLines($argsFile,$sources,[Text.UTF8Encoding]::new($false))
& javac --release 21 -encoding UTF-8 -d $classes "@$argsFile"
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
$resources=Join-Path $root 'src\main\resources'
if(Test-Path $resources){Get-ChildItem $resources | Copy-Item -Destination $classes -Recurse -Force}
$jar=Join-Path $root 'build\TFSE-Phase3-Local.jar'
$taskTemp=Join-Path $root 'build\temp'
New-Item -ItemType Directory -Force -Path $taskTemp | Out-Null
& jar "-J-Djava.io.tmpdir=$taskTemp" --create --file $jar --main-class phase3.ui.LocalMain -C $classes .
if($LASTEXITCODE -ne 0){throw 'JAR creation failed'}
if($Package){ & (Join-Path $root 'package-local.ps1') }
Write-Output "Built $jar"
