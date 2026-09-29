#Requires -Version 7.0
[CmdletBinding()]
param([switch]$RunTests)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
$nativeSuffix = if ($IsWindows) { '.exe' } else { '' }
$java = Join-Path $env:JAVA_HOME "bin/java$nativeSuffix"
$javac = Join-Path $env:JAVA_HOME "bin/javac$nativeSuffix"
$jar = Join-Path $env:JAVA_HOME "bin/jar$nativeSuffix"
foreach ($command in @($java, $javac, $jar)) {
    if (-not (Test-Path -LiteralPath $command)) { throw "JDK tool missing: $command" }
}
$toolRoot = Join-Path $PSScriptRoot 'tools'
$lock = Get-Content (Join-Path $PSScriptRoot 'toolchain.lock.json') -Raw | ConvertFrom-Json
foreach ($item in $lock.tools) {
    $target = Join-Path $toolRoot $item.file
    if (-not (Test-Path -LiteralPath $target)) { throw 'Run ./setup.ps1 before building.' }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $item.sha256) {
        throw "Toolchain checksum mismatch: $($item.file)"
    }
}
$build = Join-Path ([IO.Path]::GetTempPath()) ('niagara-open-meteo-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $build,"$build/extension","$build/extension-dex","$build/patch-dex","$build/bundle/extensions" | Out-Null
$cp = [IO.Path]::PathSeparator
function Check-Exit([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed ($LASTEXITCODE). Scratch files: $build" }
}
$javaSources = @(Get-ChildItem "$PSScriptRoot/extension/*.java" | Select-Object -ExpandProperty FullName)
& $javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$toolRoot/android-api.jar$cp$toolRoot/json.jar" -d "$build/extension" @javaSources
Check-Exit 'Java compilation'
& $jar cf "$build/extension.jar" -C "$build/extension" .
Check-Exit 'Extension archive'
& $java -cp "$toolRoot/r8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$env:JAVA_HOME" --lib "$toolRoot/android-api.jar" --output "$build/extension-dex" "$build/extension.jar"
Check-Exit 'Extension DEX'
Copy-Item "$build/extension-dex/classes.dex" "$build/bundle/extensions/open-meteo.mpe"
# Call the compiler directly to avoid platform-specific batch argument escaping.
& $java -cp "$toolRoot/kotlin/kotlinc/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler `
    -kotlin-home "$toolRoot/kotlin/kotlinc" -jvm-target 11 '-Xlambdas=class' '-Xsam-conversions=class' `
    -classpath "$toolRoot/morphe-desktop.jar" -d "$build/patch.jar" "$PSScriptRoot/patches/OpenMeteoWeatherPatch.kt"
Check-Exit 'Kotlin compilation'
& $java -cp "$toolRoot/r8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$env:JAVA_HOME" --lib "$toolRoot/android-api.jar" --lib "$toolRoot/morphe-desktop.jar" --output "$build/patch-dex" "$build/patch.jar"
Check-Exit 'Patch DEX'
Push-Location "$build/bundle"
try { & $jar xf "$build/patch.jar"; Check-Exit 'Patch extraction' } finally { Pop-Location }
Copy-Item "$build/patch-dex/classes.dex" "$build/bundle/classes.dex"
$manifest = @'
Manifest-Version: 1.0
Name: Niagara Open-Meteo
Description: Experimental weather adapter for Niagara 1.16.28 build 1634
Version: 0.1.0
License: GPLv3
Source: https://github.com/mariohenrique85/niagara-open-meteo
Patcher-Version: 1.14.1

'@
Set-Content "$build/MANIFEST.MF" $manifest -Encoding ascii
& $jar cfm "$build/niagara-open-meteo-0.1.0.mpp" "$build/MANIFEST.MF" -C "$build/bundle" .
Check-Exit 'MPP packaging'
if ($RunTests) {
    & $javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$build/extension$cp$toolRoot/json.jar" -d "$build/tests" "$PSScriptRoot/tests/OpenMeteoProviderTest.java"
    Check-Exit 'Test compilation'
    & $java -cp "$build/tests$cp$build/extension$cp$toolRoot/json.jar" app.d0nj.extension.weather.OpenMeteoProviderTest "$PSScriptRoot/tests/fixtures/open-meteo.json"
    Check-Exit 'Weather contract tests'
}
New-Item -ItemType Directory -Force "$PSScriptRoot/dist" | Out-Null
Copy-Item "$build/niagara-open-meteo-0.1.0.mpp" "$PSScriptRoot/dist/niagara-open-meteo-0.1.0.mpp"
Get-FileHash "$PSScriptRoot/dist/niagara-open-meteo-0.1.0.mpp" -Algorithm SHA256 |
    ForEach-Object { "$($_.Hash.ToLowerInvariant())  niagara-open-meteo-0.1.0.mpp" } |
    Set-Content "$PSScriptRoot/dist/SHA256SUMS" -Encoding ascii
Write-Host "Built: $PSScriptRoot/dist/niagara-open-meteo-0.1.0.mpp"
