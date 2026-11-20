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

## Condiciones de la alineación (menú ⋮ con casillas)

El botón principal **“Alinear automáticamente”** usa todas las condiciones activas; cada una se
puede desactivar en el menú ⋮ de Comparar o en Ajustes (se recuerdan):

| Condición | Qué hace | Si se desactiva |
|---|---|---|
| **Puntos anatómicos** | semejanza por puntos (Umeyama) y perspectiva por sus proporciones | se alinea solo por contornos |
| **Contornos** | afinado por bordes anatómicos y perspectiva por la silueta | se usa solo la transformación por puntos (los contornos solo puntúan) |
| **Mirada (gaze)** | compara la posición del iris dentro del ojo en ambas fotos y avisa si difiere | no se evalúa |

La mirada no se “corrige” con una transformación: es una condición de captura. En la cámara, con
“Exigir la misma mirada” activo, la coincidencia pasa a “cerca” mientras la mirada difiera
(MediaPipe: iris del modelo; ML Kit: centroide oscuro dentro del contorno del ojo, percentil 40 de
luminancia). Tolerancia: 0,15 del semiancho del ojo en horizontal y 0,22 del semialto en vertical
(mediana |x| en LFW frontal: 0,08).

## N fotografías

Con más de dos fotos (hasta 12) cada una se alinea a la **primera** (referencia) y:

- **Deslizar / Fundido**: una línea de tiempo recorre las transiciones 1→2→…→N; el divisor
  (o la opacidad) avanza dentro de la transición activa.
- **Lado a lado**: todas en fila (desplazable con más de 3).
- **Cuadrícula**: el número de columnas que maximiza el tamaño de celda 3:4 en la pantalla.

![Progresivo](capturas/compare_progresivo.png) ![Cuadrícula](capturas/compare_cuadricula.png)

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

Menú ⋮ → “Contornos (configuración)”. Esta pantalla **no modifica la comparación**: los contornos
elegidos (automáticos o con varita) quedan como configuración de la alineación automática, que los
usa para estimar la perspectiva por proporciones en lugar de los detectados.

- **Automática** (como al crear stickers): óvalo facial de ML Kit rasterizado, o silueta de la
  persona con ML Kit Selfie Segmentation (modelo incluido, en el dispositivo).
- **Varita mágica**: toque sobre la región; crecimiento desde la semilla con similitud de color en
  CIELAB (ΔE ≤ tolerancia respecto de la media de la región, que se adapta a degradados) y parada en
  bordes fuertes; después apertura/cierre, relleno de huecos y mayor componente conexo.
- **Métricas** (normalizadas por el ancho de la imagen): alto, ancho máximo, anchos al 25/50/75 % de
  la altura (frente, pómulos, mandíbula en el rostro), área, proporción ancho/alto, proporción
  superior/inferior y trazado del contorno; tabla antes/después con Δ % y perspectiva estimada por
  proporciones (informativa).

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

### 4. Salvaguardas (la alineación automática nunca empeora a la de puntos)

Evaluación `AlignmentRealTest` (84 casos: retomas reales de stock con los puntos detectados de
nuevo y cambios sintéticos conocidos de escala, giro, desplazamiento y cámara inclinada 5°).
Error medio en % del ancho de la imagen:

| Variante | Antes | Ahora |
|---|---|---|
| Sin alinear | 4,30 | 4,30 |
| Solo puntos | 0,15 | 0,15 |
| Puntos + perspectiva | 0,12 | 0,12 |
| Completa (puntos + perspectiva + contornos) | 0,11 (peor caso 0,91) | 0,10 (peor caso 0,35) |

Se encontraron dos causas de empeoramiento:

- **Perspectiva falsa**: un cambio de pose de la cabeza (3D) también reduce algo el residuo de
  los puntos con un φ inexistente (hasta −7°), y "corregirlo" deformaba la foto. Ahora solo se
  acepta si el residuo baja al menos un 40 % y |φ| ≥ 1°; la cámara inclinada de verdad (5°) se
  sigue detectando (+4,6…+5,0°).
- **Afinado por contornos a la deriva**: pelo, ropa o fondo podían arrastrar la semejanza lejos
  de los puntos. Si el afinado sube el residuo de los puntos más de un 15 % (+0,2 % del ancho),
  se descarta y queda la alineación por puntos.
- En Comparar, la foto se corrige con el φ exacto (antes se redondeaba a 0,5° y no coincidía con
  la semejanza calculada).

La prueba falla si la alineación completa empeora a la de solo puntos en algún caso.

### Bordes solo del cuerpo

El fantasma de bordes, la coincidencia en vivo por contornos y el afinado de la alineación usan
solo los bordes del paciente (`BodyRegion`): segmentación de personas (incluye pelo, orejas y
cuello) o sujeto sobre fondo liso en manos, pies y modo libre. Fuera del cuerpo el gris se aplana
al tono medio del fondo, así desaparecen los bordes de paredes, azulejos o muebles y se conserva
el contraste del contorno. Si no se aísla el cuerpo en las dos imágenes, se usan todos los bordes.

`BodyEdgesTest`: con el paciente desplazado y la pared de azulejos fija, el afinado con todos los
bordes se quedaba pegado al fondo (15 px de error); solo con los del cuerpo, 0,5 px.

### Suavizado temporal, expresión y segmentación de pelo y piel

- **One-Euro** (`OneEuro.kt`): los puntos y ángulos detectados en vivo se filtran con un paso bajo
  cuyo corte sube con la velocidad; la pose geométrica se recalcula con los puntos suavizados.
  `OneEuroTest`: temblor de 0,35 % → 0,18 % del ancho; retraso al moverse 0,8 %.
- **Expresión** (`Expression.kt`): apertura de los ojos, separación de los labios, ancho de la boca,
  elevación de las comisuras y altura de las cejas, invariantes a escala y giro, con los dos motores.
  Calibrado con las expresiones de MediaPipe en retratos de stock (`tools/eval/expression.py`):
  r = 0,89 sonrisa, 0,83 boca abierta, −0,93 ojos cerrados, 0,75 cejas. Con referencia, la
  coincidencia exige la misma expresión; sin ella, las vistas faciales completas piden expresión
  neutra y nunca ojos cerrados. `ExpressionTest`: 1 aviso falso en 48 retomas giradas ±10°.
- **Pelo y piel** (`HairSkin.kt`, MediaPipe "selfie multiclass", Apache 2.0): línea de implantación
  frontal (paso de piel del rostro a pelo por encima de las cejas, a lo largo del eje vertical del
  rostro) y altura de la frente en distancias interpupilares. Se dibuja la línea de la referencia en
  la cámara (rostro frontal y frente), se guarda `HairlineIpd` con cada foto y Comparar muestra su
  evolución ("la línea retrocede / avanza / estable"). La piel para igualar el color se limita a la
  clase "piel del rostro". `HairSkinTest`: línea en 23 de 24 retratos (el que falta es calvo).

### Ajuste del óvalo, puntos tapados y perfil por silueta

- **Óvalo ajustado a la cara** (`HairSkin.snapOval`): con la segmentación pelo / piel, cada punto del
  óvalo de la referencia se mueve por su normal (≤ 8 % del ancho de la cara) al límite real: sienes y
  mejillas contra pelo o fondo; mandíbula y mentón contra cuello, ropa o fondo (la barba cuenta como
  rostro). Retratos de stock: desplazamiento medio 1,1 % del ancho de la cara (máx. 2,7 %).
- **Puntos tapados** (`HairSkin.occluded`): rasgos interiores sobre pelo, accesorios, mano o fondo en
  alguna de las dos fotos se excluyen de la alineación (gafas de sol: 34 puntos; rostros despejados: 0–3).
- **Perfil cerca de 90°** (`Silhouette.profilePoints`): vértice, punta nasal, pogonion y occipucio
  desde la silueta; si el detector pierde el rostro, la coincidencia en vivo usa estos puntos y, sin
  referencia, un perfil reconocible en la silueta se acepta como vista de perfil.
- **Primera toma**: `FaceFraming` compara el óvalo detectado con el del dibujo; si coincide con el
  marco, la guía se adapta a la cara.

### 5. Coincidencia en vivo en la cámara

Con una foto de referencia superpuesta y la capa **Coincidir** activa, `LiveMatcher` analiza ~5
cuadros por segundo (ImageAnalysis en el mismo encuadre 3:4). La decisión (`LiveDecision`) exige:

1. **% de puntos coincidentes ≥ umbral** (configurable, **80 % por defecto**): un punto coincide si
   está a ≤ **3 %** del ancho de su posición en la referencia;
2. **geometría**: desplazamiento del pivote anatómico ≤ 3 %, escala ≤ 4 %, giro ≤ 3°;
3. **pose de la cabeza** (rostro): |Δ guiñada| y |Δ cabeceo| ≤ 6° (ángulos de Euler de ML Kit);
4. **paciente presente**: si la zona usa detector y la referencia tiene puntos, un cuadro sin
   puntos **nunca** coincide (antes, apuntar al piso podía coincidir por bordes).

Zonas o referencias sin puntos anatómicos: `ContourMatch` compara los 300 bordes más fuertes de la
referencia (en las regiones anatómicas) exigiendo **misma orientación** (±20°) y descuenta el azar
(tasa de aciertos con los puntos desplazados lejos), que es alta en superficies muy texturizadas.

#### Evaluación con imágenes reales (`FaceMatchingRealImagesTest`, `tools/eval`)

Rostros reales de LFW (Labeled Faces in the Wild; puntos con MediaPipe agrupados como ML Kit),
40 fotos de stock aleatorias (Unsplash) y 12 texturas/escenas reales (piso, ladrillo, césped, grava…):

| Caso | Solo % de puntos | Regla final |
|---|---|---|
| Retoma (temblor ≤ 1 %, escala ≤ 1,5 %, ≤ 1°, brillo y ruido; detección repetida) | 100 % | **100 %** coincide |
| Desalineación moderada (3,5 % / 5 % / 3°) | 48 % | 8 % (límite) |
| Desalineación grande (8 % / 12 % / 6°) | 18 % ✗ | **0 %** |
| Otra persona con el mismo encuadre | 1,8 % | **0,5 %** |
| Sin paciente (piso, texturas, escenas) | — | **0 %** (sin rostro) |
| Contornos: retoma / desplazamiento 8 % / sin paciente | — | 39/40 · 0/40 · **0/2080** |

Distinguir *identidades* por la geometría no es fiable (las distancias de forma de la misma persona y
de personas distintas se solapan: varían más con la pose y la expresión); y en estética médica la
forma cambia con el tratamiento. La identidad la garantiza el expediente; la coincidencia mide el
**encuadre**.

#### Motor de puntos faciales

Motor de puntos faciales (desde 2.17, sin elección en Ajustes): **MediaPipe Face Landmarker** (478
puntos 3D + matriz de pose; Apache 2.0, modelo incluido) como estándar. **Google ML Kit** (133
puntos de contorno + 7 puntos, ángulos de Euler) como respaldo en el mismo cuadro si MediaPipe falla
o no encuentra la cara (tras 5 fallos seguidos se usa solo ML Kit en esa sesión de cámara) y, en las
fotos, como comprobación de la malla: los centros de los ojos de ambos deben coincidir (± 25 % de la
interpupilar); si no, la malla se rehace sobre la cara que encuentra ML Kit.

#### Estandarización sin foto de referencia (primera toma de una zona)

`Standardization` aplica los estándares de fotografía clínica: vista (giro de la cabeza 0° frontal,
45° oblicuo hacia el lado de la zona, ≥ 70° perfil), plano de Frankfort (cabeceo ≤ 7°), cabeza recta
(alabeo ≤ 4°), iluminación uniforme (mejillas ±15 %) y exposición (L* del rostro 35–85). El sentido del
giro se obtiene de la geometría (punta nasal frente al óvalo), común a ambos motores: concuerda con la
pose 3D en el **100 %** de 227 rostros reales girados ≥ 10° (LFW).

#### Asistencia al operador

- Indicaciones **fuera de la imagen** (franja bajo la vista previa) y **flechas en el borde** hacia
  donde mover el teléfono, calculadas en coordenadas de pantalla según cámara y espejo.
- El **giro** necesario se dibuja en el nivel (línea objetivo discontinua + ángulo).
- **Zoom asistido** (`ZoomController`): filtro del error de escala, zona muerta ±2 %, 3 cuadros de
  tendencia, paso ≤ 12 %, amortiguación al sobrepasar y **espera a que el zoom se aplique y la cámara
  reenfoque** (en el pivote anatómico) antes de volver a medir; probado con una planta simulada con
  retraso y ruido (converge sin oscilar). El disparo automático también enfoca y espera; con la distancia estimada (interpupilar ≈ 6,3 cm) se recomienda trabajar a
  ≥ 1 m para evitar el “efecto selfie”.
- **Disparo automático** tras ~0,4 s de coincidencia (o de encuadre estándar), sin zoom en curso,
  tras enfocar en el pivote anatómico; una vez por coincidencia.
- **Condiciones de captura** en el XMP (`pm:Cond_*`: motor, asistencia, % de puntos, giro de la
  cabeza, distancia estimada, zoom); el comparador avisa si un par no es comparable.

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

### Igualación del color de la piel

Métodos establecidos, en tres modos (Comparar → Igualar color de piel):

1. **Automático (piel)**: piel del rostro a partir de los puntos (óvalo reducido sin ojos, cejas,
   labios ni fosas nasales) o de la extremidad (sustracción de fondo); se descartan sombras y brillos
   especulares (percentiles 5 y 95 de L*). Se supone la misma reflectancia de la piel en ambas
   sesiones, así que su cambio de color es el del iluminante y la exposición:
   - **adaptación cromática de Bradford** (von Kries en espacio de conos; la usada por ICC) que lleva
     el color medio de la piel de "después" al de "antes": balance de blancos y exposición sin las
     desviaciones de tono de escalar RGB o desplazar a*/b*;
   - **gradación tonal** por cuantiles de L* de la piel (5–95 %, curva monótona): contraste y gamma.
2. **Punto de piel**: lo mismo con la zona tocada en cada foto (sin gradación: la muestra es pequeña).
3. **Punto neutro (carta gris o blanca)**: Bradford desde una referencia neutra tocada en cada foto,
   sin suponer nada de la piel. Es la práctica de referencia en fotografía clínica: incluir una carta
   gris (o de color) en la toma.

Se muestra la diferencia **ΔE2000** (CIEDE2000; Sharma et al., 2005) de la piel antes y después
(≤ 1 imperceptible, ≤ 2–3 apenas perceptible).

Evaluación (`ColorMatchTest`): 27 rostros reales (3 de demostración + 24 de stock) con cambios de
sesión conocidos (luz cálida −0,5 EV, luz fría +0,4 EV, flash con más contraste, fluorescente
verdoso con menos contraste); ΔE2000 medio píxel a píxel en la piel frente a la foto original:

| | sin corregir | Reinhard (método anterior) | Bradford + gradación |
|---|---|---|---|
| media de 108 casos | 7,08 | 1,68 | **0,81** |

## Modo libre

Zona "Modo libre" (categoría Libre) para cualquier región sin dibujo anatómico: la guía es el
**contorno de lo visible**. En la primera toma se diseña en vivo con el cuadro de la cámara (cada
~0,6 s); con foto de referencia superpuesta, con la de la referencia (`FreeContour`):

1. silueta del sujeto sobre fondo liso (sustracción de fondo por color) o de la persona
   (segmentación), y su borde exterior suavizado;
2. si no se puede aislar un sujeto, los bordes dominantes de la imagen (Sobel, 7 % más fuerte, sin
   puntos aislados) como dibujo lineal.

La coincidencia en vivo y la alineación usan los contornos de todo el encuadre.

## Guía adaptada a la foto de referencia

**Extremidades (manos, pies, piernas): modo individual.** En la cámara, "Ambas · Derecha ·
Izquierda" (en la misma zona). La guía se acerca a la mitad del dibujo de ese lado (lado anatómico
según el dibujo: pulgar / dedo gordo; piernas de frente) y oculta la otra; se ajusta a la silueta de
la extremidad en la referencia (manos y pies: sustracción de fondo liso por color; piernas:
segmentación de la persona), con escala y desplazamiento robustos por filas. El lado se guarda en el
nombre (`manos_dorso_der_…`) y en los metadatos; Comparar avisa si se comparan lados distintos.

**Rostro (frontal, tercio superior, periocular, labios): rasgo a rasgo.** Thin-plate spline
(Bookstein, 1989) con ~140 puntos de control: los de la malla de MediaPipe detectada sobre el propio
dibujo (`tools/guides/face_controls.py`) se llevan a los mismos puntos de la foto: ojos (posición,
amplitud y altura), cejas, nariz, boca (línea media de los labios, para que la boca abierta no
ensanche el trazo), óvalo (frente, sienes, mejillas, mandíbula, mentón). Orejas (borde superior,
hélice, lóbulo) y vértice del cráneo se proyectan desde el modelo 3D con la pose de la cabeza, así
siguen giro, inclinación y cabeceo. La ilustración se dibuja como malla deformada (36×48 celdas).
Sin puntos suficientes se usa el ajuste por franjas descrito abajo.

**Orden canónico de los contornos (corrección importante).** ML Kit y la malla de MediaPipe no
numeran igual sus contornos (según el diagrama oficial de ML Kit: ojos empezando por el párpado
superior, ceja izquierda y labio inferior en sentido contrario, puente nasal hasta la punta). Con
ML Kit los puntos del dibujo se emparejaban con los equivocados y la guía se deformaba sin sentido.
Ahora todos los puntos (de ambos motores, del dibujo y del modelo 3D) pasan por `FaceKeys.canonical`
(misma regla en `tools/eval/canonical.py`): lados por posición en la imagen, ojos desde la comisura
izquierda por el párpado superior, óvalo desde el punto superior sobre la línea media de la cara,
curvas abiertas orientadas. Verificado: sin reordenaciones espurias en 297 poses sintéticas
(guiñada ±40°, cabeceo ±25°, alabeo ±15°).

**Prueba con retratos de stock** (`tools/eval/stock_faces.py`, `StockFacesGuideTest`; 24 fotos de
Unsplash/Pexels en encuadre clínico, rectas y giradas ±10°, que no se guardan en el repositorio):
la guía es la misma con los puntos de MediaPipe y con un "otro motor" que numera cada contorno
distinto, cambia de lado y añade ruido (diferencia < 5 % del ancho). Mejoras derivadas: sin puntos
sueltos (pupilas, comisuras) en la deformación —su ruido curvaba la malla—, orejas y cráneo
proyectados solo si quedan fuera del rostro (visibles), óvalo lejano oculto descartado con la cabeza
girada, puntos laterales del cráneo, y salvaguarda: si la malla se pliega dentro del rostro (> 4 %
de las celdas) se usa el ajuste por franjas. 68 de 72 rostros usan la deformación rasgo a rasgo.

**Oblicuos 45°:** la misma deformación rasgo a rasgo. Sus puntos de control salen de proyectar los
del frontal con la misma superficie de cabeza girada que sintetizó el dibujo
(`tools/guides/synth_oblique.py`); el izquierdo es el reflejo del derecho (claves simétricas de la
malla canónica, `art/source/face_key_mirror.json`). Oreja cercana marcada en el dibujo.

**Perfiles y cuerpo: silueta.** Al fijar la referencia se segmenta el sujeto (ML Kit Selfie
Segmentation) y se mide, por filas, el tramo que contiene el eje (pose u ojos). El dibujo tiene el
mismo contorno por filas (`build_guides.py`) y cada franja se escala a cada lado hasta los bordes de
la silueta: hombros, cintura, caderas, cuello, espalda, glúteos, brazos; nuca y borde facial en el
perfil. Anclas verticales: pose (hombros, codos, caderas, rodillas) en el cuerpo; en el perfil,
punta nasal y pogonion de la silueta (máxima protrusión del borde anterior y tras el surco
mentolabial, mismo algoritmo en el dibujo y en la foto) y la parte alta de la cabeza. Inclinación:
línea de hombros. Manos y pies: sin detector ni silueta fiable (la segmentación es de personas),
mantienen la guía estándar.

1. **Inclinación**: ángulo de la línea entre los centros de los contornos de los ojos (o pupilas;
   hombros en el cuerpo). Los puntos se enderezan antes de ajustar y la guía se dibuja girada ese
   ángulo. La misma medida se usa en la estandarización ("Enderece la cabeza: inclinada N° hacia…"),
   más estable que el ángulo de Euler del modo rápido de ML Kit.
2. **Vertical**: anclas (cejas, pupilas, base nasal, boca, mentón) a su altura detectada.
3. **Cabeceo (barbilla arriba/abajo)**: pose 3D de la cabeza por ajuste de los puntos al modelo
   canónico del rostro de MediaPipe (`HeadPose`, perspectiva débil, mínimos cuadrados). El vértice del
   cráneo se proyecta con esa pose y se usa como ancla: con la barbilla arriba el cráneo se acorta en
   la imagen; con la barbilla abajo se alarga. Validado con 536 rostros de LFW frente a la pose de
   MediaPipe (r = 0,958; error medio 1,96°). La misma pose se usa en la cámara: el cabeceo relativo
   a la referencia ("Suba/Baje la barbilla N°"), el plano de Frankfort sin referencia (con ML Kit se
   promedia con su ángulo de Euler) y se guarda en la foto (`HeadPitch`); Comparar avisa si el
   cabeceo de dos fotos difiere más de 6°. Corregido: el cabeceo de MediaPipe tenía el signo opuesto
   al de ML Kit.
4. **Contorno de la cabeza** (`tools/guides/face_profile.py`): bordes izquierdo/derecho del cráneo,
   sienes, mejillas, mandíbula y mentón de la ilustración, por filas. En la app cada franja de la
   guía se escala por separado a cada lado de la línea media hasta el óvalo facial detectado a esa
   altura (desde las cejas; el cráneo conserva la forma de la ilustración con el ancho de la frente).

![Guía con distintos cabeceos](capturas/guia_cabeceo.png)

![Guía adaptada a rostros inclinados](../app/src/test/snapshots/images/com.photomed.app_GuideAdaptTest_adaptedGuideOnTiltedFaces.png)

## Capas estéticas (visor y comparar)

- Rasgos con la malla completa de MediaPipe (478 puntos, conexiones oficiales): ojos, cejas, labios
  y nariz (líneas estéticas del dorso 55→45 / 285→275, alas 115→2 / 344→2, punta 45–1–275).
- Surcos (`RidgeTracker`): respuesta de valle Hessiana multiescala (σ 2–5,5 px a 720 de ancho)
  ponderada por la alineación con el pasillo; pasillos por la malla (nasogeniano 203-206-216-212,
  marioneta 43-202-210-169, valle lagrimal 128-121-120-119 y sus simétricos); trazado por
  programación dinámica; aceptado si la respuesta media ≥ 0,03, ≥ 2,2 × la media del pasillo, y
  ≥ 75 % del trazado sobre piel del rostro (sin barba, manos ni gafas). `NewFacesAnnotTest`
  (18 retratos de Pexels, `tools/eval/new_faces.py`): nasogeniano en 17; rostro joven liso: 1 trazo.

### Validación por rasgo y nuevas arrugas

- **Rasgos de la malla en la alineación**: claves `m<i>` de MediaPipe (ojos 32, cejas 20, nariz 34,
  labios 40) en las zonas faciales; Comparar alinea con MediaPipe y `FeatureMatch` da, tras alinear,
  la coincidencia por rasgo = 1 − RMS/(0,10 × interpupilar), con aviso por debajo del 60 %.
- **Nasogeniano**: pasillo 129-203-206-186-57 (y 358-423-426-410-287), anclado en el pliegue
  alar-facial (definición anatómica: del ala nasal a un punto lateral a la comisura oral).
- **Frente** (mejorado en 2.13): franjas que siguen el arco de las cejas (aplanándose hacia arriba),
  enteras y por mitades, desde las cejas hasta la línea del pelo según la segmentación (la malla
  acaba a media frente); supresión de no máximos (posición vertical y solape horizontal), hasta 8.
  **Ceño**: abanico de 6 posiciones × 3 inclinaciones (±14°) entre las cabezas de las cejas
  (verticales del corrugador, hasta 4) y horizontales del prócer en la raíz nasal.
  **Patas de gallo**: 11 rayos (−50° a +50°, cada 10°) desde el canto externo, fuera de las cejas,
  con supresión de no máximos (≥ ~13° entre rayos, hasta 4 por lado). **Ojeras**: zona entre el
  párpado inferior y el surco palpebromalar, si es ≥ 12 % más oscura que la mejilla.

## Medidas de líneas, surcos y ojeras (`LineMetrics.kt`)

Al tocar una línea en el visor se muestra su ficha; el chip **Medidas** abre la tabla (en Comparar,
una columna por foto y la diferencia entre la última y la primera). Todas son orientativas.

- **Escala en mm**: diámetro horizontal visible del iris (MediaPipe 469–471 / 474–476, el mayor)
  = 11,7 mm. El HVID adulto cae en 10–13 mm y, con un valor fijo, Pirayesh et al. (2023) midieron
  distancias faciales con un error medio del 2,9 % (horizontal) y 4,3 % (vertical). Si la
  proporción iris / interpupilar no es plausible (0,14–0,25), interpupilar = 63 mm.
- **Longitud**: longitud proyectada del trazado (no corrige la curvatura de la mejilla).
- **Perfil transversal** en cada punto (±5 mm nasogeniano, ±4 mm marioneta y valle lagrimal,
  ±2 mm arrugas finas), en L* CIE: piel de referencia = media de los hombros; fondo = mínimo.
  **Ancho** = anchura a media caída (FWHM), mediana a lo largo del trazado. **ΔL*** = caída.
- **Profundidad** (forma a partir del sombreado, 1D): con luz frontal sobre piel lambertiana,
  ρ = Y/Y_piel = cos θ, pendiente tan θ = √(1/ρ² − 1); se integra desde el borde del valle hasta
  el fondo en cada pared (respecto a la piel de su lado) y se promedian. La caída de luz real incluye
  sombra proyectada, interreflexiones y pigmento, así que se atenúa con una ganancia global de 0,25,
  ajustada para que las medianas en los 18 retratos queden en órdenes de magnitud plausibles
  (arrugas finas ~0,4 mm, nasogeniano ~1,2 mm). En la prueba sintética (surco en V de 3 × 1 mm) la
  integración sin ganancia recupera 1 mm ± 0,3. No hay validación frente a perfilometría.
- **Grado 0–5** al estilo de Lemperle (0 sin arruga … 5 muy profunda) desde la profundidad
  estimada: < 0,10 / 0,25 / 0,50 / 0,90 / 1,50 mm → 0–4; mayor → 5. Umbrales propios de la app.
- **Ojeras**: color medio L*a*b* (D65) de la zona y de un disco en la mejilla: área (cm²), ΔL*,
  ΔE*ab, ITA° = atan((L* − 50)/b*) de ambas y tipo probable: vascular (b* baja o a* sube sin
  b*), pigmentaria (diferencia de tono con b* que no baja) o estructural / sombra.

Rangos en los 18 retratos (mediana): nasogeniano 1,2 mm (grado 4), valle lagrimal 0,9 mm,
marioneta 0,8 mm, ceño 0,5 mm, frente 0,4 mm, patas de gallo 0,4 mm; rostro joven: sin arrugas.

## Relieve con la pantalla (estéreo fotométrico) y sensor de profundidad

`PhotometricStereo.kt`, `Relief.kt`, `DepthSensor.kt`, `ui/camera/ReliefCapture.kt`.

- **Captura** (cámara frontal, zonas del rostro, menú ⋯ → *Medición de relieve*): análisis a
  1280 × 960; pantalla blanca 0,7 s para la exposición automática, bloqueo de AE y AWB
  (Camera2 interop), y fotogramas con la pantalla oscura, 2 × (mitad izquierda, derecha, superior,
  inferior), oscura y blanca, cada uno ≥ 160 ms después de cambiar la pantalla (≈ 2,5 s). Después,
  la foto normal con la pantalla blanca.
- **Luces**: irradiancia vectorial de cada mitad como emisor plano lambertiano, Σ r·D·dA/|r|⁴,
  con las medidas físicas de la pantalla (DisplayMetrics) y la cámara en el borde superior; la
  mitad *izquierda* del usuario queda a +x en la imagen sin espejar. Comprobación de signos con la
  convexidad de la cara.
- **Solución**: recorte de la cara a 512 px; luminancia lineal; alineación de cada toma con la
  blanca (correlación de bordes normalizados, búsqueda gruesa y fina); se resta la oscura y se
  promedian los ciclos; g = ρn = (LᵀL)⁻¹LᵀI (Woodham 1980); pendientes integradas con Frankot y
  Chellappa (1988; FFT con derivada de diferencia central); relieve = altura − su gaussiana de 4 mm
  (solo dentro del óvalo). Se guarda en la foto (semejanza entre las mallas del fotograma y de la
  foto, con detección de espejo) como PNG de 8 bits, ±1,5 mm (≈ 12 µm por nivel), en
  `files/relieve/` junto con la escala; se carga con la misma edición que la foto.
- **Medida**: perfil perpendicular del relieve (±2–5 mm), media de los hombros − mínimo, mediana a
  lo largo del trazado; grado 0–5 con los mismos umbrales. Se descarta si la luz de la pantalla es
  < 0,25 × la ambiente o hay < 50 % de píxeles válidos (movimiento, sombras).
- **Sensor de profundidad**: cámara con DEPTH_OUTPUT y formato DEPTH16 (13 bits de mm, 3 de
  confianza), abierta aparte de CameraX; mediana del 30 % central con confianza ≥ 50 %. Fija la
  distancia (luces y mm por px = D / focal) y, en fotos normales de rostro, un factor sobre la escala
  del iris (ancho de la foto a D según la focal). Si el teléfono no permite abrirlo a la vez que la
  cámara de fotos, se usa el iris.
- **Pruebas**: `PhotometricStereoTest` (esfera con surco de 0,40 mm → 0,37 mm; piel lisa 0,007 mm);
  `ReliefPipelineTest` (retrato real como albedo, elipsoide con arruga de 0,30 mm, movimientos de
  ±1 px entre tomas → 0,27 mm en la foto y 0,28 mm en la foto espejada; DEPTH16).
- **Límites**: la pantalla da poca luz (penumbra, 25–35 cm) y ángulos pequeños (≈ ±8°), así que el
  ruido limita las arrugas muy finas; la piel no es perfectamente lambertiana (dispersión
  subsuperficial, brillos), lo que tiende a suavizar el relieve. No hay validación en personas frente
  a perfilometría.

## Barba y bigote (`FacialHair.kt`)

La segmentación multiclase etiqueta la barba y el bigote como piel del rostro (en 5 retratos con
vello: 0–10 % de «pelo» bajo la nariz), así que no servía para excluirlos. Detector propio en la
parte inferior del óvalo (bajo la base de la nariz, sin labios), a la escala de la cara (~400 px):
- energía de textura fina E = media 9 × 9 de |L* − G_σ1(L*)|; piel de referencia: discos en las
  mejillas (50, 280) y la frente (151); umbral T = máx(2,2 · mediana, 1,6 · p90);
- croma relativo c = C*ab / (L* + 10) (estable con la sombra); referencia: el menor de los discos;
- vello si (c < 0,55 · c_ref y E > 1,2 · mediana) o (E > T y c < 0,8 · c_ref); apertura, cierre y
  sin manchas < 0,4 % del área de la cara.
Los surcos usan «piel y no vello» en el trazado (`RidgeTracker`) y se recortan al tramo fuera del
vello; con ML Kit, el ajuste a la sombra ignora el vello y no se mide una línea con > 35 % sobre él.
`foldsAvoidBeardAndMustache`: trazos sobre vello 28 % → 0 % en los 5 rostros con barba o bigote;
ningún falso positivo en los rostros sin vello.
