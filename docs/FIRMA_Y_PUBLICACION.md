# Firma y publicación de APK

Este documento está dirigido al propietario del repositorio. La clave oficial permite que Android reconozca futuras versiones como actualizaciones de la misma aplicación.

## Regla principal

**No publiques ni pierdas la clave privada.** Si se pierde, no será posible actualizar las instalaciones oficiales con la misma identidad. Si se filtra, un tercero podría firmar una APK que Android acepte como actualización.

## Crear la clave una sola vez

Ejemplo con JDK 17:

```powershell
keytool -genkeypair -v `
  -keystore biogesture-release.jks `
  -alias biogesture `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000 `
  -dname "CN=Luics415, O=BioGesture Control, C=MX"
```

Usa una contraseña fuerte y única. Guarda al menos dos copias cifradas en ubicaciones distintas. Los archivos `.jks`, `.keystore`, `keystore.properties` y `local-signing/` están excluidos por `.gitignore`.

## Compilar localmente

Gradle lee cuatro propiedades privadas:

- `BIOGESTURE_STORE_FILE`
- `BIOGESTURE_STORE_PASSWORD`
- `BIOGESTURE_KEY_ALIAS`
- `BIOGESTURE_KEY_PASSWORD`

Pueden suministrarse como propiedades Gradle o variables con prefijo `ORG_GRADLE_PROJECT_`:

```powershell
$env:ORG_GRADLE_PROJECT_BIOGESTURE_STORE_FILE = "C:\ruta\biogesture-release.jks"
$env:ORG_GRADLE_PROJECT_BIOGESTURE_STORE_PASSWORD = "<contraseña>"
$env:ORG_GRADLE_PROJECT_BIOGESTURE_KEY_ALIAS = "biogesture"
$env:ORG_GRADLE_PROJECT_BIOGESTURE_KEY_PASSWORD = "<contraseña>"
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

Sin esas propiedades, `assembleRelease` produce `app-release-unsigned.apk`, útil únicamente para validar R8. No debe publicarse.

## Verificar

```powershell
apksigner verify --verbose --print-certs app-release.apk
zipalign -c -P 16 -v 4 app-release.apk
Get-FileHash app-release.apk -Algorithm SHA256
```

Comprueba versión, `applicationId`, certificado, alineación de 16 KB y hash antes de instalar o publicar.

## GitHub Actions

El workflow `release.yml` requiere cuatro secrets:

| Secret | Contenido |
|---|---|
| `BIOGESTURE_KEYSTORE_BASE64` | Keystore codificado en Base64. |
| `BIOGESTURE_STORE_PASSWORD` | Contraseña del almacén. |
| `BIOGESTURE_KEY_ALIAS` | Alias de la clave. |
| `BIOGESTURE_KEY_PASSWORD` | Contraseña de la clave. |

Para obtener Base64 en PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("biogesture-release.jks"))
```

No imprimas secrets en logs ni los incluyas en archivos versionados.

## Crear una release

1. actualiza `versionCode`, `versionName`, CHANGELOG y notas;
2. valida el commit de `main`;
3. crea un tag firmado o anotado, por ejemplo `v3.0.0`;
4. publica el tag;
5. abre **Actions → Signed Android release → Run workflow**, indica el mismo tag y ejecuta el flujo;
6. GitHub Actions compila, firma, calcula SHA-256 y crea la release;
7. descarga la APK publicada, vuelve a verificarla e instálala en un dispositivo limpio.

La ejecución es manual a propósito: crear un tag no publica una APK si los secretos todavía no están configurados. Así se evita dejar una release automática fallida o sin firma.

## Migración desde debug

La clave debug de Android Studio y la clave release son diferentes. Android no permite actualizar entre ellas. Antes de instalar la primera release oficial se debe desinstalar la versión debug; esto borra sus preferencias y obliga a reactivar Accesibilidad y calibración.
