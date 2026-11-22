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

Las imágenes se revisaron a mano una por una con criterio estricto: solo quedan fotos reales en las
que la zona se ve clara y encuadrada como en una foto clínica (se descartaron animales, pinturas,
estatuas, muñecas, dibujos y esquemas, composiciones con diagramas, paisajes con la persona diminuta,
peinados en lugar de cuero cabelludo, vistas que no son la de la zona y duplicados). Los primeros planos (frente, periocular, labios, ojo) son recortes de las fotos
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
| rostro_oblicuo_der | FACE | 12 | 11 | 0/11 | 0/11 | — | 0 | 12/12 | 0/12 | Poca luz en el rostro (4); Cabeza: girar más (25° de 45°) (2); Cabeza: girar más (24° de 45°) (2) |
| rostro_oblicuo_izq | FACE | 11 | 11 | 1/11 | 1/11 | — | 0 | 11/11 | 0/11 | Poca luz en el rostro (4); Barbilla: bajar 8° (Frankfort) (2); Cabeza: girar más (22° de 45°) (1) |
| rostro_perfil_der | FACE | 16 | 5 | 1/5 | 1/5 | — | 0 | 12/12 | 0/12 | Cabeza: girar hasta el perfil (faltan 9° (1); Cabeza: enderezar 16° (inclinada a la de (1); Cabeza: girar hasta el perfil (faltan 18 (1) |
| rostro_perfil_izq | FACE | 16 | 6 | 2/6 | 2/6 | — | 0 | 12/12 | 0/12 | Cabeza: enderezar 7° (inclinada a la der (2); Barbilla: bajar 13° (Frankfort) (1); Cabeza: enderezar 7° (inclinada a la izq (1) |
| tercio_superior | FACE | 28 | 8 | 23/28 | 23/28 | 0/8 | — | 12/12 | 0/12 | Luz desigual: lado derecho más oscuro (3); Cabeza: enderezar 7° (inclinada a la izq (2); Cabeza: girar a la derecha 6° (1) |
| periocular | FACE | 27 | 27 | 6/27 | 6/27 | — | — | 12/12 | 0/12 | Luz desigual: lado derecho más oscuro (7); Luz desigual: lado izquierdo más oscuro (7); Mirada: al frente (4) |
| ojo_der | NONE | 26 | — | 26/26 | 26/26 | 0/8 | — | 12/12 | 0/12 |  |
| ojo_izq | NONE | 26 | — | 26/26 | 26/26 | 0/8 | — | 12/12 | 0/12 |  |
| labios | FACE | 28 | 17 | 12/28 | 12/28 | 0/8 | — | 12/12 | 0/12 | Luz desigual: lado derecho más oscuro (6); Luz desigual: lado izquierdo más oscuro (5); Mirada: al frente (3) |
| cuello | POSE | 8 | 6 | 8/8 | 8/8 | 0/8 | — | 8/8 | 0/8 |  |
| cuero_cabelludo_vertice | NONE | 8 | — | 7/8 | 7/8 | 0/8 | — | 8/8 | 0/8 | Mantenga el teléfono quieto (1) |
| cuero_cabelludo_corona | NONE | 8 | — | 7/8 | 7/8 | 0/8 | — | 8/8 | 0/8 | Mantenga el teléfono quieto (1) |
| cuero_cabelludo_parietal_der | NONE | 8 | — | 7/8 | 7/8 | 0/8 | — | 8/8 | 0/8 | Mantenga el teléfono quieto (1) |
| cuero_cabelludo_parietal_izq | NONE | 8 | — | 7/8 | 7/8 | 0/8 | — | 8/8 | 0/8 | Mantenga el teléfono quieto (1) |
| abdomen_frontal | POSE | 10 | 7 | 10/10 | 10/10 | 0/8 | — | 10/10 | 0/10 |  |
| abdomen_lateral | POSE | 8 | 5 | 7/8 | 7/8 | 0/8 | — | 8/8 | 0/8 | Mantenga el teléfono quieto (1) |
| abdomen_lateral_izq | POSE | 9 | 6 | 8/9 | 8/9 | 0/8 | — | 9/9 | 0/9 | Mantenga el teléfono quieto (1) |
| torax_mamas | POSE | 11 | 9 | 11/11 | 11/11 | 0/8 | — | 11/11 | 0/11 |  |
| espalda | POSE | 10 | 1 | 10/10 | 10/10 | 0/8 | — | 10/10 | 0/10 |  |
| gluteos | POSE | 5 | 2 | 5/5 | 5/5 | 0/5 | — | 5/5 | 0/5 |  |
| muslos | POSE | 8 | 6 | 7/8 | 7/8 | 0/8 | — | 8/8 | 0/8 | Mantenga el teléfono quieto (1) |
| brazo_der | POSE | 14 | 7 | 14/14 | 14/14 | 0/8 | — | 12/12 | 0/12 |  |
| brazo_izq | POSE | 20 | 8 | 20/20 | 20/20 | 0/8 | — | 12/12 | 0/12 |  |
| manos_dorso | POSE | 15 | 0 | 15/15 | 15/15 | 0/8 | — | 12/12 | 0/12 |  |
| manos_palma | POSE | 6 | 0 | 5/6 | 5/6 | 0/6 | — | 6/6 | 0/6 | Encuadre la zona dentro de la guía (1) |
| pies_dorso | POSE | 12 | 0 | 11/12 | 11/12 | 0/8 | — | 12/12 | 0/12 | Mantenga el teléfono quieto (1) |
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

## Criterios clínicos por zona (`ZoneCriteria`)

Además del encuadre, la toma no se dispara hasta que la zona está como pide la fotografía clínica y la
guía dibujada de la app (si falta un dato, se pide; nunca se acepta por defecto):

| Zona | Se exige |
|---|---|
| Manos (dorso / palma) | Las **dos manos completas** (los 21 puntos de cada una dentro del cuadro), **todas las falanges visibles**: ningún dedo recogido (articulaciones ≥ 115–120°, pulgar ≥ 110°) ni acortado por estar doblado hacia la cámara (cada dedo ≥ 45 % del largo de la palma); los dedos **hacia abajo** (± 60°) y la **cara pedida** (dorso o palma) hacia la cámara. Dedos más abiertos, más largos o cortos, o la mano algo girada **no son incumplimiento**: la guía se ajusta |
| Pies (dorso / planta) | Los **dos pies** (separados) y **completos**: la piel de cada pie no toca los bordes del cuadro salvo arriba (piernas); un pie partido por una tira cuenta como uno; **los dedos al aire**: el borde distal de cada pie linda con el fondo, no con ropa, calcetín o calzado (≤ 35 % del borde). No se cuenta cada dedo: con fotos reales los dedos se tocan y su silueta no los separa de forma fiable (probado con las fotos de internet); los relieves hallados solo se dibujan como apoyo |
| Abdomen frontal, tórax | Hombros y caderas en el cuadro y **nivelados** (≤ 8°), **de frente**, brazos **separados** del tronco y la zona **descubierta** (≥ 70 % piel, ≤ 15 % ropa en su región; la ropa interior en la cadera es correcta) |
| Abdomen lateral | **De lado** (hombros y caderas superpuestos), brazos **subidos o cruzados** (que no tapen el flanco) y abdomen **descubierto** |
| Espalda, glúteos | **De espaldas** (sin cara), **enteros** (fondo a los dos lados, no un recorte) y **descubiertos**; si el detector ve hombros y caderas, además nivelados y brazos separados |
| Muslos / piernas | Caderas, rodillas y tobillos en el cuadro, **rodillas extendidas** (≥ 150°: se admite la flexión natural), pies **algo separados** y muslos **descubiertos** |
| Brazo (derecho / izquierdo) | Hombro, codo y muñeca de ese brazo, **codo extendido** (≥ 150°: se admite la flexión natural), **separado del eje del tronco 5–45°** (la guía, ≈ 20°, se ajusta al ángulo real) y **descubierto** (sin manga) |
| Cuello | Hombros nivelados, de frente y cuello y escote **descubiertos** |

**Zona centrada**: el centro de la parte del cuerpo (manos, pies en horizontal, brazo, piernas,
tronco, glúteos, cuello en horizontal) a menos del 13 % del centro del cuadro; si no, «Centre la zona
en el cuadro».

**Decisión estable**: la zona cumple los criterios si cumple en al menos 2 de los 3 últimos análisis;
una segmentación o detección suelta no cambia el veredicto ni hace parpadear las indicaciones. Los pies
de una máscara se calculan una sola vez (criterios, esquema y puntos la comparten).

**Más puntos de referencia (`BodyPoints`)** para alinear con la referencia y comprobar la toma: los 21
puntos de cada mano; punta, centro y bordes interno y externo (a media longitud y en el antepié) de
cada pie; y los bordes del contorno a ¼, ½ y ¾ del brazo, antebrazo, muslo, pierna y tronco, a cada
lado. Se calculan igual en la referencia y en la toma (lados por la imagen).

**Una sola extremidad**: con el selector «Derecha / Izquierda» de manos, pies y piernas se exige una
(completa y con sus falanges), no las dos. En el brazo se evalúa el brazo pedido o, si el detector
invierte los lados (vista posterior) o solo ve uno (primer plano), el que se vea entero; sin la cadera
en el cuadro no se mide la separación del cuerpo.

**Guía girada 90°** (botón «Girar la referencia 90°»): los criterios se miden en la orientación de la
guía (la escena del cuadro, puntos y piel, se gira igual antes de evaluarla): manos o pies de lado con la
guía girada cumplen («dedos hacia abajo» pasa a ser hacia donde apunta la guía). El esquema adaptativo
está en coordenadas del cuadro y se dibuja fuera del giro de la guía, sobre la mano que se ve.

**Guía adaptativa (`BodySchema`)**: en manos, pies y zonas del cuerpo, en cuanto se detecta la
anatomía la ilustración fija se sustituye por un esquema dibujado sobre lo detectado (el **contorno real de cada mano**, trazado por el borde de su piel
segmentada y separado de la otra mano por su esqueleto, con las yemas; sin segmentación, palma y falanges; contorno de cada pie y sus dedos; huesos del brazo, pierna y tronco con el
grosor según el ancho de hombros). Así la guía se adapta a la longitud y al ángulo reales de cada dedo o
miembro, y solo se exige lo clínico: que existan y se vean todas las falanges y la postura general.

Datos en el teléfono: puntos del cuerpo (ML Kit Pose), manos (**MediaPipe Hand Landmarker**, modelo
oficial incluido en la app) y piel / ropa por píxel (segmentación multiclase de MediaPipe, ya incluida).

### Prueba con dibujos propios

`ZoneCriteriaDrawingsTest` dibuja (en código, sin fotos de terceros) cada zona del cuerpo en la toma
correcta y en tomas incorrectas, y comprueba que la correcta se acepta y cada incorrecta se rechaza con
su indicación. Se aceptan (la guía se ajusta): dedos más abiertos, más largos o más cortos, manos giradas 30°, dedos de los pies más largos, hombros a 5°, flexión natural de rodillas y codo, brazo a 35°. Se rechazan: dedo recogido (anular, pulgar), puño, una sola mano, dedos fuera del cuadro, dedos hacia
arriba, dorso en lugar de palma y al revés; pies cortados, dedos tapados por un calcetín, un solo pie (también partido por una tira);
abdomen con camiseta, brazos sobre el abdomen, hombros inclinados, de espaldas; lateral con los brazos
tapando el flanco o de frente; espalda de frente o recortada; glúteos con pantalón o de frente; piernas
con la rodilla flexionada, con pantalón, con los pies juntos o fuera del cuadro; brazo con el codo
doblado, pegado al cuerpo, a 60° o con manga; cuello con jersey de cuello alto o hombros inclinados. Los
puntos salen de la geometría del dibujo y la piel y la ropa se clasifican a partir de sus **píxeles**.
Imágenes (con el esquema adaptativo en blanco): `app/build/reports/zone_criteria/`.

Con las fotos de internet (tabla de arriba), casi ninguna cumple estos criterios (ropa, manos o pies
incompletos, brazo recortado o con el codo doblado, espaldas recortadas): ya no disparan.

Limitación: la planta y el dorso del pie no se distinguen (no hay un detector de puntos del pie); lo
decide quien encuadra con la guía.
