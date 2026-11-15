param(
    [Parameter(Mandatory = $true)]
    [string]$Apk
)

$ErrorActionPreference = "Stop"
if (!(Test-Path $Apk)) { throw "APK not found: $Apk" }

$sdk = $env:ANDROID_HOME
if (!$sdk -and (Test-Path (Join-Path (Get-Location) "local.properties"))) {
    $line = Get-Content (Join-Path (Get-Location) "local.properties") |
        Where-Object { $_ -like "sdk.dir=*" } |
        Select-Object -First 1
    if ($line) { $sdk = ($line -replace "^sdk.dir=", "") -replace "\\\\", "\" }
}
if (!$sdk) { throw "Set ANDROID_HOME or add sdk.dir to local.properties" }

$buildTools = Get-ChildItem (Join-Path $sdk "build-tools") -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName "apksigner.bat"
$aapt = Join-Path $buildTools.FullName "aapt.exe"

Write-Host "Verifying signature..."
& $apksigner verify --verbose --print-certs $Apk
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }

Write-Host ""
Write-Host "Reading package metadata..."
$badging = & $aapt dump badging $Apk
$badging | Where-Object { $_ -like "package:*" -or $_ -like "sdkVersion:*" -or $_ -like "targetSdkVersion:*" }
if ($badging -match "application-debuggable") {
    throw "This APK is debuggable. Do not publish it."
}

$hash = (Get-FileHash $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host ""
Write-Host "SHA-256: $hash"
Write-Host "Release checks passed: signed, non-debuggable APK."
