# Política de privacidad

**BioGesture Control Android 3.0.0**  
Última actualización: 23 de agosto de 2026

## Resumen

BioGesture Control procesa la imagen de la cámara frontal **localmente en el dispositivo** para detectar una mano y convertir gestos confirmados en acciones de Android. La aplicación no incorpora analítica, publicidad, cuentas de usuario ni un servidor propio.

## Datos procesados

- Fotogramas temporales de la cámara frontal.
- Los 21 landmarks de la mano calculados por MediaPipe.
- Preferencias locales: espejo, perfil de rendimiento, diagnóstico y límites de calibración.
- Información operativa de Android necesaria para ejecutar acciones de accesibilidad sobre la ventana activa.

## Tratamiento y almacenamiento

- Los fotogramas se mantienen únicamente el tiempo necesario para una inferencia y no se guardan como fotografías o video.
- Los landmarks se mantienen en memoria y se reemplazan con cada resultado.
- La calibración y los ajustes se guardan en las preferencias privadas de la aplicación y pueden transferirse mediante la copia de seguridad de Android.
- BioGesture no transmite imágenes, landmarks ni contenido de accesibilidad a Luics415 ni a un servicio remoto.

## Permisos

| Permiso/capacidad | Motivo |
|---|---|
| Cámara | Detectar la mano con la cámara frontal. |
| Servicio en primer plano de cámara | Mantener la detección activa de forma visible y conforme a Android. |
| Accesibilidad | Inyectar toques, arrastres y acciones globales, y localizar controles compatibles. |
| Modificar ajustes de audio | Cambiar el volumen multimedia desde el menú radial. |

## Control del usuario

El usuario puede detener todo el procesamiento desactivando **BioGesture Control** en Ajustes de Accesibilidad. También puede revocar la cámara, borrar los datos de la aplicación o desinstalarla desde los ajustes de Android.

## Aplicaciones y sitios externos

Los botones Manual y GitHub abren enlaces en el navegador elegido por el usuario. Desde ese momento aplican las políticas del navegador y del sitio externo.

## Alcance

Esta política describe el código publicado en este repositorio. Una APK modificada o redistribuida por otra persona puede comportarse de forma diferente; verifica siempre el origen y la firma del archivo instalado.

## Contacto

Para reportar una duda de privacidad, abre un issue en el [repositorio oficial](https://github.com/Luics415/Bio-Gesture-Control-Android/issues).
