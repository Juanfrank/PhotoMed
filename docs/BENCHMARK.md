# Benchmark del motor frente a mediciones manuales

Noviembre de 2026 (versión 2.40). Se buscaron conjuntos públicos con fotografías y anotación manual, y
se pasó el motor de PhotoMed por esas mismas fotos.

## 1. Arrugas y surcos: FFHQ-Wrinkle

- **Datos**: [FFHQ-Wrinkle](https://github.com/labhai/ffhq-wrinkle-dataset) (Moon, Chung y Jang, ICPR
  2024, [arXiv 2408.10060](https://arxiv.org/abs/2408.10060)): 1 000 fotos de FFHQ (1024 × 1024) con las
  arrugas marcadas a mano por 3 anotadores (frente, patas de gallo y surcos nasogenianos, sobre todo).
  Muestra aleatoria de **100 fotos** (semilla 7).
- **Motor**: el de la app (`FaceCrop.annotate`, mismo código que en el teléfono) con la malla y la
  segmentación de MediaPipe. Capas comparadas: frente, ceño, patas de gallo y surcos.
- **Reproducir**: `tools/eval/bench_wrinkles.py` (descarga solo las fotos con máscara manual) y la
  prueba `WrinkleBenchmarkTest` (resultados en `app/build/reports/bench/wrinkles.json` y superposiciones).

### Con tolerancia de 8 px (0,8 % del ancho, ≈ 1–1,5 mm en una cara)

| Región | Precisión | Exhaustividad | F1 |
|---|---|---|---|
| Frente | **0,72** | 0,54 | **0,62** |
| Ceño (entrecejo) | 0,35 | 0,23 | 0,28 |
| Patas de gallo | 0,51 | 0,34 | 0,41 |
| Surcos nasogenianos | 0,69 | 0,37 | 0,48 |
| **Total (regiones)** | **0,63** | **0,42** | **0,50** |

Precisión = fracción de lo trazado por el motor que cae sobre una arruga marcada; exhaustividad =
fracción de lo marcado que el motor encuentra.

### Métrica estricta del artículo (píxel a píxel, toda la foto)

| Método | F1 | Jaccard (JSI) |
|---|---|---|
| Acuerdo entre anotadores humanos (artículo, tabla 2) | — | 0,29 |
| Modelos profundos entrenados con 800 fotos del conjunto (artículo, tabla 3) | 0,59–0,64 | 0,42–0,48 |
| **PhotoMed** (sin entrenar con este conjunto; trazos de 5 px) | **0,25** | **0,14** |

### Lectura

- El motor es **conservador**: lo que traza casi siempre es una arruga real en frente y surcos
  (precisión 0,69–0,72), pero encuentra menos de la mitad de lo que marcan los anotadores, que
  incluyen muchas líneas finas (sobre todo bajo los ojos y en el entrecejo).
- La métrica estricta castiga desplazamientos de 2–3 px; los propios anotadores solo coinciden un 29 %
  entre sí. Los modelos del artículo se entrenaron con estas mismas anotaciones; el motor de PhotoMed
  no.
- Puntos débiles medidos: **ceño** (F1 0,28) y **patas de gallo** (0,41).

### Partición de prueba del artículo (comparación directa)

El repositorio publica la lista de fotos de prueba del artículo (`test_file_lists.txt`, 100 fotos); la
muestra aleatoria anterior solo compartía 6. Con esa partición:

| | Precisión | Exhaustividad | F1 (8 px) | F1 estricto | JSI |
|---|---|---|---|---|---|
| Frente | 0,77 | 0,47 | 0,59 | | |
| Ceño | 0,26 | 0,22 | 0,24 | | |
| Patas de gallo | 0,49 | 0,34 | 0,40 | | |
| Nasogenianos | 0,69 | 0,38 | 0,49 | | |
| **PhotoMed, total** | **0,62** | **0,39** | **0,48** | **0,23** | **0,13** |
| Mejor modelo del artículo (U-Net con preentrenamiento de textura) | | | | 0,64 | 0,48 |

### Experimentos

- **Resolución del análisis** (cara a 640 px en lugar de 400): mismo resultado (F1 0,50 → 0,50). El
  rastreador ya ajusta sus escalas al tamaño de la cara; la resolución no es el cuello de botella.
- **Sensibilidad** (umbrales de fuerza y contraste × 0,5–1 y hasta 2,5 veces más líneas por zona):

| Sensibilidad | Precisión | Exhaustividad | F1 |
|---|---|---|---|
| Calibrada (actual) | 0,62 | 0,39 | 0,48 |
| Media (umbrales × 0,8 / 0,9; líneas × 1,5) | 0,59 | 0,41 | 0,48 |
| Alta (× 0,65 / 0,8; × 2) | 0,55 | 0,43 | 0,48 |
| Muy alta (× 0,5 / 0,7; × 2,5) | 0,51 | 0,46 | 0,48 |

  El F1 no cambia: bajar umbrales solo cambia precisión por exhaustividad. Para mejorar hay que
  cambiar el **método** (cómo se proponen y puntúan las líneas), no los umbrales.

### Brechas del conjunto y de su documentación

- **Licencia CC BY-NC-SA 4.0 (no comercial)** para las máscaras, las imágenes de FFHQ y los modelos
  publicados: sirven para evaluar, pero **no** para entrenar ni incluir un modelo en una versión
  comercial de PhotoMed.
- **Una sola máscara por foto, sin capas**: no distingue frente, ceño, patas de gallo o surcos (aquí se
  separan por regiones aproximadas de la malla), ni publica las máscaras de cada anotador (no se puede
  calcular el acuerdo humano con estas métricas).
- **Sin escala ni gravedad**: no hay mm, profundidad ni grado; no valida las medidas de los surcos, solo
  su localización.
- **Fotos no clínicas**: FFHQ son fotos de Flickr (pose, luz, maquillaje y expresión variables, sin
  metadatos de expresión); en fotos clínicas estandarizadas el resultado debería ser mejor, pero no está
  medido.
- **Alcance de la anotación** («sobre todo frente, patas de gallo y nasogenianos») con muchas líneas
  finas bajo los ojos y periorales, que la app no busca como arrugas.
- La evaluación del artículo enmascara todo salvo cara y nariz (face-parsing.PyTorch); aquí se usa la
  malla de MediaPipe.

### Recomendaciones para aumentar precisión y exhaustividad

1. **Detección libre además de los pasillos**: un mapa de crestas (Hessiano / Frangi multiescala) en
   toda la piel de la cara y seguimiento de crestas conectadas en cualquier orientación; hoy solo se
   buscan líneas en posiciones y ángulos fijos (por eso el ceño y las patas de gallo pierden líneas).
2. **Puntuar los candidatos con un clasificador pequeño** (pocas decenas de miles de parámetros, sobre
   el perfil de luz a lo largo y a través de la línea): permite subir la sensibilidad (exhaustividad
   0,46) sin perder precisión; es la forma de mover el F1, que con umbrales no se mueve.
3. **Ceño** (el peor, precisión 0,26): excluir el borde de las cejas con la segmentación, exigir
   orientación vertical estable y evaluar sobre todo con el gesto de fruncir (en reposo hay pocas
   líneas y la sombra de las cejas confunde).
4. **Capas nuevas para lo que los anotadores marcan y la app no busca**: líneas infraorbitarias (bajo
   el ojo) y periorales (código de barras); suben la exhaustividad de patas de gallo y nasogenianos.
5. **Modelo de segmentación en el teléfono** (U-Net ligera en TFLite, 2–5 M parámetros) entrenado con
   fotos clínicas **propias** (500–1 000 fotos con 2–3 anotadores), preentrenado con mapas de textura
   generados automáticamente de fotos propias sin anotar (el método del artículo, sin sus datos).
   FFHQ-Wrinkle sirve solo para evaluarlo, por su licencia.
6. **Normalizar la luz** antes de buscar (ecualización local de contraste / campo plano), sobre todo con
   flash o luz lateral, y aprovechar las tomas con relieve (estéreo fotométrico) cuando existan.
7. **Validar en fotos clínicas**: anotar 50–100 fotos propias estandarizadas con 2 profesionales y
   repetir este benchmark con las mismas métricas.
8. **Sensibilidad elegible** en la app (conservadora / equilibrada / exhaustiva): el ajuste ya existe
   (`WrinkleTuning`); el usuario elegiría entre menos falsos positivos o encontrar más líneas.

### Mejoras aplicadas (versión 2.43) y benchmark repetido

Implementadas las recomendaciones 1, 2, 3, 4, 6 y 8 (la 5, un modelo entrenado, se omitió por
decisión del usuario; la 7 necesita fotos clínicas anotadas):

1. **Detección libre** (`WrinkleField`): mapa de crestas multiescala en toda la piel permitida (sin
   ojos, cejas ni labios), con la luz normalizada; crestas finas conectadas → polilíneas, que se vuelven
   a seguir con el mismo rastreador de los pasillos.
2. **Puntuación de candidatos** (pasillos y libres) con rasgos medibles: contraste y fuerza sobre el
   mínimo de la zona, longitud, acuerdo con la orientación esperada (horizontal en la frente, vertical en
   el ceño y perioral, radial en las patas de gallo) y rectitud. **Pesos fijados por diseño**, no
   entrenados: FFHQ-Wrinkle es no comercial, así que solo se usó para evaluar (calibración de umbrales
   con la muestra aleatoria; resultados con la partición de prueba del artículo, no usada para calibrar).
3. **Ceño**: búsqueda fuera del borde de las cejas y solo líneas casi verticales (± 30°).
4. **Capas nuevas**: líneas infraorbitarias y periorales.
6. **Luz normalizada** en la detección libre (en los pasillos empeoraba: 0,51 → 0,48, se dejó apagada).
8. **Sensibilidad elegible** en Ajustes: conservadora, equilibrada (por defecto) y exhaustiva.

Resultados en la **partición de prueba del artículo** (100 fotos, no usada para calibrar):

| | 2.42 | **2.43 (equilibrada)** |
|---|---|---|
| F1 estricto (píxel a píxel) | 0,234 | **0,280** (+20 %) |
| JSI estricto | 0,132 | **0,163** (+23 %) |
| F1 con tolerancia, 4 regiones originales | 0,48 (P 0,62 / R 0,39) | **0,50** (P 0,60 / R 0,43) |
| F1 con tolerancia, 6 regiones (con infraorbitaria y perioral) | — | 0,52 (P 0,62 / R 0,46) |
| Frente | 0,59 | 0,58 |
| Ceño | 0,24 | **0,30** |
| Patas de gallo | 0,40 | **0,45** |
| Nasogenianos | 0,49 | 0,49 |
| Infraorbitaria (nueva) | — | **0,61** |
| Perioral (nueva) | — | 0,24 (precisión 0,66, exhaustividad 0,15) |

Por sensibilidad (misma partición):

| Sensibilidad | Precisión | Exhaustividad | F1 (6 regiones) | F1 estricto |
|---|---|---|---|---|
| Conservadora | 0,66 | 0,41 | 0,51 | 0,270 |
| **Equilibrada** | 0,62 | 0,46 | 0,52 | **0,281** |
| Exhaustiva | 0,55 | 0,48 | 0,51 | 0,275 |

Coste: el análisis de una cara pasa de 342 a 425 ms (3 núcleos de cálculo en la prueba).

Control de falsos positivos en piel joven: en un retrato joven y liso de prueba, la detección libre
añadía dos «patas de gallo» que eran pestañas y delineado en la cola del ojo; se excluyó esa zona
(ningún punto a menos de 0,045 del ancho de la cara del canto externo) y el rostro vuelve a 2 líneas.

Lectura: la detección libre y la puntuación mejoran sobre todo el ceño y las patas de gallo, y la capa
infraorbitaria encuentra lo que los anotadores marcan bajo los ojos (la zona con más arrugas marcadas
tras la frente). La métrica del artículo mejora un 20 %, pero sigue lejos de un modelo entrenado
(0,64): el siguiente salto requiere datos propios anotados (recomendaciones 5 y 7). Las líneas
periorales son escasas en el conjunto y su detección es la menos fiable (por eso solo se muestran con
alta puntuación).

### Mapa de textura del artículo como rasgo de la puntuación

Se reimplementó el mapa de textura de FFHQ-Wrinkle (T = 1 − I / (1 + I_G), σ ≈ 5 px a 1024) y se probó
como rasgo adicional de la puntuación de candidatos (pesos 0,8–2,5, muestra de calibración): F1 0,51 →
0,50–0,51 y F1 estricto 0,294 → 0,289. **No mejora** (aporta lo mismo que el contraste del rastreador);
queda implementado pero desactivado (`WrinkleTuning.textureW = 0`). Su valor está como etiqueta débil
para preentrenar un modelo propio (`tools/train/`).

## 2. Puntos anatómicos y proporciones: AFW (300-W)

- **Datos**: AFW de [300-W (iBUG)](https://ibug.doc.ic.ac.uk/resources/300-W/): 337 caras con **68 puntos
  marcados a mano**. MediaPipe encontró 178 (las demás son caras pequeñas en fotos de grupo); 55 de
  ellas de frente.
- **Motor**: la malla de MediaPipe (la que usa la app para rasgos y proporciones), con la
  correspondencia habitual malla → 68 puntos. Las medidas se calculan igual con los puntos manuales y
  con los de la malla.
- **Reproducir**: `tools/eval/bench_landmarks.py` → `app/build/reports/bench/landmarks.json`.

### Error de puntos (NME: error medio / distancia entre cantos externos)

| Zona | Todas (178) | De frente (55) |
|---|---|---|
| Ojos | 3,5 % | 3,2 % |
| Boca | 3,9 % | 3,5 % |
| Nariz | 4,4 % | 3,8 % |
| Cejas | 6,9 % | 6,4 % |
| Mandíbula | 11,6 % | 9,0 % |
| Contorno mandibular (distancia a la curva) | 5,9 % | 4,1 % |

### Medidas de la app (de frente, 55 caras)

| Medida | Error medio | Sesgo | Correlación con el manual |
|---|---|---|---|
| Ancho de la boca | 4,1 % | −2,1 % | 0,99 |
| Ancho del ojo | 5,6 % | +1,5 % | 0,97 |
| Intercantal | 5,6 % | −1,3 % | 0,98 |
| Altura nasal (nasion → subnasal) | 5,7 % | −0,9 % | 0,97 |
| Tercio inferior (subnasal → mentón) | 6,0 % | +1,2 % | 0,96 |
| Estomion → mentón / subnasal → estomion | 7,7 % | −1,1 % | 0,82 |
| Índice facial | 5,7 % | +3,6 % | 0,83 |
| Quintos faciales | 6–7 % | +2 a +5 % | 0,46–0,65 |
| Ancho alar | 20,6 % | **+20,6 %** | 0,96 |

### Lectura

- Las distancias de **ojos, boca, nariz y tercio inferior** coinciden con las manuales con un error de
  4–6 % y correlación ≥ 0,96: sirven para comparar a un mismo paciente en el tiempo.
- Las **proporciones** (cocientes) acumulan el error de dos medidas: 6–8 %, correlación 0,5–0,8. Son
  orientativas; diferencias de pocos puntos porcentuales entre fotos no son significativas.
- El **ancho alar** sale un 21 % mayor: la app mide el pliegue alar (puntos 98 / 327 de la malla) y
  iBUG marca el borde de las narinas. Es en parte una diferencia de definición, pero conviene
  revisarla.
- La **mandíbula** es la zona menos precisa de la malla (NME 9 % de frente). La línea mandibular de la
  app se ajusta después a la sombra del borde (`JawContour`), lo que este benchmark no mide.

## 3. Lo que no se pudo medir

- **Ángulo cervicomentoniano, ángulo goníaco y papada**: no se encontró un conjunto público con fotos
  de perfil y estos ángulos medidos. El más cercano ([Mendeley, 400 perfiles con puntos de tejido
  blando](https://data.mendeley.com/datasets/4mxj6rpv48)) no expone sus archivos sin navegador y no
  queda claro que incluya el punto cervical. Las bases con medidas antropométricas completas (Texas 3D,
  10k US Adult Faces) piden un formulario de acceso.
- **Medidas en mm**: los conjuntos no tienen escala real; todo se compara en proporciones.

## Próximos pasos propuestos

1. Ajustar los umbrales de ceño y patas de gallo con FFHQ-Wrinkle (mejorar exhaustividad sin perder
   precisión), validando con otra muestra.
2. Revisar los puntos del ancho alar.
3. Medir la línea mandibular ajustada (`JawContour`) frente a los 68 puntos de AFW.
4. Pedir acceso a una base con ángulos de perfil (o anotar 30–50 perfiles propios) para validar el
   ángulo cervicomentoniano.
