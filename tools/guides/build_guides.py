#!/usr/bin/env python3
"""Genera las guías de cámara (contornos) a partir de las ilustraciones de art/source.

Para cada zona: recorte 3:4 de la fuente → máscara de los trazos negros → esqueletización
y engrosado uniforme → PNG/WebP con trazo cian y halo oscuro (res/drawable-nodpi/guide_<zona>.webp).
También escribe GuideArt.kt con las líneas y marcas de referencia en coordenadas del recorte,
y una lámina de vista previa en docs/referencias/guias_nuevas.png.

Uso: python3 tools/guides/build_guides.py
"""
import pathlib
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy.ndimage import binary_dilation
from skimage.morphology import skeletonize, disk, remove_small_objects

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "art/source"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
KT = ROOT / "app/src/main/java/com/photomed/app/ui/camera/GuideArt.kt"
W, H = 600, 800
LINE = (0x7C, 0xF5, 0xE4)
LINE_R, HALO_R = 2, 5

def auto(name, pad=0.04):
    """Recorte 3:4 que encuadra todo el contenido (para manos y pies)."""
    g = np.array(Image.open(SRC / f"{name}.png").convert("L"))
    ys, xs = np.where(g < 90)
    x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
    w, h = (x1 - x0) * (1 + 2 * pad), (y1 - y0) * (1 + 2 * pad)
    h = max(h, w * 4 / 3)
    return ((x0 + x1) / 2, (y0 + y1) / 2, h)

import json, subprocess, sys
# Vistas derivadas: oblicuo 45° (de frontal + perfil) y lateral del tronco (de frente + espalda)
for script in ("synth_oblique.py", "synth_side.py"):
    subprocess.run([sys.executable, str(pathlib.Path(__file__).with_name(script))], check=True, stdout=subprocess.DEVNULL)
OB = json.loads((SRC / "face_oblique.json").read_text())
OW = OB["width"]
SIDE_AX = 640

# zona: (fuente, (cx, cy, alto) en px de la fuente, refs)
# refs: ("h", y, x0, x1) | ("v", x, y0, y1) | ("m", x, y)  — en px de la fuente; None = todo el ancho/alto
Z = {
 "rostro_frontal": ("face_front", (718, 948, 1896), [
    ("v", 718, None, None), ("h", 770, 250, 1190), ("h", 903, 250, 1190), ("h", 1250, 450, 990), ("h", 1740, 560, 880),
    ("v", 483, 760, 1500), ("v", 949, 760, 1500), ("m", 483, 903), ("m", 949, 903)]),
 "tercio_superior": ("face_front", (718, 610, 1100), [("v", 718, None, None), ("h", 770, None, None)]),
 "periocular": ("face_front", (716, 903, 1200), [("v", 716, None, None), ("h", 903, None, None), ("m", 483, 903), ("m", 949, 903)]),
 "labios": ("face_front", (722, 1430, 693), [("v", 722, None, None), ("h", 1430, None, None)]),
 "rostro_oblicuo_der": ("face_oblique_right", (OW / 2, 948, OW * 4 / 3), [
    ("h", 770, None, None), ("h", 903, None, None), ("h", 1250, None, None),
    ("v", OB["nose_x"], 640, 1560), ("m", OB["pupil_r"], 903), ("m", OB["pupil_l"], 903)]),
 "rostro_oblicuo_izq": ("face_oblique_left", (OW / 2, 948, OW * 4 / 3), [
    ("h", 770, None, None), ("h", 903, None, None), ("h", 1250, None, None),
    ("v", OW - OB["nose_x"], 640, 1560), ("m", OW - OB["pupil_r"], 903), ("m", OW - OB["pupil_l"], 903)]),
 "abdomen_lateral": ("body_side", (680, 1180, 1120), [
    ("v", SIDE_AX, 660, 1640), ("h", 1221, 420, 940), ("h", 1628, 380, 900), ("m", SIDE_AX + 168, 1221)]),
 "rostro_perfil_der": ("face_profiles", (867, 1050, 1600), [
    ("h", 976, 250, 1550), ("v", 1354, 300, 1800), ("m", 802, 1004), ("m", 1275, 948)]),
 "rostro_perfil_izq": ("face_profiles", (3240 - 867, 1050, 1600), [
    ("h", 976, 3240 - 1550, 3240 - 250), ("v", 3240 - 1354, 300, 1800), ("m", 3240 - 802, 1004), ("m", 3240 - 1275, 948)]),
 "cuello": ("body_front", (710, 760, 780), [("v", 710, None, None), ("h", 508, 560, 860), ("m", 710, 625)]),
 "abdomen_frontal": ("body_front", (710, 1145, 1010), [("v", 710, None, None), ("h", 1221, 480, 940), ("h", 1472, 480, 940), ("m", 710, 1221)]),
 "torax_mamas": ("body_front", (710, 900, 860), [("v", 710, None, None), ("h", 863, 420, 1000), ("h", 1139, 460, 960),
    ("m", 561, 863), ("m", 863, 863), ("m", 710, 640)]),
 "muslos": ("body_front", (710, 2190, 1480), [("v", 710, None, None), ("h", 2061, 420, 1000), ("h", 1573, 480, 940),
    ("m", 584, 2061), ("m", 842, 2061)]),
 "espalda": ("body_back", (659, 1020, 1080), [("v", 659, None, None), ("h", 868, 450, 870), ("h", 1232, 430, 890), ("m", 659, 577)]),
 "gluteos": ("body_back", (659, 1600, 900), [("v", 659, None, None), ("h", 1442, 420, 900), ("h", 1628, 420, 900)]),
 "brazo_der": ("body_back", (1050, 1170, 1180), [("h", 1108, 860, 1240)]),
 "brazo_izq": ("body_back", (267, 1170, 1180), [("h", 1108, 80, 460)]),
 "manos_dorso": ("hands_dorsal", None, [("v", "c", None, None)]),
 "manos_palma": ("hands_palmar", None, [("v", "c", None, None)]),
 "pies_dorso": ("feet_dorsal", None, [("v", "c", None, None)]),
 "pies_planta": ("feet_plantar", None, [("v", "c", None, None)]),
}

def render(src, crop):
    im = Image.open(SRC / f"{src}.png").convert("L")
    cx, cy, h = crop if crop else auto(src)
    w = h * 3 / 4
    box = (int(round(cx - w / 2)), int(round(cy - h / 2)), int(round(cx + w / 2)), int(round(cy + h / 2)))
    canvas = Image.new("L", (box[2] - box[0], box[3] - box[1]), 255)
    canvas.paste(im.crop((max(box[0], 0), max(box[1], 0), min(box[2], im.width), min(box[3], im.height))),
                 (max(-box[0], 0), max(-box[1], 0)))
    small = np.array(canvas.resize((W, H), Image.LANCZOS)).astype(np.float32) / 255
    ink = small < 0.62
    ink = remove_small_objects(ink, max_size=12)
    sk = skeletonize(ink)
    line = binary_dilation(sk, structure=disk(LINE_R))
    halo = binary_dilation(sk, structure=disk(HALO_R))
    rgba = np.zeros((H, W, 4), np.uint8)
    rgba[halo] = (0, 0, 0, 115)
    rgba[line] = (*LINE, 255)
    return Image.fromarray(rgba, "RGBA"), (box[0], box[1], w, h)

def norm(refs, frame, cxc):
    x0, y0, w, h = frame
    nx = lambda x: (x - x0) / w
    ny = lambda y: (y - y0) / h
    out = []
    for r in refs:
        if r[0] == "m":
            out.append(("m", nx(r[1]), ny(r[2])))
        elif r[0] == "h":
            out.append(("h", ny(r[1]), 0.04 if r[2] is None else nx(r[2]), 0.96 if r[3] is None else nx(r[3])))
        else:
            x = cxc if r[1] == "c" else r[1]
            out.append(("v", nx(x), 0.03 if r[2] is None else ny(r[2]), 0.97 if r[3] is None else ny(r[3])))
    return out

def f(v):
    return f"{max(min(v, 0.98), 0.02):.4f}f"

OUT.mkdir(parents=True, exist_ok=True)
kt = ["// ARCHIVO GENERADO por tools/guides/build_guides.py — no editar a mano.",
      "package com.photomed.app.ui.camera", "",
      "import androidx.compose.ui.geometry.Offset", "import com.photomed.app.R", "",
      "/** Ilustración de la guía (trazo ya coloreado) y referencias en coordenadas normalizadas del encuadre 3:4. */",
      "class GuideArt(val drawable: Int, val lines: List<Pair<Offset, Offset>>, val marks: List<Offset>)", "",
      "val GuideArtByZone: Map<String, GuideArt> = mapOf("]
previews = []
for zone, (src, crop, refs) in Z.items():
    img, frame = render(src, crop)
    name = "guide_" + zone
    img.save(OUT / f"{name}.webp", "WEBP", lossless=True, quality=100, method=6)
    cxc = frame[0] + frame[2] / 2
    n = norm(refs, frame, cxc)
    lines = [f"Offset({f(r[2])}, {f(r[1])}) to Offset({f(r[3])}, {f(r[1])})" for r in n if r[0] == "h"] + \
            [f"Offset({f(r[1])}, {f(r[2])}) to Offset({f(r[1])}, {f(r[3])})" for r in n if r[0] == "v"]
    marks = [f"Offset({f(r[1])}, {f(r[2])})" for r in n if r[0] == "m"]
    kt.append(f'    "{zone}" to GuideArt(R.drawable.{name},')
    kt.append(f"        listOf({', '.join(lines)}),")
    kt.append(f"        listOf({', '.join(marks)})),")
    # vista previa
    p = Image.new("RGB", (W, H), (0x2B, 0x29, 0x30)); p.paste(img, (0, 0), img)
    d = ImageDraw.Draw(p)
    for k, *v in n:
        if k == "h":
            y, a, b = v
            for s in np.arange(a * W, b * W, 22): d.line([(s, y * H), (min(s + 12, b * W), y * H)], fill=(255, 213, 79), width=3)
        elif k == "v":
            x, a, b = v
            for s in np.arange(a * H, b * H, 22): d.line([(x * W, s), (x * W, min(s + 12, b * H))], fill=(255, 213, 79), width=3)
        else:
            x, y = v; d.ellipse([x * W - 14, y * H - 14, x * W + 14, y * H + 14], outline=(255, 213, 79), width=3)
    previews.append((zone, p))
kt.append(")")
KT.write_text("\n".join(kt) + "\n")

F = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 22)
cols, tw, th = 6, 300, 400
sheet = Image.new("RGB", (cols * (tw + 12) + 12, ((len(previews) + cols - 1) // cols) * (th + 44) + 12), "white")
d = ImageDraw.Draw(sheet)
for i, (zone, p) in enumerate(previews):
    x = 12 + (i % cols) * (tw + 12); y = 12 + (i // cols) * (th + 44)
    sheet.paste(p.resize((tw, th), Image.LANCZOS), (x, y)); d.text((x, y + th + 8), zone, font=F, fill="black")
(ROOT / "docs/referencias").mkdir(parents=True, exist_ok=True)
sheet.save(ROOT / "docs/referencias/guias_nuevas.png")
print("ok", len(previews), "zonas;", sum((OUT / f"guide_{z}.webp").stat().st_size for z in Z) // 1024, "KB")
