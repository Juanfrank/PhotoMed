import os
import json, random, pathlib, cv2, numpy as np, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import landmarks, crop34
EV = pathlib.Path(os.environ.get('PHOTOMED_EVAL', '/tmp/photomed-eval') + ''); L = EV / 'lfw/lfw_funneled'
random.seed(7)
people = sorted(p for p in L.iterdir() if p.is_dir())
multi = [p for p in people if len(list(p.glob('*.jpg'))) >= 3]
random.shuffle(multi)
chosen = multi[:150]
cache = {}
def add(path):
    img = crop34(cv2.imread(str(path)))
    r = landmarks(img)
    if r: cache[str(path.relative_to(L))] = r
for p in chosen:
    for f in sorted(p.glob('*.jpg'))[:3]: add(f)
singles = [p for p in people if p not in chosen]; random.shuffle(singles)
for p in singles[:350]: add(sorted(p.glob('*.jpg'))[0])
json.dump(cache, open(EV / 'lfw_cache.json', 'w'))
print('imágenes con rostro:', len(cache))
