#!/usr/bin/env python3
"""Paletas tonales de la marca a partir de los colores del logo (art/logo/logo.svg).

Sin dependencias: cada paleta conserva el matiz (CIELAB LCh, D65) del color semilla y su tono es la
luminosidad L* (como el «tone» de HCT en Material 3). El croma se acota al gamut sRGB en cada tono.

Semillas (del logo):
  primary    #0F766E  verde azulado del objetivo / degradado
  secondary  #0369A1  azul del degradado
  tertiary   #0B2545  azul marino del objetivo
  neutral    matiz del primario, croma 4; neutralVariant croma 8
"""
import math

SEEDS = {"primary": ("#0F766E", 40), "secondary": ("#0369A1", 36), "tertiary": ("#0B2545", 34),
         "neutral": ("#0F766E", 4), "neutralVariant": ("#0F766E", 8)}
TONES = {"default": [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, 100],
         "neutral": [0, 4, 6, 10, 12, 17, 20, 22, 24, 30, 40, 50, 60, 70, 80, 87, 90, 92, 94, 95, 96, 98, 99, 100]}


def _lin(c):
    c /= 255
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _gam(c):
    c = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
    return c * 255


WHITE = (0.95047, 1.0, 1.08883)


def hex_to_lch(h):
    h = h.lstrip("#")
    r, g, b = (_lin(int(h[i:i + 2], 16)) for i in (0, 2, 4))
    x = 0.4124 * r + 0.3576 * g + 0.1805 * b
    y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    z = 0.0193 * r + 0.1192 * g + 0.9505 * b
    f = lambda t: t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116
    fx, fy, fz = f(x / WHITE[0]), f(y / WHITE[1]), f(z / WHITE[2])
    L, a, bb = 116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)
    return L, math.hypot(a, bb), math.degrees(math.atan2(bb, a)) % 360


def lch_to_rgb(L, C, H):
    a, b = C * math.cos(math.radians(H)), C * math.sin(math.radians(H))
    fy = (L + 16) / 116; fx = fy + a / 500; fz = fy - b / 200
    inv = lambda t: t ** 3 if t ** 3 > 216 / 24389 else (116 * t - 16) / (24389 / 27)
    x, y, z = inv(fx) * WHITE[0], inv(fy) * WHITE[1], inv(fz) * WHITE[2]
    r = 3.2406 * x - 1.5372 * y - 0.4986 * z
    g = -0.9689 * x + 1.8758 * y + 0.0415 * z
    bl = 0.0557 * x - 0.2040 * y + 1.0570 * z
    return r, g, bl


def tone(H, C, L):
    if L <= 0: return "#000000"
    if L >= 100: return "#FFFFFF"
    lo, hi = 0.0, C
    for _ in range(30):                       # mayor croma dentro del gamut
        mid = (lo + hi) / 2
        if all(-1e-4 <= v <= 1 + 1e-4 for v in lch_to_rgb(L, mid, H)): lo = mid
        else: hi = mid
    r, g, b = (min(255, max(0, round(_gam(min(1, max(0, v)))))) for v in lch_to_rgb(L, lo, H))
    return f"#{r:02X}{g:02X}{b:02X}"


def palettes():
    out = {}
    for name, (seed, chroma) in SEEDS.items():
        _, c, h = hex_to_lch(seed)
        c = chroma if chroma is not None else c
        for t in TONES["neutral" if name.startswith("neutral") else "default"]:
            key = ("neutral-variant" if name == "neutralVariant" else name) + str(t)
            # tonos claros con menos croma (contenedores suaves, sin colores fluorescentes)
            out[key] = tone(h, c * (1 - 0.55 * max(0.0, (t - 50) / 50)), t)
    return out


if __name__ == "__main__":
    for k, v in palettes().items(): print(k, v)
