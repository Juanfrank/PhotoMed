#!/usr/bin/env python3
"""Extrae los textos traducibles del código a recursos Android.

Busca `tr("…")` y `trn("…")` (literales simples, o varios unidos con `+`) en app/src/main/java y
escribe:
  app/src/main/res/values/strings_i18n.xml          texto de origen (español)
  app/src/main/res/values-<idioma>/strings_i18n.xml traducciones de tools/i18n/<idioma>/*.json
El nombre de cada recurso es el de I18n.key (FNV-1a de 64 bits del texto). Comprueba que cada
traducción tenga los mismos marcadores de formato (%1$s, %d…) que el original.

Uso: python3 tools/i18n/extract.py [--check]   (--check: falla si falta alguna traducción)
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/java"
RES = ROOT / "app/src/main/res"
CATALOGS = ROOT / "tools/i18n"

CALL = re.compile(r'\btrn?\(\s*"')
FMT = re.compile(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXcbeEgGoh%]')


def fnv_key(s: str) -> str:
    h = 0xcbf29ce484222325
    for b in s.encode("utf-8"):
        h ^= b
        h = (h * 0x100000001b3) & 0xFFFFFFFFFFFFFFFF
    return "t_" + f"{h:016x}"[-12:]


def read_literal(text: str, i: int):
    """Lee un literal de Kotlin que empieza en text[i] == '"'. Devuelve (valor, índice tras la comilla)."""
    assert text[i] == '"'
    if text.startswith('"""', i):
        raise ValueError("cadena en bruto (\"\"\") no admitida")
    i += 1
    out = []
    while True:
        c = text[i]
        if c == '"':
            return "".join(out), i + 1
        if c == "\\":
            n = text[i + 1]
            if n == "u":
                out.append(chr(int(text[i + 2:i + 6], 16))); i += 6; continue
            out.append({"n": "\n", "t": "\t", "r": "\r", "b": "\b", '"': '"', "'": "'", "\\": "\\", "$": "$"}[n]); i += 2; continue
        if c == "$" and (text[i + 1] == "{" or text[i + 1].isalpha() or text[i + 1] == "_"):
            raise ValueError("plantilla $ dentro de tr(): use %1$s y argumentos")
        if c == "\n":
            raise ValueError("literal sin cerrar")
        out.append(c); i += 1


def scan():
    found, errors = {}, []
    for f in sorted(SRC.rglob("*.kt")):
        text = f.read_text(encoding="utf-8")
        for m in CALL.finditer(text):
            i = m.end() - 1
            parts = []
            try:
                while True:
                    v, i = read_literal(text, i)
                    parts.append(v)
                    j = i
                    while j < len(text) and text[j] in " \t\r\n": j += 1
                    if j < len(text) and text[j] == "+":
                        j += 1
                        while j < len(text) and text[j] in " \t\r\n": j += 1
                        if j < len(text) and text[j] == '"':
                            i = j; continue
                    break
            except (ValueError, KeyError, IndexError) as e:
                line = text.count("\n", 0, m.start()) + 1
                errors.append(f"{f.relative_to(ROOT)}:{line}: {e}")
                continue
            s = "".join(parts)
            if s.strip():
                found.setdefault(s, f"{f.relative_to(ROOT)}:{text.count(chr(10), 0, m.start()) + 1}")
    return found, errors


def xml_escape(s: str) -> str:
    s = s.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t")
    if s[:1] in ("@", "?"): s = "\\" + s
    # espacios al principio o al final, o dobles: Android los quita salvo entre comillas
    raw_ws = s != s.strip() or "  " in s
    return f'"{s}"' if raw_ws else s


def write(path: pathlib.Path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- ARCHIVO GENERADO por tools/i18n/extract.py: no editar a mano. -->",
             '<resources xmlns:tools="http://schemas.android.com/tools" tools:ignore="MissingTranslation,UnusedResources,Typos">']
    for k, v in entries:
        lines.append(f'    <string name="{k}" formatted="false">{xml_escape(v)}</string>')
    lines.append("</resources>")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    check = "--check" in sys.argv
    found, errors = scan()
    keys = {}
    for s in found:
        k = fnv_key(s)
        if k in keys and keys[k] != s:
            errors.append(f"colisión de clave {k}: {keys[k]!r} / {s!r}")
        keys[k] = s
    write(RES / "values/strings_i18n.xml", sorted(((fnv_key(s), s) for s in found), key=lambda e: e[0]))
    status = 0
    for lang_dir in sorted(p for p in CATALOGS.iterdir() if p.is_dir()):
        cat = {}
        for j in sorted(lang_dir.glob("*.json")):
            try:
                cat.update(json.loads(j.read_text(encoding="utf-8")))
            except json.JSONDecodeError as e:
                errors.append(f"{j.relative_to(ROOT)}: JSON no válido: {e}")
        out, missing = [], []
        for s in found:
            t = cat.get(s)
            if t is None:
                missing.append(s); continue
            if sorted(FMT.findall(s)) != sorted(FMT.findall(t)):
                errors.append(f"{lang_dir.name}: marcadores distintos en {s!r} → {t!r}")
                continue
            out.append((fnv_key(s), t))
        write(RES / f"values-{lang_dir.name}/strings_i18n.xml", sorted(out, key=lambda e: e[0]))
        unused = len(set(cat) - set(found))
        print(f"{lang_dir.name}: {len(out)}/{len(found)} traducidos, {len(missing)} sin traducir, {unused} sin uso en el código")
        if missing:
            for s in missing[:40]: print(f"  sin traducir: {found[s]}: {s!r}")
            if check: status = 1
    for e in errors: print("ERROR", e)
    print(f"{len(found)} textos traducibles")
    return 1 if errors else status


if __name__ == "__main__":
    sys.exit(main())
