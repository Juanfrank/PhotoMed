"""Orden canónico de los contornos faciales (idéntico a FaceKeys.canonical en la app)."""

def _group(p, g):
    out = []; i = 0
    while f"{g}{i}" in p: out.append(p[f"{g}{i}"]); i += 1
    return out

def canonical(points):
    out = dict(points)
    def put(g, l):
        for i, v in enumerate(l): out[f"{g}{i}"] = v
    mean = lambda l: sum(v[0] for v in l) / len(l)
    for l, r in (("eye_l_", "eye_r_"), ("brow_l_top_", "brow_r_top_"), ("brow_l_bottom_", "brow_r_bottom_")):
        gl, gr = _group(points, l), _group(points, r)
        if gl and gr and mean(gl) < mean(gr): put(r, gl); put(l, gr)
    for l, r in (("eye_l", "eye_r"), ("mouth_l", "mouth_r")):
        if l in points and r in points and points[l][0] < points[r][0]: out[l], out[r] = points[r], points[l]
    for g in ("eye_l_", "eye_r_"):
        e = _group(out, g)
        if len(e) >= 4:
            s = min(range(len(e)), key=lambda i: e[i][0]); r = [e[(s + i) % len(e)] for i in range(len(e))]
            if r[1][1] > r[-1][1]: r = [r[0]] + r[1:][::-1]
            put(g, r)
    b = _group(out, "nose_bridge_")
    if len(b) >= 2 and b[-1][1] < b[0][1]: put("nose_bridge_", b[::-1])
    o = _group(out, "oval_")
    if len(o) >= 8:
        cx = mean(o); cy = sum(v[1] for v in o) / len(o)
        top = out.get("nose_bridge_0"); low = out.get("lip_upper_top_5") or out.get("nose_bottom_1")
        s = None
        if top and low and (top[0] != low[0] or top[1] != low[1]):
            ux, uy = top[0] - low[0], top[1] - low[1]
            n = max((ux * ux + uy * uy) ** 0.5, 1e-6); ux /= n; uy /= n
            cand = [i for i in range(len(o)) if (o[i][0] - top[0]) * ux + (o[i][1] - top[1]) * uy > 0]
            if cand: s = min(cand, key=lambda i: abs((o[i][0] - top[0]) * uy - (o[i][1] - top[1]) * ux))
        if s is None: s = min([i for i in range(len(o)) if o[i][1] < cy], key=lambda i: abs(o[i][0] - cx))
        r = [o[(s + i) % len(o)] for i in range(len(o))]
        if r[1][0] < r[0][0]: r = [r[0]] + r[1:][::-1]
        put("oval_", r)
    for g, inc in (("brow_r_top_", True), ("brow_r_bottom_", True), ("brow_l_top_", False), ("brow_l_bottom_", False),
                   ("lip_upper_top_", True), ("lip_upper_bottom_", True), ("lip_lower_top_", False), ("lip_lower_bottom_", False),
                   ("nose_bottom_", True)):
        c = _group(out, g)
        if len(c) >= 2 and (c[-1][0] > c[0][0]) != inc: put(g, c[::-1])
    b = _group(out, "nose_bridge_")
    if len(b) >= 2 and b[-1][1] < b[0][1]: put("nose_bridge_", b[::-1])
    return out
