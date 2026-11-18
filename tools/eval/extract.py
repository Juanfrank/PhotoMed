import os
"""Extrae puntos faciales (MediaPipe Face Landmarker, 478 pts) de imágenes recortadas a 3:4."""
import sys, json, pathlib
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions

EV = pathlib.Path(os.environ.get('PHOTOMED_EVAL', '/tmp/photomed-eval') + '')
opts = vision.FaceLandmarkerOptions(base_options=BaseOptions(model_asset_path=str(EV / 'face_landmarker.task')),
                                    output_facial_transformation_matrixes=True, num_faces=1)
det = vision.FaceLandmarker.create_from_options(opts)

# Índices MediaPipe → grupos equivalentes a ML Kit
FACE_OVAL = [10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176,
             149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109]
GROUPS = {
    'oval_': FACE_OVAL,
    'brow_l_top_': [300, 293, 334, 296, 336], 'brow_l_bottom_': [276, 283, 282, 295, 285],
    'brow_r_top_': [70, 63, 105, 66, 107], 'brow_r_bottom_': [46, 53, 52, 65, 55],
    'eye_l_': [362, 398, 384, 385, 386, 387, 388, 466, 263, 249, 390, 373, 374, 380, 381, 382],
    'eye_r_': [33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7],
    'lip_upper_top_': [61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291],
    'lip_upper_bottom_': [78, 191, 80, 81, 82, 13, 312, 311, 310],
    'lip_lower_top_': [318, 402, 317, 14, 87, 178, 88, 95, 78],
    'lip_lower_bottom_': [375, 321, 405, 314, 17, 84, 181, 91, 146],
    'nose_bridge_': [168, 4], 'nose_bottom_': [98, 2, 327],
}
SINGLE = {'eye_l': 473, 'eye_r': 468, 'nose_base': 2, 'mouth_l': 291, 'mouth_r': 61}

def crop34(img):
    h, w = img.shape[:2]
    if w / h > 0.75:
        nw = int(h * 0.75); x0 = (w - nw) // 2; img = img[:, x0:x0 + nw]
    else:
        nh = int(w / 0.75); y0 = (h - nh) // 2; img = img[y0:y0 + nh]
    return cv2.resize(img, (360, 480), interpolation=cv2.INTER_AREA)

def euler(m):
    R = np.array(m)[:3, :3]
    yaw = np.degrees(np.arcsin(-R[2, 0])); pitch = np.degrees(np.arctan2(R[2, 1], R[2, 2])); roll = np.degrees(np.arctan2(R[1, 0], R[0, 0]))
    return [float(yaw), float(pitch), float(roll)]

def landmarks(img_bgr):
    rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
    r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
    if not r.face_landmarks: return None
    lm = r.face_landmarks[0]; w = img_bgr.shape[1]; h = img_bgr.shape[0]
    P = lambda i: [lm[i].x * w / w, lm[i].y * h / w]  # normalizado por el ancho
    out = {}
    for g, idx in GROUPS.items():
        for k, i in enumerate(idx): out[f'{g}{k}'] = P(i)
    for k, i in SINGLE.items(): out[k] = P(i)
    return {'pts': out, 'euler': euler(r.facial_transformation_matrixes[0]) if r.facial_transformation_matrixes else None}

if __name__ == '__main__':
    img = crop34(cv2.imread(sys.argv[1]))
    print(json.dumps(landmarks(img))[:300])
