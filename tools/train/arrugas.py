"""Detección de arrugas: mapa de textura + U-Net propia.
Dependencias: numpy, scipy, Pillow, torch (BSD-3 / MIT-CMU / BSD-3)."""
import os
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image
from scipy import ndimage as ndi

# tamaño de trabajo (1024 como el artículo; menor para pruebas o poca memoria: ARRUGAS_SIZE=512)
SIZE = int(os.environ.get("ARRUGAS_SIZE", "1024"))


# ---------- 1. Mapa de textura (etiqueta débil) ----------
def texture_map(rgb: np.ndarray, sigma: float = 5.0) -> np.ndarray:
    """T = (1 - I / (1 + I_G)) * 255, recortado a [0, 255].
    truncate=2.0 con sigma=5 da un núcleo de 21x21."""
    gray = rgb.astype(np.float32) @ np.array([0.299, 0.587, 0.114], np.float32)
    blur = ndi.gaussian_filter(gray, sigma=sigma, truncate=2.0)
    t = (1.0 - gray / (1.0 + blur)) * 255.0
    return np.clip(t, 0, 255).astype(np.uint8)


# ---------- 2. Máscara de piel (sin modelos preentrenados) ----------
def skin_mask(rgb: np.ndarray, small_hole: float = 0.004) -> np.ndarray:
    """Piel por umbrales YCrCb + mayor componente conexa centrada.
    Rellena agujeros pequeños (poros, sombras) y deja abiertos ojos y boca."""
    r, g, b = [rgb[..., i].astype(np.float32) for i in range(3)]
    cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
    cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
    m = (cr >= 135) & (cr <= 185) & (cb >= 75) & (cb <= 130)
    m = ndi.binary_opening(m, iterations=3)
    lab, n = ndi.label(m)
    if n == 0:
        return m
    h, w = m.shape
    c = lab[h // 4: 3 * h // 4, w // 4: 3 * w // 4]  # la cara está centrada
    ids, cnt = np.unique(c[c > 0], return_counts=True)
    m = lab == (ids[np.argmax(cnt)] if len(ids) else 1)
    holes, nh = ndi.label(ndi.binary_fill_holes(m) & ~m)
    if nh:
        area = ndi.sum(np.ones_like(m), holes, range(1, nh + 1))
        m |= np.isin(holes, 1 + np.flatnonzero(area < small_hole * m.size))
    return ndi.binary_erosion(m, iterations=4)  # aparta bordes de pelo y fondo


def prepare(path: str):
    """Devuelve (rgb_enmascarada, textura_enmascarada), ambas SIZE x SIZE uint8.
    Si existe `<foto>_piel.png` junto a la foto (p. ej. de MediaPipe, ver preparar_mediapipe.py), se usa
    como máscara de la cara en lugar de los umbrales de color (más robusta con fondos beige o piel rojiza)."""
    rgb = np.asarray(Image.open(path).convert("RGB").resize((SIZE, SIZE), Image.BILINEAR))
    piel = Path(path).with_name(Path(path).stem + "_piel.png")
    if piel.exists():
        m = np.asarray(Image.open(piel).convert("L").resize((SIZE, SIZE), Image.NEAREST)) > 127
    else:
        m = skin_mask(rgb)
    tex = texture_map(rgb) * m
    return (rgb * m[..., None]).astype(np.uint8), tex.astype(np.uint8)


def to_tensor(rgb: np.ndarray, tex: np.ndarray | None = None) -> torch.Tensor:
    """[0,255] -> [-1,1]; 3 canales (etapa 1) o 4 canales (etapa 2)."""
    x = rgb.transpose(2, 0, 1).astype(np.float32)
    if tex is not None:
        x = np.concatenate([x, tex[None].astype(np.float32)])
    return torch.from_numpy(x / 127.5 - 1.0)


# ---------- 3. U-Net propia ----------
def block(i, o):
    return nn.Sequential(
        nn.Conv2d(i, o, 3, padding=1, bias=False), nn.BatchNorm2d(o), nn.ReLU(inplace=True),
        nn.Conv2d(o, o, 3, padding=1, bias=False), nn.BatchNorm2d(o), nn.ReLU(inplace=True))


class UNet(nn.Module):
    def __init__(self, in_ch: int, out_ch: int, w: int = 32):
        super().__init__()
        c = [w, w * 2, w * 4, w * 8, w * 16]
        self.enc = nn.ModuleList([block(in_ch, c[0])] + [block(c[k], c[k + 1]) for k in range(4)])
        self.dec = nn.ModuleList([block(c[k + 1] + c[k], c[k]) for k in reversed(range(4))])
        self.head = nn.Conv2d(c[0], out_ch, 1)

    def forward(self, x):
        skips = []
        for k, e in enumerate(self.enc):
            x = e(x if k == 0 else F.max_pool2d(x, 2))
            skips.append(x)
        skips.pop()
        for d in self.dec:
            x = F.interpolate(x, scale_factor=2, mode="bilinear", align_corners=False)
            x = d(torch.cat([x, skips.pop()], 1))
        return self.head(x)


# ---------- 4. Métricas (clase arruga; la exactitud no sirve con tan pocos píxeles) ----------
def scores(pred: np.ndarray, gt: np.ndarray):
    """Jaccard (JSI) y F1 píxel a píxel, como el artículo. pred y gt booleanos."""
    tp = np.logical_and(pred, gt).sum(); fp = np.logical_and(pred, ~gt).sum(); fn = np.logical_and(~pred, gt).sum()
    return tp / max(tp + fp + fn, 1), 2 * tp / max(2 * tp + fp + fn, 1)


def load_mask(path: Path) -> np.ndarray:
    """Máscara a SIZE x SIZE sin perder las líneas finas: vecino más cercano (la bilineal + umbral
    borraba arrugas de 1–3 px al reducir)."""
    return np.asarray(Image.open(path).convert("L").resize((SIZE, SIZE), Image.NEAREST)) > 127


# ---------- 5. Pérdidas y transferencia de pesos ----------
def soft_dice_loss(logits, target, eps=1.0):
    """logits: B x 2 x H x W; target: B x H x W con 0/1."""
    p = logits.softmax(1)[:, 1]
    t = target.float()
    inter = (p * t).sum((1, 2))
    return (1 - (2 * inter + eps) / (p.sum((1, 2)) + t.sum((1, 2)) + eps)).mean()


def stage1_to_stage2(net1: UNet, w: int = 32) -> UNet:
    """Copia los pesos de la etapa 1 (3->1) a una red 4->2.
    El 4.º canal de entrada empieza en cero y la cabeza se reinicia."""
    net2 = UNet(4, 2, w)
    sd = {k: v for k, v in net1.state_dict().items() if not k.startswith("head")}
    w0 = sd["enc.0.0.weight"]
    sd["enc.0.0.weight"] = torch.cat([w0, torch.zeros_like(w0[:, :1])], 1)
    net2.load_state_dict(sd, strict=False)
    return net2


@torch.inference_mode()
def predict(net: UNet, path: str) -> Image.Image:
    net.eval()
    rgb, tex = prepare(path)
    logits = net(to_tensor(rgb, tex)[None])
    return Image.fromarray((logits.argmax(1)[0].numpy() * 255).astype(np.uint8))
