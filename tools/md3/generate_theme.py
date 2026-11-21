#!/usr/bin/env python3
"""Genera app/src/main/java/com/photomed/app/ui/theme/Md3Tokens.kt a partir de tokens.json.

tokens.json son los tokens oficiales de Material Design 3 publicados por Google en Material Web
(github.com/material-components/material-web, paquete @material/web, tokens v0_192; especificación en
m3.material.io). Las paletas de color se sustituyen por las del logo (brand_palette.py). Uso:

    python3 tools/md3/generate_theme.py [ruta/tokens.json]
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "tools/md3/tokens.json"
OUT = ROOT / "app/src/main/java/com/photomed/app/ui/theme/Md3Tokens.kt"

t = json.loads(SRC.read_text())


def camel(name: str) -> str:
    parts = re.split(r"[-_]", name)
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def hex_color(v: str) -> str:
    v = v.lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    return f"Color(0xFF{v.upper()})"


def rem(v: str) -> float:
    return float(v.replace("rem", "")) * 16


def num(x: float) -> str:
    s = f"{x:.4f}".rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


palette = {k: v for k, v in t["palette"].items() if k not in ("black", "white")}
# colores de la marca (logo): primario, secundario, terciario y neutros; el error queda el de M3
from brand_palette import palettes as brand_palettes  # noqa: E402
palette.update(brand_palettes())
weights = {
    "var(--md-ref-typeface-weight-regular)": "FontWeight.W" + t["typeface"]["weight-regular"],
    "var(--md-ref-typeface-weight-medium)": "FontWeight.W" + t["typeface"]["weight-medium"],
    "var(--md-ref-typeface-weight-bold)": "FontWeight.W" + t["typeface"]["weight-bold"],
}

L = []
w = L.append
w("// ARCHIVO GENERADO por tools/md3/generate_theme.py — no editar a mano.")
w(f"// Fuente: tokens oficiales de Material Design 3, Material Web ({t.get('source', '')}).")
w("// Paletas de color: las del logo (tools/md3/brand_palette.py).")
w("@file:Suppress(\"unused\", \"MagicNumber\")")
w("")
w("package com.photomed.app.ui.theme")
w("")
w("import androidx.compose.animation.core.CubicBezierEasing")
w("import androidx.compose.foundation.shape.RoundedCornerShape")
w("import androidx.compose.material3.ColorScheme")
w("import androidx.compose.material3.Shapes")
w("import androidx.compose.material3.Typography")
w("import androidx.compose.material3.darkColorScheme")
w("import androidx.compose.material3.lightColorScheme")
w("import androidx.compose.ui.graphics.Color")
w("import androidx.compose.ui.text.TextStyle")
w("import androidx.compose.ui.text.font.FontFamily")
w("import androidx.compose.ui.text.font.FontWeight")
w("import androidx.compose.ui.unit.dp")
w("import androidx.compose.ui.unit.sp")
w("")
w("/** md.ref.palette: paletas tonales de referencia. */")
w("object Md3Palette {")
for k in sorted(palette, key=lambda s: (re.sub(r"\d+$", "", s), int(re.search(r"\d+$", s).group()))):
    w(f"    val {camel(k)} = {hex_color(palette[k])}")
w("}")
w("")

roles = t["sys_color"]
compose_roles = {
    "primary", "on-primary", "primary-container", "on-primary-container", "inverse-primary",
    "secondary", "on-secondary", "secondary-container", "on-secondary-container",
    "tertiary", "on-tertiary", "tertiary-container", "on-tertiary-container",
    "background", "on-background", "surface", "on-surface", "surface-variant", "on-surface-variant",
    "surface-tint", "inverse-surface", "inverse-on-surface", "error", "on-error", "error-container",
    "on-error-container", "outline", "outline-variant", "scrim", "surface-bright", "surface-container",
    "surface-container-high", "surface-container-highest", "surface-container-low",
    "surface-container-lowest", "surface-dim",
}
for mode, fn in (("light", "lightColorScheme"), ("dark", "darkColorScheme")):
    w(f"/** md.sys.color ({mode}). */")
    w(f"val Md3{mode.capitalize()}ColorScheme: ColorScheme = {fn}(")
    for r in sorted(roles):
        if r in compose_roles:
            w(f"    {camel(r)} = Md3Palette.{camel(roles[r][mode])},")
    w(")")
    w("")

extra = sorted(set(roles) - compose_roles)
w("/** Roles de color de M3 que ColorScheme de Compose aún no expone (fixed, shadow). */")
w("data class Md3ExtendedColors(")
for r in extra:
    w(f"    val {camel(r)}: Color,")
w(")")
w("")
for mode in ("light", "dark"):
    w(f"val Md3{mode.capitalize()}ExtendedColors = Md3ExtendedColors(")
    for r in extra:
        w(f"    {camel(r)} = Md3Palette.{camel(roles[mode and r][mode])},")
    w(")")
    w("")

ts = t["typescale"]
styles = sorted({k.rsplit("-", 1)[0].replace("-line", "") for k in ts if k.endswith("-size")})
w("/** md.sys.typescale (tipografía Roboto del sistema). */")
w("val Md3Typography = Typography(")
for s in ["display-large", "display-medium", "display-small", "headline-large", "headline-medium",
          "headline-small", "title-large", "title-medium", "title-small", "body-large", "body-medium",
          "body-small", "label-large", "label-medium", "label-small"]:
    w(f"    {camel(s)} = TextStyle(")
    w("        fontFamily = FontFamily.Default,")
    w(f"        fontWeight = {weights[ts[s + '-weight']]},")
    w(f"        fontSize = {num(rem(ts[s + '-size']))}.sp,")
    w(f"        lineHeight = {num(rem(ts[s + '-line-height']))}.sp,")
    w(f"        letterSpacing = {num(rem(ts[s + '-tracking']))}.sp,")
    w("    ),")
w(")")
w("")

shape = t["sys"]["shape"]
px = lambda v: num(float(v.replace("px", "")))
w("/** md.sys.shape. */")
w("object Md3Corner {")
for k in ["corner-none", "corner-extra-small", "corner-small", "corner-medium", "corner-large", "corner-extra-large"]:
    w(f"    val {camel(k.replace('corner-', ''))} = {px(shape[k])}.dp")
w("}")
w("")
w("val Md3Shapes = Shapes(")
for k in ["extra-small", "small", "medium", "large", "extra-large"]:
    w(f"    {camel(k)} = RoundedCornerShape(Md3Corner.{camel(k)}),")
w(")")
w("")

w("/** md.sys.elevation (dp). */")
w("object Md3Elevation {")
for k, v in sorted(t["sys"]["elevation"].items()):
    w(f"    val {camel(k)} = {num(float(v))}.dp")
w("}")
w("")

w("/** md.sys.motion: duraciones (ms) y curvas de easing. */")
w("object Md3Motion {")
for k, v in t["sys"]["motion"].items():
    if k.startswith("duration"):
        w(f"    const val {camel(k)} = {int(v.replace('ms', ''))}")
for k, v in t["sys"]["motion"].items():
    if k.startswith("easing"):
        a = re.findall(r"[\d.]+", v)
        w(f"    val {camel(k)} = CubicBezierEasing({', '.join(x + 'f' for x in a)})")
w("}")
w("")

w("/** md.sys.state: opacidades de las capas de estado. */")
w("object Md3State {")
for k, v in t["sys"]["state"].items():
    w(f"    const val {camel(k.replace('-state-layer-opacity', ''))} = {v}f")
w("}")
w("")
w("/** Retícula base de 4/8 dp, objetivo táctil (48 dp) y separación mínima (8 dp). */")
w("object Md3Spacing {")
for i, v in [(0, 0), (1, 4), (2, 8), (3, 12), (4, 16), (5, 20), (6, 24), (7, 28), (8, 32), (10, 40), (12, 48), (14, 56), (16, 64)]:
    w(f"    val s{i} = {v}.dp")
w("    val touchTarget = 48.dp")
w("    val touchTargetGap = 8.dp")
w("    /** Margen de ventana compacta (< 600 dp) y media/expandida. */")
w("    val marginCompact = 16.dp")
w("    val marginMedium = 24.dp")
w("}")

OUT.write_text("\n".join(L) + "\n")
print(f"Escrito {OUT.relative_to(ROOT)} ({len(L)} líneas)")
