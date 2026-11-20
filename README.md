# PhotoMed

Aplicación Android para fotografía clínica en **estética médica**: captura estandarizada por zonas,
comparación antes/después con alineación automática y organización por expediente.

Autor: **Juan Medina** · juanfrank11@gmail.com · [Licencia comercial](LICENSE) (uso concedido a ProntoCare) ·
[Novedades](CHANGELOG.md) ·
[Política de privacidad](PRIVACY.md) · [Términos y condiciones](TERMS.md)

## Funciones

### Captura por zonas
- 22 zonas estandarizadas (9 faciales, 13 corporales, incluidas manos y pies) con **contornos anatómicos ilustrados** y
  **líneas de referencia** (tercios faciales, Frankfort, línea media, ombligo, pliegues…) —
  ver [docs/CONTORNOS.md](docs/CONTORNOS.md). Ilustraciones base: *Open Pain Illustration*
  (Digital Health Zurich, CC BY 4.0).
- **Asistencia sin referencia** (primera toma): vista estándar por la pose de la cabeza (0°/45°/perfil),
  plano de Frankfort, iluminación y exposición; condiciones de captura guardadas en el XMP.
- **Dos motores de puntos faciales** intercambiables en Ajustes: Google ML Kit o MediaPipe Face Landmarker.
- **Guía adaptada a la foto de referencia**: el contorno y las líneas (cejas, ojos, nariz, boca,
  mandíbula; hombros, caderas, rodillas…) se desplazan a la anatomía detectada.
- **Superposición de la foto anterior** con opacidad ajustable, vista en **espejo** (frontal, por defecto), cuadrícula, **nivel** (inclinación y
  cabeceo), toque para enfocar, flash y cámara frontal/trasera. Encuadre 3:4 idéntico en vista previa y captura.
- **Coincidencia en vivo**: la guía cambia de color (cian → ámbar → verde, con vibración) cuando el
  porcentaje de puntos coincidentes (tolerancia 3 %) alcanza el umbral (80 % por defecto) con la
  geometría y la pose de la cabeza; flechas en el borde, giro en el nivel, **zoom asistido estable**
  (espera el enfoque) y
  **disparo automático** opcional. Validado con imágenes reales (ver docs/ALINEACION.md).
- **Medición de contornos**: selección automática (óvalo facial / silueta, como los stickers) o con
  **varita mágica**; alto, anchos por nivel, área y proporciones antes/después.

- **Toma más sencilla**: indicaciones de una en una por prioridad (“+N” muestra el resto),
  vibración que se acelera al acercarse y tono al coincidir, disparo automático también “casi”
  alineado (se alinea después en Comparar), ráfaga de 3 fotos guardando la de mayor coincidencia,
  fantasma de bordes de la referencia, y zoom/distancia de la referencia aplicados al elegirla.
- **Plantillas y secuencias**: en el expediente, mantener presionada una zona elige varias (o se
  toca una plantilla, predefinida o propia); “Iniciar secuencia de fotos” recorre las zonas en orden
  y pasa sola a la siguiente tras cada foto.
- **Modo libre**: zona sin dibujo cuya guía es el contorno de lo visible (silueta o bordes).
- **Extremidades en modo individual**: una sola mano, pie o pierna dentro de la misma zona.
- **Zoom manual** con deslizador o pellizco (pausa el zoom asistido; “Auto” lo reanuda).
- **Guía adaptada a la referencia**: sigue la inclinación de la cabeza (línea entre los ojos) y la
  forma del cráneo, mejillas, mandíbula y mentón (óvalo facial detectado, fila a fila).

### Galería del teléfono y metadatos
- Las fotos se guardan en la galería, en un **álbum por paciente**: `Pictures/PhotoMed/<expediente>/`.
- Cada foto incluye los datos del paciente y del expediente como **metadatos EXIF y XMP**
  (`dc:title`, `dc:description`, `dc:subject` y el espacio `pm:` con paciente, número, zona, fecha y
  consentimiento).
- **Compartir sin datos del paciente** (copia anonimizada) o con ellos.
- **Recuperar desde la galería**: reconstruye los expedientes a partir de los metadatos tras reinstalar.
- Migración automática de las fotos de la versión 1 (almacenamiento privado) a la galería.

### Comparación
- **Juxtaposition slider**, lado a lado, **cuadrícula** y fundido; selección múltiple por toggle
  (2 a 12 fotos), filtro por zona y zoom sincronizado. Con N fotos la yuxtaposición es
  **progresiva** (línea de tiempo 1→2→…→N) y cada foto se alinea a la primera.
- **Alineación configurable**: puntos anatómicos, contornos y **mirada (gaze)**, activas por defecto
  y desactivables con casillas en el menú.
- **Exportar imágenes ajustadas**: cada foto con perspectiva, color y alineación aplicados (y una
  composición en cuadrícula con fechas), sin datos del paciente por defecto; se guardan en el
  subálbum `PhotoMed/<expediente>/Exportadas` o se comparten.
- **Selección múltiple en la galería** (mantener pulsado): comparar, compartir (con o sin datos del
  paciente) o eliminar varias fotos.
- **Alineación automática por zona**: hasta 140 puntos faciales o 33 corporales detectados en el
  dispositivo + coincidencia por **contornos anatómicos** (mandíbula, frente, orejas, caderas,
  falanges…) y **corrección de perspectiva vertical por proporciones de los contornos** (sin sensores);
  e **igualación del color de la piel** (brillo, contraste, gamma, temperatura) —
  ver [docs/ALINEACION.md](docs/ALINEACION.md).

### Expedientes
- Crear (formulario a pantalla completa), buscar, editar y eliminar; registro de **consentimiento informado**.
- Datos del expediente en almacenamiento privado; fotos en la galería.

## Diseño (Material Design 3)
El tema se genera desde los tokens del repositorio [Juanfrank/MD](https://github.com/Juanfrank/MD)
(`docs/material3/tokens.json`, `@material/web` v0_192): 49 roles de color en claro y oscuro, escala
tipográfica, formas, elevación, movimiento, capas de estado y retícula de 4/8 dp.

```bash
python3 tools/md3/generate_theme.py   # regenera ui/theme/Md3Tokens.kt desde tools/md3/tokens.json
```

Prácticas aplicadas: barras superiores grandes/medianas que se contraen, FAB extendido que se reduce
al desplazarse, estados vacíos con acción, formularios a pantalla completa con validación,
confirmación de acciones destructivas, snackbars, objetivos táctiles ≥ 48 dp, descripciones de
accesibilidad y estados, contraste WCAG AA verificado por prueba, modo oscuro, edge-to-edge, gesto
atrás predictivo, cámara y visor inmersivos, transiciones de eje compartido con las curvas de M3.

## Compilar y probar

Requisitos: JDK 17+ y Android SDK (API 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # pruebas unitarias + verificación de capturas
./gradlew recordPaparazziDebug   # regenera las capturas de referencia (app/src/test/snapshots)
```

Buenas prácticas y bibliografía: [docs/BUENAS_PRACTICAS.md](docs/BUENAS_PRACTICAS.md).
Logo: esfigmomanómetro cuyo manómetro es el lente de una cámara (`art/logo/logo.svg`).

Stack: Kotlin, Jetpack Compose (Material 3), CameraX, MediaStore, ExifInterface, Navigation
Compose, Coil, Google ML Kit (rostro, pose, segmentación) y MediaPipe Face Landmarker, modelos incluidos. minSdk 26.

## Cámara: modo simple y modo experto

- **Simple** (por defecto): contorno (su color indica la distancia a la coincidencia), un icono con
  la corrección principal y un anillo de progreso en el disparador. Las líneas de referencia
  aparecen al acercarse a la coincidencia; el nivel, solo con el teléfono torcido; la barra de zoom y
  el control de opacidad, unos segundos al usarlos. Cuadrícula apagada por defecto.
- **Experto** (Ajustes de coincidencia): además líneas siempre, puntos detectados, porcentajes,
  franja de indicaciones, barra de zoom y todas las capas.

## Comparar: recorte, orden y exportación

- **Recortar según la alineación** (menú ⋮, junto a las condiciones de la alineación): tras alinear,
  todas las fotos se recortan al mayor encuadre 3:4 que cubren todas a la vez (incluida la
  perspectiva), sin bordes negros. Se aplica en pantalla y al exportar.
- **Fechas**: la hora y minuto se añaden solo a las fotos que comparten día con otra de la selección.
- **Orden de la cuadrícula** (menú ⋮): fecha antigua primero, reciente primero o manual; en la
  cuadrícula se reordena manteniendo pulsada una foto y arrastrándola (o con las acciones de
  accesibilidad "Mover antes/después"). Es solo presentación: la referencia de la alineación no cambia.
- **Exportar** (una opción a la vez): fotos ajustadas, cuadrícula, **lado a lado** (una fila) o
  **GIF animado** en modo desplazamiento (cortinilla como el deslizador) o fundido, con tiempo por
  imagen (0,3–5 s) y de transición (0–3 s; 0 = corte) ajustables y **vista previa animada** que usa
  el mismo dibujo y los mismos tiempos que el archivo. Codificador GIF propio (paleta de 256
  colores por corte de la mediana, LZW).

## Primera toma, ayudas y modelo opcional

- **Primera toma (sin referencia)**: cuando el óvalo de la cara coincide con el del dibujo de la guía
  (centro ±5 %, tamaño ±12 %, con histéresis), el contorno se ajusta a la cara del paciente (cráneo,
  mentón, ojos, boca…) usando los puntos suavizados del cuadro.
- **Ayudas de primera vez**: una tarjeta por pantalla (expediente, cámara, comparar) explica los
  gestos ocultos; "Entendido" la cierra para siempre (Ajustes o ⋯ de la cámara: mostrarlas otra vez).
- **Comparar**: menú ⋮ corto (Ajustes de comparación, Exportar, Restablecer zoom); la hoja de
  ajustes agrupa alineación, presentación (recorte, orden), color y perspectiva. Los controles bajo
  las fotos se ocultan a los 3 s sin tocar.
- **Expediente**: primero las zonas con fotos; el resto por categoría, en secciones plegables.
- **Modelo de pelo y piel** (MediaPipe Selfie Multiclass, 16 MB): incluido en la app.
- **Calibración con fotos propias**: `python3 tools/eval/calibrate.py carpeta_neutras --smiles carpeta_sonrisas`
  mide la expresión en sus fotos (en su equipo) y propone los límites de expresión neutra.
