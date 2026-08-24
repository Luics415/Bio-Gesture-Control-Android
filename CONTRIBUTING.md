# Contribuir a BioGesture Control Android

Gracias por mejorar el proyecto. Los cambios de visión y accesibilidad pueden afectar todo el dispositivo; por eso cada contribución debe ser pequeña, verificable y explicada.

## Preparación

- Android Studio compatible con AGP 8.13.2.
- JDK 17.
- Android SDK 36.
- Dispositivo Android 10 o posterior para validación física.

```powershell
git clone https://github.com/Luics415/Bio-Gesture-Control-Android.git
cd Bio-Gesture-Control-Android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## Flujo recomendado

1. Crea una rama descriptiva desde `main`.
2. Mantén la lógica de gestos pura en `gesture/` siempre que sea posible.
3. Añade o actualiza pruebas deterministas.
4. Ejecuta pruebas, Lint y compilación.
5. Prueba físicamente vertical y horizontal.
6. Explica en el pull request qué cambió, por qué y cómo se comprobó.

## Reglas de seguridad funcional

- Una lectura ambigua nunca debe producir un clic.
- Un arrastre debe terminar de forma explícita al perder la mano, pausar, rotar o interrumpir el servicio.
- La V está reservada desde el primer fotograma y no puede convertirse en clic.
- El menú radial no debe repetir acciones sin volver al centro.
- No se deben transmitir, registrar o conservar fotogramas de cámara.
- No añadas telemetría ni red sin documentar y obtener consentimiento explícito.

## Estilo

- Kotlin idiomático y nombres descriptivos.
- Estados y tiempos expresados de forma explícita.
- Comentarios para invariantes, no para repetir el código.
- Textos visibles en `strings.xml`.
- Comportamiento equivalente en `layout/` y `layout-land/`.

## Reportes de fallos

Incluye dispositivo, Android, versión, perfil, orientación, iluminación, mano utilizada, pasos, resultado esperado y evidencia visual sin información privada.

Al contribuir aceptas que tu aportación se publique bajo [Apache License 2.0](LICENSE).
