# PhotoMed

Aplicación Android para fotografía clínica en **estética médica**: captura estandarizada por zonas,
comparación antes/después con alineación automática y organización por expediente.

Autor: **Juan Medina** · juanfrank11@gmail.com · Licencia [MIT](LICENSE) ·
[Política de privacidad](PRIVACY.md) · [Términos y condiciones](TERMS.md)

## Funciones

### Captura por zonas
- 17 zonas estandarizadas (9 faciales, 8 corporales) con **contornos anatómicos propios** y
  **líneas de referencia** (tercios faciales, Frankfort, línea media, ombligo, pliegues…) —
  ver [docs/CONTORNOS.md](docs/CONTORNOS.md).
- **Superposición de la foto anterior** con opacidad ajustable, cuadrícula, **nivel** (inclinación y
  cabeceo), toque para enfocar, flash y cámara frontal/trasera. Encuadre 3:4 idéntico en vista previa y captura.

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
- **Alineación automática por zona** (ojos, oreja, comisuras, hombros, caderas, rodillas… detectados
  en el dispositivo + coincidencia de contornos) o **manual con 2 puntos** —
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

Stack: Kotlin, Jetpack Compose (Material 3), CameraX, MediaStore, ExifInterface, Navigation
Compose, Coil, Google ML Kit (rostro y pose, modelos incluidos). minSdk 26.
