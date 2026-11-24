"""Segmentación multiclase (la misma que la app) de fotos del cuero cabelludo, a 384 px de ancho,
para validar HairDensity en la JVM (HairDensityTest).

Uso: python3 tools/eval/hair_labels.py <salida.json> <foto> [<foto> ...]
Salida: {nombre: {"w": .., "h": .., "labels": base64 de un byte por píxel}} (fila mayor).
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
out = {}
for f in sys.argv[2:]:
    im = ImageOps.exif_transpose(Image.open(f)).convert("RGB")
    h = round(im.height * W / im.width)
    small = np.array(im.resize((W, h), Image.BILINEAR))
    m = seg.segment(mp.Image(image_format=mp.ImageFormat.SRGB, data=small)).category_mask.numpy_view().squeeze().astype(np.uint8)
    out[pathlib.Path(f).parent.name + "/" + pathlib.Path(f).name] = {"w": W, "h": h, "labels": base64.b64encode(m.tobytes()).decode()}
pathlib.Path(sys.argv[1]).write_text(json.dumps(out))
print(len(out), "fotos")
seg.close()
