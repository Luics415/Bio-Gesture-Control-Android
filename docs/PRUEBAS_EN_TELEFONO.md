# Pruebas de aceptación en teléfono

Este documento separa las comprobaciones automatizadas de las que requieren cámara, mano, pantalla real y temperatura. No marques una sección como aprobada sin observarla en el dispositivo objetivo.

## Preparación

- [ ] Para release, instalar el APK firmado y verificar previamente su SHA-256.
- [ ] Conceder cámara.
- [ ] Activar BioGesture en Accesibilidad.
- [ ] Desactivar ahorro de batería solo durante la prueba comparativa.
- [ ] Anotar modelo, versión de Android y frecuencia de pantalla.
- [ ] Reiniciar la app después de una actualización para evitar comparar estados antiguos.
- [ ] Confirmar que el panel muestra versión 3.0.0, cámara y estado de Accesibilidad.
- [ ] Revisar que no exista desbordamiento visual en vertical ni horizontal.

## Detección y coordenadas

- [ ] Los 21 puntos permanecen sobre la mano con la palma abierta.
- [ ] El punto 8 coincide con la punta del índice.
- [ ] La etiqueta derecha/izquierda coincide con la mano mostrada.
- [ ] Al subir la mano, el ancla sube; al bajarla, baja.
- [ ] Con espejo activado, mover la mano a la derecha lleva el ancla a la derecha como en una selfie.
- [ ] Con espejo desactivado, el eje horizontal se invierte una sola vez.
- [ ] No hay salto de coordenadas al alternar el diagnóstico.

## Calibración vertical

- [ ] La vista temporal de cámara y los landmarks están alineados.
- [ ] La sesión termina aproximadamente a los seis segundos aunque se pierda momentáneamente la mano.
- [ ] Un intento insuficiente conserva la calibración anterior.
- [ ] Después de calibrar se alcanzan las cuatro esquinas con un movimiento cómodo.
- [ ] Un punto aislado incorrecto no comprime todo el recorrido.

## Clic y arrastre `4–8`

- [ ] Una pinza breve produce exactamente un clic.
- [ ] Una pinza accidental menor al tiempo mínimo no produce clic.
- [ ] Mantener 300 ms inicia arrastre y no produce clic adicional.
- [ ] El arrastre continúa al menos diez segundos sin soltarse.
- [ ] Un salto de un fotograma no termina el arrastre.
- [ ] Al separar la pinza de forma estable, el arrastre termina una sola vez.
- [ ] Al perder la mano brevemente, el arrastre se recupera; al perderla de forma sostenida, se suelta.
- [ ] Una pérdida de detección menor a 350 ms no hace parpadear el ancla ni el esqueleto.
- [ ] Probar arrastre en pantalla de inicio, lista desplazable, selector y aplicación de dibujo.

## Acción contextual `4–12`

- [ ] Se ejecuta una sola vez por pinza.
- [ ] Abre el menú contextual o pulsación larga cuando la aplicación lo permite.
- [ ] No dispara también el clic `4–8`.
- [ ] Cerrar ambas pinzas se considera ambiguo y no ejecuta acciones.

## Reposo V

- [ ] Una V menor a tres segundos no pausa.
- [ ] Una V de tres segundos activa reposo una sola vez.
- [ ] Pequeños parpadeos de detección no reinician todo el conteo.
- [ ] En reposo no funcionan clic, arrastre, contexto ni menú.
- [ ] Es necesario liberar la V antes de volver a armarla.
- [ ] Una segunda V de tres segundos reactiva el control.

## Menú radial

- [ ] La pose de pulgar durante 800 ms abre el menú.
- [ ] Todos los niveles muestran únicamente opciones reales; no aparece `NULL`.
- [ ] Después de abrir, cambiar la forma de la mano no cierra el menú mientras la mano siga visible.
- [ ] Mantener un sector 750 ms ejecuta una sola vez y el arco muestra el avance.
- [ ] Mantener el pulgar fuera después de ejecutar no repite la acción.
- [ ] Volver al centro iluminado rearma una nueva selección.
- [ ] **VOLVER** regresa a PRINCIPAL y **BACK** cierra.
- [ ] Probar cada acción de CONFIG, EDIT, WEB, MEDIA, PLAY, VOLUME y NAV.
- [ ] En VOLUME, subir y bajar cambian realmente el volumen multimedia un nivel y muestran el porcentaje leído.
- [ ] Silenciar deja el canal multimedia en cero y una segunda selección restaura el nivel anterior.

## Horizontal y rotación

- [ ] Calibrar horizontal y alcanzar los cuatro bordes.
- [ ] El menú cabe dentro de barras, recorte y esquinas.
- [ ] El ancla coincide con el índice en ambas orientaciones horizontales.
- [ ] Girar durante un arrastre lo suelta sin salto ni clic.
- [ ] Girar durante calibración la cancela y solicita repetirla.
- [ ] Volver a vertical conserva su calibración anterior.

## Temperatura y duración

Realiza la misma secuencia con Ahorro, Equilibrado y Precisión.

- [ ] Medir temperatura inicial.
- [ ] Usar el cursor y realizar arrastres durante 15 minutos.
- [ ] Anotar temperatura a 5, 10 y 15 minutos.
- [ ] Confirmar que no hay cierre, congelamiento o retraso creciente.
- [ ] Verificar que la carga baja cuando Android informa estado térmico severo.
- [ ] Mantener reposo diez minutos y confirmar menor calentamiento.

## Registro mínimo del resultado

```text
Dispositivo:
Android:
Perfil:
Orientación:
Versión APK:
Temperatura inicial/final:
Prueba aprobada o fallida:
Pasos exactos para reproducir:
Video o captura:
```
