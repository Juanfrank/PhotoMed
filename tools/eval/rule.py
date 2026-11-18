import os
import json, itertools, random, numpy as np
EV = os.environ.get('PHOTOMED_EVAL', '/tmp/photomed-eval') + '/'
c = json.load(open(EV + 'lfw_cache.json')); fx = json.load(open(EV + 'perturb.json'))
ALL = sorted(next(iter(c.values()))['pts'].keys())
def sim(a, b):  # b -> a
    ma, mb = a.mean(0), b.mean(0); A, B = a - ma, b - mb
    s1 = (B[:, 0] * A[:, 0] + B[:, 1] * A[:, 1]).sum(); s2 = (B[:, 0] * A[:, 1] - B[:, 1] * A[:, 0]).sum()
    th = np.arctan2(s2, s1); sc = np.hypot(s1, s2) / (B ** 2).sum()
    R = np.array([[np.cos(th), -np.sin(th)], [np.sin(th), np.cos(th)]]) * sc
    t = ma - mb @ R.T
    return sc, np.degrees(th), t, R
def decide(ref, cur, thr=0.8, tol=0.03, off_max=0.03, sc_max=0.04, rot_max=3.0, pose_max=6.0, use_pose=True):
    if cur is None: return False
    keys = [k for k in ALL if k in ref['pts'] and k in cur['pts']]
    A = np.array([ref['pts'][k] for k in keys]); B = np.array([cur['pts'][k] for k in keys])
    frac = (np.linalg.norm(A - B, axis=1) <= tol).mean()
    sc, rot, t, R = sim(A, B)
    piv = A.mean(0); off = np.linalg.norm(piv @ R.T + t - piv)  # desplazamiento del pivote
    ok = frac >= thr and off <= off_max and abs(np.log(sc)) <= sc_max and abs(rot) <= rot_max
    if use_pose and ref.get('euler') and cur.get('euler'):
        dy, dp = abs(ref['euler'][0] - cur['euler'][0]), abs(ref['euler'][1] - cur['euler'][1])
        ok = ok and dy <= pose_max and dp <= pose_max
    return ok
by = {}
for k in c: by.setdefault(k.split('/')[0], []).append(k)
random.seed(3)
same = [(a, b) for p, ks in by.items() if len(ks) >= 2 for a, b in itertools.combinations(ks, 2)]
keys = list(c); diff = []
while len(diff) < 2000:
    a, b = random.sample(keys, 2)
    if a.split('/')[0] != b.split('/')[0]: diff.append((a, b))
for label, kw in [('solo % puntos', dict(off_max=9, sc_max=9, rot_max=99, use_pose=False)),
                  ('% + geometría', dict(use_pose=False)),
                  ('% + geometría + pose cabeza', dict())]:
    print('==', label)
    for lv in ['retoma', 'moderada', 'grande']:
        sel = [f for f in fx if f['level'].startswith(lv)]
        print(f'   {lv}: coincide = {np.mean([decide(c[f["ref"]], f["cur"], **kw) for f in sel]):.2f}')
    print(f'   distinta persona, mismo encuadre (LFW): FP = {np.mean([decide(c[a], c[b], **kw) for a, b in diff]):.3f}')
    print(f'   misma persona, otra sesión (LFW): coincide = {np.mean([decide(c[a], c[b], **kw) for a, b in same]):.3f}')
# pose de cabeza: variación entre fotos de la misma persona (referencia)
dy = [abs(c[a]['euler'][0] - c[b]['euler'][0]) for a, b in same]
print('Δguiñada misma persona p50/p90:', np.percentile(dy, [50, 90]).round(1))
