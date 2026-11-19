"""Calibración con fotos propias (pacientes reales, en su equipo; nada sale de él).

Mide, sobre una carpeta de fotos de rostro de frente en expresión neutra (las que usted considera
correctas para la vista frontal), los mismos rasgos que usa la app:
- expresión (apertura de ojos, labios, ancho de la boca, comisuras, cejas: Expression.kt);
- línea de implantación (HairSkin.kt): en cuántas fotos se encuentra y la altura de la frente.
y propone los límites de la expresión neutra (percentiles 5/95 con un margen), para comparar con
los de Expression.kt. Con fotos de sonrisa en otra carpeta (--smiles) comprueba que se detectan.

Uso: python3 tools/eval/calibrate.py carpeta_neutras [--smiles carpeta_sonrisas]
"""
import argparse, pathlib, sys, json, os
# modelo de puntos de la app (mismo que en el teléfono)
os.environ.setdefault("PHOTOMED_EVAL", str(pathlib.Path(__file__).resolve().parents[2] / "app/src/main/assets"))
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import extract, canonical, expression

# Límites actuales de la app (Expression.kt)
APP = {"eyes_min": 0.13, "open_max": 0.15, "width_max": 0.97, "lift_max": 0.10, "brows_max": 0.27}


def measure(folder):
    rows = []
    for f in sorted(pathlib.Path(folder).glob("*")):
        if f.suffix.lower() not in (".jpg", ".jpeg", ".png"): continue
        img = cv2.imread(str(f))
        if img is None: continue
        r = extract.landmarks(extract.crop34(img))
        if not r: print("sin rostro:", f.name); continue
        rows.append((f.name, expression.feats(canonical.canonical(r["pts"]))))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("neutral"); ap.add_argument("--smiles")
    a = ap.parse_args()
    neu = measure(a.neutral)
    if not neu: sys.exit("No se encontraron rostros")
    F = lambda rows, k: np.array([g[k] for _, g in rows])
    prop = {
        "eyes_min": float(np.percentile(F(neu, "eyes"), 5) * 0.85),
        "open_max": float(np.percentile(F(neu, "mouth_open"), 95) * 1.15 + 0.01),
        "width_max": float(np.percentile(F(neu, "mouth_width"), 95) * 1.04),
        "lift_max": float(np.percentile(F(neu, "smile_lift"), 95) + 0.02),
        "brows_max": float(np.percentile(F(neu, "brows"), 95) * 1.08),
    }
    print(f"{len(neu)} fotos neutras")
    print(f"{'límite':12s} {'app':>7s} {'propuesto':>10s}")
    for k in APP: print(f"{k:12s} {APP[k]:7.3f} {prop[k]:10.3f}")
    def flagged(g, lim):
        return g["eyes"] < lim["eyes_min"] or g["mouth_width"] > lim["width_max"] or g["smile_lift"] > lim["lift_max"] \
            or g["mouth_open"] > lim["open_max"] or g["brows"] > lim["brows_max"]
    fp = [n for n, g in neu if flagged(g, APP)]
    print(f"Avisos falsos con los límites de la app: {len(fp)}/{len(neu)} {fp[:5]}")
    if a.smiles:
        sm = measure(a.smiles)
        hit = sum(1 for _, g in sm if flagged(g, APP))
        print(f"Sonrisas detectadas con los límites de la app: {hit}/{len(sm)}")
    pathlib.Path("calibracion.json").write_text(json.dumps({"app": APP, "propuesto": prop}, indent=2))
    print("Guardado calibracion.json")


if __name__ == "__main__":
    main()
