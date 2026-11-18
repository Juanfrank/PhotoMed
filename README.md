# PhotoMed

Aplicación Android para fotografía clínica en **estética médica**: captura estandarizada por zonas,
comparación antes/después con alineación automática y organización por expediente.

Autor: **Juan Medina** · juanfrank11@gmail.com · Licencia [MIT](LICENSE) ·
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

### Galería del teléfono y metadatos
- Las fotos se guardan en la galería, en un **álbum por paciente**: `Pictures/PhotoMed/<expediente>/`.
- Cada foto incluye los datos del paciente y del expediente como **metadatos EXIF y XMP**
  (`dc:title`, `dc:description`, `dc:subject` y el espacio `pm:` con paciente, número, zona, fecha y
  consentimiento).
- **Compartir sin datos del paciente** (copia anonimizada) o con ellos.
- **Recuperar desde la galería**: reconstruye los expedientes a partir de los metadatos tras reinstalar.
- Migración automática de las fotos de la versión 1 (almacenamiento privado) a la galería.

### Comparación
- **Juxtaposition slider**, lado a lado y fundido; selección por toggle (A = antes, B = después),
  filtro por zona y zoom sincronizado.
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
