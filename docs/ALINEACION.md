# Algoritmo de coincidencia contornal (alineación antes/después)

Objetivo: dadas dos fotografías de la **misma zona**, encontrar la transformación de semejanza
`T` (escala uniforme `s`, rotación `θ`, traslación `t`) que superpone la foto **después** sobre la
foto **antes**, para que el comparador muestre cambios reales y no diferencias de encuadre.

```
p_antes ≈ T(p_después) = s·R(θ)·p_después + t
```

Coordenadas normalizadas: `x ∈ [0,1]`, `y ∈ [0, 4/3]` (unidad = ancho de la imagen 3:4), así la
transformación es independiente de la resolución. En pantalla se aplica con
`graphicsLayer(transformOrigin = 0,0; scale = s; rotationZ = θ; translation = t·ancho)`.

## Etapas

### 1. Puntos anatómicos según la zona (automático, en el dispositivo)

`ZoneAlignment.of(zone)` decide el detector y los puntos que se usan:

| Zona | Detector | Puntos |
|---|---|---|
| Rostro frontal | ML Kit rostro | ojos, base nasal, comisuras + 36 puntos del óvalo facial |
| Oblicuos 45° | ML Kit rostro | ojos, base nasal, comisuras |
| Perfiles | ML Kit rostro | **oreja (tragus)**, ojo visible, base nasal, comisura |
| Frente / periocular | ML Kit rostro | ojos |
| Labios | ML Kit rostro | comisuras, base nasal |
| Cuello | ML Kit pose | hombros, boca |
| Abdomen, tórax, espalda | ML Kit pose | hombros, caderas |
| Abdomen lateral | ML Kit pose | hombro, cadera, rodilla |
| Glúteos, muslos | ML Kit pose | caderas, rodillas, tobillos |
| Brazos | ML Kit pose | hombro, codo, muñeca |

Se usan solo los puntos detectados **en ambas** fotos (pose: probabilidad en cuadro ≥ 0,8).

### 2. Estimación robusta de la semejanza

Con ≥ 2 pares de puntos se resuelve por mínimos cuadrados (Umeyama, 1991) en forma cerrada:

```
a = Σ (s̃·d̃)   b = Σ (s̃ × d̃)   σ² = Σ |s̃|²      (s̃, d̃ centrados en sus medias)
θ = atan2(b, a)   s = √(a² + b²) / σ²   t = μ_d − s·R(θ)·μ_s
```

Con ≥ 4 puntos se descarta el par con residuo > 9 × mediana (detección errónea) y se reestima.

### 3. Coincidencia de contornos (afinado o búsqueda completa)

`EdgeAligner` compara **mapas de bordes**, no colores, para tolerar cambios de iluminación, de
maquillaje o de tono de piel entre sesiones:

1. Escala de grises 192×256, estandarizada (media 0, desviación 1).
2. Pirámide de 3 niveles (192×256 → 96×128 → 48×64).
3. En cada nivel: suavizado → magnitud de gradiente **Sobel** → desenfoque de caja (amplía la cuenca
   de convergencia). Resultado: un “mapa de contornos” (silueta corporal, ojos, labios, oreja…).
4. Función objetivo: **correlación cruzada normalizada (NCC)** ponderada entre el mapa “antes” y el
   mapa “después” deformado por `T⁻¹` (muestreo bilineal), con pesos gaussianos centrados en la
   región de interés de la zona (el paciente pesa más que el fondo) y penalización por la parte que
   queda fuera del encuadre.
5. Optimización:
   - **Sin puntos anatómicos** (close-ups, glúteos, detección fallida): búsqueda exhaustiva en el
     nivel grueso de desplazamiento (±18 %), escala (±16 %) y rotación (±12°) alrededor del centro de
     la región de interés.
   - **Con puntos anatómicos**: se parte de su estimación y se busca solo en una ventana estrecha
     (±6 % de traslación, ±5°, ±8 % de escala).
   - Descenso por coordenadas sobre `(tx, ty, θ, ln s)` de grueso a fino; al cambiar la escala, la
     traslación se compensa para mantener fijo el centro de la región de interés.

### 4. Alternativa manual

Menú ⋮ → “Alinear manualmente”: el usuario marca dos puntos anatómicos propios de la zona (por
ejemplo, ambas pupilas; tragus y punta nasal en perfil; pezones en tórax; rodillas en muslos) en
cada foto. Dos pares determinan la semejanza de forma exacta y después se afina por contornos.

## Resultado

El comparador muestra el método usado, la escala y la rotación aplicadas y un interruptor para ver
la foto original o la alineada en los tres modos (deslizador, lado a lado, fundido).

## Verificación

`app/src/test/java/com/photomed/app/AlignmentTest.kt`:

- semejanza exacta con 2 puntos, inversa y composición;
- rechazo de un punto anatómico erróneo;
- recuperación de una transformación conocida (escala 1,08, rotación 4°, desplazamiento 7 %) **solo
  por contornos**, con error < 1,2 % del ancho;
- afinado de una estimación anatómica ruidosa: el error se reduce a menos de un tercio y < 1 %.

Limitaciones: no corrige cambios de perspectiva (distancia o inclinación de la cámara muy distintas),
ni cambios de postura no rígidos (por ejemplo, un brazo en otra posición). Las guías de la cámara,
el nivel y la superposición de la foto anterior reducen estas diferencias en el momento de la toma.
