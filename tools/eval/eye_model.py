"""Validación del modelo de ojo (iris landmark de MediaPipe) frente a la malla de la foto entera en recortes de ojo:
encuadre normal y cerrado, búsqueda sin malla y refinamiento con el recorte de la malla. Requiere ai-edge-litert,
mediapipe, el modelo en el directorio y gt.json de tools/eval/eye_composite.py."""
import json, numpy as np, cv2, sys
from ai_edge_litert.interpreter import Interpreter
Z='/home/user/PhotoMed/app/src/test/resources/stock/zones/'
gt=json.load(open('../eye/gt.json'))
it=Interpreter(model_path='iris_landmark.tflite'); it.allocate_tensors()
I=it.get_input_details()[0]['index']; O=[o['index'] for o in it.get_output_details()]
# orden de los 71 puntos (ojo derecho de la persona = izquierda de la imagen, índices de la malla)
MAP=[33,7,163,144,145,153,154,155,133,246,161,160,159,158,157,173,130,25,110,24,23,22,26,112,243,247,30,29,27,28,56,190,
     226,31,228,229,230,231,232,233,244,113,225,224,223,222,221,189,35,124,46,53,52,65,143,111,117,118,119,120,121,128,245,
     156,70,63,105,66,107,55,193]
EYE16=list(range(16))
def run(img, cx, cy, size, flip, rng):
    # recorte cuadrado centrado en (cx,cy) de lado size -> 64x64
    s=size/64.0
    M=np.float32([[1/s,0,-(cx-size/2)/s],[0,1/s,-(cy-size/2)/s]])
    crop=cv2.warpAffine(img,M,(64,64),flags=cv2.INTER_AREA,borderMode=cv2.BORDER_REPLICATE)
    if flip: crop=crop[:,::-1]
    x=cv2.cvtColor(crop,cv2.COLOR_BGR2RGB).astype(np.float32)
    x = x/127.5-1 if rng=='-1' else x/255.0
    it.set_tensor(I,x[None]); it.invoke()
    c=it.get_tensor(O[0]).reshape(71,3)[:,:2].copy(); ir=it.get_tensor(O[1]).reshape(5,3)[:,:2].copy()
    if flip: c[:,0]=64-c[:,0]; ir[:,0]=64-ir[:,0]
    c=c*s+[cx-size/2,cy-size/2]; ir=ir*s+[cx-size/2,cy-size/2]
    return c,ir
def detect(img, flip, rng, iters=3, init=None):
    h,w=img.shape[:2]
    cx,cy,size=(w/2,h*0.55,min(w,h)*0.9) if init is None else init
    for _ in range(iters):
        c,ir=run(img,cx,cy,size,flip,rng)
        a=c[0]; b=c[8]; ew=np.linalg.norm(a-b)
        cx,cy=(a+b)/2; size=2.3*ew   # como MediaPipe: 2,3 anchos de ojo
    return c,ir
def tight(img,f):
    h,w=img.shape[:2]; ch,cw=int(h*f),int(w*f); y0=(h-ch)//2+int(0.1*h*(1-f)); x0=(w-cw)//2
    return img[y0:y0+ch,x0:x0+cw], (x0,y0)
for flip in ():
  for rng in ('-1','0'):
    for fc in (1.0,0.8,0.65):
        errs=[];irs=[]
        for f,Q in gt.items():
            Q=np.array(Q); img=cv2.imread(Z+'ojo/'+f)
            im2,(x0,y0)=tight(img,fc) if fc<1 else (img,(0,0))
            c,ir=detect(im2,flip,rng); c=c+[x0,y0]; ir=ir+[x0,y0]
            P=np.array([Q[m] for m in MAP[:16]])
            ew=np.linalg.norm(Q[33]-Q[133]); errs.append(np.mean(np.linalg.norm(c[:16]-P,axis=1))/ew)
            irs.append(np.linalg.norm(ir[0]-Q[468])/ew)
        e=np.array(errs)
        print('flip',flip,'rango',rng,'recorte %.2f: contorno %.3f (mediana %.3f), <25%%: %d/%d, iris %.3f'%(fc,e.mean(),np.median(e),(e<0.25).sum(),len(e),np.median(irs)))

print('--- con el recorte inicial desde las esquinas de referencia (cota superior)')
for sc in (1.8,2.3,2.8):
    errs=[]
    for f,Q in gt.items():
        Q=np.array(Q); img=cv2.imread(Z+'ojo/'+f)
        a=Q[33];b=Q[133]; ew=np.linalg.norm(a-b); c0=(a+b)/2
        c,ir=run(img,c0[0],c0[1],sc*ew,False,'0')
        P=np.array([Q[m] for m in MAP[:16]]); errs.append(np.mean(np.linalg.norm(c[:16]-P,axis=1))/ew)
    print('escala %.1f: contorno %.3f (mediana %.3f)'%(sc,np.mean(errs),np.median(errs)))

def search(img):
    h,w=img.shape[:2]; best=None
    for fs in (0.45,0.65,0.9):
        size=fs*min(w,h)*1.0
        for gy in (0.35,0.5,0.65):
            for gx in (0.3,0.5,0.7):
                cx,cy=gx*w,gy*h
                c,ir=run(img,cx,cy,size,False,'0')
                a=c[0];b=c[8]; ew=np.linalg.norm(a-b); m=(a+b)/2
                # una pasada más centrada en lo predicho
                c2,ir2=run(img,m[0],m[1],2.6*ew,False,'0')
                a2=c2[0];b2=c2[8]; ew2=np.linalg.norm(a2-b2); m2=(a2+b2)/2
                cost=np.linalg.norm(m2-m)/ew2+abs(ew2/ew-1)
                if ew2<0.1*min(w,h): cost+=1
                if best is None or cost<best[0]: best=(cost,c2,ir2,m2,ew2)
    cost,c,ir,m,ew=best
    for _ in range(2):
        c,ir=run(img,m[0],m[1],2.6*ew,False,'0'); a=c[0];b=c[8]; ew=np.linalg.norm(a-b); m=(a+b)/2
    return c,ir,cost
import importlib.util
spec=importlib.util.spec_from_file_location('ev3','../eye/ev3.py')
src=open('../eye/ev3.py').read().split('for f_crop in')[0].replace("open('gt.json')","open('../eye/gt.json')")
exec(src)
print('--- búsqueda en rejilla (sin malla) y combinado')
for fc in (1.0,0.8,0.65):
    e_s=[];e_c=[];nm=0
    for f,Q in gt.items():
        Q=np.array(Q); img=cv2.imread(Z+'ojo/'+f)
        im2,(x0,y0)=tight(img,fc) if fc<1 else (img,(0,0))
        ew=np.linalg.norm(Q[33]-Q[133]); P=np.array([Q[m] for m in MAP[:16]])
        c,ir,cost=search(im2); c=c+[x0,y0]
        es=np.mean(np.linalg.norm(c[:16]-P,axis=1))/ew; e_s.append(es)
        Pm=composite2(im2,0.0)
        if Pm is not None:
            nm+=1; Pm=Pm+[x0,y0]; e_c.append(np.mean(np.linalg.norm(Pm[MAP[:16]]-P,axis=1))/ew)
        else: e_c.append(es)
    e_s=np.array(e_s); e_c=np.array(e_c)
    print('recorte %.2f: modelo de ojo %.3f (mediana %.3f, <25%% %d/26) | malla y si no, modelo de ojo %.3f (mediana %.3f, <25%% %d/26; malla en %d)'%(fc,e_s.mean(),np.median(e_s),(e_s<0.25).sum(),e_c.mean(),np.median(e_c),(e_c<0.25).sum(),nm))
print('--- malla + refinamiento con el modelo de ojo (recorte desde las esquinas de la malla)')
for fc in (1.0,0.8,0.65):
    e_m=[];e_r=[];e_a=[]
    for f,Q in gt.items():
        Q=np.array(Q); img=cv2.imread(Z+'ojo/'+f)
        im2,(x0,y0)=tight(img,fc) if fc<1 else (img,(0,0))
        Pm=composite2(im2,0.0)
        if Pm is None: continue
        ew=np.linalg.norm(Q[33]-Q[133]); P=np.array([Q[m] for m in MAP[:16]])
        a=Pm[33];b=Pm[133]; m=(a+b)/2; e2=np.linalg.norm(a-b)
        c,ir=run(im2,m[0],m[1],2.8*e2,False,'0')
        a2=c[0];b2=c[8]; m2=(a2+b2)/2; c,ir=run(im2,m2[0],m2[1],2.8*np.linalg.norm(a2-b2),False,'0')
        c=c+[x0,y0]; Pm=Pm+[x0,y0]
        e_m.append(np.mean(np.linalg.norm(Pm[MAP[:16]]-P,axis=1))/ew); e_r.append(np.mean(np.linalg.norm(c[:16]-P,axis=1))/ew)
        e_a.append(np.mean(np.linalg.norm((c[:16]+Pm[MAP[:16]])/2-P,axis=1))/ew)
    print('recorte %.2f (n=%d): malla %.3f | modelo de ojo %.3f | media de ambos %.3f'%(fc,len(e_m),np.median(e_m),np.median(e_r),np.median(e_a)))
