# Novedades de PhotoMed

Registro de cambios de cada versión, de la más reciente a la más antigua.

## 2.15 — 20 de noviembre de 2026

- **Importar fotos a un expediente** (menú ⋮ del expediente → *Importar fotos*): se eligen varias
  fotos con el selector del sistema y se asigna a cada una su ángulo (o el mismo a todas). Las
  vistas del rostro llegan con una sugerencia (frontal, oblicuo o perfil y su lado, por el giro de
  la cabeza). Se conserva la fecha de toma de la foto; se guardan con los datos del paciente y sin
  los metadatos originales (ubicación, dispositivo).
- **Corrección**: al girar la referencia 90° el contorno adaptado se deformaba (la adaptación de la
  guía solo admite cabezas casi verticales). Ahora se adapta a la referencia sin girar y se gira
  entero, alineado con la foto superpuesta; la guía genérica girada cabe entera en el encuadre.

## 2.14 — 20 de noviembre de 2026

- **Medición de relieve con la pantalla** (cámara frontal, menú ⋯ → *Medición de relieve*): al
  disparar, la pantalla ilumina la cara por mitades (izquierda, derecha, arriba, abajo) con la
  exposición bloqueada; con estéreo fotométrico se obtiene el relieve de la piel y la profundidad de
  cada arruga y surco se mide en él (en lugar de estimarla por el sombreado de una sola foto).
- **Sensor de profundidad del teléfono** (si lo tiene): distancia real a la cara para la escala en
  mm en lugar del tamaño medio del iris.
- La ficha y la tabla de medidas indican el origen de la profundidad y de la escala.

## 2.13 — 20 de noviembre de 2026

- **Medidas de cada línea**: toque una línea (surco, frente, ceño, patas de gallo) u ojera en el
  visor para ver su ficha; el chip **Medidas** abre la tabla. En Comparar, una columna por foto y
  la diferencia.
  - Longitud y ancho en mm (escala por el diámetro del iris), contraste ΔL*, profundidad estimada
    por el sombreado y grado orientativo 0–5 al estilo de Lemperle.
  - Ojeras: área, ΔL*, ΔE*ab e ITA° frente a la mejilla, y tipo probable (vascular, pigmentaria o
    estructural).
- **Detectores mejorados**: líneas de la frente que siguen el arco de las cejas hasta la línea del
  pelo (enteras o por mitades); ceño en abanico (verticales e inclinadas, y horizontales de la raíz
  nasal); patas de gallo con 11 rayos, fuera de las cejas, con supresión de duplicados.

## 2.12 — 20 de noviembre de 2026

- **Alineación y validación con ojos, cejas, nariz y labios** (malla completa de MediaPipe): más
  puntos para alinear y para la coincidencia en vivo, y en Comparar la coincidencia por rasgo
  (ojos, cejas, nariz, labios, contorno) con aviso del rasgo que no coincide.
- **Surco nasogeniano anatómico**: nace en el pliegue alar-facial y baja hasta el lateral de la
  comisura.
- **Nuevas capas**: arrugas de la frente, ceño, patas de gallo y ojeras (zona más oscura que la
  mejilla).

- **Rasgos con la malla completa de MediaPipe (478 puntos)**: ojos, cejas, labios (contornos externo
  e interno) y nariz con trazado estético (líneas estéticas del dorso, bordes de las alas y punta).
- **Surcos detectados en la imagen**: detector de crestas Hessiano multiescala dentro de pasillos
  definidos por la malla (nasogeniano, marioneta, valle lagrimal), trazado por programación
  dinámica y solo sobre piel del rostro; si no hay surco claro, no se dibuja. En 18 retratos
  reales nuevos: nasogeniano en 17; en piel joven y lisa, prácticamente nada.

- **Capas sobre las fotos** (visor y comparar): contorno, línea del pelo, ojos y cejas, nariz,
  labios y surcos faciales (nasogenianos, marioneta, valle lagrimal), con el estilo del contorno.
  Los surcos se estiman con la geometría del rostro y se ajustan al valle de sombra de la piel; los
  que no se pueden medir se dibujan discontinuos.
- **Visor**: girar 90°, espejo horizontal y recorte de cada foto, sin modificar el original; la
  edición se aplica también en Comparar, en la alineación y al exportar.
- **Comparar**: girar 90°, espejo y recorte manual de toda la comparación; lo exportado incluye las
  capas visibles, el recorte y la orientación.
- **Cámara**: girar 90° y espejar la referencia superpuesta (o la guía, si no hay referencia).

## 2.11 — 19 de noviembre de 2026

- **Licencia comercial de pago** (profesional, clínica e institucional; suscripción o perpetua),
  con **licencia institucional gratuita para ProntoCare**.
- **Nuevo logotipo**: cámara clínica cuyo visor es un estetoscopio (icono adaptativo y temático).
- **Novedades** dentro de la app (Acerca de y legal): este registro de cambios.
- El modelo de pelo y piel vuelve a venir **incluido en la app** (sin descarga).
- **Línea de implantación en vivo**: se ve también en la primera toma y sin superponer la referencia
  (actual: línea continua; referencia: discontinua); aviso en pantalla si la segmentación no carga.

## 2.10 — 19 de noviembre de 2026

- **Primera toma ajustada a la cara**: sin foto de referencia, cuando la cara coincide con el marco
  de la guía, el contorno se adapta a ella.
- **Comparar**: menú corto y una hoja de *Ajustes de comparación* (alineación, presentación, color,
  perspectiva); los controles bajo las fotos se ocultan a los 3 s sin tocar.
- **Ayudas de primera vez** en expediente, cámara y comparar; menú "⋯" en la cámara.
- **Expediente**: primero las zonas con fotos; el resto por categoría en secciones plegables.
- **Óvalo ajustado al límite real de la cara**, **puntos tapados** (flequillo, gafas, mano) fuera de
  la alineación y **perfil cerca de 90°** por la silueta.
- Herramienta de **calibración con fotos propias** (`tools/eval/calibrate.py`).

## 2.9 — 19 de noviembre de 2026

- **Cámara más limpia**: modo simple (contorno, un icono y anillo de progreso en el disparador) y
  modo experto; líneas, nivel, zoom y opacidad solo cuando hacen falta.
- **Suavizado temporal** (One-Euro) de los puntos en vivo, **control de expresión** (ojos, labios,
  sonrisa, cejas) y **segmentación de pelo y piel**: línea de implantación y altura de la frente.
- **Comparar**: recorte según la alineación, orden de la cuadrícula (arrastrar para reordenar), hora
  en las fotos del mismo día.
- **Exportar**: una sola opción a elegir; lado a lado y **GIF animado** (desplazamiento o fundido) con
  tiempos ajustables y vista previa.

## 2.8 — 19 de noviembre de 2026

- **Zona capilar** (tricología): vértice, corona y parietales derecho e izquierdo, con guías
  proyectadas desde un modelo 3D de la cabeza y ángulo de cámara indicado por el sensor.
- **Primer plano de cada ojo** (derecho e izquierdo); plantillas Tricología y Ojos.
- El botón **Atrás** ya no saca de la aplicación; los bordes se toman solo del cuerpo, no del fondo.
- **Flash de pantalla** en la cámara frontal, que se mantiene durante la ráfaga.

## 2.7 — 19 de noviembre de 2026

- **Toma más sencilla**: indicaciones de una en una, vibración y tono por grado, disparo casi
  alineado, ráfaga que guarda la mejor foto, fantasma de bordes, zoom y distancia de la referencia.
- **Plantillas y secuencias de fotos** con selección múltiple de zonas.
- Salvaguardas en la **alineación automática** contra perspectiva falsa y deriva de contornos.

## 2.6 — 18 y 19 de noviembre de 2026

- **Extremidades en modo individual** (una mano, un pie, una pierna) en la misma zona.
- **Igualación de color de piel** con adaptación cromática de Bradford y ΔE2000; modo carta gris.
- **Modo libre**: la guía es el contorno de lo visible.
- **Indicaciones como iconos** pequeños y translúcidos; contorno que pasa de gris a rojo, naranja
  y verde según la coincidencia.

## 2.5 — 18 de noviembre de 2026

- Corrección: la detección en vivo se detenía al cambiar de cámara.
- **Zoom manual** (deslizador y pellizco) y **exportación** de imágenes ajustadas.
- **Guía que sigue la cabeza**: inclinación, cabeceo (pose 3D) y rasgo a rasgo (ojos, cejas, nariz,
  boca, óvalo, orejas); guías oblicuas, de perfil y corporales ajustadas a la silueta.
- Corrección de la guía deformada con ML Kit (orden de contornos común a ambos motores).

## 2.4 — 18 de noviembre de 2026

- Análisis en vivo más estable y detección de la **mirada**.
- Alineación configurable (puntos, contornos, mirada); **comparación de N fotos** progresiva y en
  cuadrícula; selección múltiple en la galería.
- Interruptores de la cámara con estado inequívoco.

## 2.3 — 18 de noviembre de 2026

- Coincidencia validada con imágenes reales, asistencia de cámara, guía adaptada a la referencia,
  color y logotipo.
- **Zoom asistido**, motores de puntos ML Kit y MediaPipe, y estandarización de la primera toma.

## 2.2 — 18 de noviembre de 2026

- **Coincidencia en vivo** con la foto de referencia, contornos anatómicos y perspectiva vertical.
- Varita mágica, umbral de coincidencia y **disparo automático**.

## 2.1 — 18 de noviembre de 2026

- Guías de cámara con **ilustraciones anatómicas** (Open Pain Illustration, CC BY 4.0); zonas de
  manos y pies, oblicuos a 45° y abdomen lateral.

## 2.0 — 18 de noviembre de 2026

- Interfaz **Material Design 3**, galería con metadatos de cada foto, alineación por zona y
  documentos legales (privacidad, términos, licencia).

## 1.0 — 18 de noviembre de 2026

- Primera versión: fotografía clínica para estética médica con expedientes por paciente.
