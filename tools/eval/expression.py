"""Calibra los rasgos geométricos de expresión (Expression.kt) con las expresiones (blendshapes)
de MediaPipe Face Landmarker sobre los retratos de stock (carpeta ignorada).
Uso: python3 tools/eval/expression.py
"""
import json, pathlib, sys
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import canonical

ROOT = pathlib.Path(__file__).resolve().parents[2]
STOCK = ROOT / "app/src/test/resources/stock"
opts = vision.FaceLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(ROOT / "app/src/main/assets/face_landmarker.task")),
                                    output_face_blendshapes=True, num_faces=1)
det = vision.FaceLandmarker.create_from_options(opts)


def feats(p):
    """Mismo cálculo que Expression.measure (Kotlin)."""
    P = lambda k: np.array(p[k])
    ipd = np.linalg.norm(P("eye_l") - P("eye_r"))
    out = {}
    eo = []
    for g in ("eye_l_", "eye_r_"):
        e = [P(f"{g}{i}") for i in range(16)]
        w = np.linalg.norm(e[0] - e[8])
        eo.append(np.mean([np.linalg.norm(e[i] - e[16 - i]) for i in range(2, 7)]) / w)
    out["eyes"] = min(eo)
    ub = [P(f"lip_upper_bottom_{i}") for i in range(9)]; lt = [P(f"lip_lower_top_{i}") for i in range(9)]
    cl, cr = P("lip_upper_top_0"), P("lip_upper_top_10")
    mw = np.linalg.norm(cl - cr)
    out["mouth_open"] = np.linalg.norm(ub[4] - lt[4]) / mw
    centre = (ub[4] + lt[4]) / 2
    # +: comisuras por encima del centro de la boca (sonrisa); eje vertical del rostro = perpendicular a la línea de los ojos
    ex = P("eye_l") - P("eye_r"); ex /= np.linalg.norm(ex); up = np.array([ex[1], -ex[0]])
    out["smile_lift"] = (np.dot((cl + cr) / 2 - centre, up)) / ipd
    out["mouth_width"] = mw / ipd
    br = []
    for b, e in (("brow_l_bottom_", "eye_l_"), ("brow_r_bottom_", "eye_r_")):
        bb = np.mean([P(f"{b}{i}") for i in range(5)], 0); ee = np.mean([P(f"{e}{i}") for i in range(1, 8)], 0)
        br.append(np.dot(bb - ee, up) / ipd)
    out["brows"] = float(np.mean(br))
    return out


if __name__ == "__main__":
    rows = []
    for f in sorted(STOCK.glob("*_r0.jpg")):
        img = cv2.imread(str(f)); rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
        if not r.face_landmarks: continue
        data = json.loads((STOCK / "stock_faces.json").read_text())[f.stem]
        p = canonical.canonical(data["pts"])
        bs = {c.category_name: c.score for c in r.face_blendshapes[0]}
        g = feats(p)
        rows.append((f.stem, g, {"smile": (bs["mouthSmileLeft"] + bs["mouthSmileRight"]) / 2, "jaw": bs["jawOpen"],
                                 "blink": max(bs["eyeBlinkLeft"], bs["eyeBlinkRight"]), "browUp": bs["browInnerUp"]}))
    for n, g, b in rows:
        print(f"{n[:22]:22s} " + " ".join(f"{k}={v:.3f}" for k, v in g.items()) + " | " + " ".join(f"{k}={v:.2f}" for k, v in b.items()))
    def corr(a, b): return float(np.corrcoef(a, b)[0, 1])
    G = lambda k: [g[k] for _, g, _ in rows]; B = lambda k: [b[k] for _, _, b in rows]
    print("r sonrisa (elevación comisuras)", round(corr(G("smile_lift"), B("smile")), 2), "· (ancho)", round(corr(G("mouth_width"), B("smile")), 2))
    print("r boca abierta", round(corr(G("mouth_open"), B("jaw")), 2), "· r ojos", round(corr(G("eyes"), B("blink")), 2), "· r cejas", round(corr(G("brows"), B("browUp")), 2))
