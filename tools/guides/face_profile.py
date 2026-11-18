"""Perfil del contorno facial de la ilustración frontal (cráneo, sienes, mejillas, mandíbula, mentón).

Para cada fila de face_front.png devuelve los bordes izquierdo y derecho del contorno de la cabeza
(sin orejas ni cuello), en px de la fuente:
- cráneo y frente: extremos del relleno (no hay orejas por encima de ~860 px);
- franja de las orejas (860–1290 px): interpolación monótona (PCHIP) entre la sien y la mejilla;
- mejillas y mandíbula: extremos del relleno hasta el cuello; mentón: trazos interiores.
Lo usa build_guides.py para que la app deforme la guía horizontalmente fila a fila hasta el óvalo
facial detectado en la foto de referencia.
"""
import pathlib
import numpy as np
from PIL import Image
from scipy.interpolate import PchipInterpolator

ROOT = pathlib.Path(__file__).resolve().parents[2]
CX, CHIN, EAR = 718, 1740, (860, 1290)

def runs(row):
    xs = np.where(row)[0]
    if not len(xs): return []
    out, s, p = [], xs[0], xs[0]
    for x in xs[1:]:
        if x != p + 1: out.append((s + p) / 2); s = x
        p = x
    out.append((s + p) / 2)
    return out

def face_profile():
    g = np.array(Image.open(ROOT / "art/source/face_front.png").convert("L")).astype(int)
    fill, ink = g < 250, g < 100
    top = next(y for y in range(g.shape[0]) if ink[y].any())
    ys, ls, rs = [], [], []
    for y in range(top + 6, CHIN - 4):
        if EAR[0] <= y <= EAR[1]: continue
        if y < 1600:
            xs = np.where(fill[y])[0]
            l, r = xs.min(), xs.max()
        else:
            rr = runs(ink[y]); li = [x for x in rr if x < CX]; ri = [x for x in rr if x > CX]
            if len(li) < 2 or len(ri) < 2: continue
            l, r = li[1], ri[-2]
        ys.append(y); ls.append(l); rs.append(r)
    ys.append(CHIN); ls.append(CX - 2); rs.append(CX + 2)
    ys = np.array(ys, float)
    fl, fr = PchipInterpolator(ys, ls), PchipInterpolator(ys, rs)
    samples = np.linspace(top + 6, CHIN, 48)
    return top, [(float(y), float(fl(y)), float(fr(y))) for y in samples]

if __name__ == "__main__":
    top, p = face_profile()
    for y, l, r in p[::4]: print(f"{y:7.1f} {l:7.1f} {r:7.1f} {r - l:7.1f}")
    im = Image.open(ROOT / "art/source/face_front.png").convert("RGB")
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.line([(l, y) for y, l, r in p], fill=(255, 0, 0), width=6); d.line([(r, y) for y, l, r in p], fill=(255, 0, 0), width=6)
    im.resize((im.width // 3, im.height // 3)).save("/tmp/claude-0/profile.png")
