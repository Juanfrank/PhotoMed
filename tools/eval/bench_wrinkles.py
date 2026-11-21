"""Benchmark de arrugas y surcos con FFHQ-Wrinkle (máscaras manuales de 3 anotadores, ICPR 2024).

Descarga solo las fotos con máscara manual (lectura parcial de los zips de FFHQ 1024 en Hugging Face),
genera la malla de MediaPipe y la segmentación pelo / piel con los mismos modelos que la app, y lo
deja en app/src/test/resources/bench/wrinkles/ (carpeta ignorada: FFHQ es CC BY-NC-SA, no se sube).
La prueba WrinkleBenchmarkTest pasa el motor de la app y compara con las máscaras.

Uso: python3 tools/eval/bench_wrinkles.py carpeta_mascaras/ [n=100]
  (máscaras: https://github.com/labhai/ffhq-wrinkle-dataset, manual_wrinkle_masks)
"""
import sys, json, pathlib, random, io
import numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions
from remotezip import RemoteZip

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/test/resources/bench/wrinkles"; OUT.mkdir(parents=True, exist_ok=True)
A = ROOT / "app/src/main/assets"
masks = pathlib.Path(sys.argv[1]); n = int(sys.argv[2]) if len(sys.argv) > 2 else 100
ids = sorted(p.stem for p in masks.glob("*.png"))
random.seed(7); pick = set(random.sample(ids, min(n, len(ids))))
det = vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(
    base_options=BaseOptions(model_asset_path=str(A / "face_landmarker.task")), num_faces=1))
seg = vision.ImageSegmenter.create_from_options(vision.ImageSegmenterOptions(
    base_options=BaseOptions(model_asset_path=str(A / "selfie_multiclass.tflite")), output_category_mask=True))
data = json.loads((OUT / "mesh.json").read_text()) if (OUT / "mesh.json").exists() else {}
for k in range(1, 8):
    url = f"https://huggingface.co/datasets/pravsels/FFHQ_1024/resolve/main/shard_{k}_of_7.zip"
    with RemoteZip(url) as z:
        want = [nm for nm in z.namelist() if nm[:-4] in pick and nm[:-4] not in data]
        print(f"zip {k}: {len(want)} fotos")
        for nm in want:
            i = nm[:-4]
            img = cv2.imdecode(np.frombuffer(z.read(nm), np.uint8), cv2.IMREAD_COLOR)
            r = det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(img, cv2.COLOR_BGR2RGB)))
            if not r.face_landmarks: print(i, "sin cara"); continue
            h, w = img.shape[:2]
            m = np.array([[p.x * w, p.y * h] for p in r.face_landmarks[0]])
            lab = seg.segment(mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(img, cv2.COLOR_BGR2RGB))).category_mask.numpy_view()
            cv2.imwrite(str(OUT / f"{i}.jpg"), img, [cv2.IMWRITE_JPEG_QUALITY, 93])
            cv2.imwrite(str(OUT / f"{i}_labels.png"), lab.astype(np.uint8))
            gt = cv2.imread(str(masks / f"{i}.png"), cv2.IMREAD_GRAYSCALE)
            cv2.imwrite(str(OUT / f"{i}_gt.png"), ((gt > 0) * 255).astype(np.uint8))
            data[i] = (m / w).round(5).tolist()
            (OUT / "mesh.json").write_text(json.dumps(data))
            print(i, "ok")
print(len(data), "fotos listas")
