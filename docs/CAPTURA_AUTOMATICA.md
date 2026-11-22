# Captura automática por zona: prueba con fotos reales

`ZoneAutoCaptureTest` comprueba el disparo automático en **las 30 zonas** de la app con fotos reales
descargadas de internet (Openverse y Wikimedia Commons, licencias libres; no se guardan en el
repositorio). Los puntos de cara y cuerpo los calcula **MediaPipe** (los mismos modelos que la app:
`face_landmarker.task` y BlazePose) y la prueba los pasa por **el mismo código que la cámara**:
conversión de la malla y de la pose, luz del rostro, estandarización (primera foto), coincidencia con
la referencia (por puntos o por bordes) y la condición del disparo (`AutoCapture.ready`).

## Reproducir

```bash
python3 tools/eval/fetch_zone_images.py          # imágenes → app/src/test/resources/stock/zones/<zona>/
python3 tools/eval/zone_landmarks.py             # puntos (y primeros planos y espejos) → landmarks.json
gradle :app:testDebugUnitTest --tests '*ZoneAutoCaptureTest'   # informe: app/build/reports/zone_autocapture.md
```

Las imágenes se revisaron a mano (se descartaron animales, pinturas, estatuas, duplicados y fotos que
no muestran la zona). Los primeros planos (frente, periocular, labios, ojo) son recortes de las fotos
frontales, como los encuadraría la cámara; los lados izquierdos, espejos de los derechos (y, para
comprobar la simetría de la lógica, también los puntos reflejados de cada original).

## Qué se comprueba en cada zona

- **Primera foto (sin referencia)**: dispara con una foto bien tomada; no dispara moviendo el
  teléfono ni con la vista equivocada (frontal en oblicuo o perfil, lado contrario).
- **Foto de control (con referencia)**: dispara con el mismo encuadre (1 % de desplazamiento) y no si
  el paciente o la cámara se mueven (15 % y zoom ×0,8). Es también lo que vigila la cancelación
  durante la captura.
- Ningún rechazo sin explicación: cada vez que no dispara, la app dice por qué.

## Resultado

| Zona | Detector | Imágenes | Con puntos | 1.ª foto: dispara | 1.ª foto: cumplen la geometría | 1.ª foto moviendo el teléfono: dispara | Vista equivocada: dispara | Referencia, mismo encuadre: dispara | Referencia, movido: dispara | Indicaciones más frecuentes al no disparar |
|---|---|---|---|---|---|---|---|---|---|---|
| rostro_frontal | FACE | 28 | 28 | 5/28 | 6/28 | — | — | 12/12 | 0/12 | Luz desigual: lado derecho más oscuro (9); Expresión: relajar la sonrisa (9); Luz desigual: lado izquierdo más oscuro (6) |
| rostro_oblicuo_der | FACE | 21 | 20 | 0/20 | 0/20 | — | 0 | 12/12 | 0/12 | Poca luz en el rostro (5); Expresión: relajar la sonrisa (3); Cabeza: girar más (1° de 45°) (3) |
| rostro_oblicuo_izq | FACE | 17 | 17 | 1/17 | 1/17 | — | 0 | 12/12 | 0/12 | Barbilla: subir 8° (Frankfort) (3); Expresión: relajar la sonrisa (3); Poca luz en el rostro (3) |
| rostro_perfil_der | FACE | 24 | 13 | 1/13 | 1/13 | — | 0 | 12/12 | 0/12 | Cabeza: enderezar 4° (inclinada a la izq (2); Poca luz en el rostro (2); Demasiada luz: sin flash (2) |
| rostro_perfil_izq | FACE | 27 | 16 | 2/16 | 2/16 | — | 0 | 12/12 | 0/12 | Poca luz en el rostro (3); Cabeza: enderezar 19° (inclinada a la de (2); Barbilla: bajar 9° (Frankfort) (2) |
| tercio_superior | FACE | 28 | 11 | 21/28 | 21/28 | 0/8 | — | 12/12 | 0/12 | Luz desigual: lado derecho más oscuro (3); Cabeza: enderezar 7° (inclinada a la izq (2); Luz desigual: lado izquierdo más oscuro (2) |
| periocular | FACE | 28 | 26 | 10/28 | 10/28 | 0/2 | — | 12/12 | 0/12 | Luz desigual: lado derecho más oscuro (6); Luz desigual: lado izquierdo más oscuro (6); Mirada: al frente (5) |
| ojo_der | NONE | 28 | — | 28/28 | 28/28 | 0/8 | — | 12/12 | 0/12 |  |
| ojo_izq | NONE | 28 | — | 28/28 | 28/28 | 0/8 | — | 12/12 | 0/12 |  |
| labios | FACE | 28 | 18 | 11/28 | 11/28 | 0/8 | — | 12/12 | 0/12 | Luz desigual: lado derecho más oscuro (9); Mirada: al frente (5); Cabeza: girar a la derecha 6° (3) |
| cuello | POSE | 11 | 8 | 11/11 | 11/11 | 0/8 | — | 11/11 | 0/11 |  |
| cuero_cabelludo_vertice | NONE | 15 | — | 14/15 | 14/15 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |
| cuero_cabelludo_corona | NONE | 15 | — | 14/15 | 14/15 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |
| cuero_cabelludo_parietal_der | NONE | 15 | — | 14/15 | 14/15 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |
| cuero_cabelludo_parietal_izq | NONE | 15 | — | 14/15 | 14/15 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |
| abdomen_frontal | POSE | 9 | 9 | 9/9 | 9/9 | 0/8 | — | 9/9 | 0/9 |  |
| abdomen_lateral | POSE | 11 | 7 | 10/11 | 10/11 | 0/8 | — | 11/11 | 0/11 | Mantenga el teléfono quieto (1) |
| abdomen_lateral_izq | POSE | 14 | 8 | 12/14 | 12/14 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (2) |
| torax_mamas | POSE | 15 | 13 | 15/15 | 15/15 | 0/8 | — | 12/12 | 0/12 |  |
| espalda | POSE | 13 | 1 | 13/13 | 13/13 | 0/8 | — | 12/12 | 0/12 |  |
| gluteos | POSE | 6 | 2 | 6/6 | 6/6 | 0/6 | — | 6/6 | 0/6 |  |
| muslos | POSE | 15 | 12 | 14/15 | 14/15 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |
| brazo_der | POSE | 10 | 9 | 9/10 | 9/10 | 0/8 | — | 10/10 | 0/10 | Mantenga el teléfono quieto (1) |
| brazo_izq | POSE | 14 | 10 | 13/14 | 13/14 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |
| manos_dorso | POSE | 12 | 2 | 12/12 | 12/12 | 0/8 | — | 12/12 | 0/12 |  |
| manos_palma | POSE | 6 | 1 | 5/6 | 5/6 | 0/6 | — | 6/6 | 0/6 | Encuadre la zona dentro de la guía (1) |
| pies_dorso | POSE | 14 | 0 | 12/14 | 12/14 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (2) |
| pies_planta | POSE | 8 | 0 | 8/8 | 8/8 | 0/8 | — | 8/8 | 0/8 |  |
| libre | NONE | 28 | — | 27/28 | 27/28 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |

En las vistas completas del rostro, las fotos de internet que no disparan fallan por motivos reales
de la fotografía clínica (luz desigual entre mejillas, sonrisa, barbilla fuera del plano de Frankfort,
cabeza inclinada, giro fuera de 45° ± 8°): toda foto que cumple la geometría de la vista dispara.

## Errores encontrados y corregidos

1. **Mirada vertical sesgada**: se medía respecto de la línea de las comisuras y el iris está por
   encima de ella; una mirada al frente salía «hacia arriba» y bloqueaba la vista frontal (26 de 28).
   Ahora se mide respecto del centro de los párpados (frontales que disparan: 1 → 5 de 28).
2. **Mirada en vistas giradas**: con la cabeza a 45° o de perfil, los ojos que miran al frente de la
   cara proyectan el iris desplazado; exigir «iris centrado» pedía mirar a la cámara. Solo se exige de
   frente (con referencia se sigue comparando la mirada entre fotos).
3. **Perfil inalcanzable**: se pedían ≥ 70° y MediaPipe estima 55–60° en un perfil verdadero (más allá
   pierde el rostro). Ahora ≥ 55°, o ≥ 48° con la punta nasal en el borde del óvalo.
4. **Perfil «casi» con una foto frontal**: faltar todo el giro pesaba como un detalle y el «casi
   alineada» (≥ 80 %) disparaba con fotos frontales en la zona de perfil (4 de 28). Ahora pesa por
   grados; además se exige el lado (derecho o izquierdo).
5. **Luz desigual en oblicuos y perfiles**: una mejilla queda de lado; solo se exige de frente. Y en
   cabezas muy giradas, la luz se mide en el rostro y no en mejillas proyectadas fuera de él.
6. **Cuerpo de espaldas, manos, pies, cuero cabelludo, ojo y modo libre: nunca disparaban sin
   referencia**. El detector de cuerpo localiza a la persona por la cara (de espaldas o en primer
   plano no la ve: espalda 1 de 13, manos 2 de 12, pies 0 de 14) y en las zonas sin detector no había
   nada que evaluar. Ahora, en el cuerpo y sin puntos que comprobar, dispara cuando el encuadre queda **quieto
   0,7 s** con imagen con detalle (no con el teléfono en movimiento ni con la cámara tapada); en el
   cuero cabelludo, además, con el ángulo de la cámara que pide cada vista.
7. **Con referencia, esperaba puntos que no existían**: si la foto de referencia tenía algún punto del
   cuerpo pero no los de la zona, la cámara se quedaba buscándolos. Ahora se usan los puntos solo si
   la referencia tiene los de la zona; si no, se alinean los bordes.
8. **Con referencia por bordes, casi nunca coincidía**: el % de bordes coincidentes, corregido por
   azar, sale bajo en escenas con mucha textura (piel, pelo, manos) incluso con la misma imagen (0,00–0,55).
   Con la alineación de bordes segura decide su geometría (desplazamiento, escala y giro); si no, ese %
   como antes. Con el mismo encuadre: manos 6/12 → 12/12, pies 6/12 → 12/12, palmas 0/6 → 6/6.

## Ver lo que la app identifica

`ZoneAutoCaptureTest.drawOverlays` dibuja sobre cada foto de prueba lo que usa la cámara
(`app/build/reports/zone_overlays/<zona>.jpg`): contornos de la cara (óvalo en verde, ojos en azul,
cejas en violeta, nariz en naranja, labios en rojo) y pupilas; esqueleto del cuerpo; áreas donde se
mide la luz (cuadros amarillos; naranjas si es por regiones); regiones de contorno de la guía de la
zona (elipses celestes, donde se buscan los bordes con referencia) y bordes de referencia (puntos
amarillos); abajo, si la primera foto dispara o la primera indicación por la que no.

Limitación: en las zonas que se deciden por la quietud del encuadre (cuerpo, cuero cabelludo, ojo,
modo libre) la app no comprueba que se vea la zona correcta, solo que el encuadre esté quieto y con
detalle; el encuadre lo decide quien toma la foto con la guía.
