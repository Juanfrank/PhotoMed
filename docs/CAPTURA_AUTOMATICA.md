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
| Pies (dorso / planta) | Los **dos pies** (separados) y **completos**: la piel de cada pie no toca los bordes del cuadro salvo arriba (piernas); un pie partido por una tira cuenta como uno; **los dedos al aire** (el borde distal linda con el fondo, no con calcetín, ropa o calzado: ≤ 35 %) y **distinguibles**: al menos 3 dedos en cada pie (ver «Dedos del pie») |
| Abdomen frontal, tórax | Hombros y caderas en el cuadro y **nivelados** (≤ 8°), **de frente**, brazos **separados** del tronco y la zona **descubierta** (≥ 70 % piel, ≤ 15 % ropa en su región; la ropa interior en la cadera es correcta) |
| Abdomen lateral | **De lado** (hombros y caderas superpuestos), brazos **subidos o cruzados** (que no tapen el flanco) y abdomen **descubierto** |
| Espalda, glúteos | **De espaldas** (sin cara), **enteros** (fondo a los dos lados, no un recorte) y **descubiertos**; si el detector ve hombros y caderas, además nivelados y brazos separados |
| Muslos / piernas | Caderas, rodillas y tobillos en el cuadro, **rodillas extendidas** (≥ 150°: se admite la flexión natural), pies **algo separados** y muslos **descubiertos** |
| Brazo (derecho / izquierdo) | Hombro, codo y muñeca de ese brazo, **codo extendido** (≥ 150°: se admite la flexión natural), **separado del eje del tronco 5–45°** (la guía, ≈ 20°, se ajusta al ángulo real) y **descubierto** (sin manga) |
| Cuello | Hombros nivelados, de frente y cuello y escote **descubiertos** |

### Dedos del pie (`ToeDetector`)

Contar los dedos solo por la silueta de la segmentación falla con fotos reales (se tocan). Se combinan
tres pasos, ajustados con 20 fotos reales de pies (de internet, solo para evaluar, fuera del repositorio):

1. **Piel del pie con su propio color**: modelo de cromaticidad y luminosidad del núcleo del pie; los
   píxeles en sombra (surcos y huecos entre dedos) quedan fuera, unido al interior de la máscara para
   que los dedos no se separen del pie.
2. **Contorno en nube**: el borde de la punta del pie es una curva por dedo. Se envuelve por fuera
   (envolvente convexa) y el punto más hondo bajo cada lado es un **valle entre dos dedos**; de forma
   recursiva, porque con dedos de distinta longitud un lado de la envolvente cubre varios valles. La
   franja distal se amplía hasta que el borde sea continuo (dedos largos).
3. **Surco**: de cada valle nace la línea de separación hacia dentro, con la inclinación (± 25°) en la que
   la piel es más oscura que a sus lados (el borde de cada dedo se ve como una arruga). Si el contorno ya
   muestra varios dedos, los tramos demasiado anchos para un dedo (el más ancho, el gordo) se revisan
   buscando un surco sin valle (dedos muy juntos).

Resultado con las 17 fotos evaluables: 5 dedos exactos en 12 y 4–5 en 14 (`ToeDetectorPhotosTest`,
informe `pies_fotos_reales.png`). Fallan sobre todo cuando la segmentación no separa bien el pie (pies en
el agua, entre tiras de sandalia). En la cámara, la guía dibuja la separación entre dedos y su punta.

**Estabilidad entre cuadros (pies)**: `FootStabilityTest` genera, con fotos reales de pies, cuadros
casi iguales (ruido, ±8 % de luz, desplazamientos de medio por ciento, recompresión) segmentados con el
mismo modelo, y mide cuánto cambian el veredicto, los dedos y los puntos, y si cada cuadro coincide con el
primero. Medidas que lo estabilizan: máscara de clases por mayoría de las tres últimas segmentaciones
(`ClassesFusion`; la referencia, con tres luces), puntos del pie referidos a la punta y al ancho del
antepié, cada pie nombrado por su mitad de la imagen, y tolerancia de 5 % para los bordes de contorno
(3 % para los puntos anatómicos). Resultado: 0 cambios de veredicto en 50 cuadros y 38/50 coincidencias;
fallan las fotos con el pie cortado por el borde, pies diminutos en arena o plantas cruzadas.

### Uñas (`Nails`)

La uña solo se ve por el dorso, en la falange distal: una placa de color distinto de la piel que la rodea
en el mismo dedo (anillo de piel alrededor de la placa, sin el fondo junto a la punta). Evaluado con fotos
reales (`NailsPhotosTest`, informe `unas.png`): las uñas pintadas o muy marcadas destacan con claridad
(ΔE 15–50) y ninguna planta muestra 3 así; las uñas naturales dan un contraste parecido al de las yemas de
la planta (ΔE 3–8), así que con el color solo no se distinguen de forma fiable. Por eso se usan solo las
claramente visibles (ΔE ≥ 15):

- **Pies**: cada uña es un dedo (confirman el recuento si el contorno se quedó corto); en la toma de
  planta, 3 o más uñas en un pie avisan «se ven las uñas; muestre la planta».
- **Manos (dorso)**: 3 o más uñas en una mano confirman el dorso aunque la orientación de los puntos diga
  otra cosa (y en la toma de palma, avisan de que se ve el dorso).
- Se dibujan en el esquema como puntos de referencia.

### Zonas del cuerpo sin los puntos del detector

- **Manos** (el detector de manos falla en primeros planos y con algunas luces): por la piel, como los
  pies. Cada mano es una mancha de piel con los dedos hacia abajo: entera (solo sale del cuadro por la
  muñeca), con los dedos extendidos (contorno en nube con ≥ 4 dedos y surcos hondos: profundidad ≥ 0,35
  anchos; en los pies ≈ 0,1, así un par de pies no pasa por manos) y, con uñas claramente visibles, por el
  dorso. Contorno y centrado también sin puntos.
- **Brazo en primer plano** (sin puntos del cuerpo): la mayor mancha de piel del cuerpo (sin cara a la
  vista), alargada (≥ 3:1) y que recorre ≥ 55 % de la diagonal (una manga la corta), con su línea central
  recta (codo extendido, desviación ≤ 9 % del largo) y sin ropa encima.
- **Luz desigual entre mejillas**: solo en el rostro frontal (en los primeros planos las mejillas quedan en
  el borde o fuera); **mirada al frente**: no en los labios.

### Precisión entre zonas (`crossZonePrecision`)

La primera foto de cada zona (con sus criterios) se evalúa sobre las fotos de prueba de **las demás zonas**:
no debería disparar. Las zonas sin detector o con primeros planos sin cara (ojo, cuero cabelludo, labios,
periocular, frente) disparaban con casi cualquier escena quieta. Con la segmentación (piel de la cara, pelo)
y el detector de rostro se comprueba el contenido:

| Comprobación | Datos (fotos reales, centro del cuadro) |
|---|---|
| Primer plano del rostro: piel de la cara en el centro ≥ 0,35 | ojo, periocular, labios, frente: mediana 0,80–0,99; cuerpo ≈ 0 |
| … y llenando el cuadro: piel de la cara en el borde ≥ 0,25 | primeros planos 0,31–0,92; cara entera ≈ 0 |
| Ojo, periocular, frente: piel de la cara en el borde inferior ≥ 0,4; labios: < 0,6 | ojo 0,99, frente 0,97, periocular 0,78; labios 0,00 |
| Ojo: el detector de rostro no ve los dos ojos | primer plano de un ojo: cara detectada 2/26; periocular 25/27 |
| Cuero cabelludo: pelo en el centro ≥ 0,25 y piel de la cara < 0,5 | cuero cabelludo: pelo 0,66 |
| Cuello: del mentón a los hombros, centrado también en vertical | un retrato de la cara no es la toma del cuello |
| Espalda y glúteos sin puntos del cuerpo: sin piel de la cara (> 12 %) | |

Falsos disparos: **1174 → 63 de 3974** (ojo 143 → 7, cuero cabelludo 144 → 3, labios 95 → 1,
periocular 92 → 13, frente 89 → 10, cuello 16 → 1). Lo que queda es la confusión entre primeros planos
parecidos (frente y periocular). Exhaustividad con las fotos propias: ojo 26 → 22, labios 12 → 9 primeras
fotos, cuero cabelludo 7 → 5; el resto sin cambios.

Luz en vistas giradas (oblicuo, perfil): el lado lejano queda en sombra por la propia vista; se mide el
lado que mira a la cámara (mínimo 0,28), así no se penaliza la sombra natural ni la piel oscura bien
iluminada.

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

## Evaluación con fotos etiquetadas (culturismo, pedicura, manicura)

`ZoneEvalTest` evalúa fotos de internet etiquetadas a mano (`stock/zones/eval_labels.json`, fuera del
repositorio): por zona, cuántas fotos que cumplen se aceptan, cuántas que muestran la zona sin cumplir se
rechazan, el motivo de cada rechazo y qué otras zonas las aceptarían. Las fotos de pies se evalúan también
giradas 180° (la guía se puede girar). Informe: `build/reports/zone_eval.md`.

Conclusiones de la primera ronda:

- **Pedicura**: casi todas las fotos promocionales recortan los dedos o los pies por el borde o los muestran
  juntos; se rechazan, con razón. Sirvieron para separar dos pies unidos por las piernas y para medir el
  margen lateral solo en el pie.
- **Culturismo**: las vistas de espalda se rechazaban porque el detector del cuerpo «ve» la nariz también
  de espaldas. Ahora la orientación sale del lado de los hombros (sin un error en estas fotos) y la espalda
  pasa de 1 a 3 aceptadas de 5; el tórax deja de aceptar espaldas y el brazo deja de aceptar vistas de frente.
  Límite conocido: a la resolución de la segmentación (96 px) un bikini pequeño se ve como piel.
- **Manicura**: manos sueltas recortadas (rechazadas) y escenas con personas como negativos; un primer plano
  de una mano ya no dispara glúteos ni espalda.
