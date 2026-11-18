import os
import json, random, pathlib, sys, cv2, numpy as np, glob
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import crop34
from perturb import warp
EV = pathlib.Path(os.environ.get('PHOTOMED_EVAL', '/tmp/photomed-eval') + ''); L = EV / 'lfw/lfw_funneled'
OUT = pathlib.Path(__file__).resolve().parents[2] / 'app/src/test/resources/eval'
c = json.load(open(EV / 'lfw_cache.json')); fx = json.load(open(EV / 'perturb.json'))
random.seed(5)
def r4(d):  # redondeo para reducir tamaño
    return None if d is None else {'pts': {k: [round(v[0], 4), round(v[1], 4)] for k, v in d['pts'].items()},
                                   'euler': [round(e, 2) for e in d['euler']] if d.get('euler') else None}
refs = sorted({f['ref'] for f in fx})
perturb = [{'ref': f['ref'], 'level': f['level'].split(' ')[0], 'cur': r4(f['cur'])} for f in fx]
keys = list(c); diff = []
while len(diff) < 400:
    a, b = random.sample(keys, 2)
    if a.split('/')[0] != b.split('/')[0]: diff.append([a, b])
used = sorted(set(refs) | {k for p in diff for k in p})
json.dump({'faces': {k: r4(c[k]) for k in used}, 'perturb': perturb, 'diff': diff,
           'negatives': sorted(p.name for p in (EV / 'neg').iterdir())},
          open(OUT / 'face_matching.json', 'w'), separators=(',', ':'))
# escala de grises 96×128 (PGM) para el modo por contornos
def pgm(img, path):
    g = cv2.cvtColor(cv2.resize(img, (96, 128), interpolation=cv2.INTER_AREA), cv2.COLOR_BGR2GRAY)
    with open(path, 'wb') as f: f.write(b'P5 96 128 255\n' + g.tobytes())
np.random.seed(2)
for i, r in enumerate(refs[:40]):
    img = crop34(cv2.imread(str(L / r)))
    pgm(img, OUT / f'gray/ref_{i:02d}.pgm')
    pgm(warp(img, 0.006, -0.005, 1.01, 0.6, 1.08, 3), OUT / f'gray/retake_{i:02d}.pgm')
    pgm(warp(img, 0.08, 0.0, 1.0, 0.0, 1.0, 3), OUT / f'gray/shift8_{i:02d}.pgm')
for p in sorted((EV / 'neg').iterdir()):
    pgm(crop34(cv2.imread(str(p))), OUT / f'gray/neg_{p.stem}.pgm')
print('ok', len(used), 'rostros;', len(list((OUT / 'gray').iterdir())), 'pgm;', (OUT / 'face_matching.json').stat().st_size // 1024, 'KB')
