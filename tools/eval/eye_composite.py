"""Validación de EyeComposite (ojo + su espejo → malla facial): recortes de ojo de tools/eval/zone_landmarks.py
con la malla de la foto original como referencia (scratch gt.json). Uso: python3 tools/eval/eye_composite.py"""
import json, numpy as np, cv2, mediapipe as mp
from mediapipe.tasks.python import vision, BaseOptions
Z='/home/user/PhotoMed/app/src/test/resources/stock/zones/'
gt=json.load(open('gt.json'))
fl=vision.FaceLandmarker.create_from_options(vision.FaceLandmarkerOptions(base_options=BaseOptions(model_asset_path='/home/user/PhotoMed/app/src/main/assets/face_landmarker.task'),num_faces=1,min_face_detection_confidence=0.5,min_face_presence_confidence=0.5))
EYE_R=[33,246,161,160,159,158,157,173,133,155,154,153,145,144,163,7]; BROW_R=[70,63,105,66,107]
def run(img):
    h,w=img.shape[:2]
    comp=np.hstack([img, img[:, ::-1]])           # ojo derecho + su espejo: «cara superior»
    # abajo: se continúa con el color medio de la mejilla (zona inferior del recorte)
    cheek=np.median(img[int(h*0.85):,:].reshape(-1,3),0).astype(np.uint8)
    pad=np.full((int(h*0.9),2*w,3),cheek,np.uint8)
    full=np.vstack([comp,pad])
    rgb=cv2.cvtColor(full,cv2.COLOR_BGR2RGB)
    r=fl.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=np.ascontiguousarray(rgb)))
    if not r.face_landmarks: return None
    P=np.array([[p.x*full.shape[1],p.y*full.shape[0]] for p in r.face_landmarks[0]])
    return P
ok=0; errs=[]
for f,Q in gt.items():
    Q=np.array(Q); img=cv2.imread(Z+'ojo/'+f)
    P=run(img)
    if P is None: continue
    ok+=1
    ew=np.linalg.norm(Q[33]-Q[133])
    errs.append((np.mean(np.linalg.norm(P[EYE_R]-Q[EYE_R],axis=1))/ew, np.mean(np.linalg.norm(P[BROW_R]-Q[BROW_R],axis=1))/ew,
                 np.linalg.norm(P[468]-np.mean(Q[EYE_R],0))/ew))
print('con malla',ok,'de',len(gt))
a=np.array(errs); print('ojo %.3f ceja %.3f iris %.3f media; mediana'%tuple(a.mean(0)), np.median(a,0).round(3))
