"""Genera HeadModel.kt: coordenadas 3D (cm) del modelo canónico de rostro de MediaPipe
(canonical_face_model.obj, Apache 2.0) para las claves de puntos que usa la app (mismos índices
que FaceMeshMap), y valida el ajuste de pose de HeadPose contra la pose de MediaPipe en LFW.

Uso: curl -LO https://raw.githubusercontent.com/google-ai-edge/mediapipe/master/mediapipe/modules/face_geometry/data/canonical_face_model.obj
     python3 tools/eval/head_model.py canonical_face_model.obj
"""
import pathlib, re, sys
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parents[2]
kt = (ROOT / "app/src/main/java/com/photomed/app/align/FaceEngines.kt").read_text()
groups = {g: [int(x) for x in re.findall(r"\d+", idx)] for g, idx in re.findall(r'"(\w+_)" to intArrayOf\(([\d,\s]+)\)', kt)}
V = np.array([[float(t) for t in l.split()[1:4]] for l in open(sys.argv[1]) if l.startswith("v ")])
model = {}
for g, idx in groups.items():
    for k, i in enumerate(idx): model[f"{g}{k}"] = V[i]
model["eye_l"] = np.mean([V[i] for i in groups["eye_l_"]], 0)
model["eye_r"] = np.mean([V[i] for i in groups["eye_r_"]], 0)
model["nose_base"] = V[2]; model["mouth_l"] = V[291]; model["mouth_r"] = V[61]
out = ["// ARCHIVO GENERADO por tools/eval/head_model.py — no editar a mano.",
       "// Modelo canónico de rostro de MediaPipe (Apache 2.0), en cm: x a la derecha de la imagen, y arriba, z hacia la cámara.",
       "package com.photomed.app.align", "", "internal object HeadModel {",
       "    /** Vértice del cráneo (estimación antropométrica: ~11,5 cm sobre los ojos, sobre las orejas). */",
       "    val VERTEX = floatArrayOf(0f, 14.0f, -1.5f)",
       "    val POINTS: Map<String, FloatArray> = mapOf("]
out += [f'        "{k}" to floatArrayOf({v[0]:.3f}f, {v[1]:.3f}f, {v[2]:.3f}f),' for k, v in model.items()]
out += ["    )", "}"]
(ROOT / "app/src/main/java/com/photomed/app/align/HeadModel.kt").write_text("\n".join(out) + "\n")
print(len(model), "puntos")
