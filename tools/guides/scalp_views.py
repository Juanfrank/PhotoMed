#!/usr/bin/env python3
"""Guías del cuero cabelludo (tricología) proyectando un modelo 3D de la cabeza.

En lugar de dibujar a mano cada vista, se modela la cabeza (cráneo ovoide con proporciones
antropométricas de adulto, cuello, orejas, línea de implantación frontal y nucal, raya y
remolino de la coronilla) y se proyecta desde la posición de cámara de cada vista estándar:

- vértice: cenital, mirando el punto más alto del cráneo;
- corona: perpendicular a la superficie en el remolino (detrás y encima de la cabeza);
- parietal: perpendicular a la eminencia parietal (encima y al lado, ≈ 45°).

La silueta sale de la máscara de la superficie proyectada (contorno exterior); las curvas
anatómicas se dibujan solo donde la superficie mira a la cámara. Salida: Kotlin generado
(app/src/main/java/com/photomed/app/ui/camera/ScalpGuideArt.kt) y una vista previa PNG.

Ejes (cm): x hacia la izquierda del paciente, y hacia arriba, z hacia delante; origen en el
conducto auditivo.
"""
import pathlib
import sys
import numpy as np
from PIL import Image, ImageDraw
from skimage import measure
from scipy.ndimage import binary_closing, gaussian_filter1d

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/java/com/photomed/app/ui/camera/ScalpGuideArt.kt"

# --- cráneo: ovoide (más ancho detrás, en las eminencias parietales) ---
C = np.array([0.0, 2.2, -0.6])
RX, RY, RZ = 7.6, 11.0, 9.9


def skull_point(u, v):
    """u: acimut (0 = delante, +90° = izquierda del paciente), v: elevación (−90..90)."""
    cu, su, cv, sv = np.cos(u), np.sin(u), np.cos(v), np.sin(v)
    z = RZ * cv * cu
    # más ancho detrás (eminencias parietales), frente algo más estrecha
    rx = RX * (1.0 - 0.07 * (z / RZ))
    return C + np.stack([rx * cv * su, RY * sv, z], axis=-1)


def implicit(p):
    """< 1 dentro del cráneo; su gradiente da la normal (sin singularidades en los polos)."""
    p = np.asarray(p, float)
    z = p[..., 2] - C[2]
    rx = RX * (1.0 - 0.07 * (z / RZ))
    return (p[..., 0] / rx) ** 2 + ((p[..., 1] - C[1]) / RY) ** 2 + (z / RZ) ** 2


def normal_at(p, e=1e-3):
    p = np.asarray(p, float)
    g = np.stack([(implicit(p + d) - implicit(p - d)) for d in np.eye(3) * e], -1)
    return g / np.linalg.norm(g, axis=-1, keepdims=True)


def skull_dir(az_deg, el_deg):
    u, v = np.radians(az_deg), np.radians(el_deg)
    p = skull_point(u, v)
    return p, normal_at(p)


# --- cuello: cilindro elíptico detrás de la mandíbula ---
NECK_C = np.array([0.0, 0.0, -2.2])
NECK_RX, NECK_RZ = 5.6, 5.4


def neck_points(n=120, ys=np.linspace(-16, -1, 40)):
    a = np.linspace(0, 2 * np.pi, n, endpoint=False)
    pts, nor = [], []
    for y in ys:
        x = NECK_RX * np.sin(a); z = NECK_RZ * np.cos(a)
        pts.append(np.stack([x + NECK_C[0], np.full_like(a, y), z + NECK_C[2]], -1))
        nn = np.stack([np.sin(a) / NECK_RX, 0 * a, np.cos(a) / NECK_RZ], -1)
        nor.append(nn / np.linalg.norm(nn, axis=-1, keepdims=True))
    return np.concatenate(pts), np.concatenate(nor)


# --- orejas: óvalo inclinado hacia atrás, separado del cráneo por arriba y por detrás ---
def ear_curve(side):
    """side: +1 izquierda del paciente, −1 derecha."""
    t = np.linspace(0, 2 * np.pi, 48, endpoint=False)
    h, w = 3.1, 1.7  # semiejes (cm)
    tilt = np.radians(18)  # inclinada hacia atrás
    ly = h * np.sin(t); lz = w * np.cos(t) - 0.4
    y = ly * np.cos(tilt) - lz * np.sin(tilt) + 0.6
    z = ly * np.sin(tilt) + lz * np.cos(tilt) - 0.6
    # separación del cráneo: mayor en el borde posterior y superior (hélix)
    base = RX * (1.0 - 0.07 * ((z - C[2]) / RZ)) * np.sqrt(np.clip(1 - ((y - C[1]) / RY) ** 2 - ((z - C[2]) / RZ) ** 2, 0.05, 1))
    sep = 0.3 + 1.3 * np.clip((-(z + 0.6) / w + (y - 0.6) / h) / 2 + 0.5, 0, 1)
    x = side * (base + sep)
    nrm = np.tile([side * np.cos(np.radians(25)), 0, -np.sin(np.radians(25))], (len(t), 1))
    return np.stack([x, y, z], -1), nrm


# --- curvas anatómicas sobre el cráneo ---
def surface_curve(az, el, n=200):
    az = np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(az)), az)
    el = np.interp(np.linspace(0, 1, n), np.linspace(0, 1, len(el)), el)
    az = gaussian_filter1d(az, 4, mode="nearest"); el = gaussian_filter1d(el, 4, mode="nearest")
    u, v = np.radians(az), np.radians(el)
    p = skull_point(u, v)
    return p, normal_at(p)


# Línea de implantación frontal y temporal: de la patilla derecha a la izquierda (entradas suaves)
HAIRLINE = surface_curve(
    [-84, -78, -66, -50, -34, -18, 0, 18, 34, 50, 66, 78, 84],
    [-12, 6, 22, 30, 33, 35, 36, 35, 33, 30, 22, 6, -12])
# Raya (línea media): de la línea frontal a la nuca
def midline(t0=36, t1=165, n=200):
    t = np.radians(np.linspace(t0, t1, n))
    p = C + np.stack([0 * t, RY * np.sin(t), RZ * np.cos(t)], -1)
    return p, normal_at(p)


MIDLINE = midline()
# Implantación nucal: alrededor de la nuca, detrás de las orejas
NAPE = surface_curve([-112, -125, -140, -160, -180, -200, -220, -235, -248],
                     [-22, -30, -36, -40, -41, -40, -36, -30, -22])
# Implantación nucal sobre el cuello: en "V" suave, más baja en la línea media
def nape_on_neck(n=120, half=1.05):
    a = np.linspace(np.pi - half, np.pi + half, n)
    r = np.abs(a - np.pi) / half
    # punta central leve y laterales que suben detrás de las orejas
    y = -6.6 + 0.3 * r + 1.3 * r ** 3
    p = np.stack([NECK_RX * np.sin(a), y, NECK_C[2] + NECK_RZ * np.cos(a)], -1)
    nn = np.stack([np.sin(a), 0 * a, np.cos(a)], -1)
    return p, nn


NAPE_NECK = nape_on_neck()


# Surco de la nuca (línea media posterior del cuello, hacia C7)
def neck_furrow(n=40):
    y = np.linspace(-7.2, -13.5, n)
    p = np.stack([0 * y, y, NECK_C[2] - NECK_RZ * np.ones(n) - 0.1 * (y + 7.2) * 0], -1)
    return p, np.tile([0, 0, -1.0], (n, 1))


FURROW = neck_furrow()


# Línea de los trapecios: del cuello al hombro, por detrás
def trapezius(side, n=60):
    t = np.linspace(0, 1, n)
    x = side * (NECK_RX - 0.2 + 13.0 * t ** 1.2)
    y = -8.5 - 6.0 * t ** 1.6
    z = NECK_C[2] - 2.0 - 1.5 * t
    return np.stack([x, y, z], -1), np.tile([0, 0.6, -0.8], (n, 1))


# Remolino de la coronilla: en la línea media, por detrás del vértice
WHORL_AZ, WHORL_EL = 180.0, 62.0


def spiral_on_skull(az0, el0, r_deg=12.0, turns=1.75, n=90):
    t = np.linspace(0, 1, n)
    a = 2 * np.pi * turns * t
    return surface_curve(az0 + r_deg * t * np.cos(a) / np.cos(np.radians(el0)), el0 + r_deg * t * np.sin(a), n)


# --- rostro: modelo canónico de MediaPipe (HeadModel.kt), llevado a estos ejes ---
def face_model():
    import re
    kt = (ROOT / "app/src/main/java/com/photomed/app/align/HeadModel.kt").read_text()
    pts = {k: np.array([float(a), float(b), float(c)]) for k, a, b, c in
           re.findall(r'"(\w+)" to floatArrayOf\(([-\d.]+)f, ([-\d.]+)f, ([-\d.]+)f\)', kt)}
    shift = np.array([0.0, 0.0, 4.5])  # ojos ≈ 8 cm por delante del conducto auditivo
    return {k: v + shift for k, v in pts.items()}


FACE = face_model()
FACE_C = np.array([0.0, 1.0, 1.0])


def face_group(prefix, closed=False):
    ks = sorted([k for k in FACE if k.startswith(prefix) and k[len(prefix):].isdigit()], key=lambda k: int(k[len(prefix):]))
    p = np.array([FACE[k] for k in ks])
    if closed: p = np.vstack([p, p[:1]])
    n = p - FACE_C
    return p, n / np.linalg.norm(n, axis=-1, keepdims=True)


FACE_CURVES = [face_group("brow_r_top_"), face_group("brow_l_top_"), face_group("eye_r_", True), face_group("eye_l_", True),
               face_group("nose_bottom_"), face_group("lip_upper_top_"), face_group("lip_lower_bottom_")]


# --- cámara ortográfica ---
class Cam:
    def __init__(self, target, normal, up_hint, width_cm, center=(0.5, 0.5)):
        self.d = -normal / np.linalg.norm(normal)  # dirección de la mirada
        up = up_hint - np.dot(up_hint, self.d) * self.d
        self.up = up / np.linalg.norm(up)
        self.right = np.cross(self.d, self.up)
        self.t = target; self.w = width_cm; self.c = center

    def proj(self, p):
        q = p - self.t
        x = np.sum(q * self.right, -1) / self.w + self.c[0]
        y = -np.sum(q * self.up, -1) / (self.w * 4 / 3) + self.c[1]
        return np.stack([x, y], -1)

    def visible(self, n, eps=0.05):
        return np.sum(n * -self.d, -1) > eps


# rostro y mandíbula (solo para la silueta): elipsoide por delante y debajo del cráneo
JAW_C, JAW_R = np.array([0.0, -2.8, 3.8]), np.array([5.1, 7.0, 6.0])


def grid_quads(P):
    """Cuadriláteros de una malla (rejilla) de puntos 3D."""
    a, b = P[:-1, :-1], P[1:, :-1]; c, d = P[1:, 1:], P[:-1, 1:]
    return np.stack([a, b, c, d], -2).reshape(-1, 4, 3)


def surfaces(jaw_on=True, torso=False):
    u, v = np.meshgrid(np.radians(np.linspace(-180, 180, 181)), np.radians(np.linspace(-30, 90, 61)))
    quads = [grid_quads(skull_point(u, v))]
    a, t = np.meshgrid(np.linspace(0, 2 * np.pi, 121), np.linspace(-np.pi / 2, np.pi / 2, 61))
    jaw = JAW_C + np.stack([JAW_R[0] * np.cos(t) * np.sin(a), JAW_R[1] * np.sin(t), JAW_R[2] * np.cos(t) * np.cos(a)], -1)
    if jaw_on: quads.append(grid_quads(jaw))
    # cuello que se abre hacia los hombros (trapecios) y el tronco
    a, y = np.meshgrid(np.linspace(0, 2 * np.pi, 121), np.linspace(-32, -1, 63))
    k = np.clip((-8.5 - y) / 7.0, 0, 1); k = k * k * (3 - 2 * k)
    if not torso: k = 0 * k; y = np.clip(y, -11, -1)
    rx = NECK_RX + (19.0 - NECK_RX) * k
    rz = NECK_RZ + (10.0 - NECK_RZ) * k
    neck = np.stack([NECK_C[0] + rx * np.sin(a), y, NECK_C[2] - 1.0 * k + rz * np.cos(a)], -1)
    quads.append(grid_quads(neck))
    return np.concatenate(quads)


QUADS = {(j, t): surfaces(j, t) for j in (True, False) for t in (True, False)}


def silhouette(cam, res=900, jaw=False, torso=False):
    W, H = res, res * 4 // 3
    img = Image.new("L", (W, H), 0); dr = ImageDraw.Draw(img)
    q = cam.proj(QUADS[(jaw, torso)].reshape(-1, 3)).reshape(-1, 4, 2) * np.array([W, H])
    for quad in q:
        dr.polygon([tuple(p) for p in quad], fill=255)
    for s in (1, -1):  # orejas
        e = cam.proj(ear_curve(s)[0]) * np.array([W, H]); dr.polygon([tuple(p) for p in e], fill=255)
    m = np.array(img) > 0
    cs = measure.find_contours(np.pad(m, 1).astype(float), 0.5)
    c = max(cs, key=len) - 1
    c = np.stack([gaussian_filter1d(c[:, 1], 3, mode="wrap") / W, gaussian_filter1d(c[:, 0], 3, mode="wrap") / H], -1)
    return [tuple(p) for p in c]


def clip_runs(pts, closed=False, margin=0.0):
    """Tramos dentro del encuadre (0..1)."""
    runs, cur = [], []
    for x, y in pts:
        if -margin <= x <= 1 + margin and -margin <= y <= 1 + margin:
            cur.append((float(np.clip(x, 0, 1)), float(np.clip(y, 0, 1))))
        elif cur:
            runs.append(cur); cur = []
    if cur:
        if closed and runs and len(runs[0]) and pts[0] == (runs[0][0]) and len(runs) > 0:
            runs[0] = cur + runs[0]
        else:
            runs.append(cur)
    return [r for r in runs if len(r) >= 3]


def visible_runs(cam, curve):
    p, n = curve
    vis = cam.visible(n)
    q = cam.proj(p)
    runs, cur = [], []
    for ok, xy in zip(vis, q):
        if ok: cur.append(tuple(xy))
        elif cur: runs.append(cur); cur = []
    if cur: runs.append(cur)
    out = []
    for r in runs:
        out += clip_runs(r)
    return out


def simplify(run, n):
    if len(run) <= n: return run
    idx = np.linspace(0, len(run) - 1, n).round().astype(int)
    return [run[i] for i in idx]


def view(name, cam, extra_marks, whorl=False, face=False, jaw=False, back=False):
    contours = []
    sil = silhouette(cam, jaw=jaw, torso=back)
    # silueta como contorno cerrado si cabe entera; si no, tramos dentro del encuadre
    inside = all(0 <= x <= 1 and 0 <= y <= 1 for x, y in sil)
    if inside:
        contours.append((simplify(sil[::-1], 90), True))
    else:
        # rotar para que el corte quede en un extremo
        k = next(i for i, (x, y) in enumerate(sil) if not (0 <= x <= 1 and 0 <= y <= 1))
        sil = sil[k:] + sil[:k]
        for r in clip_runs(sil):
            contours.append((simplify(r, 70), False))
    for s in (1, -1):
        e, en = ear_curve(s)
        if cam.visible(en[:1], eps=-0.35)[0]:
            q = [tuple(x) for x in cam.proj(e)]
            if all(-0.02 <= x <= 1.02 and -0.02 <= y <= 1.02 for x, y in q):
                contours.append((simplify(q, 24), True))
                # antehélix: arco interior paralelo al hélix (de arriba atrás hacia la concha)
                c = np.mean(q, 0)
                inner = [tuple(c + 0.6 * (np.array(pt) - c)) for pt in q[6:30]]
                contours.append((simplify(inner, 14), False))
    for curve in (HAIRLINE, MIDLINE) + ((NAPE_NECK, FURROW, trapezius(1), trapezius(-1)) if back else (NAPE,)):
        for r in visible_runs(cam, curve):
            contours.append((simplify(r, 40), False))
    for curve in (FACE_CURVES if face else []):
        for r in visible_runs(cam, curve):
            contours.append((simplify(r, 30), False))
    if whorl:
        for r in visible_runs(cam, spiral_on_skull(WHORL_AZ, WHORL_EL)):
            contours.append((simplify(r, 40), False))
    marks = [tuple(cam.proj(m)) for m in extra_marks]
    return name, contours, marks


def build():
    views = []
    # Vértice: cenital sobre el punto más alto del cráneo; frente arriba del encuadre
    top, ntop = skull_dir(0, 90)
    cam = Cam(top, np.array([0, 1.0, 0]), np.array([0, 0, 1.0]), 21.0, (0.5, 0.5))
    views.append(view("VERTEX", cam, [top, HAIRLINE[0][100]]) + (cam,))
    # Corona: desde detrás y encima (≈ 35° sobre la horizontal), mirando el remolino; se ven la
    # nuca, la implantación nucal, el cuello y el arranque de los hombros
    w, nw = skull_dir(WHORL_AZ, WHORL_EL)
    el = np.radians(35)
    cam = Cam(w, np.array([0, np.sin(el), -np.cos(el)]), np.array([0, 1.0, 0]), 27.0, (0.5, 0.30))
    views.append(view("CROWN", cam, [w], whorl=True, back=True) + (cam,))
    # Parietal derecho: cámara encima y al lado (≈ 45°), mirando la eminencia parietal derecha
    p, npar = skull_dir(-95, 45)
    cam = Cam(p, npar, np.array([0, 1.0, 0]), 20.0, (0.48, 0.47))
    ear = ear_curve(-1)[0].mean(0)
    views.append(view("PARIETAL", cam, [p, ear], jaw=True) + (cam,))
    return views


def kotlin(views):
    out = ["// Generado por tools/guides/scalp_views.py — no editar a mano.",
           "package com.photomed.app.ui.camera", "",
           "import androidx.compose.ui.geometry.Offset", "",
           "/** Guías del cuero cabelludo proyectadas desde un modelo 3D de la cabeza (ver el script). */",
           "internal object ScalpGuideArt {",
           "    private fun pts(vararg v: Float) = (v.indices step 2).map { Offset(v[it], v[it + 1]) }"]
    for name, contours, marks, cam in views:
        out.append(f"    val {name}_CONTOURS: List<Pair<List<Offset>, Boolean>> = listOf(")
        for pts, closed in contours:
            flat = ", ".join(f"{x:.3f}f, {y:.3f}f" for x, y in pts)
            out.append(f"        pts({flat}) to {'true' if closed else 'false'},")
        out.append("    )")
        out.append(f"    val {name}_MARKS: List<Offset> = pts(" + ", ".join(f"{x:.3f}f, {y:.3f}f" for x, y in marks) + ")")
    out.append("}")
    OUT.write_text("\n".join(out) + "\n")


def preview(views, path):
    W, H = 360, 480
    im = Image.new("RGB", (W * len(views) + 10 * (len(views) - 1), H), (38, 50, 56))
    for k, (name, contours, marks, cam) in enumerate(views):
        ox = k * (W + 10)
        d = ImageDraw.Draw(im)
        d.rectangle([ox, 0, ox + W - 1, H - 1], fill=(69, 90, 100))
        for pts, closed in contours:
            xy = [(ox + x * W, y * H) for x, y in pts]
            if closed: xy = xy + xy[:1]
            d.line(xy, fill=(230, 230, 230), width=2)
        for x, y in marks:
            d.ellipse([ox + x * W - 6, y * H - 6, ox + x * W + 6, y * H + 6], outline=(255, 200, 0), width=2)
    im.save(path)


if __name__ == "__main__":
    v = build()
    kotlin(v)
    if len(sys.argv) > 1:
        preview(v, sys.argv[1])
    print("ok", [(n, len(c)) for n, c, *_ in v])
