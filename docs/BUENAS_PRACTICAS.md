# Buenas prácticas, bibliotecas y bibliografía aplicadas

Cada referencia indica **dónde se aplica** en PhotoMed.

## Fotografía clínica estandarizada

| Referencia | Recomendación | Aplicación en PhotoMed |
|---|---|---|
| DiBernardo BE, Adams RL, Krause J, Fiorillo MA, Gheradini G. *Photographic standards in plastic surgery.* Plast Reconstr Surg 1998;102(2):559-68. | Vistas estándar (frontal, oblicuas 45°, perfiles), mismo encuadre, fondo, luz y distancia entre sesiones. | 22 zonas estandarizadas con guía, superposición de la foto anterior y coincidencia en vivo; **estandarización de la primera toma** por la pose de la cabeza medida (0°/45°/perfil). |
| Yavuzer R, Smirnes S, Jackson IT. *Guidelines for standard photography in plastic surgery.* Ann Plast Surg 2001;46(3):293-300. | Reproducibilidad: posición del paciente y de la cámara, plano de Frankfort horizontal, fondo uniforme. | Línea de Frankfort en perfiles, nivel, pose de la cabeza (giro / cabeceo ≤ 6°) como condición de coincidencia; Frankfort (cabeceo ≤ 7°) y cabeza recta en la estandarización; **control de iluminación** (mejillas ±15 %, exposición). |
| American Society of Plastic Surgeons, *Photographic guidelines* (plasticsurgery.org). | Vistas y encuadres por región corporal; consentimiento del paciente. | Encuadres corporales (abdomen frontal/lateral con brazos elevados, espalda, glúteos, muslos, manos, pies) y registro de consentimiento. |
| Ward B, Ward M, Fried O, Paskhover B. *Nasal distortion in short-distance photographs: the selfie effect.* JAMA Facial Plast Surg 2018 (doi:10.1001/jamafacial.2018.0009). | A ~30 cm la nariz parece ~30 % mayor; a 1,5 m la distorsión es despreciable. | Distancia estimada con la distancia interpupilar (≈ 6,3 cm) y la focal; aviso “aléjese ≥ 1 m y use zoom”; **zoom asistido** para trabajar a distancia sin perder el encuadre. |
| Farkas LG. *Anthropometry of the Head and Face.* Raven Press, 1994. | Proporciones faciales (tercios, quintos, distancia interpupilar). | Líneas de referencia de la guía y adaptación a la anatomía de la foto de referencia. |

## Puntos anatómicos y pose

| Biblioteca / artículo | Aplicación |
|---|---|
| **Google ML Kit** Face Detection (contornos de 133 puntos, ángulos de Euler) y Pose Detection (33 puntos, BlazePose). | Detector en el dispositivo; la pose de la cabeza (guiñada/cabeceo) se exige igual a la de la referencia. |
| Bazarevsky V et al. *BlazePose: On-device real-time body pose tracking.* arXiv:2006.10204 (2020). | Base del detector corporal (hombros, caderas, rodillas, manos, pies). |
| Kartynnik Y et al. *Real-time facial surface geometry from monocular video on mobile GPUs.* arXiv:1907.06724 (2019) — **MediaPipe Face Landmarker** (478 puntos 3D + matriz de pose, Apache 2.0). | **Segundo motor seleccionable** en la app y detector de la evaluación con imágenes reales (`tools/eval`). |
| Huang GB et al. *Labeled Faces in the Wild.* UMass Tech. Rep. 07-49 (2007). | Banco de pruebas de falsos positivos / negativos (misma persona, otra persona, mismo encuadre). |

## Registro (alineación) de imágenes

| Artículo | Aplicación |
|---|---|
| Umeyama S. *Least-squares estimation of transformation parameters between two point patterns.* IEEE TPAMI 1991;13(4):376-380. | Semejanza entre puntos (forma cerrada) con rechazo de atípicos. |
| Tzimiropoulos G, Argyriou V, Zafeiriou S, Stathaki T. *Robust FFT-based scale-invariant image registration with image gradients.* IEEE TPAMI 2010;32(10):1899-1906. | Correlación de **orientación de gradientes** (robusta a iluminación y polaridad) para la coincidencia por contornos. |
| Evangelidis GD, Psarakis EZ. *Parametric image alignment using enhanced correlation coefficient maximization.* IEEE TPAMI 2008;30(10):1858-1865. | Criterio de correlación normalizada y optimización de grueso a fino. |
| Hartley R, Zisserman A. *Multiple View Geometry in Computer Vision.* 2.ª ed., 2004 (homografía por rotación K·R·K⁻¹). | Corrección de perspectiva vertical, estimada por proporciones de contornos. |

## Segmentación y color

| Artículo / biblioteca | Aplicación |
|---|---|
| Adams R, Bischof L. *Seeded region growing.* IEEE TPAMI 1994;16(6):641-647. | **Varita mágica** (crecimiento de región en CIELAB con parada en bordes). |
| ML Kit Selfie Segmentation (MediaPipe). | Selección automática de la silueta (como los stickers). |
| Reinhard E, Ashikhmin M, Gooch B, Shirley P. *Color transfer between images.* IEEE CG&A 2001;21(5):34-41. | **Igualación del color de la piel** (media/dispersión en espacio perceptual), descompuesta en brillo, contraste, gamma, temperatura y tinte. |
| CIE 15:2004 *Colorimetry* (CIELAB, ΔE). | Métrica de diferencia de color (ΔE) en las pruebas. |

## Buenas prácticas aplicadas en la interfaz de captura

- Indicaciones fuera de la imagen; flechas en el borde con el sentido de movimiento **del teléfono**
  (considerando cámara frontal/trasera y espejo); giro mostrado en el nivel.
- Coincidencia solo con el paciente detectado (nunca con el fondo); umbral de puntos configurable
  (80 % por defecto) con geometría y pose como condiciones adicionales; histéresis para evitar parpadeo.
- Disparo automático solo tras coincidencia sostenida, una vez por coincidencia.
- Vista en espejo opcional para la cámara frontal (como las cámaras del sistema); las fotos se
  guardan siempre sin espejar (orientación anatómica real, imprescindible en registros clínicos).

## Registro de las condiciones de captura

Las guías de fotografía clínica recomiendan documentar cómo se tomó cada foto para que las series sean
comparables. PhotoMed guarda en el XMP de cada foto el motor de puntos, el modo de asistencia, el % de
puntos coincidentes, el giro de la cabeza, la distancia estimada y el zoom, los muestra en el visor y
avisa en el comparador cuando un par difiere (giro > 6°, distancia > 25 %, otro motor o encuadre no
confirmado).
