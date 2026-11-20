"""Retratos reales nuevos para validar las capas estéticas (nariz, labios, ojos, surcos).

Para cada foto de una carpeta (Pexels, licencia libre): recorte clínico 3:4 de 720×960 alrededor
del rostro, malla completa de MediaPipe (478 puntos, x e y divididas por el ancho) y etiquetas de
pelo / piel. Se guarda en la carpeta ignorada app/src/test/resources/stock/new/ (no se sube al repo).
Uso: python3 tools/eval/new_faces.py carpeta_fotos/ [ids separados por comas]
"""
import sys, json, pathlib, glob
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/test/resources/stock/new"; OUT.mkdir(parents=True, exist_ok=True)
A = ROOT / "app/src/main/assets"
det = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(A / "face_landmarker.task")), num_faces=1))
seg = vision.ImageSegmenter.create_from_options(vision.ImageSegmenterOptions(
    base_options=BaseOptions(model_asset_path=str(A / "selfie_multiclass.tflite")), output_category_mask=True))
W, H = 720, 960

def mesh(img):
    r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(img, cv2.COLOR_BGR2RGB)))
    if not r.face_landmarks: return None
    h, w = img.shape[:2]
    return np.array([[p.x * w, p.y * h] for p in r.face_landmarks[0]])

keep = set(sys.argv[2].split(",")) if len(sys.argv) > 2 else None
data = {}
for f in sorted(glob.glob(sys.argv[1] + "/*.jpg")):
    name = pathlib.Path(f).stem
    if keep and name.replace("px_", "") not in keep: continue
    img = cv2.imread(f)
    if img is None: continue
    m = mesh(img)
    if m is None: continue
    oval = m[[10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109]]
    cx, cy = oval[:, 0].mean(), oval[:, 1].mean(); fh = oval[:, 1].max() - oval[:, 1].min()
    hh = fh / 0.55; ww = hh * 0.75; x0, y0 = cx - ww / 2, cy - hh * 0.52
    M = np.array([[W / ww, 0, -x0 * W / ww], [0, H / hh, -y0 * H / hh]])
    crop = cv2.warpAffine(img, M, (W, H), flags=cv2.INTER_AREA, borderMode=cv2.BORDER_REPLICATE)
    m2 = mesh(crop)
    if m2 is None: continue
    lab = seg.segment(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(crop, cv2.COLOR_BGR2RGB))).category_mask.numpy_view()
    cv2.imwrite(str(OUT / f"{name}.jpg"), crop, [cv2.IMWRITE_JPEG_QUALITY, 92])
    cv2.imwrite(str(OUT / f"{name}_labels.png"), lab.astype(np.uint8))
    data[name] = (m2 / W).round(5).tolist()
    print(name, "ok")
(OUT / "mesh.json").write_text(json.dumps(data))
print(len(data), "rostros")
