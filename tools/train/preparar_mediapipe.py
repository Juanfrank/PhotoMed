"""Prepara fotos para entrenar (opcional): recorte cuadrado de 1024 px centrado en la cara con la malla
de MediaPipe y máscara de piel de la cara (`<foto>_piel.png`) con el segmentador multiclase, los mismos
modelos que usa la app (Apache 2.0: uso comercial permitido). Sustituye al recorte manual y a la máscara
por umbrales de color de arrugas.py, que falla con fondos beige, piel rojiza o luz fuerte.

Uso: python preparar_mediapipe.py fotos_originales/ fotos_preparadas/
"""
import sys, pathlib
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

A = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets"
det = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(A / "face_landmarker.task")), num_faces=1))
seg = vision.ImageSegmenter.create_from_options(vision.ImageSegmenterOptions(
    base_options=BaseOptions(model_asset_path=str(A / "selfie_multiclass.tflite")), output_category_mask=True))
FACE_SKIN = 3   # clase «piel de la cara» del segmentador multiclase
OUT = 1024

src, dst = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]); dst.mkdir(parents=True, exist_ok=True)
for f in sorted(list(src.glob("*.jpg")) + list(src.glob("*.png"))):
    img = cv2.imread(str(f))
    if img is None: continue
    rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
    if not r.face_landmarks: print(f.name, "sin cara"); continue
    h, w = img.shape[:2]
    m = np.array([[p.x * w, p.y * h] for p in r.face_landmarks[0]])
    # cuadrado de 1,6 veces el alto de la malla, centrado en ella (como FFHQ)
    cx, cy = m.mean(0); side = (m[:, 1].max() - m[:, 1].min()) * 1.6
    M = np.array([[OUT / side, 0, OUT / 2 - cx * OUT / side], [0, OUT / side, OUT / 2 - cy * OUT / side]])
    crop = cv2.warpAffine(img, M, (OUT, OUT), flags=cv2.INTER_AREA, borderMode=cv2.BORDER_REPLICATE)
    lab = seg.segment(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(crop, cv2.COLOR_BGR2RGB))).category_mask.numpy_view()
    cv2.imwrite(str(dst / f"{f.stem}.png"), crop)
    cv2.imwrite(str(dst / f"{f.stem}_piel.png"), ((lab == FACE_SKIN) * 255).astype(np.uint8))
    print(f.name, "ok")
