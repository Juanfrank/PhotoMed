#!/usr/bin/env python3
"""Sintetiza la vista oblicua 45° del rostro a partir de las vistas frontal y lateral.

Modelo: para cada altura y, la sección transversal de la cabeza se aproxima por una
superelipse de semiancho w(y) (vista frontal) y profundidad entre z_atrás(y) y z_frente(y)
(vista lateral, sin la nariz). Se rota θ = 45° alrededor del eje vertical y se proyecta:
x' = x·cosθ + z·sinθ. La silueta es el mínimo/máximo de x' en cada sección (la nariz y los
labios del perfil se suman al máximo); los rasgos frontales se proyectan sobre la superficie
frontal y se ocultan donde ésta deja de mirar a la cámara; la oreja visible sale del perfil.
"""
import pathlib
import numpy as np
from PIL import Image, ImageDraw
from scipy.ndimage import label, uniform_filter1d

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "art/source"
TH = np.radians(45)
C, S = np.cos(TH), np.sin(TH)

front = np.array(Image.open(SRC / "face_front.png").convert("L")) < 90
prof = np.array(Image.open(SRC / "face_profiles.png").convert("L")) < 90
FH, FW = front.shape
CX = 718                         # eje medio de la vista frontal
TOP_F, CHIN_F = 155, 1740        # vértice y mentón (frontal)
TOP_P, CHIN_P = 283, 1555        # vértice y mentón (perfil, cabeza izquierda)
K = (CHIN_F - TOP_F) / (CHIN_P - TOP_P)
EAR_X = 802                      # tragion (perfil) = origen de profundidad
NASION_F, SUBNASAL_F = 885, 1252

def p2f(yp): return TOP_F + (yp - TOP_P) * K
def f2p(yf): return TOP_P + (yf - TOP_F) / K

# --- perfil: borde anterior y posterior por fila (cabeza izquierda, x < 1600) ---
P = prof[:, :1600]
ys = np.arange(FH)
zf = np.full(FH, np.nan); zb = np.full(FH, np.nan)
for yf in range(TOP_F, FH):
    yp = int(round(f2p(yf)))
    if yp >= 1805 or yp >= P.shape[0]: continue
    xs = np.where(P[yp])[0]
    if len(xs) == 0: continue
    zf[yf] = (xs.max() - EAR_X) * K
    zb[yf] = (xs.min() - EAR_X) * K
valid = ~np.isnan(zf)
zf = np.interp(ys, ys[valid], zf[valid]); zb = np.interp(ys, ys[valid], zb[valid])
# superficie frontal sin nariz (para mejillas y rasgos)
zs = zf.copy()
a, b = NASION_F, SUBNASAL_F
zs[a:b] = np.interp(np.arange(a, b), [a, b], [zf[a], zf[b]])
# sin labios: de la base nasal al pogonion la mejilla/mandíbula es una curva suave
POGONION_F = 1619
c = POGONION_F
zs[b:c] = np.interp(np.arange(b, c), [b, c], [zf[b] - 25 * K, zf[c]])
zs = uniform_filter1d(zs, 41)

# --- frontal: semiancho por fila (sin orejas) ---
w = np.full(FH, np.nan)
for y in range(TOP_F, FH):
    xs = np.where(front[y])[0]
    if len(xs) == 0: continue
    w[y] = max(CX - xs.min(), xs.max() - CX)
EAR_ROWS = (860, 1310)
m = ~np.isnan(w); m[EAR_ROWS[0]:EAR_ROWS[1]] = False
w = np.interp(ys, ys[m], w[m])
w = uniform_filter1d(w, 9)
NECK_Y = 1600  # debajo, el contorno lateral es el cuello

def surface(u, y, sign=1.0, p=2.3):
    zc = (zs[y] + zb[y]) / 2; D = (zs[y] - zb[y]) / 2
    return zc + sign * D * (1 - np.abs(u) ** p) ** (1 / p)

def proj(x, z): return x * C + z * S

pts = []      # puntos de tinta proyectados (x', y)
near, far = [], []
U = np.linspace(-1, 1, 241)
for y in range(TOP_F + 2, FH):
    xx = w[y] * U
    xp = np.concatenate([proj(xx, surface(U, y, 1)), proj(xx, surface(U, y, -1))])
    near.append((xp.min(), y)); far.append((xp.max(), y))   # mejilla lejana: superficie lisa, sin perfil

# nariz: solo la parte del perfil que sobresale de la mejilla lejana (dorso → punta → subnasal)
far_x = dict((y, x) for x, y in far)
nose = [(proj(0, zf[y]), y) for y in range(NASION_F - 10, SUBNASAL_F + 15)]
nose_out = [(x, y) for x, y in nose if x > far_x.get(y, 1e9) - 4]
# rasgos frontales (sin contorno exterior, orejas ni líneas del cuello)
lab, n = label(front)
sizes = np.bincount(lab.ravel())
for y, x in zip(*np.where(front)):
    if y < TOP_F + 5: continue
    dx = x - CX
    if abs(dx) > w[y] - 30: continue                       # contorno y orejas
    if y > 1560 and abs(dx) > 200: continue                 # líneas del cuello
    if sizes[lab[y, x]] > 20000: continue                   # contorno principal
    u = dx / w[y]
    z = surface(np.array([u]), y, 1)[0]
    # visibilidad: la superficie debe mirar a la cámara
    du = 1e-3
    dz = (surface(np.array([min(u + du, 1)]), y, 1)[0] - z) / du
    if w[y] * C + dz * S <= 0.15 * w[y]: continue
    pts.append((proj(dx, z), y))

# oreja visible (del perfil) en el lado cercano, x = −w
ear = P[810:1160, 640:860]
for yy, xx in zip(*np.where(ear)):
    yp, xpp = 810 + yy, 640 + xx
    yf = int(round(p2f(yp)))
    if yf >= FH: continue
    z = (xpp - EAR_X) * K
    pts.append((proj(-w[yf] * 0.97, z), yf))

# dorso nasal (del perfil) en el eje medio
for yf in range(NASION_F - 40, SUBNASAL_F + 8):
    pts.append((proj(0, zf[yf]), yf))

allx = [p[0] for p in near + far + pts + nose_out]
x0, x1 = min(allx), max(allx)
OW = int(x1 - x0 + 160); off = -x0 + 80
img = Image.new("L", (OW, FH), 255); d = ImageDraw.Draw(img)
# suavizado vertical de las siluetas (evita escalones)
def smooth(line, k=15):
    xs = uniform_filter1d(np.array([p[0] for p in line]), k, mode="nearest")
    return list(zip(xs, [p[1] for p in line]))
near, far = smooth(near), smooth(far)
for line in (near, far):
    d.line([(x + off, y) for x, y in line], fill=0, width=16)
if nose_out:
    d.line([(x + off, y) for x, y in nose_out], fill=0, width=16)
d.line([(near[0][0] + off, near[0][1]), (far[0][0] + off, far[0][1])], fill=0, width=16)  # coronilla
for x, y in pts:
    d.ellipse([x + off - 5, y - 5, x + off + 5, y + 5], fill=0)
img.save(SRC / "face_oblique_right.png")
img.transpose(Image.FLIP_LEFT_RIGHT).save(SRC / "face_oblique_left.png")
# datos para las referencias
nose_tip = max(range(NASION_F, SUBNASAL_F), key=lambda y: zf[y])
info = {
    "width": OW, "offset": off,
    "nose_x": proj(0, zf[nose_tip]) + off, "nose_y": nose_tip,
    "mid_x": proj(0, zs[903]) + off,
    "pupil_r": proj(483 - CX, surface(np.array([(483 - CX) / w[903]]), 903, 1)[0]) + off,
    "pupil_l": proj(949 - CX, surface(np.array([(949 - CX) / w[903]]), 903, 1)[0]) + off,
}
import json
(SRC / "face_oblique.json").write_text(json.dumps({k: round(float(v), 1) for k, v in info.items()}, indent=1))
print({k: round(float(v), 1) for k, v in info.items()})
