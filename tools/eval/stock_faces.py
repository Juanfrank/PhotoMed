"""Prueba de la guía adaptada con retratos de stock (Unsplash / Pexels, licencias de uso libre).

Para cada foto: encuadre clínico 3:4 alrededor del rostro (alto del óvalo ≈ 55 % del encuadre),
360×480, puntos MediaPipe agrupados como la app, y variantes giradas ±10°. Las imágenes NO se
guardan en el repositorio (carpeta ignorada app/src/test/resources/stock/); la prueba Kotlin
StockFacesGuideTest las usa si existen.
Uso: PHOTOMED_EVAL=/ruta/con/face_landmarker.task python3 tools/eval/stock_faces.py carpeta_fotos/
"""
import sys, json, pathlib, glob
import numpy as np, cv2
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import extract

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/test/resources/stock"
OUT.mkdir(parents=True, exist_ok=True)

def clinical_crop(img, pts):
    h, w = img.shape[:2]
    oval = np.array([pts[f"oval_{i}"] for i in range(36)]) * w      # px (y normalizada por el ancho)
    cx, cy = oval[:, 0].mean(), oval[:, 1].mean()
    fh = oval[:, 1].max() - oval[:, 1].min()
    H = fh / 0.55; W = H * 0.75
    x0, y0 = cx - W / 2, cy - H * 0.52
    M = np.array([[360 / W, 0, -x0 * 360 / W], [0, 480 / H, -y0 * 480 / H]])
    return cv2.warpAffine(img, M, (360, 480), borderMode=cv2.BORDER_REPLICATE)

data = {}
for f in sorted(glob.glob(str(pathlib.Path(sys.argv[1]) / "*.jpg"))):
    img = cv2.imread(f)
    full = extract.landmarks(img)
    if not full: print("sin rostro", f); continue
    base = clinical_crop(img, full["pts"])
    name = pathlib.Path(f).stem
    for rot in (0, -10, 10):
        im = base if rot == 0 else cv2.warpAffine(base, cv2.getRotationMatrix2D((180, 240), rot, 1.0), (360, 480), borderMode=cv2.BORDER_REPLICATE)
        r = extract.landmarks(im)
        if not r: continue
        key = f"{name}_r{rot}"
        cv2.imwrite(str(OUT / f"{key}.jpg"), im, [cv2.IMWRITE_JPEG_QUALITY, 85])
        data[key] = r
(OUT / "stock_faces.json").write_text(json.dumps(data))
print(len(data), "rostros")
