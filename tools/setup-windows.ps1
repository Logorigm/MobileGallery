$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Tools = Join-Path $Root "tools\portable"
$JdkDir = Join-Path $Tools "jdk"
$GradleDir = Join-Path $Tools "gradle-8.9"
$SdkDir = Join-Path $Tools "android-sdk"
New-Item -ItemType Directory -Force -Path $Tools | Out-Null

Write-Host "1/4: Downloading portable JDK 17..."
$jdkZip = Join-Path $Tools "jdk17.zip"
Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $jdkZip
$tmpJdk = Join-Path $Tools "jdk-unpack"
Remove-Item $tmpJdk -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive $jdkZip $tmpJdk -Force
$jdkHome = Get-ChildItem $tmpJdk -Directory | Select-Object -First 1
Remove-Item $JdkDir -Recurse -Force -ErrorAction SilentlyContinue
Move-Item $jdkHome.FullName $JdkDir
Remove-Item $tmpJdk -Recurse -Force
Remove-Item $jdkZip -Force

Write-Host "2/4: Downloading Gradle 8.9..."
$gradleZip = Join-Path $Tools "gradle-8.9-bin.zip"
Invoke-WebRequest "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -OutFile $gradleZip
$tmpGradle = Join-Path $Tools "gradle-unpack"
Remove-Item $tmpGradle -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive $gradleZip $tmpGradle -Force
Remove-Item $GradleDir -Recurse -Force -ErrorAction SilentlyContinue
Move-Item (Join-Path $tmpGradle "gradle-8.9") $GradleDir
Remove-Item $tmpGradle -Recurse -Force
Remove-Item $gradleZip -Force

Write-Host "3/4: Downloading Android SDK command-line tools (sdkmanager)..."
$sdkZip = Join-Path $Tools "android-cli.zip"
Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $sdkZip
$tmpSdk = Join-Path $Tools "sdk-unpack"
Remove-Item $tmpSdk -Recurse -Force -ErrorAction SilentlyContinue
Expand-Archive $sdkZip $tmpSdk -Force
New-Item -ItemType Directory -Force -Path (Join-Path $SdkDir "cmdline-tools\latest") | Out-Null
Copy-Item (Join-Path $tmpSdk "cmdline-tools\*") (Join-Path $SdkDir "cmdline-tools\latest") -Recurse -Force
Remove-Item $tmpSdk -Recurse -Force
Remove-Item $sdkZip -Force

$env:JAVA_HOME = $JdkDir
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir
$env:PATH = "$JdkDir\bin;$(Join-Path $GradleDir 'bin');$(Join-Path $SdkDir 'cmdline-tools\latest\bin');$(Join-Path $SdkDir 'platform-tools');$env:PATH"
$sdkmanager = Join-Path $SdkDir "cmdline-tools\latest\bin\sdkmanager.bat"
Write-Host "4/4: Installing Android SDK platform/build tools..."
"y`ny`ny`ny`ny`ny`ny`n" | & $sdkmanager --sdk_root=$SdkDir --licenses
& $sdkmanager --sdk_root=$SdkDir "platform-tools" "platforms;android-35" "build-tools;35.0.0"
if ($LASTEXITCODE -ne 0) { throw "sdkmanager failed with exit code $LASTEXITCODE" }

Write-Host "`nSetup complete."
Write-Host "JDK: $JdkDir"
Write-Host "Gradle: $GradleDir"
Write-Host "Android SDK / sdkmanager: $SdkDir"
Write-Host "Next build command:"
Write-Host "  `$env:JAVA_HOME='$JdkDir'; `$env:ANDROID_HOME='$SdkDir'; `$env:ANDROID_SDK_ROOT='$SdkDir'; & '$GradleDir\bin\gradle.bat' -p '$Root' assembleDebug"
