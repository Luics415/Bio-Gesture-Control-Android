[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'
$apkFullPath = (Resolve-Path -LiteralPath $ApkPath).Path
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdkRoot) { throw 'Configura ANDROID_SDK_ROOT o ANDROID_HOME.' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'No se encontró adb.exe.' }

$devices = & $adb devices
$readyDevices = @($devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
if ($readyDevices.Count -ne 1) {
    throw "Se requiere exactamente un teléfono autorizado. Detectados: $($readyDevices.Count)."
}

Write-Host "Instalando: $apkFullPath"
& $adb install -r $apkFullPath
if ($LASTEXITCODE -ne 0) {
    Write-Warning 'Si ya existe una versión con otra firma, desactívala, desinstálala y repite.'
    exit $LASTEXITCODE
}

& $adb shell am start -n com.luics415.biogesture/.MainActivity
