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
hands = vision.HandLandmarker.create_from_options(vision.HandLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(ROOT / "app/src/main/assets/hand_landmarker.task")), num_hands=2))
segm = vision.ImageSegmenter.create_from_options(vision.ImageSegmenterOptions(
    base_options=BaseOptions(model_asset_path=str(ROOT / "app/src/main/assets/selfie_multiclass.tflite")), output_category_mask=True))
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
    # manos (21 puntos, etiqueta de MediaPipe) y clases por píxel (piel, ropa…) a 96×128, como la app
    hr = hands.detect(im)
    out["hands"] = [{"label": hd[0].category_name, "points": [[q.x, q.y] for q in lm]} for lm, hd in zip(hr.hand_landmarks, hr.handedness)]
    cm = segm.segment(im).category_mask.numpy_view()
    small = cv2.resize(cm.astype(np.uint8), (96, 128), interpolation=cv2.INTER_NEAREST)
    out["classes"] = "".join(str(int(v)) for v in small.flatten())
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


# fotos frontales que no sirven para un primer plano (gafas de sol, ojo cerrado)
CLOSEUP_SKIP = {"periocular": {"s_p_1043471_r0"}, "ojo": {"s_p_1043471_r0", "s_p_1858175_r0"}}


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
        def put(z, im):
            if f.stem in CLOSEUP_SKIP.get(z, set()):
                (ZONES / z / f.name).unlink(missing_ok=True); return
            cv2.imwrite(str(ZONES / z / f.name), im)
        put("tercio_superior", crop(img, oval_l, top, oval_r, P[6][1]))
        x0, _, x1, _ = box(brows + [33, 133, 362, 263])
        put("periocular", crop(img, oval_l, P[105][1] - (P[1][1] - P[105][1]) * 0.4, oval_r, P[1][1]))
        put("labios", crop(img, P[61][0] - (P[291][0] - P[61][0]) * 0.6, P[4][1], P[291][0] + (P[291][0] - P[61][0]) * 0.6, P[152][1]))
        # ojo derecho del paciente (izquierda de la imagen)
        put("ojo", crop(img, P[127][0], P[105][1] - (P[159][1] - P[105][1]), P[6][0], P[50][1]))


PAIRS = []
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


def mirror_pairs():
    """Pareja izquierda ↔ derecha de cada punto de la malla, de las caras frontales del conjunto: malla
    media centrada, reflejada y emparejada por el punto más cercano (iris 468–472 ↔ 473–477)."""
    d = json.loads((ZONES / "rostro_frontal" / "landmarks.json").read_text())
    meshes = []
    for v in d.values():
        if not v["face"]: continue
        P = np.array(v["face"]["mesh"])[:468] * [v["w"], v["h"]]
        if abs(P[1, 0] - (P[234, 0] + P[454, 0]) / 2) > 0.03 * (P[454, 0] - P[234, 0]): continue   # solo de frente
        P = (P - P[1]) / (P[454, 0] - P[234, 0]); meshes.append(P)
    M = np.mean(meshes, 0)
    R = M * [-1, 1]
    pairs = np.array([int(np.argmin(((M - r) ** 2).sum(1))) for r in R])
    sym = float(np.mean(pairs[pairs] == np.arange(len(pairs))))
    pairs = list(pairs) + [473, 474, 475, 476, 477, 468, 469, 470, 471, 472]
    print(f"parejas de la malla: {len(meshes)} caras de frente, simetría {sym:.3f}")
    return pairs


def mirror_face(face, pairs):
    """Malla y matriz de la cara reflejada (x → 1 − x, puntos izquierda ↔ derecha)."""
    mesh = face["mesh"]
    out = {"mesh": [[1 - mesh[pairs[i]][0], mesh[pairs[i]][1]] for i in range(len(mesh))], "matrix": None}
    if face.get("matrix"):
        M = np.array(face["matrix"]).reshape(4, 4).T; S = np.diag([-1, 1, 1, 1])
        out["matrix"] = [float(v) for v in (S @ M @ S).T.flatten()]
    return out


def main():
    closeups()
    mirrors()
    for d in sorted(p for p in ZONES.iterdir() if p.is_dir()):
        res = {}
        for f in sorted(d.glob("*.jpg")):
            img = cv2.imread(str(f))
            if img is None: continue
            img = frame34(img)
            a = analyze(img)
            if f.name.startswith("m_") and a["face"] is None:
                # espejo sin cara: el detector es sensible al encuadre; se prueba algo más abierto
                for pad in (0.12, 0.25):
                    h, w = img.shape[:2]
                    big = cv2.copyMakeBorder(img, int(h * pad), int(h * pad), int(w * pad), int(w * pad), cv2.BORDER_REPLICATE)
                    b = analyze(big)
                    if b["face"] is not None:
                        img, a = big, b; break
            cv2.imwrite(str(f), img)        # la prueba usa la imagen tal como se analizó (3:4)
            res[f.name] = a
        # espejos en los que el detector no ve la cara: los puntos reflejados de la original
        if d.name in MIRROR and (ZONES / "rostro_frontal" / "landmarks.json").exists():
            pairs = PAIRS[0] if PAIRS else PAIRS.append(mirror_pairs()) or PAIRS[0]
            for n, v in res.items():
                o = res.get(n[2:]) if n.startswith("m_") else None
                if v["face"] is None and o and o["face"] and o["w"] == v["w"] and o["h"] == v["h"]:
                    v["face"] = mirror_face(o["face"], pairs); v["mirrored_points"] = True
            # además, de cada original con cara, su reflejo exacto (pm_…: imagen m_…, puntos reflejados):
            # lo que dispara en un lado debe disparar en el otro (simetría de la lógica)
            for n, o in list(res.items()):
                m = res.get("m_" + n)
                if not n.startswith("m_") and o["face"] and m and o["w"] == m["w"] and o["h"] == m["h"]:
                    res["pm_" + n] = {"w": o["w"], "h": o["h"], "image": "m_" + n, "face": mirror_face(o["face"], pairs), "pose": None}
        (d / "landmarks.json").write_text(json.dumps(res))
        nf = sum(1 for v in res.values() if v["face"]); npz = sum(1 for v in res.values() if v["pose"])
        print(f"{d.name}: {len(res)} imágenes, cara en {nf}, cuerpo en {npz}")


if __name__ == "__main__":
    main()
