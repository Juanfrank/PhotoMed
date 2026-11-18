# Evaluación con imágenes reales (falsos positivos / negativos)

Reproduce los datos de `app/src/test/resources/eval/` que usan `FaceMatchingRealImagesTest`.
Ninguna foto de personas reales se guarda en el repositorio: solo coordenadas de puntos y
miniaturas de 96×128 en escala de grises.

```bash
pip install mediapipe opencv-python-headless scikit-image
export PHOTOMED_EVAL=/tmp/photomed-eval && mkdir -p $PHOTOMED_EVAL/neg && cd $PHOTOMED_EVAL
# Modelos MediaPipe (Apache 2.0)
curl -LO https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
# LFW funneled (Huang et al., 2007) — 13 233 fotos, 5 749 personas
curl -L -o lfw.tgz https://ndownloader.figshare.com/files/5976015 && mkdir lfw && tar xzf lfw.tgz -C lfw
# Negativas: 40 fotos de stock aleatorias (Unsplash vía picsum.photos) + texturas de scikit-image
for i in $(seq 1 40); do curl -sL -o neg/picsum_$i.jpg "https://picsum.photos/seed/pm$i/600/800"; done
cd - && python3 tools/eval/build_cache.py && python3 tools/eval/perturb.py && python3 tools/eval/rule.py && python3 tools/eval/export.py
```

| Script | Qué hace |
|---|---|
| `extract.py` | Puntos faciales MediaPipe (478) agrupados como los contornos de ML Kit (óvalo, cejas, ojos, labios, nariz) + pose de la cabeza |
| `build_cache.py` | 150 personas con ≥ 3 fotos + 350 personas distintas (798 rostros) |
| `analyze.py` | Distribuciones misma/distinta persona (fracción de puntos y distancia de forma) |
| `perturb.py` | Retoma (temblor real), desalineación moderada y grande, con detección repetida; detección en negativas |
| `rule.py` | Tasas de la regla de coincidencia (puntos + geometría + pose) |
| `export.py` | Fixtures para las pruebas Kotlin |
