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
