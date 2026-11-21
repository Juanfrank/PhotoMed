"""Entrenamiento en dos etapas. Uso:
  python entrenar.py etapa1 fotos/                       -> etapa1.pt
  python entrenar.py etapa2 fotos_etiquetadas/ mascaras/ -> etapa2.pt (el mejor en validación)
  python entrenar.py predecir etapa2.pt foto.png         -> foto_mask.png
  python entrenar.py evaluar etapa2.pt fotos/ mascaras/  -> JSI y F1 (métricas del artículo)
  python entrenar.py exportar etapa2.pt modelo.onnx      -> ONNX para el teléfono (ONNX Runtime, MIT)
Variables: ARRUGAS_SIZE (1024), EPOCAS1 (300), EPOCAS2 (150), VALIDACION (0.1).
"""
import os
import sys
from pathlib import Path

import numpy as np
import torch
from PIL import Image
from torch.utils.data import DataLoader, Dataset

import arrugas as A

DEV = "cuda" if torch.cuda.is_available() else "cpu"


class Fotos(Dataset):
    """Etapa 1: (RGB, textura). Etapa 2: (RGB+textura, máscara manual)."""

    def __init__(self, img_dir, mask_dir=None):
        # sin las máscaras de piel auxiliares (*_piel.png)
        self.imgs = [p for p in sorted(Path(img_dir).glob("*.png")) + sorted(Path(img_dir).glob("*.jpg")) if not p.stem.endswith("_piel")]
        if mask_dir:  # solo las fotos que tienen máscara
            self.imgs = [p for p in self.imgs if (Path(mask_dir) / (p.stem + ".png")).exists()]
        self.mask_dir = Path(mask_dir) if mask_dir else None

    def __len__(self):
        return len(self.imgs)

    def __getitem__(self, i):
        rgb, tex = A.prepare(str(self.imgs[i]))
        if self.mask_dir is None:
            return A.to_tensor(rgb), torch.from_numpy(tex[None] / 127.5 - 1.0).float()
        m = A.load_mask(self.mask_dir / (self.imgs[i].stem + ".png"))
        return A.to_tensor(rgb, tex), torch.from_numpy(m).long()


@torch.inference_mode()
def evaluate(net, data):
    """JSI y F1 de la clase arruga sobre un conjunto (acumulados, como el artículo)."""
    net.eval(); tp = fp = fn = 0
    for x, y in DataLoader(data, batch_size=1):
        p = net(x.to(DEV)).argmax(1).cpu().numpy().astype(bool); g = y.numpy().astype(bool)
        tp += np.logical_and(p, g).sum(); fp += np.logical_and(p, ~g).sum(); fn += np.logical_and(~p, g).sum()
    net.train()
    return tp / max(tp + fp + fn, 1), 2 * tp / max(2 * tp + fp + fn, 1)


def fit(net, data, loss_fn, epochs, lr, batch=2, val=None, out=None):
    """Con [val] (etapa 2): JSI / F1 de validación en cada época y se guarda el mejor en [out]."""
    net.to(DEV).train()
    opt = torch.optim.AdamW(net.parameters(), lr=lr, weight_decay=0.05)
    sched = torch.optim.lr_scheduler.CosineAnnealingWarmRestarts(opt, T_0=max(1, epochs // 3))
    dl = DataLoader(data, batch_size=batch, shuffle=True, num_workers=2)
    best = -1.0
    for ep in range(epochs):
        for x, y in dl:
            x, y = x.to(DEV), y.to(DEV)
            # aumento simple: volteo horizontal
            if torch.rand(1) < 0.5:
                x, y = x.flip(-1), y.flip(-1)
            loss = loss_fn(net(x), y)
            opt.zero_grad()
            loss.backward()
            opt.step()
        sched.step()
        msg = f"época {ep + 1}/{epochs}  pérdida {loss.item():.4f}"
        if val is not None and len(val):
            jsi, f1 = evaluate(net, val)
            msg += f"  validación JSI {jsi:.4f}  F1 {f1:.4f}"
            if jsi > best and out:
                best = jsi; torch.save(net.state_dict(), out); msg += "  (mejor)"
        print(msg)
    return net


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "etapa1":
        net = fit(A.UNet(3, 1), Fotos(sys.argv[2]), torch.nn.functional.mse_loss, epochs=int(os.environ.get("EPOCAS1", 300)), lr=1e-3)
        torch.save(net.state_dict(), "etapa1.pt")
    elif cmd == "etapa2":
        net1 = A.UNet(3, 1)
        net1.load_state_dict(torch.load("etapa1.pt", map_location="cpu"))
        data = Fotos(sys.argv[2], sys.argv[3])
        # validación aparte (por defecto 10 %; el artículo separa 80 / 10 / 10)
        nv = max(1, int(len(data) * float(os.environ.get("VALIDACION", 0.1)))) if len(data) > 1 else 0
        g = torch.Generator().manual_seed(7)
        tr, va = torch.utils.data.random_split(data, [len(data) - nv, nv], generator=g) if nv else (data, None)
        net = fit(A.stage1_to_stage2(net1), tr, A.soft_dice_loss, epochs=int(os.environ.get("EPOCAS2", 150)), lr=1e-4,
                  val=va, out="etapa2.pt")
        if va is None: torch.save(net.state_dict(), "etapa2.pt")
    elif cmd == "evaluar":
        net = A.UNet(4, 2).to(DEV)
        net.load_state_dict(torch.load(sys.argv[2], map_location="cpu"))
        jsi, f1 = evaluate(net, Fotos(sys.argv[3], sys.argv[4]))
        print(f"JSI {jsi:.4f}  F1 {f1:.4f}")
    elif cmd == "exportar":
        net = A.UNet(4, 2)
        net.load_state_dict(torch.load(sys.argv[2], map_location="cpu")); net.eval()
        torch.onnx.export(net, torch.zeros(1, 4, A.SIZE, A.SIZE), sys.argv[3], input_names=["rgb_textura"],
                          output_names=["logits"], opset_version=17)
        print("exportado", sys.argv[3])
    elif cmd == "predecir":
        net = A.UNet(4, 2)
        net.load_state_dict(torch.load(sys.argv[2], map_location="cpu"))
        out = Path(sys.argv[3]).with_name(Path(sys.argv[3]).stem + "_mask.png")
        A.predict(net, sys.argv[3]).save(out)
        print("guardado", out)
