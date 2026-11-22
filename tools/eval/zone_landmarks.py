#!/usr/bin/env python3
"""Puntos de cara y cuerpo con MediaPipe (los mismos modelos de la app) para la prueba de captura
automática por zona (ZoneAutoCaptureTest).

Para cada imagen de app/src/test/resources/stock/zones/<zona>/*.jpg escribe en landmarks.json:
  w, h; face: {mesh: [[x, y], …] normalizados (478), matrix: 16 valores column-major} o null;
  pose: [[x, y, visibilidad], …] (33 puntos de BlazePose, normalizados) o null.
También genera los primeros planos (frente, periocular, labios, ojo) recortando las fotos frontales,
como los vería la cámara, y les calcula los puntos.

Uso: python3 tools/eval/zone_landmarks.py   (modelo de pose: app/build/models/pose_landmarker_full.task)
"""
import json, pathlib
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

ROOT = pathlib.Path(__file__).resolve().parents[2]
ZONES = ROOT / "app/src/test/resources/stock/zones"
face = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(ROOT / "app/src/main/assets/face_landmarker.task")),
    num_faces=1, min_face_detection_confidence=0.5, output_facial_transformation_matrixes=True))
pose = vision.PoseLandmarker.create_from_options(vision.PoseLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(ROOT / "app/build/models/pose_landmarker_full.task")), num_poses=1))


def frame34(img):
    """Recorte central 3:4 vertical (el encuadre de la cámara de la app)."""
    h, w = img.shape[:2]
    if w / h > 0.75:
        nw = int(h * 0.75); x = (w - nw) // 2; return img[:, x:x + nw]
    nh = int(w / 0.75); y = (h - nh) // 2; return img[y:y + nh] if nh < h else img


def analyze(img):
    rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    im = mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb))
    out = {"w": img.shape[1], "h": img.shape[0], "face": None, "pose": None}
    r = face.detect(im)
    if r.face_landmarks:
        m = r.facial_transformation_matrixes[0] if r.facial_transformation_matrixes else None
        out["face"] = {"mesh": [[p.x, p.y] for p in r.face_landmarks[0]],
                       "matrix": None if m is None else [float(v) for v in np.asarray(m).T.flatten()]}
    p = pose.detect(im)
    if p.pose_landmarks:
        out["pose"] = [[q.x, q.y, q.visibility] for q in p.pose_landmarks[0]]
    return out


def crop(img, x0, y0, x1, y1):
    """Recorte 3:4 que contiene el rectángulo (en píxeles), centrado en él."""
    h, w = img.shape[:2]
    cw, ch = x1 - x0, y1 - y0
    if cw / ch > 0.75: ch = cw / 0.75
    else: cw = ch * 0.75
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    a, b = int(max(0, cx - cw / 2)), int(max(0, cy - ch / 2))
    return img[b:min(h, int(b + ch)), a:min(w, int(a + cw))]


def closeups():
    """Primeros planos a partir de las fotos frontales (como se encuadran en la cámara)."""
    src = ZONES / "rostro_frontal"
    for z in ("tercio_superior", "periocular", "labios", "ojo"):
        (ZONES / z).mkdir(exist_ok=True)
    for f in sorted(src.glob("*.jpg")):
        img = cv2.imread(str(f))
        if img is None: continue
        a = analyze(img)
        if not a["face"]: continue
        h, w = img.shape[:2]
        P = np.array(a["face"]["mesh"]) * [w, h]
        def box(idx, pad=0.15):
            q = P[idx]; x0, y0 = q.min(0); x1, y1 = q.max(0); px, py = (x1 - x0) * pad, (y1 - y0) * pad
            return x0 - px, y0 - py, x1 + px, y1 + py
        oval_l, oval_r = P[234][0], P[454][0]
        brows = [70, 63, 105, 66, 107, 336, 296, 334, 293, 300]
        top = P[10][1] - (P[152][1] - P[10][1]) * 0.15
        cv2.imwrite(str(ZONES / "tercio_superior" / f.name), crop(img, oval_l, top, oval_r, P[6][1]))
        x0, _, x1, _ = box(brows + [33, 133, 362, 263])
        cv2.imwrite(str(ZONES / "periocular" / f.name), crop(img, oval_l, P[105][1] - (P[1][1] - P[105][1]) * 0.4, oval_r, P[1][1]))
        cv2.imwrite(str(ZONES / "labios" / f.name), crop(img, P[61][0] - (P[291][0] - P[61][0]) * 0.6, P[4][1], P[291][0] + (P[291][0] - P[61][0]) * 0.6, P[152][1]))
        # ojo derecho del paciente (izquierda de la imagen)
        cv2.imwrite(str(ZONES / "ojo" / f.name), crop(img, P[127][0], P[105][1] - (P[159][1] - P[105][1]), P[6][0], P[50][1]))


MIRROR = ("rostro_oblicuo", "rostro_perfil", "ojo", "brazo", "abdomen_lateral")


def mirrors():
    """Copias en espejo (m_…): el lado contrario de las vistas laterales (derecho ↔ izquierdo)."""
    for z in MIRROR:
        d = ZONES / z
        if not d.exists(): continue
        for f in sorted(d.glob("*.jpg")):
            if f.name.startswith("m_"): continue
            m = d / ("m_" + f.name)
            if not m.exists():
                img = cv2.imread(str(f))
                if img is not None: cv2.imwrite(str(m), cv2.flip(img, 1))


def main():
    closeups()
    mirrors()
    for d in sorted(p for p in ZONES.iterdir() if p.is_dir()):
        res = {}
        for f in sorted(d.glob("*.jpg")):
            img = cv2.imread(str(f))
            if img is None: continue
            img = frame34(img)
            cv2.imwrite(str(f), img)        # la prueba usa la imagen tal como se analizó (3:4)
            res[f.name] = analyze(img)
        (d / "landmarks.json").write_text(json.dumps(res))
        nf = sum(1 for v in res.values() if v["face"]); npz = sum(1 for v in res.values() if v["pose"])
        print(f"{d.name}: {len(res)} imágenes, cara en {nf}, cuerpo en {npz}")


if __name__ == "__main__":
    main()
