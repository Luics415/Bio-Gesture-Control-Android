# BioGesture Control Android 3.0.0

Primera versión estable del núcleo rediseñado para Android.

## Descarga

Descarga `BioGesture-Control-v3.0.0.apk` desde los assets de esta release y compara su SHA-256 con `BioGesture-Control-v3.0.0.sha256`.

## Novedades

- Coordenadas correctas en vertical y horizontal.
- Cursor de ancla compacto sobre el landmark 8.
- Clic y arrastre 4–8 estables.
- Contexto 4–12.
- Reposo/reactivación mediante V de tres segundos.
- Menú radial modal con siete submenús, recentrado y progreso.
- Volumen multimedia aplicado y verificado.
- Calibración separada por orientación.
- Perfiles 12/20/30 FPS y protección térmica.
- Nueva interfaz Material, manual y documentación legal.

## Actualización desde versiones de prueba

Las compilaciones de desarrollo 2.x fueron firmadas con una clave de depuración. Android puede impedir instalar la 3.0.0 firmada para publicación como actualización directa. En ese caso:

1. anota tus ajustes;
2. desactiva BioGesture en Accesibilidad;
3. desinstala la compilación de prueba;
4. instala el APK oficial 3.0.0;
5. vuelve a conceder cámara, Accesibilidad y calibración.

Las futuras versiones oficiales firmadas con la misma clave sí podrán actualizar 3.0.0 directamente.

## Compatibilidad

- Android 10 o posterior.
- Cámara frontal obligatoria.
- ABI incluida por MediaPipe: consulta el APK de la release para el dispositivo concreto.

## Validación

- 72 pruebas JVM.
- Android Lint sin errores.
- APK alineado para bibliotecas nativas de páginas de 16 KB.
- Prueba física principal en Vivo V2314/V2317.

## Limitaciones conocidas

Las acciones web y multimedia dependen de lo que la aplicación activa exponga a Accesibilidad o de que reconozca las teclas/gestos multimedia usados por Android.
