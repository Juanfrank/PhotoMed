"""Puntos de control de la ilustración frontal para la deformación de la guía (thin-plate spline).

1. Malla de MediaPipe (478 puntos) detectada sobre el propio dibujo face_front.png (la ilustración
   es lo bastante realista para el detector) → mismas claves que usa la app (FaceMeshMap).
2. Orejas (borde superior, hélice externa, lóbulo) y vértice del cráneo, marcados en el dibujo;
   en la foto se estiman proyectando sus equivalentes 3D con la pose de la cabeza (HeadPose).

Escribe art/source/face_front_controls.json (px de la fuente). Requiere mediapipe y el modelo
face_landmarker.task solo para regenerar; build_guides.py usa el JSON guardado.
Uso: python3 tools/guides/face_controls.py /ruta/face_landmarker.task
"""
import json, pathlib, re, sys
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "art/source/face_front.png"
OUT = ROOT / "art/source/face_front_controls.json"

# Orejas y vértice (px de face_front.png, 1446×1896); la oreja derecha de la imagen es simétrica.
CX = 718
EXTRA = {
    "ear_r_top": (150, 885), "ear_r_out": (92, 985), "ear_r_low": (240, 1285),
    "vertex": (CX, 165),
}
EXTRA.update({k.replace("ear_r_", "ear_l_"): (2 * CX - x, y) for k, (x, y) in list(EXTRA.items()) if k.startswith("ear_r_")})

def mesh(task):
    import mediapipe as mp
    from mediapipe.tasks import python as mpt
    from mediapipe.tasks.python import vision
    from PIL import Image
    lm = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
        base_options=mpt.BaseOptions(model_asset_path=task), num_faces=1,
        min_face_detection_confidence=0.1, min_face_presence_confidence=0.1))
    im = Image.open(SRC).convert("RGB")
    r = lm.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.array(im)))
    return [(p.x * im.width, p.y * im.height) for p in r.face_landmarks[0]]

if __name__ == "__main__":
    pts = mesh(sys.argv[1])
    kt = (ROOT / "app/src/main/java/com/photomed/app/align/FaceEngines.kt").read_text()
    groups = {g: [int(x) for x in re.findall(r"\d+", idx)] for g, idx in re.findall(r'"(\w+_)" to intArrayOf\(([\d,\s]+)\)', kt)}
    singles = {"eye_l": 473, "eye_r": 468, "nose_base": 2, "mouth_l": 291, "mouth_r": 61}
    out = {}
    for g, idx in groups.items():
        for k, i in enumerate(idx): out[f"{g}{k}"] = [round(pts[i][0], 1), round(pts[i][1], 1)]
    for k, i in singles.items(): out[k] = [round(pts[i][0], 1), round(pts[i][1], 1)]
    for k, v in EXTRA.items(): out[k] = list(v)
    OUT.write_text(json.dumps(out, indent=0))
    print(len(out), "puntos →", OUT)
