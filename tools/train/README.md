# Replicar la detección de arrugas de FFHQ-Wrinkle sin licencias más exigentes que MIT

Se reimplementa el método del artículo de Moon, Chung y Jang (ICPR 2024): primero se preentrena la red con mapas de textura y luego se ajusta con máscaras manuales. **No se reutiliza nada** de lo que en ese repositorio trae licencia restrictiva. Las ideas y la fórmula se reimplementan desde cero; los datos y los pesos se generan por cuenta propia.

> No es asesoría legal. Confirme las licencias vigentes antes de distribuir.

## 1. Qué se evita y con qué se reemplaza

| Componente del repo original | Licencia | Reemplazo | Licencia del reemplazo |
| --- | --- | --- | --- |
| Imágenes FFHQ y máscaras FFHQ-Wrinkle | CC BY-NC-SA 4.0 (no comercial, compartir igual) | Fotos propias con consentimiento, o fotos CC0 / dominio público | Propia / CC0 |
| Pesos preentrenados `stage1_*.pth` / `stage2_*.pth` | Entrenados con datos CC BY-NC-SA | Entrenar los pesos propios (`entrenar.py`) | Propia |
| U-Net (`unet_model.py`, derivada de Pytorch-UNet) | GPLv3 | U-Net escrita desde cero (`arrugas.py`) | Propia (MIT, si se desea) |
| Swin UNETR (derivada de MONAI) | Apache 2.0 | No se usa; basta con la U-Net | — |
| face-parsing.PyTorch (pesos entrenados con CelebAMask-HQ) | Datos solo para investigación no comercial | Máscara de piel por color YCrCb, o polígono de la cara dibujado en CVAT | Propia |
| `gdown`, scripts .sh de descarga | — | Innecesarios | — |

**Dependencias permitidas:** Python, NumPy (BSD-3), SciPy (BSD-3), Pillow (MIT-CMU), PyTorch (BSD-3). Para anotar se usa CVAT (MIT); la licencia de la herramienta no se transmite a las etiquetas que se crean con ella.

> OpenCV ≥ 4.5 y MediaPipe usan Apache 2.0. Por eso el código usa solo SciPy.

## 2. Procedimiento

1. **Reunir fotos.** Junte unas 1.000+ fotos de rostro frontal sin etiquetar (más es mejor) para la etapa 1. Para la etapa 2 basta un subconjunto de 100 a 1.000. Recorte cada foto cuadrada y centrada en la cara, con tamaño final de 1024×1024.
2. **Anotar arrugas (etapa 2).** En CVAT dibuje cada arruga como polilínea o pincel (frente, patas de gallo, surcos nasogenianos). Exporte las máscaras en PNG con el mismo nombre que la foto: 0 = fondo, 255 = arruga. Si es posible, use 2–3 anotadores y conserve un píxel solo si lo marcó la mayoría.
3. **Mapa de textura (etiqueta débil).** Es automático y está en `arrugas.texture_map`. La foto se pasa a escala de grises *I*, se suaviza con un filtro gaussiano *I_G* (σ = 5, núcleo de 21×21) y se calcula:

   ```
   T = clip( (1 − I / (1 + I_G)) × 255 , 0, 255 )
   ```

   Un píxel más oscuro que su entorno, como un surco, produce un valor alto de T.
4. **Máscara facial.** `arrugas.skin_mask` usa umbrales de piel en YCrCb y se queda con la componente conexa central. Se aplica a la foto y al mapa de textura.
5. **Etapa 1: preentrenamiento.** Ejecute `python entrenar.py etapa1 fotos/`. La U-Net aprende a producir el mapa de textura (1 canal) a partir de la foto RGB (3 canales), con pérdida MSE.
6. **Etapa 2: ajuste fino.** Ejecute `python entrenar.py etapa2 fotos_etiquetadas/ mascaras/`. Los pesos de la etapa 1 se copian a una red de 4 canales a 2 clases. El canal de textura empieza en cero y la pérdida es Soft Dice.
7. **Inferencia.** Ejecute `python entrenar.py predecir etapa2.pt foto.png`. Genera `foto_mask.png`, con argmax por píxel: blanco = arruga.

## 3. Hiperparámetros de partida (del artículo)

| | Etapa 1 | Etapa 2 |
| --- | --- | --- |
| Entrada → salida | RGB → textura | RGB + textura → fondo/arruga |
| Pérdida | MSE | Soft Dice |
| Épocas | 300 | 150 |
| LR máx. | 1e-3 | 1e-4 |
| Optimizador | AdamW, decaimiento 0,05, reinicios coseno | igual |
| Tamaño | 1024×1024 | 1024×1024 |

Para evaluar, separe 80/10/10 y mida el índice de Jaccard (IoU) y F1 sobre la clase arruga. La exactitud no sirve, porque las arrugas ocupan muy pocos píxeles.

## 4. Limitaciones conocidas

- **La máscara de piel por color es frágil.** Falla con fondos beige, piel muy clara o rojiza e iluminación fuerte. Si eso molesta, dibuje en CVAT también un polígono "cara" y entrene una segunda U-Net (`UNet(3, 2)`) para segmentarla. Otra opción es omitir el enmascarado y dejar que la red aprenda a ignorar el fondo.
- **Sin alineación facial automática.** Los detectores de puntos faciales habituales traen pesos o datos no comerciales, o usan Apache 2.0. Recorte las fotos a mano o con un recorte centrado fijo.
- **Datos personales.** Las fotos de rostros son datos personales. Obtenga consentimiento y cumpla la ley aplicable (en RD, la Ley 172-13).
- **Resultados de referencia.** El artículo reporta JSI ≈ 0,48 con U-Net y 1.000 etiquetas. Con menos datos o datos distintos, el resultado propio puede quedar por debajo.

## Archivos

- `arrugas.py`: mapa de textura, máscara de piel, U-Net, pérdida Dice, transferencia de pesos de la etapa 1 a la 2 y predicción.
- `entrenar.py`: entrenamiento de las etapas 1 y 2 e inferencia por línea de comandos.

El código usa solo las dependencias de la sección 1 y se probó con PyTorch 2.x en CPU.

## Cambios al incorporarlo en PhotoMed (noviembre de 2026)

Revisado y probado de punta a punta (prueba de humo en CPU: etapa 1 → etapa 2 con validación →
evaluar → predecir → exportar). Cambios respecto a la versión recibida:

| Cambio | Por qué |
|---|---|
| Máscaras redimensionadas con vecino más cercano (`load_mask`) | Con la bilineal por defecto + umbral > 127, las arrugas de 1–3 px desaparecían al reducir. |
| Validación aparte (`VALIDACION`, 10 % por defecto) con JSI y F1 por época; se guarda el mejor `etapa2.pt` | Antes se guardaba el de la última época y solo se veía la pérdida del último lote. |
| Comando `evaluar` (JSI y F1 acumulados, como el artículo) | Comparable con `docs/BENCHMARK.md`. |
| Comando `exportar` a ONNX (opset 17) | Para el teléfono con ONNX Runtime Mobile (licencia MIT). |
| `ARRUGAS_SIZE`, `EPOCAS1`, `EPOCAS2` por variables de entorno | Pruebas rápidas y entrenar con menos memoria. |
| Máscara de piel externa opcional (`<foto>_piel.png`) y `preparar_mediapipe.py` | Recorte centrado en la cara y piel con los modelos que ya usa la app (MediaPipe, Apache 2.0: uso comercial permitido). Resuelve las dos limitaciones de la sección 4 (máscara por color frágil y sin alineación). Es opcional: sin el archivo se usa la máscara por color. |
| Solo las fotos con máscara en la etapa 2 | Antes fallaba si alguna foto no tenía máscara. |

### Pendiente antes de llevarlo al teléfono

- **Tamaño del modelo**: con `w = 32` son 7,8 M de parámetros (31 MB en float32) y una entrada de
  1024 × 1024: demasiado para un teléfono. Recomendado: `w = 16` (≈ 2 M, 8 MB; 2 MB en int8) y entrada
  del recorte de la cara a 512 px (la app ya recorta la cara a ese orden de tamaño).
- **Entrenar con GPU**: 300 + 150 épocas a 1024 px con 1 000 fotos no son viables en CPU (el artículo
  usó RTX A6000).
- **Datos propios**: fotos clínicas con consentimiento y 2–3 anotadores (voto mayoritario por píxel).
  FFHQ-Wrinkle solo para evaluar (licencia no comercial); los pesos de la prueba de humo se descartaron.
- **Integración en la app**: cargar el ONNX (o TFLite) como recurso opcional y usar su mapa de
  probabilidad para generar y puntuar los candidatos de `WrinkleField` (la estructura ya existe).
