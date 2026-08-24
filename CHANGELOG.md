# Historial de cambios

Todos los cambios relevantes de BioGesture Control Android se documentan aquí.

## [3.0.0] — 2026-08-23

### Añadido

- Interfaz final Material con estado de cámara y accesibilidad.
- Diseño específico de dos columnas para orientación horizontal.
- Identidad visual, nuevo icono y cursor de ancla compacto.
- Manual de usuario ilustrado, privacidad, seguridad, contribución y arquitectura.
- Licencia Apache 2.0, NOTICE y avisos completos de terceros.
- Selección de perfiles 12/20/30 FPS mediante botones segmentados.
- Lectura posterior del volumen multimedia para confirmar el nivel aplicado.
- Proceso de release con reducción R8, verificación de 16 KB y suma SHA-256.

### Mejorado

- Seguimiento con umbrales 0.55/0.55/0.50 y gracia de pérdida de 350 ms.
- Navegación del menú radial modal, pulgar filtrado, centro ampliado y selección de 750 ms.
- Calibración independiente para vertical y horizontal.
- Gestión térmica, negociación del rango de cámara y reintentos acotados.

### Corregido

- Rotación de 90°, eje vertical invertido y doble espejo.
- Handedness derecha/izquierda incorrecto.
- Arrastre 4–8 que se soltaba como un clic breve.
- Órdenes de volumen que mostraban el panel sin cambiar el nivel real.

## [2.0.2] — 2026-08-23

- Versión de validación física del nuevo núcleo de visión y gestos.
- Estabilización del menú radial y volumen multimedia.

## [1.x] — Prototipo original

- Primera prueba de concepto Android.
- Detección MediaPipe, overlay, clics y menú radial inicial.
- Esta etapa demostró la idea, pero conservaba problemas de coordenadas, rotación, espejo y continuidad que motivaron el rediseño completo.
