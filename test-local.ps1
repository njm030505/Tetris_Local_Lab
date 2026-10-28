param([string]$SelectClass='', [switch]$VerifyCoverage, [switch]$RequireNetwork)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
Push-Location $root
try {
& (Join-Path $root 'build-local.ps1')
$main=Join-Path $root 'build\classes\java\main'
$tests=Join-Path $root 'build\classes\java\test'
$libs=Join-Path $root 'tools\lib'
$junit=Join-Path $libs 'junit-platform-console-standalone-1.11.4.jar'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
$sources=Get-ChildItem (Join-Path $root 'src\test\java') -Recurse -Filter *.java | ForEach-Object { '"'+$_.FullName.Replace('\','/')+'"' }
$argsFile=Join-Path $root 'build\test-sources.txt'
[IO.File]::WriteAllLines($argsFile,$sources,[Text.UTF8Encoding]::new($false))
& javac --release 21 -encoding UTF-8 -cp "$main;$junit" -d $tests "@$argsFile"
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
[string[]]$selection=@('--scan-class-path')
if($SelectClass){$selection=@('--select-class',$SelectClass)}
$agent=Join-Path $libs 'jacocoagent-0.8.12.jar'
$exec=Join-Path $root 'build\jacoco.exec'
& java "-javaagent:${agent}=destfile=$exec,append=false" "-Dtetris.dataDir=$root\build\test-data" "-Dtetris.requireNetwork=$($RequireNetwork.IsPresent.ToString().ToLowerInvariant())" -jar $junit execute --class-path "$main;$tests" @selection --details summary --reports-dir "$root\build\test-results\local"
if($LASTEXITCODE -ne 0){throw 'Tests failed'}
$cli=Join-Path $libs 'jacococli-0.8.12.jar'
New-Item -ItemType Directory -Force -Path "$root\build\reports" | Out-Null
& java -jar $cli report $exec --classfiles $main --sourcefiles "$root\src\main\java" --html "$root\build\reports\coverage" --xml "$root\build\reports\coverage.xml" --name 'All authored Java (same scope as gate)'
if($LASTEXITCODE -ne 0){throw 'Coverage report failed'}
[xml]$report=Get-Content "$root\build\reports\coverage.xml" -Raw
$line=$report.report.counter | Where-Object type -eq 'LINE'
$ratio=[double]$line.covered/([double]$line.covered+[double]$line.missed)
Write-Output ('ALL authored LINE coverage: {0:P2} ({1}/{2})' -f $ratio,$line.covered,([int]$line.covered+[int]$line.missed))
if($VerifyCoverage -and $ratio -lt 0.70){throw 'Line coverage below70%'}
} finally { Pop-Location }
