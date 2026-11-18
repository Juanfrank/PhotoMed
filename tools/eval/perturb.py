import os
import json, random, pathlib, sys, cv2, numpy as np, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import landmarks, crop34
EV = pathlib.Path(os.environ.get('PHOTOMED_EVAL', '/tmp/photomed-eval') + ''); L = EV / 'lfw/lfw_funneled'
c = json.load(open(EV / 'lfw_cache.json'))
random.seed(11); np.random.seed(11)
refs = random.sample(sorted(c), 120)

def warp(img, dx, dy, s, rot, bright, noise):
    h, w = img.shape[:2]
    M = cv2.getRotationMatrix2D((w / 2, h / 2), rot, s); M[0, 2] += dx * w; M[1, 2] += dy * w
    out = cv2.warpAffine(img, M, (w, h), borderMode=cv2.BORDER_REFLECT)
    out = np.clip(out.astype(float) * bright + np.random.normal(0, noise, out.shape), 0, 255).astype(np.uint8)
    return out

LEVELS = {
    'retoma (≤1 % despl., ≤1,5 % escala, ≤1°)': (0.01, 0.015, 1.0),
    'moderada (3–4 % / 5 % / 3°)': (0.035, 0.05, 3.0),
    'grande (8 % / 12 % / 6°)': (0.08, 0.12, 6.0),
}
if __name__ == "__main__":
    results = {k: [] for k in LEVELS}
    fixtures = []
    for r in refs:
        img = crop34(cv2.imread(str(L / r)))
        ref = c[r]
        for name, (sh, sc, ro) in LEVELS.items():
            sgn = lambda: random.choice([-1, 1])
            if name.startswith('retoma'):
                p = (sgn() * random.uniform(0, sh), sgn() * random.uniform(0, sh), 1 + sgn() * random.uniform(0, sc), sgn() * random.uniform(0, ro))
            else:  # un solo eje grande, el resto pequeño
                axis = random.choice(['x', 'y', 's', 'r'])
                p = [sgn() * 0.004, sgn() * 0.004, 1.0, 0.0]
                if axis == 'x': p[0] = sgn() * sh
                if axis == 'y': p[1] = sgn() * sh
                if axis == 's': p[2] = 1 + sgn() * sc
                if axis == 'r': p[3] = sgn() * ro
            cur = landmarks(warp(img, *p, random.uniform(0.85, 1.15), 3))
            results[name].append(cur)
            fixtures.append({'ref': r, 'level': name, 'perturb': p, 'cur': cur})
    json.dump(fixtures, open(EV / 'perturb.json', 'w'))
    
    ALL = sorted(c[refs[0]]['pts'].keys())
    def frac(a, b, tol=0.03):
        keys = [k for k in ALL if k in a and k in b]
        A = np.array([a[k] for k in keys]); B = np.array([b[k] for k in keys])
        return float((np.linalg.norm(A - B, axis=1) <= tol).mean())
    for name in LEVELS:
        fr = []
        for fx in [f for f in fixtures if f['level'] == name]:
            fr.append(0.0 if fx['cur'] is None else frac(c[fx['ref']]['pts'], fx['cur']['pts']))
        fr = np.array(fr)
        print(f'{name}: p10/50/90 fracción = {np.percentile(fr,[10,50,90]).round(3)}  coincide@80% = {np.mean(fr>=0.8):.2f}')
    
    # negativas: ¿detecta rostro?
    neg = sorted(glob.glob(str(EV / 'neg/*')))
    faces = [(f, landmarks(crop34(cv2.imread(f)))) for f in neg]
    print('negativas con rostro detectado:', [pathlib.Path(f).name for f, r in faces if r])
    json.dump({pathlib.Path(f).name: r for f, r in faces}, open(EV / 'neg_cache.json', 'w'))
