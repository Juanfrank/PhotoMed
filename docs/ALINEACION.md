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

### 0. Corrección de perspectiva vertical

Al capturar se registran en el XMP la inclinación de la cámara respecto de la horizontal
(`pm:CameraPitch`, del sensor de gravedad), el giro (`pm:Roll`) y la focal en unidades de alto de
imagen (`pm:FocalNorm`, de Camera2: focal / lado largo del sensor). Antes de comparar o alinear, cada
foto se re-proyecta como si la cámara hubiera estado nivelada:

```
d = ((x − cx)/f, (y − cy)/f, 1)   →   R_x(φ)·d   →   x' = cx + f·d'x/d'z,  y' = cy + f·d'y/d'z
```

(homografía H = K·R·K⁻¹). Las verticales del paciente dejan de converger; después se recorta el
mayor rectángulo 3:4 dentro del trapecio y se escala al encuadre. En el comparador: menú ⋮ →
“Perspectiva y ajustes” (automática con los datos registrados, ajustable ±20° por foto).

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
cuadros por segundo (ImageAnalysis en el mismo encuadre 3:4): puntos anatómicos en modo continuo o,
si no hay, contornos anatómicos en baja resolución partiendo de la última estimación. `LiveMatch`
mide la desviación en el pivote anatómico de la zona:

| Estado | Criterio | Guía |
|---|---|---|
| Coincide | desplazamiento ≤ 2 % del ancho, escala ≤ 3 %, rotación ≤ 2° (con histéresis del 30 %) | verde + vibración |
| Casi | hasta 2,5 × esos umbrales | ámbar |
| Lejos / buscando | resto | cian |

Se dibujan los puntos de la referencia (anillos) y los actuales (puntos) y se dan indicaciones
(“Mueva la cámara a la izquierda”, “Acérquese”, “Gire la cámara ↺”…).

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

**Pendiente de verificar en un teléfono real:** el signo de la inclinación del sensor de gravedad
(cámara trasera / frontal) y el rendimiento del análisis en vivo.

Limitaciones: la corrección de perspectiva cubre la inclinación vertical, no la distancia ni giros laterales de la cámara;
ni cambios de postura no rígidos (por ejemplo, un brazo en otra posición). Las guías de la cámara,
el nivel y la superposición de la foto anterior reducen estas diferencias en el momento de la toma.
