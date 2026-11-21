"""Benchmark de puntos anatómicos y medidas faciales frente a anotación manual (AFW / 300-W, iBUG:
68 puntos marcados a mano). Se pasa la malla de MediaPipe (la misma que usa la app) y se compara:

- NME por zona (error medio / distancia entre cantos externos), la métrica estándar de 300-W;
- las medidas de la app calculadas con los puntos manuales y con los de la malla (anchos de boca, nariz
  y ojos, intercantal, tercio inferior y su subdivisión, quintos, índice facial, contorno mandibular):
  error absoluto medio en % del valor manual y correlación.

Uso: python3 tools/eval/bench_landmarks.py carpeta_afw/  → build/reports/bench/landmarks.json
"""
import sys, json, glob, pathlib
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

ROOT = pathlib.Path(__file__).resolve().parents[2]
A = ROOT / "app/src/main/assets"
OUT = ROOT / "app/build/reports/bench"; OUT.mkdir(parents=True, exist_ok=True)
det = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(A / "face_landmarker.task")), num_faces=6))
# malla de MediaPipe → 68 puntos de iBUG (correspondencia habitual)
MP68 = [162, 234, 93, 58, 172, 136, 149, 148, 152, 377, 378, 365, 397, 288, 323, 454, 389,
        70, 63, 105, 66, 107, 336, 296, 334, 293, 300, 168, 197, 5, 4, 75, 97, 2, 326, 305,
        33, 160, 158, 133, 153, 144, 362, 385, 387, 263, 373, 380,
        61, 39, 37, 0, 267, 269, 291, 405, 314, 17, 84, 181, 78, 82, 13, 312, 308, 317, 14, 87]
GROUPS = {"mandíbula": range(0, 17), "cejas": range(17, 27), "nariz": range(27, 36), "ojos": range(36, 48), "boca": range(48, 68)}

def pts(f):
    lines = open(f).read().split("{")[1].split("}")[0].split()
    return np.array(lines, float).reshape(-1, 2)

def d(a, b): return float(np.hypot(*(a - b)))

def measures(p, alar=None):
    """Medidas de la app con 68 puntos (iguales para el manual y la malla)."""
    sto = (p[62] + p[66]) / 2
    up = p[27]; sn = p[33]; me = p[8]
    lowA = d(sn, sto); lowB = d(sto, me)
    w = d(p[0], p[16])
    cuts = sorted([p[0][0], p[36][0], p[39][0], p[42][0], p[45][0], p[16][0]])
    fifths = [(cuts[i + 1] - cuts[i]) / (cuts[-1] - cuts[0]) * 100 for i in range(5)]
    return {
        "Ancho de la boca": d(p[48], p[54]), "Ancho alar": alar if alar is not None else d(p[31], p[35]),
        "Ojo derecho (ancho)": d(p[36], p[39]), "Ojo izquierdo (ancho)": d(p[42], p[45]),
        "Intercantal": d(p[39], p[42]), "Altura nasal (nasion → subnasal)": d(up, sn),
        "Tercio inferior (subnasal → mentón)": d(sn, me),
        "Estomion→mentón / subnasal→estomion": lowB / max(lowA, 1e-6),
        "Índice facial (nasion→mentón / ancho)": d(up, me) / w * 100,
        "Quinto central (entre los ojos), %": fifths[2], "Quinto ojo derecho, %": fifths[1],
    }

rows = []
for f in sorted(glob.glob(sys.argv[1] + "/*.pts")):
    img = cv2.imread(f[:-4] + ".jpg")
    if img is None: continue
    gt = pts(f)
    r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(img, cv2.COLOR_BGR2RGB)))
    if not r.face_landmarks: rows.append({"id": pathlib.Path(f).stem, "found": False}); continue
    h, w = img.shape[:2]
    cands = [np.array([[q.x * w, q.y * h] for q in fl]) for fl in r.face_landmarks]
    c = gt.mean(0)
    m = min(cands, key=lambda m: np.hypot(*(m[MP68].mean(0) - c)))
    pr = m[MP68]
    io = d(gt[36], gt[45])
    if d(pr.mean(0), c) > io: rows.append({"id": pathlib.Path(f).stem, "found": False}); continue
    err = np.hypot(*(pr - gt).T) / io
    # contorno mandibular: distancia de cada punto manual 2–14 a la polilínea de la mandíbula de la malla
    jaw = m[[132, 58, 172, 136, 150, 149, 176, 148, 152, 377, 400, 378, 379, 365, 397, 288, 361]]
    def seg(q, a, b):
        v = b - a; t = np.clip(np.dot(q - a, v) / max(np.dot(v, v), 1e-9), 0, 1); return np.hypot(*(q - (a + v * t)))
    jawd = np.mean([min(seg(q, jaw[i], jaw[i + 1]) for i in range(len(jaw) - 1)) for q in gt[2:15]]) / io
    yawR = d(gt[30], gt[2]) / max(d(gt[30], gt[14]), 1e-6)
    rows.append({"id": pathlib.Path(f).stem, "found": True, "frontal": bool(0.75 < yawR < 1.33),
                 "nme": {g: float(err[list(ix)].mean()) for g, ix in GROUPS.items()} | {"total": float(err.mean())},
                 "jaw": float(jawd), "gt": measures(gt), "mp": measures(pr, alar=d(m[98], m[327]))})

ok = [r for r in rows if r["found"]]
def summary(sub):
    s = {"n": len(sub)}
    s["NME por zona"] = {g: round(float(np.mean([r["nme"][g] for r in sub])), 4) for g in list(GROUPS) + ["total"]}
    s["Contorno mandibular (distancia media / interocular)"] = round(float(np.mean([r["jaw"] for r in sub])), 4)
    meas = {}
    for k in sub[0]["gt"]:
        g = np.array([r["gt"][k] for r in sub]); p = np.array([r["mp"][k] for r in sub])
        meas[k] = {"error medio %": round(float(np.mean(np.abs(p - g) / np.abs(g)) * 100), 1),
                   "sesgo %": round(float(np.mean((p - g) / np.abs(g)) * 100), 1),
                   "correlación": round(float(np.corrcoef(g, p)[0, 1]), 2)}
    s["Medidas"] = meas
    return s
res = {"dataset": "AFW (300-W, iBUG), 68 puntos manuales", "caras": len(rows), "detectadas": len(ok),
       "todas": summary(ok), "de frente": summary([r for r in ok if r["frontal"]])}
(OUT / "landmarks.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
print(json.dumps(res, ensure_ascii=False, indent=1))
