import os
import json, itertools, random, numpy as np
c = json.load(open(os.environ.get('PHOTOMED_EVAL', '/tmp/photomed-eval') + '/lfw_cache.json'))
by = {}
for k in c: by.setdefault(k.split('/')[0], []).append(k)
random.seed(3)

def arr(k, keys): return np.array([c[k]['pts'][x] for x in keys])
ALL = sorted(next(iter(c.values()))['pts'].keys())
STABLE = [k for k in ALL if k.startswith(('eye_l_', 'eye_r_', 'nose_', 'brow_')) ] + ['eye_l', 'eye_r', 'nose_base']

def umeyama(a, b):
    ma, mb = a.mean(0), b.mean(0); A, B = a - ma, b - mb
    s1 = (A[:, 0] * B[:, 0] + A[:, 1] * B[:, 1]).sum(); s2 = (A[:, 0] * B[:, 1] - A[:, 1] * B[:, 0]).sum()
    th = np.arctan2(s2, s1); sc = np.hypot(s1, s2) / (A ** 2).sum()
    R = np.array([[np.cos(th), -np.sin(th)], [np.sin(th), np.cos(th)]]) * sc
    return lambda p: (p - ma) @ R.T + mb

def shape_dist(k1, k2, keys=STABLE):
    a, b = arr(k1, keys), arr(k2, keys)
    f = umeyama(b, a); res = np.linalg.norm(f(b) - a, axis=1)
    iod = np.linalg.norm(np.array(c[k1]['pts']['eye_l']) - np.array(c[k1]['pts']['eye_r']))
    return float(np.sqrt((res ** 2).mean()) / iod)

def frac(k1, k2, tol=0.03):
    a, b = arr(k1, ALL), arr(k2, ALL)
    return float((np.linalg.norm(a - b, axis=1) <= tol).mean())

same = [(a, b) for p, ks in by.items() if len(ks) >= 2 for a, b in itertools.combinations(ks, 2)]
keys = list(c); diff = []
while len(diff) < len(same):
    a, b = random.sample(keys, 2)
    if a.split('/')[0] != b.split('/')[0]: diff.append((a, b))
for name, f in [('fracción de puntos (tol 3 %)', frac), ('distancia de forma estable / DIO', shape_dist)]:
    s = np.array([f(*p) for p in same]); d = np.array([f(*p) for p in diff])
    print(f'{name}: misma persona p10/50/90 = {np.percentile(s,[10,50,90]).round(3)}  distinta = {np.percentile(d,[10,50,90]).round(3)}')
    if 'fracción' in name:
        for thr in (0.5, 0.8, 0.9, 0.99): print(f'   umbral {thr}: coinciden misma={np.mean(s>=thr):.2f}  distinta (FP)={np.mean(d>=thr):.2f}')
    else:
        for thr in (0.04, 0.05, 0.06, 0.07, 0.08):
            print(f'   umbral {thr}: acepta misma={np.mean(s<=thr):.2f}  acepta distinta (FP)={np.mean(d<=thr):.2f}')
print('pares misma', len(same), 'distinta', len(diff))
