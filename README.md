# BioGesture Control

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin)
![MediaPipe](https://img.shields.io/badge/Computer%20Vision-MediaPipe-FF6B6B)
![Status](https://img.shields.io/badge/Status-Experimental-orange)

</div>

Aplicación Android experimental desarrollada por Luics415 para controlar un dispositivo mediante gestos de mano en tiempo real, usando visión por computadora con MediaPipe y accesibilidad del sistema.

## Tabla de contenidos

- [Descripción general](#descripción-general)
- [Objetivo del proyecto](#objetivo-del-proyecto)
- [Características principales](#características-principales)
- [Arquitectura](#arquitectura)
- [Requisitos](#requisitos)
- [Instalación](#instalación)
- [Uso](#uso)
- [Gestos y comandos soportados](#gestos-y-comandos-soportados)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Licencia y atribución](#licencia-y-atribución)
- [Capturas](#capturas)
- [Autor](#autor)

## Descripción general

BioGesture Control es un proyecto de interacción por gestos diseñado para Android. La aplicación utiliza la cámara frontal del dispositivo para detectar la mano y convertir los landmarks de MediaPipe en comandos del sistema.

La solución combina tres capas principales:

1. Detección visual de la mano
2. Interpretación de gestos y posiciones
3. Ejecución de acciones del sistema con `AccessibilityService`

Esto permite controlar elementos del sistema operativo, navegar, abrir menús, ajustar volumen y realizar acciones de interacción sin usar la pantalla táctil.

## Objetivo del proyecto

El objetivo de BioGesture Control es crear una interfaz de control alternativa basada en gestos naturales, orientada a accesibilidad, automatización y exploración tecnológica en entornos Android.

Su enfoque principal es hacer que la cámara funcione como un canal de entrada inteligente, donde los movimientos de la mano se reinterpretan como órdenes útiles para el dispositivo.

## Características principales

- Detección de manos en tiempo real con MediaPipe
- Cursor virtual controlado por la posición del dedo índice
- Menú radial interactivo para acceso rápido a comandos
- Funciones de navegación del sistema
- Control de volumen y reproducción multimedia
- Visualización de esqueleto para depuración y análisis visual
- Integración con Overlay y Accessibility Service de Android

## Arquitectura

El proyecto está estructurado en torno a componentes sencillos y funcionales:

### `MainActivity`
Pantalla principal de entrada. Solicita acceso a lo siguiente:

- cámara
- superposición sobre otras aplicaciones
- acceso a configuración de accesibilidad

### `BioGestureService`
Es el núcleo del sistema. Aquí ocurre lo siguiente:

- inicialización del servicio en segundo plano
- carga del modelo `hand_landmarker.task`
- análisis de fotogramas en vivo con CameraX
- interpretación de landmarks con MediaPipe
- ejecución de acciones del sistema mediante `GestureDescription`
- representación visual del cursor, el menu radial y el esqueleto de la mano

### `RadialMenuView` y `SkeletonView`
Encargados de la visualización y apoyo a la experiencia del usuario:

- mostrar opciones rápidas en forma radial
- presentar el esqueleto de la mano detectada
- ayudar en pruebas y depuración visual

## Requisitos

### Requisitos mínimos

- Android 10 o superior (`minSdk = 29`)
- Cámara frontal disponible
- Permisos de cámara y superposición
- Servicio de accesibilidad habilitado
- Android Studio con soporte para Kotlin y Android SDK

### Dependencias principales

- `com.google.mediapipe:tasks-vision:0.10.14`
- `androidx.camera:camera-core:1.3.4`
- `androidx.camera:camera-camera2:1.3.4`
- `androidx.camera:camera-lifecycle:1.3.4`
- `androidx.camera:camera-view:1.3.4`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`

## Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/TU_USUARIO/TU_REPOSITORIO.git
cd TU_REPOSITORIO
```

### 2. Abrir el proyecto en Android Studio

1. Abre Android Studio
2. Selecciona `Open an existing project`
3. Navega a la raíz del proyecto

### 3. Sincronizar Gradle

Android Studio descargará las dependencias necesarias.

### 4. Ejecutar la aplicación

- conecta un dispositivo real o usa un emulador
- selecciona una configuración de ejecución
- ejecuta la aplicación desde Android Studio

## Uso

### Paso 1: Dar permisos
La app solicita permiso de cámara y superposición al iniciarse.

### Paso 2: Activar accesibilidad
Desde la configuración del sistema, activa el servicio de accesibilidad de la aplicación.

### Paso 3: Usar gestos
Una vez habilitado el servicio, la cámara analiza la mano y la app convierte el gesto en una acción del sistema.

## Gestos y comandos soportados

### Control de cursor
- el dedo índice se usa como referencia para mover el cursor

### Click
- una aproximación de dedos desencadena un toque virtual

### Arrastre
- el sistema interpreta movimientos de la mano como desplazamiento continuo

### Menú radial
- se activa a partir de una posición específica con el pulgar
- permite acceso a submenús y acciones múltiples

### Acciones del sistema
- inicio
- atrás
- recientes
- notificaciones
- volumen
- reproducción multimedia
- fullscreen
- navegación web

### Modo reposo
- una secuencia tipo “victory” puede activar o desactivar el modo reposo

## Estructura del proyecto

```text
.
├── app/
│   ├── src/main/java/com/luics415/biogesture/
│   ├── src/main/res/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── README.md
```

## Licencia y atribución

### Copyright

Copyright © 2026 Luics415. Todos los derechos reservados.

El contenido original, la idea, la arquitectura, la implementación, el diseño funcional y el código fuente de este proyecto son propiedad intelectual de Luics415.

### Atribución de terceros

Este proyecto utiliza MediaPipe para detección de manos.

MediaPipe está distribuido bajo la licencia Apache License 2.0.

Por lo tanto:

- se debe preservar la atribución a Google / MediaPipe
- debe mantenerse la licencia Apache 2.0 correspondiente
- deben respetarse los avisos de copyright de terceros

### Aviso de uso responsable

Este proyecto es un prototipo experimental con fines de aprendizaje, desarrollo, investigación y demostración técnica. No debe usarse para vulnerar la privacidad, la seguridad o la autonomía de otros dispositivos.

## Capturas

<img width="1080" height="1496" alt="image" src="https://github.com/user-attachments/assets/51d55349-0b2f-4e64-961c-1442005efc45" />
<img width="705" height="691" alt="image" src="https://github.com/user-attachments/assets/253b9d71-f597-4484-9d7f-5fadf0e91083" />

## Autor

Luics415

## Estado del proyecto

Proyecto en desarrollo experimental, enfocado en visión artificial, accesibilidad y control por gestos en Android.
