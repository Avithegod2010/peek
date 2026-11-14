param(
    [string]$KeystorePath = (Join-Path $env:USERPROFILE ".peek\peek-release.jks"),
    [string]$PropertiesPath = (Join-Path $env:USERPROFILE ".peek\peek-release.properties"),
    [string]$Version = "1.5.0"
)

$ErrorActionPreference = "Stop"
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-17" }
$keytool = Join-Path $javaHome "bin\keytool.exe"
$gradlew = Join-Path (Get-Location) "gradlew.bat"

if (!(Test-Path $KeystorePath)) {
    $password = [guid]::NewGuid().ToString("N")
    New-Item -ItemType Directory -Force -Path (Split-Path $KeystorePath) | Out-Null
    & $keytool -genkeypair -v `
        -keystore $KeystorePath `
        -alias "peek-release" `
        -keyalg RSA -keysize 4096 -validity 10000 `
        -storetype PKCS12 `
        -storepass $password -keypass $password `
        -dname "CN=Peek, OU=Mobile, O=Peek, C=US"
    @"
PEEK_KEYSTORE=$KeystorePath
PEEK_KEYSTORE_PASSWORD=$password
PEEK_KEY_ALIAS=peek-release
PEEK_KEY_PASSWORD=$password
"@ | Set-Content -Path $PropertiesPath -Encoding UTF8
    Write-Host "Created a new release key outside the repository:"
    Write-Host "  keystore: $KeystorePath"
    Write-Host "  secrets:  $PropertiesPath"
    Write-Host "Back up both files securely. Losing them means you cannot update the app in place."
}

$config = ConvertFrom-StringData (Get-Content $PropertiesPath -Raw)
$env:PEEK_KEYSTORE = $config["PEEK_KEYSTORE"]
$env:PEEK_KEYSTORE_PASSWORD = $config["PEEK_KEYSTORE_PASSWORD"]
$env:PEEK_KEY_ALIAS = $config["PEEK_KEY_ALIAS"]
$env:PEEK_KEY_PASSWORD = $config["PEEK_KEY_PASSWORD"]

& $gradlew :app:assembleRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Release build failed." }

$apk = Join-Path (Get-Location) "app\build\outputs\apk\release\app-release.apk"
if (!(Test-Path $apk)) { throw "Release APK was not produced." }

$outputDir = Join-Path (Get-Location) "release"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$target = Join-Path $outputDir "peek-$Version-release.apk"
Copy-Item $apk $target -Force
$hash = (Get-FileHash $target -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  peek-$Version-release.apk" | Set-Content (Join-Path $outputDir "SHA256SUMS.txt") -Encoding ASCII

Write-Host ""
Write-Host "Signed release ready:"
Write-Host "  $target"
Write-Host "  SHA-256: $hash"
Write-Host "Never commit the keystore or its password file."
