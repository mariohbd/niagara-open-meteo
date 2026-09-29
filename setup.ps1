#Requires -Version 7.0
[CmdletBinding()]
param([string]$CacheDirectory)
$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $PSScriptRoot 'tools'
$lock = Get-Content (Join-Path $PSScriptRoot 'toolchain.lock.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Force $toolRoot | Out-Null
foreach ($item in $lock.tools) {
    $target = Join-Path $toolRoot $item.file
    if (Test-Path -LiteralPath $target) {
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $item.sha256) {
            throw "Checksum mismatch: $target. Replace this file before retrying."
        }
    } else {
        $download = Join-Path $toolRoot ([Guid]::NewGuid().ToString('N') + '.download')
        $cached = if ($CacheDirectory) { Join-Path $CacheDirectory $item.file } else { $null }
        if ($cached -and (Test-Path -LiteralPath $cached)) {
            Copy-Item -LiteralPath $cached -Destination $download
        } else {
            Write-Host "Downloading $($item.file)"
            Invoke-WebRequest -Uri $item.url -OutFile $download
        }
        if ((Get-FileHash -LiteralPath $download -Algorithm SHA256).Hash -ne $item.sha256) {
            throw "Checksum mismatch for $($item.file); untrusted download retained at $download."
        }
        Move-Item -LiteralPath $download -Destination $target
    }
}
# Re-extract the verified archive so an older compiler directory is never reused silently.
Expand-Archive -LiteralPath (Join-Path $toolRoot 'kotlin-compiler.zip') -DestinationPath (Join-Path $toolRoot 'kotlin') -Force
Write-Host 'Toolchain verified. Set JAVA_HOME to JDK 21, then run ./build.ps1 -RunTests.'
