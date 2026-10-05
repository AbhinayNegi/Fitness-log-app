$ErrorActionPreference = 'Stop'

$project = $PSScriptRoot
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$sdkManager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
$avdManager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
$avdName = 'RepLog_API_36'
$systemImage = 'system-images;android-36;google_apis;x86_64'
$avdHome = Join-Path $project '.android\avd'

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_AVD_HOME = $avdHome

function Invoke-SdkManager([string[]]$Arguments) {
    & $script:sdkManager "--sdk_root=$script:sdk" @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Android SDK Manager failed: $($Arguments -join ' ')" }
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Debug APK not found: $apk"
}

if (-not (Test-Path -LiteralPath $sdkManager)) {
    Write-Host 'Downloading official Android command-line tools into your local SDK folder...'
    $zip = Join-Path $env:TEMP 'rep-log-commandlinetools-win.zip'
    $staging = Join-Path $env:TEMP ('rep-log-commandlinetools-' + [guid]::NewGuid().ToString('N'))
    Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $zip
    $expectedHash = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    $actualHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) { throw 'The Android command-line tools download did not match the published checksum.' }
    Expand-Archive -LiteralPath $zip -DestinationPath $staging -Force
    $toolsRoot = Join-Path $sdk 'cmdline-tools'
    New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
    Move-Item -LiteralPath (Join-Path $staging 'cmdline-tools') -Destination (Join-Path $toolsRoot 'latest') -Force
}

New-Item -ItemType Directory -Force -Path $sdk, $avdHome | Out-Null
Write-Host 'Accepting the Android SDK licenses you approved, then installing the emulator and API 36 image...'
1..32 | ForEach-Object { 'y' } | & $sdkManager "--sdk_root=$sdk" --licenses | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not accept/install the Android SDK licenses.' }
Invoke-SdkManager @('--install', 'emulator', 'platform-tools', $systemImage)

if (-not (Test-Path -LiteralPath (Join-Path $avdHome "$avdName.avd\config.ini"))) {
    'no' | & $avdManager create avd --name $avdName --package $systemImage --force
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the Rep Log API 36 virtual device.' }
}

$deviceLines = & $adb devices
$usedPorts = @(
    foreach ($line in $deviceLines) {
        if ($line -match '^emulator-(\d+)\s+device') { [int]$Matches[1] }
    }
)
$port = 5554
while ($usedPorts -contains $port) { $port += 2 }
$serial = "emulator-$port"

Write-Host "Starting $avdName ($serial)..."
Start-Process -FilePath $emulator -ArgumentList @('-avd', $avdName, '-port', "$port", '-no-snapshot-load', '-no-boot-anim') | Out-Null
& $adb -s $serial wait-for-device

$booted = $false
for ($attempt = 0; $attempt -lt 120; $attempt++) {
    $state = & $adb -s $serial shell getprop sys.boot_completed 2>$null
    if ($state -match '1') { $booted = $true; break }
    Start-Sleep -Seconds 5
}
if (-not $booted) { throw 'Android did not finish booting within ten minutes.' }

Write-Host 'Installing and opening Rep Log...'
& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Could not install Rep Log.' }
& $adb -s $serial shell am start -n 'com.example.replog/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Rep Log installed, but Android could not launch its main screen.' }
Write-Host 'Rep Log is open in the Android Emulator.'
