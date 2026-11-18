# PhotoMed

Aplicación Android para fotografía clínica en **estética médica**: captura estandarizada por zonas,
comparación antes/después y organización por expediente.

## Funciones

### 1. Toma de fotografías por zonas
- 17 zonas estandarizadas (facial y corporal): rostro frontal, oblicuos 45°, perfiles, frente/glabela,
  periocular, labios, cuello, abdomen frontal y lateral, tórax/mamas, espalda, glúteos, muslos, brazos.
- **Contornos anatómicos** por zona (silueta) y **líneas de referencia** (línea media, tercios faciales,
  línea pupilar, plano de Frankfort, ombligo, pliegue glúteo, rodillas…), con indicaciones de posición.
- **Cuadrícula** de tercios y cruz central.
- **Superposición de la foto anterior** (“fantasma”) semitransparente, con opacidad ajustable y selector
  de qué foto previa usar como referencia.
- **Nivel** (inclinación y cabeceo del teléfono, en verde cuando está nivelado).
- Encuadre fijo 3:4: la vista previa y la foto guardada tienen exactamente el mismo campo, para que la
  superposición coincida. Toque para enfocar, flash (apagado/encendido/auto) y cámara frontal/trasera.

### 2. Comparación (juxtaposition slider)
- Selección por **toggle**: toque las miniaturas para seleccionar/deseleccionar (máx. 2; A = antes,
  B = después, ordenadas por fecha). Filtro por zona.
- Modos: **Deslizador** (divisor arrastrable), **Lado a lado** y **Fundido**.
- Zoom con dos dedos sincronizado en ambas imágenes, intercambio A/B e intervalo en días.

### 3. Expedientes
- Crear, buscar, editar y eliminar expedientes (nombre, número, notas).
- Cada expediente es una carpeta con una subcarpeta por zona:
  ```
  Android/data/com.photomed.app/files/Pictures/PhotoMed/
    0012_Maria_Perez/
      expediente.json
      rostro_frontal/rostro_frontal_20261118_101530_123.jpg
      labios/...
  ```
- Las fotos se guardan en el almacenamiento privado de la app (no aparecen en la galería pública).
  Se pueden compartir individualmente desde el visor.

## Compilar

Requisitos: JDK 17+ y Android SDK (API 35).

```bash
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # pruebas unitarias + verificación de capturas de las guías
./gradlew recordPaparazziDebug   # regenera la imagen de referencia de las guías
```

Stack: Kotlin, Jetpack Compose (Material 3), CameraX, Navigation Compose, Coil. minSdk 26.
