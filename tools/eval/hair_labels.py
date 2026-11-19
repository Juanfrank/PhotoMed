"""Etiquetas de la segmentación multiclase de MediaPipe (selfie_multiclass_256x256, Apache 2.0) para
los retratos de stock: 0 fondo, 1 pelo, 2 piel del cuerpo, 3 piel del rostro, 4 ropa, 5 otros.
Se guardan como PNG en la carpeta ignorada app/src/test/resources/stock/labels/ (para HairSkinTest).
Uso: python3 tools/eval/hair_labels.py
"""
import pathlib
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

ROOT = pathlib.Path(__file__).resolve().parents[2]
STOCK = ROOT / "app/src/test/resources/stock"
OUT = STOCK / "labels"; OUT.mkdir(exist_ok=True)
seg = vision.ImageSegmenter.create_from_options(vision.ImageSegmenterOptions(
    base_options=BaseOptions(model_asset_path=str(ROOT / "app/src/main/assets/selfie_multiclass.tflite")),
    output_category_mask=True, output_confidence_masks=False))
n = 0
for f in sorted(STOCK.glob("*_r0.jpg")):
    rgb = cv2.cvtColor(cv2.imread(str(f)), cv2.COLOR_BGR2RGB)
    r = seg.segment(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
    lab = r.category_mask.numpy_view().astype(np.uint8)
    cv2.imwrite(str(OUT / f"{f.stem}.png"), lab); n += 1
    print(f.stem, lab.shape, np.bincount(lab.ravel(), minlength=6))
print(n, "máscaras")
