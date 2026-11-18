# Algoritmo de coincidencia contornal (alineación antes/después)

Objetivo: dadas dos fotografías de la **misma zona**, encontrar la transformación de semejanza
`T` (escala uniforme `s`, rotación `θ`, traslación `t`) que superpone la foto **después** sobre la
foto **antes**, para que el comparador muestre cambios reales y no diferencias de encuadre.

```
p_antes ≈ T(p_después) = s·R(θ)·p_después + t
```

Coordenadas normalizadas: `x ∈ [0,1]`, `y ∈ [0, 4/3]` (unidad = ancho de la imagen 3:4), así la
transformación es independiente de la resolución. En pantalla se aplica con
`graphicsLayer(transformOrigin = 0,0; scale = s; rotationZ = θ; translation = t·ancho)`.

## Etapas

### 0. Perspectiva vertical por proporciones de contornos (sin sensores)

Una diferencia de inclinación de la cámara entre tomas (keystone) hace que el ancho de cada franja
horizontal cambie en proporción a su altura: p. ej. la frente se ensancha respecto de la mandíbula.
**No se usa el giroscopio**: la inclinación φ se estima de la geometría de los contornos
(`KeystoneFit`) y se corrige re-proyectando la foto “después” como con una cámara nivelada
(homografía H = K·R_x(φ)·K⁻¹ + recorte 3:4):

1. **Puntos de contorno** (hasta 133 del contorno facial, o los puntos corporales): para cada φ
   candidato se transforman los puntos de “después”, se ajusta la semejanza a “antes” y se mide el
   residuo; búsqueda en rejilla (±15°, paso 1°) + sección áurea. Solo se acepta si reduce el residuo
   ≥ 10 % (evita sobreajuste).
2. **Proporciones de la silueta** (cuando no hay puntos suficientes): con el contorno del óvalo
   facial o de la silueta del sujeto (ver “Medición de contornos”), el perfil de anchos del polígono
   a 61 alturas, normalizado por su altura (invariante a escala y posición), se calcula de forma
   exacta por intersección con las aristas; se busca el φ que iguala el perfil de “después” con el de
   “antes”. Se acepta si reduce el error ≥ 20 %.

En el comparador: menú ⋮ → “Perspectiva y ajustes” (automática o ajuste manual ±15°); el resultado de
la alineación indica el valor estimado y su origen. La inclinación del sensor se sigue guardando en
el XMP solo como dato informativo.

### Medición de contornos (varita mágica / selección automática)

Menú ⋮ → “Medir contornos”:

- **Automática** (como al crear stickers): óvalo facial de ML Kit rasterizado, o silueta de la
  persona con ML Kit Selfie Segmentation (modelo incluido, en el dispositivo).
- **Varita mágica**: toque sobre la región; crecimiento desde la semilla con similitud de color en
  CIELAB (ΔE ≤ tolerancia respecto de la media de la región, que se adapta a degradados) y parada en
  bordes fuertes; después apertura/cierre, relleno de huecos y mayor componente conexo.
- **Métricas** (normalizadas por el ancho de la imagen): alto, ancho máximo, anchos al 25/50/75 % de
  la altura (frente, pómulos, mandíbula en el rostro), área, proporción ancho/alto, proporción
  superior/inferior y trazado del contorno; tabla antes/después con Δ % y perspectiva estimada por
  proporciones, aplicable a la comparación.

![Varita mágica](referencias/varita_magica.png)

### 1. Puntos anatómicos según la zona (automático, en el dispositivo)

`LandmarkDetector` (ML Kit, modelos incluidos) entrega hasta **140 puntos faciales** (7 puntos +
133 de contorno: óvalo —frente, mejillas, mandíbula—, cejas, ojos, labios y nariz) o **33 puntos
corporales** (hombros, codos, muñecas, dedos índice/meñique/pulgar, caderas, rodillas, tobillos,
talones, dedos de los pies). `ZoneAlignment.of(zone)` elige el subconjunto de cada zona
(p. ej. periocular: contornos de ojos y cejas; labios: bermellón y base nasal; frente: cejas y
óvalo superior; manos: muñecas y dedos; pies: tobillos, talones y dedos).

### 2. Estimación robusta de la semejanza

Mínimos cuadrados de Umeyama en forma cerrada (ver más abajo) con **rechazo iterativo de atípicos**:
mientras queden ≥ 4 puntos se descarta el par con residuo > 9 × la mediana, hasta un 25 % de los puntos.

### 3. Coincidencia por contornos anatómicos

`EdgeAligner` compara **la orientación de los contornos** (campo de gradiente en “ángulo doble”
(gx² − gy², 2·gx·gy)/|g|), que es invariante a la polaridad del borde: funciona igual con fondo claro
u oscuro y tolera cambios de iluminación. La puntuación es una correlación ponderada

```
Σ w·⟨b(p), R(2θ)·a(T⁻¹p)⟩ / √(Σ w|b|² · Σ w|a|²)
```

donde los pesos `w` vienen de **regiones anatómicas por zona** (gaussianas en el encuadre 3:4):

| Zona | Regiones de contorno |
|---|---|
| Rostro frontal | mandíbula, frente, orejas, ojos y cejas |
| Oblicuos 45° | mandíbula, frente, oreja, mejilla y nariz |
| Perfiles | frente, nariz y labios, mandíbula, oreja, occipucio |
| Frente / periocular / labios | cejas y frente / ojos y cejas / labios, base nasal y mentón |
| Cuello | mandíbula, hombros y clavículas |
| Abdomen, tórax, espalda | cintura, caderas, ombligo / contorno mamario, hombros / escápulas, cintura |
| Abdomen lateral | silueta abdominal, glúteo, axila |
| Glúteos, muslos, brazos | pliegues glúteos, caderas / rodillas, muslos / codo, brazo |
| Manos, pies | falanges, dorso o palma / falanges, tobillos y talones |

El resultado informa la coincidencia de cada región (p. ej. “mandíbula 92 % · frente 88 %”).
Optimización: búsqueda exhaustiva gruesa (desplazamiento ±18 %, escala ±16 %, rotación ±12°) cuando
no hay puntos, o ventana estrecha alrededor de la estimación anatómica; descenso por coordenadas de
grueso a fino escalando alrededor del centroide de las regiones.

### 4. Alternativa manual

Menú ⋮ → “Alinear manualmente”: dos puntos propios de la zona en cada foto (pupilas, tragus y punta
nasal, comisuras, pezones, rodillas, puntas de los dedos…), afinados después por contornos.

### 5. Coincidencia en vivo en la cámara

Con una foto de referencia superpuesta y la capa **Coincidir** activa, `LiveMatcher` analiza ~5
cuadros por segundo (ImageAnalysis en el mismo encuadre 3:4):

- **Porcentaje de puntos coincidentes**: con puntos anatómicos, fracción de los puntos de la
  referencia que están a ≤ 1,5 % del ancho de su posición en el cuadro actual; sin puntos, fracción
  de los 300 puntos de contorno más fuertes de la referencia (dentro de las regiones anatómicas)
  que encuentran borde en el cuadro actual a esa distancia.
- **Umbral mínimo** configurable (⚙ en la cámara): 80–100 %, **por defecto 99 %**; histéresis de 2
  puntos porcentuales para no parpadear.

| Estado | Criterio | Guía |
|---|---|---|
| Coincide | puntos coincidentes ≥ umbral | verde + vibración |
| Casi | ≥ umbral − 25 pp, o desviación ≤ 2,5 × (2 %, 3 %, 2°) | ámbar |
| Lejos / buscando | resto | cian |

Indicaciones a partir de la semejanza actual → referencia y de la perspectiva vertical estimada por
proporciones (“Mueva la cámara a la izquierda”, “Acérquese”, “Gire la cámara ↺”, “Incline la cámara
hacia abajo”…).

**Disparo automático** (botón AUTO en la barra superior o en ⚙): si la coincidencia se mantiene
~0,4 s se toma la foto; una sola captura por cada coincidencia (se rearma al perderla).

## Resultado

El comparador muestra el método usado, la escala y la rotación aplicadas y un interruptor para ver
la foto original o la alineada en los tres modos (deslizador, lado a lado, fundido).

## Verificación

`app/src/test/java/com/photomed/app/AlignmentTest.kt`:

- semejanza exacta con 2 puntos, inversa y composición;
- rechazo de un punto anatómico erróneo;
- recuperación de una transformación conocida (escala 1,08, rotación 4°, desplazamiento 7 %) **solo
  por contornos**, con error < 1,2 % del ancho;
- afinado de una estimación anatómica ruidosa: el error se reduce a menos de un tercio y < 1 %;
- coincidencia guiada por regiones anatómicas con puntuación por región;
- rechazo de varios puntos erróneos entre 36 del óvalo facial.

`PerspectiveAndLiveTest.kt`: la corrección nivela exactamente una cámara inclinada sintética
(−12° y +8°) y deja verticales las verticales del mundo; estados e indicaciones de la coincidencia en
vivo, incluida la histéresis.

`KeystoneAndWandTest.kt`: la perspectiva se recupera desde puntos del contorno facial (±0,3°) y
desde las proporciones de la silueta con otra escala y encuadre (±1°); sin inclinación no se inventa
corrección; la varita mágica selecciona una región de piel con degradado y ruido (área ±6 %) y mide
alto y anchos (±3 %). `PerspectiveAndLiveTest.kt`: umbral de puntos (99 %, 98 %, histéresis) e
indicaciones de inclinación.

**Pendiente de verificar en un teléfono real:** rendimiento del análisis en vivo y de la
segmentación, y ajuste fino de la tolerancia por punto (1,5 %) con fotos reales.

Limitaciones: la corrección de perspectiva cubre la inclinación vertical, no la distancia ni giros laterales de la cámara;
ni cambios de postura no rígidos (por ejemplo, un brazo en otra posición). Las guías de la cámara,
el nivel y la superposición de la foto anterior reducen estas diferencias en el momento de la toma.
