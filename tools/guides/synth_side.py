#!/usr/bin/env python3
"""Sintetiza la silueta lateral del tronco (abdomen lateral) a partir de las vistas frontal y posterior.

Las alturas de las referencias (escotadura yugular, pezón, pliegue inframamario, ombligo,
cresta ilíaca, pliegue interglúteo y glúteo, rodilla) y los anchos del tronco se toman de las
ilustraciones frontal y posterior (mismas coordenadas: 1366×3096). La profundidad
anteroposterior se obtiene del ancho frontal con proporciones antropométricas habituales
(profundidad/ancho ≈ 0,7 en tórax y cintura, ≈ 0,85 en caderas), repartida entre el contorno
anterior y el posterior respecto del eje acromion–trocánter (x = 0).
"""
import pathlib
import numpy as np
from PIL import Image, ImageDraw
from scipy.interpolate import PchipInterpolator

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "art/source"
W, H, AX = 1366, 3096, 640           # lienzo como las vistas frontal/posterior; eje x=AX

# (y, contorno anterior, contorno posterior) en px respecto del eje; alturas de las vistas F/P
CTRL = [
    (500, 55, -60),    # cuello bajo el mentón
    (600, 72, -82),    # base del cuello
    (650, 95, -125),   # escotadura yugular / trapecio
    (760, 150, -168),  # tórax superior / escápula
    (863, 228, -172),  # pezón (vértice mamario)
    (935, 212, -168),
    (1000, 168, -160), # pliegue inframamario
    (1139, 160, -146), # reborde costal
    (1221, 178, -138), # ombligo (abdomen) / lordosis lumbar
    (1300, 184, -150), # cresta ilíaca
    (1442, 168, -208), # inicio del pliegue interglúteo / sacro
    (1530, 150, -236), # máximo glúteo / pubis
    (1628, 140, -188), # pliegue glúteo
    (1750, 136, -136), # muslo
    (2000, 116, -110), # suprarrotuliano
    (2061, 122, -96),  # rodilla
    (2200, 100, -104),
]
ys = np.arange(500, 2201)
cy = np.array([c[0] for c in CTRL])
fr = PchipInterpolator(cy, [c[1] for c in CTRL])(ys)
bk = PchipInterpolator(cy, [c[2] for c in CTRL])(ys)
# ombligo: pequeña depresión del contorno anterior
fr -= 10 * np.exp(-((ys - 1221) / 9.0) ** 2)

img = Image.new("L", (W, H), 255); d = ImageDraw.Draw(img)
LW = 4
d.line([(AX + x, y) for x, y in zip(fr, ys)], fill=0, width=LW)
d.line([(AX + x, y) for x, y in zip(bk, ys)], fill=0, width=LW)

def at(arr, y): return float(arr[int(y) - 500])
# pliegue inframamario (curva corta desde el contorno anterior hacia dentro)
yb = 1000
d.line([(AX + at(fr, yb) - 2, yb - 4), (AX + at(fr, yb) - 30, yb + 6), (AX + at(fr, yb) - 62, yb + 4)], fill=0, width=LW, joint="curve")
# pezón
d.ellipse([AX + at(fr, 863) - 16, 856, AX + at(fr, 863) - 4, 870], outline=0, width=LW)
# pliegue glúteo
yg = 1628
d.line([(AX + at(bk, yg) + 2, yg), (AX + at(bk, yg) + 40, yg + 8), (AX + at(bk, yg) + 80, yg + 6)], fill=0, width=LW, joint="curve")
# cresta ilíaca (marca lateral)
d.arc([AX - 70, 1280, AX + 60, 1360], start=200, end=330, fill=0, width=LW)
img.save(SRC / "body_side.png")
print("ok", AX)
