[CmdletBinding()]
param(
    [string]$KeystorePath = (Join-Path $PSScriptRoot '..\local-signing\biogesture-release.jks')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$keystoreFullPath = [IO.Path]::GetFullPath($KeystorePath)
$keyAlias = 'biogesture'
$securePassword = $null
$passwordPointer = [IntPtr]::Zero
$plainPassword = $null
$previousJavaHome = $env:JAVA_HOME

function Find-CompatibleJavaHome {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += Get-ChildItem -LiteralPath 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'jdk-17*' } |
        Sort-Object Name -Descending |
        ForEach-Object FullName
    $candidates += Get-ChildItem -LiteralPath 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'jdk-17*' } |
        Sort-Object Name -Descending |
        ForEach-Object FullName

    foreach ($candidate in $candidates | Select-Object -Unique) {
        $java = Join-Path $candidate 'bin\java.exe'
        $keytool = Join-Path $candidate 'bin\keytool.exe'
        if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $keytool)) { continue }
        $versionText = (& $java -version 2>&1 | Select-Object -First 1).ToString()
        if ($versionText -match 'version "17[\.]') { return [IO.Path]::GetFullPath($candidate) }
    }
    throw 'No se encontró un JDK 17 compatible. Instala Temurin 17 antes de firmar.'
}

function Find-AndroidSdk {
    foreach ($candidate in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'build-tools'))) {
            return [IO.Path]::GetFullPath($candidate)
        }
    }
    throw 'No se encontró Android SDK. Instálalo desde Android Studio.'
}

function Read-VerifiedPassword {
    while ($true) {
        $first = Read-Host 'Contraseña de la clave de firma' -AsSecureString
        $second = Read-Host 'Repite la contraseña' -AsSecureString
        $firstPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($first)
        $secondPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($second)
        try {
            $firstText = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($firstPtr)
            $secondText = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secondPtr)
            if ($firstText.Length -lt 12) {
                Write-Warning 'Usa al menos 12 caracteres.'
            } elseif ($firstText -ne $secondText) {
                Write-Warning 'Las contraseñas no coinciden.'
            } else {
                return $first
            }
        } finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($firstPtr)
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secondPtr)
            $firstText = $null
            $secondText = $null
        }
    }
}

try {
    $javaHomePath = Find-CompatibleJavaHome
    $env:JAVA_HOME = $javaHomePath
    $keytoolPath = Join-Path $javaHomePath 'bin\keytool.exe'

    if (Test-Path -LiteralPath $keystoreFullPath) {
        Write-Host "Usando clave existente: $keystoreFullPath"
        $securePassword = Read-Host 'Contraseña de la clave de firma' -AsSecureString
    } else {
        $keystoreDirectory = Split-Path -Parent $keystoreFullPath
        New-Item -ItemType Directory -Force -Path $keystoreDirectory | Out-Null
        Write-Host 'Se creará la clave oficial. Guárdala y recuerda su contraseña.' -ForegroundColor Yellow
        $securePassword = Read-VerifiedPassword
    }

    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    if (-not (Test-Path -LiteralPath $keystoreFullPath)) {
        & $keytoolPath -genkeypair -v `
            -keystore $keystoreFullPath `
            -storetype JKS `
            -alias $keyAlias `
            -keyalg RSA `
            -keysize 4096 `
            -validity 10000 `
            -dname 'CN=Luics415, O=BioGesture Control, C=MX' `
            -storepass $plainPassword `
            -keypass $plainPassword
        if ($LASTEXITCODE -ne 0) { throw 'No fue posible crear la clave.' }
    }

    $env:ORG_GRADLE_PROJECT_BIOGESTURE_STORE_FILE = $keystoreFullPath
    $env:ORG_GRADLE_PROJECT_BIOGESTURE_STORE_PASSWORD = $plainPassword
    $env:ORG_GRADLE_PROJECT_BIOGESTURE_KEY_ALIAS = $keyAlias
    $env:ORG_GRADLE_PROJECT_BIOGESTURE_KEY_PASSWORD = $plainPassword

    Push-Location $projectRoot
    try {
        & '.\gradlew.bat' testDebugUnitTest lintDebug assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'La validación Gradle falló.' }

        $gradleText = Get-Content -Raw -LiteralPath 'app\build.gradle.kts'
        $versionMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
        if (-not $versionMatch.Success) { throw 'No se encontró versionName.' }
        $versionName = $versionMatch.Groups[1].Value

        $sourceApk = 'app\build\outputs\apk\release\app-release.apk'
        if (-not (Test-Path -LiteralPath $sourceApk)) {
            throw 'Gradle no produjo un APK firmado.'
        }
        $releaseDirectory = Join-Path $projectRoot 'release'
        New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
        $releaseApk = Join-Path $releaseDirectory "BioGesture-Control-v$versionName.apk"
        $releaseHash = Join-Path $releaseDirectory "BioGesture-Control-v$versionName.sha256"
        Copy-Item -LiteralPath $sourceApk -Destination $releaseApk -Force

        $sdkRoot = Find-AndroidSdk
        $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory |
            Where-Object { $_.Name -match '^\d+(\.\d+)*$' } |
            Sort-Object { [version]$_.Name } -Descending |
            Select-Object -First 1
        if (-not $buildTools) { throw 'No se encontraron Android SDK Build Tools.' }
        & (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose --print-certs $releaseApk
        if ($LASTEXITCODE -ne 0) { throw 'La firma APK no superó la verificación.' }
        & (Join-Path $buildTools.FullName 'zipalign.exe') -c -P 16 -v 4 $releaseApk | Select-Object -Last 1
        if ($LASTEXITCODE -ne 0) { throw 'El APK no está alineado correctamente.' }

        $hash = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $(Split-Path -Leaf $releaseApk)" | Set-Content -LiteralPath $releaseHash -Encoding ascii

        Write-Host ''
        Write-Host "APK final: $releaseApk" -ForegroundColor Green
        Write-Host "SHA-256:  $hash" -ForegroundColor Green
        Write-Host "CLAVE:     $keystoreFullPath" -ForegroundColor Yellow
        Write-Host 'Haz ahora una copia cifrada de la clave. No la subas a GitHub.' -ForegroundColor Yellow
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:ORG_GRADLE_PROJECT_BIOGESTURE_STORE_PASSWORD = $null
    $env:ORG_GRADLE_PROJECT_BIOGESTURE_KEY_PASSWORD = $null
    $plainPassword = $null
    if ($passwordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}
