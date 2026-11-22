#!/usr/bin/env python3
"""Descarga imágenes de prueba por zona desde Openverse (Flickr, Wikimedia Commons…; licencias libres) para la prueba de
captura automática (ZoneAutoCaptureTest). Se guardan en app/src/test/resources/stock/zones/<zona>/
(fuera del repositorio: .gitignore) con un sources.json (título, autor, licencia, URL).

Uso: python3 tools/eval/fetch_zone_images.py [zona ...]   (sin argumentos: todas)
"""
import json, pathlib, sys, time, urllib.error, urllib.parse, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/test/resources/stock/zones"
UA = {"User-Agent": "PhotoMedZoneTest/1.0 (automated test fixture download)"}
QUERIES = {
    "rostro_frontal": ["frontal face portrait neutral expression", "passport photo face frontal"],
    "rostro_oblicuo": ["three-quarter view portrait face", "portrait 3/4 view head"],
    "rostro_perfil": ["side profile portrait face", "profile view head man", "profile view woman face"],
    "cuello": ["head and shoulders portrait", "neck and shoulders woman portrait"],
    "abdomen_frontal": ["man shirtless torso front standing", "abdomen front view standing person"],
    "abdomen_lateral": ["person standing side view full body", "man standing side view shirtless"],
    "torax": ["sports bra woman front torso", "man chest front shirtless"],
    "espalda": ["man back shirtless standing", "bare back person standing"],
    "gluteos": ["person standing rear view full body", "back view standing man shorts"],
    "piernas": ["legs standing front view", "full body standing front view person shorts"],
    "brazo": ["arm outstretched person", "person raising arm full body"],
    "manos_dorso": ["back of hands", "dorsal hands both"],
    "manos_palma": ["palms of hands", "open palms hands"],
    "pies_dorso": ["bare feet top view", "dorsum of feet"],
    "pies_planta": ["soles of feet", "plantar surface feet"],
    "cuero_cabelludo": ["top of head hair vertex", "bald head top view", "crown of head hair"],
}


def get(url, tries=6):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code != 429 or i == tries - 1:
                raise
            time.sleep(5 * (i + 1))   # demasiadas peticiones: espera creciente


def search(q, n):
    """Openverse: imágenes con licencia libre (Flickr, Commons…); miniatura de Flickr o del propio Openverse."""
    api = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode({"q": q, "page_size": n})
    out = []
    for r in json.loads(get(api)).get("results", []):
        url = r["url"] if "staticflickr.com" in r.get("url", "") else r.get("thumbnail")
        if not url: continue
        out.append({"title": r.get("title"), "url": url, "page": r.get("foreign_landing_url"),
                    "license": f'{r.get("license")} {r.get("license_version") or ""}'.strip(), "artist": r.get("creator")})
    return out


def search_commons(q, n):
    api = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({
        "action": "query", "generator": "search", "gsrnamespace": 6, "gsrsearch": q + " filetype:bitmap", "gsrlimit": n,
        "prop": "imageinfo", "iiprop": "url|extmetadata|mime", "iiurlwidth": 960, "format": "json"})
    pages = json.loads(get(api)).get("query", {}).get("pages", {})
    out = []
    for p in sorted(pages.values(), key=lambda p: p.get("index", 0)):
        ii = (p.get("imageinfo") or [{}])[0]
        if ii.get("mime") not in ("image/jpeg", "image/png"):
            continue
        md = ii.get("extmetadata", {})
        out.append({"title": p["title"], "url": ii.get("thumburl") or ii.get("url"), "page": ii.get("descriptionurl"),
                    "license": md.get("LicenseShortName", {}).get("value"), "artist": md.get("Artist", {}).get("value")})
    return out


def main():
    zones = sys.argv[1:] or list(QUERIES)
    for z in zones:
        d = OUT / z; d.mkdir(parents=True, exist_ok=True)
        if (d / "sources.json").exists():
            print(z, "ya descargada"); continue
        meta = {}
        k = 0
        for q in QUERIES[z]:
            try:
                found = search(q, 12)
            except Exception as e:
                print("búsqueda fallida", q, e); time.sleep(20); continue
            for item in found:
                f = d / f"c{k:02d}.jpg"
                try:
                    f.write_bytes(get(item["url"]))
                    meta[f.name] = item; k += 1
                except Exception as e:
                    print("fallo", item["title"], e)
                time.sleep(2.0)
        (d / "sources.json").write_text(json.dumps(meta, ensure_ascii=False, indent=1))
        print(z, k)


if __name__ == "__main__":
    main()


def add(zone, queries, n=15):
    """Añade imágenes a una zona con otras búsquedas (sin borrar las existentes)."""
    d = OUT / zone; d.mkdir(parents=True, exist_ok=True)
    mf = d / "sources.json"
    meta = json.loads(mf.read_text()) if mf.exists() else {}
    k = 1 + max([int(f.stem[1:]) for f in d.glob("c*.jpg")] + [-1])
    for q in queries:
        for item in search(q, n):
            if any(v.get("url") == item["url"] for v in meta.values()): continue
            f = d / f"c{k:02d}.jpg"
            try:
                f.write_bytes(get(item["url"])); meta[f.name] = item; k += 1
            except Exception as e:
                print("fallo", item["title"], e)
            time.sleep(0.5)
    mf.write_text(json.dumps(meta, ensure_ascii=False, indent=1))
    print(zone, k)
