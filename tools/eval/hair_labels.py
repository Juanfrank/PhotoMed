"""Segmentación multiclase (la misma que la app) de fotos del cuero cabelludo, a 384 px de ancho,
para validar HairDensity en la JVM (HairDensityTest).

Uso: python3 tools/eval/hair_labels.py <salida.json> <foto> [<foto> ...]
Salida: {nombre: {"w": .., "h": .., "labels": base64 de un byte por píxel (fila mayor),
         "mesh": [[x, y], …] (478, normalizados) o null}}.
"""
import base64, json, pathlib, sys
import numpy as np
from PIL import Image, ImageOps
import mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

ROOT = pathlib.Path(__file__).resolve().parents[2]
W = 384
seg = vision.ImageSegmenter.create_from_options(vision.ImageSegmenterOptions(
    base_options=BaseOptions(model_asset_path=str(ROOT / "app/src/main/assets/selfie_multiclass.tflite")), output_category_mask=True))
face = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(ROOT / "app/src/main/assets/face_landmarker.task")),
    num_faces=1, min_face_detection_confidence=0.5))
out = {}
for f in sys.argv[2:]:
    im = ImageOps.exif_transpose(Image.open(f)).convert("RGB")
    h = round(im.height * W / im.width)
    small = np.array(im.resize((W, h), Image.BILINEAR))
    img = mp.Image(image_format=mp.ImageFormat.SRGB, data=small)
    m = seg.segment(img).category_mask.numpy_view().squeeze().astype(np.uint8)
    fr = face.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.array(im)))
    mesh = [[p.x, p.y] for p in fr.face_landmarks[0]] if fr.face_landmarks else None
    out[pathlib.Path(f).parent.name + "/" + pathlib.Path(f).name] = {"w": W, "h": h, "labels": base64.b64encode(m.tobytes()).decode(), "mesh": mesh}
pathlib.Path(sys.argv[1]).write_text(json.dumps(out))
print(len(out), "fotos")
seg.close(); face.close()
