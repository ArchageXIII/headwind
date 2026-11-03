# Dot-source this file: . ./scripts/dev.ps1
$java17 = Get-ChildItem (Join-Path $env:ProgramFiles 'Microsoft') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
$androidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if ($java17) {
    $env:JAVA_HOME = $java17.FullName
}
if (-not $env:ANDROID_HOME -and (Test-Path -LiteralPath $androidSdk)) {
    $env:ANDROID_HOME = $androidSdk
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw 'Install Microsoft OpenJDK 17 or set JAVA_HOME to JDK 17 or 21.'
}
if (-not $env:ANDROID_HOME -or -not (Test-Path -LiteralPath $env:ANDROID_HOME)) {
    throw 'Set ANDROID_HOME to your Android SDK directory.'
}
$devPaths = @("$env:JAVA_HOME\bin", "$env:ANDROID_HOME\platform-tools", "$env:ANDROID_HOME\emulator", "$env:ANDROID_HOME\cmdline-tools\latest\bin")
foreach ($devPath in $devPaths) {
    if (($env:Path -split ';') -notcontains $devPath) {
        $env:Path = "$devPath;$env:Path"
    }
}
Write-Host "Java: $env:JAVA_HOME"
Write-Host "Android SDK: $env:ANDROID_HOME"
