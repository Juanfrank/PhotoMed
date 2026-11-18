#!/usr/bin/env python3
"""Sintetiza la silueta lateral del tronco (abdomen lateral) a partir de las vistas frontal y posterior.

Alturas de las referencias (escotadura yugular, pezón, pliegue inframamario, ombligo, cresta
ilíaca, pliegue interglúteo y glúteo, rodilla) y anchos del tronco medidos en las ilustraciones
frontal y posterior (1366×3096). La profundidad anteroposterior sale del ancho frontal con
proporciones antropométricas habituales (≈ 0,7 en tórax y cintura, ≈ 0,85 en caderas).
Postura clínica estándar del perfil: brazos elevados (axila visible) para no ocultar el flanco.
Las curvas se generan con splines suavizados (sin aristas).
"""
import pathlib
import numpy as np
from PIL import Image, ImageDraw
from scipy.interpolate import make_interp_spline
from scipy.ndimage import gaussian_filter1d

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "art/source"
W, H, AX = 1366, 3096, 640
LW = 4

def curve(pts, n=600, sigma=6):
    """Spline cúbico paramétrico por longitud de arco + suavizado gaussiano."""
    p = np.array(pts, float)
    t = np.concatenate([[0], np.cumsum(np.hypot(*np.diff(p, axis=0).T))])
    sx = make_interp_spline(t, p[:, 0], k=min(3, len(p) - 1))(np.linspace(0, t[-1], n))
    sy = make_interp_spline(t, p[:, 1], k=min(3, len(p) - 1))(np.linspace(0, t[-1], n))
    return list(zip(gaussian_filter1d(sx, sigma, mode="nearest") + AX, gaussian_filter1d(sy, sigma, mode="nearest")))

# Contorno anterior (de arriba abajo): brazo elevado → pliegue axilar anterior → mama → abdomen → muslo
front = [(70, 430), (78, 560), (98, 680), (132, 760), (186, 820), (228, 866), (220, 925), (172, 1000),
         (160, 1080), (166, 1160), (180, 1235), (186, 1310), (172, 1420), (152, 1530), (140, 1630),
         (136, 1760), (124, 1900), (116, 2000), (122, 2061), (102, 2200)]
# Contorno posterior: brazo elevado → pliegue axilar posterior → escápula → lordosis → glúteo → muslo
back = [(-62, 430), (-80, 560), (-112, 680), (-150, 790), (-168, 880), (-164, 980), (-150, 1080),
        (-138, 1180), (-142, 1260), (-160, 1340), (-196, 1430), (-228, 1500), (-238, 1550),
        (-222, 1600), (-190, 1645), (-150, 1700), (-134, 1800), (-118, 1950), (-98, 2061), (-104, 2200)]
# Axila: hueco entre los pliegues axilares, bajo el brazo
axilla = [(118, 790), (70, 822), (10, 838), (-60, 832), (-118, 812)]
inframammary = [(205, 990), (176, 1004), (138, 1008)]
gluteal = [(-212, 1622), (-170, 1640), (-122, 1644)]
crest = [(-96, 1318), (-40, 1296), (24, 1298), (70, 1318)]

img = Image.new("L", (W, H), 255); d = ImageDraw.Draw(img)
for c in (front, back):
    d.line(curve(c, 1400, 5), fill=0, width=LW, joint="curve")
for c in (axilla, inframammary, gluteal, crest):
    d.line(curve(c, 200, 3), fill=0, width=LW, joint="curve")
# ombligo y pezón
d.arc([AX + 172, 1214, AX + 190, 1232], start=90, end=270, fill=0, width=LW)
d.ellipse([AX + 206, 858, AX + 218, 872], outline=0, width=LW)
img.save(SRC / "body_side_right.png")
img.transpose(Image.FLIP_LEFT_RIGHT).save(SRC / "body_side_left.png")
print("ok", AX)
