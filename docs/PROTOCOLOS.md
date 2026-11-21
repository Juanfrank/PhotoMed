# Protocolos de fotos

Un **protocolo** es una lista ordenada de tomas que un profesional define en PhotoMed y comparte con
otra persona (colega, asistente o el propio paciente). Quien lo recibe lo importa y la cámara le
guía paso a paso con las mismas exigencias. Al terminar, puede **devolver los resultados**: un paquete
con las fotos y un informe de cada toma, que el autor importa en su expediente.

## Flujo

1. **Crear** (expediente → «Protocolos de fotos» → *Nuevo*): nombre, autor, notas y las tomas. Cada
   toma tiene zona (ángulo), gesto facial, cantidad de fotos, % mínimo de coincidencia, disparo
   automático, luz mínima, luz pareja entre mejillas, mirada al frente, cámara e indicaciones.
   - *Usar una foto como referencia*: guarda de una foto existente **solo sus características** (puntos
     anatómicos, pose de la cabeza, mirada, línea del pelo, perfil, contornos y surcos, distancia y
     zoom). La imagen no se incluye.
   - *Añadir tomas desde fotos del expediente*: una toma por foto elegida, con su zona, su gesto y su
     referencia.
2. **Compartir** (⋯ → *Compartir el protocolo*): un archivo `Nombre.photomed.json`.
3. **Importar**: «Abrir con PhotoMed» desde el correo, WhatsApp o Archivos, o expediente →
   *Importar*.
4. **Tomar** (▶ en el protocolo, dentro del expediente del paciente): la cámara recorre las tomas en
   orden. En cada una fija el gesto y exige su coincidencia, su luz y, si la hay, la referencia
   (los contornos y surcos de la referencia se dibujan como guía). Se queda en la toma hasta guardar
   la cantidad pedida; *Saltar* pasa a la siguiente.
5. **Devolver resultados** (⋯ → *Compartir resultados*, con o sin datos del paciente): un `.zip`.
6. **Importar resultados** (expediente del autor → *Importar* → el `.zip`): las fotos entran en ese
   expediente con su zona, gesto, fecha y condiciones. Reimportar el mismo paquete no duplica fotos.

## Limitaciones

- La referencia sin imagen coincide por **puntos anatómicos** (rostro y zonas con detector) o por el
  **perfil**. En zonas corporales sin puntos (contornos del cuerpo), la coincidencia necesita la
  imagen: ahí la toma se estandariza (encuadre, nivel) sin referencia.
- La luz se mide en el rostro (mejillas y centro); en otras zonas no se exige.
- Con protocolo no se dispara «casi alineada»: la toma debe coincidir.

## Formato del protocolo (`.photomed.json`)

```json
{
  "format": "photomed.protocol",
  "version": 1,
  "id": "9b1f…",
  "name": "Toxina: control a 15 días",
  "author": "Dra. Ejemplo",
  "notes": "Sin maquillaje, pelo recogido",
  "createdAt": 1763400000000,
  "steps": [
    {
      "id": "a1b2c3d4",
      "zone": "rostro_frontal",
      "expression": "reposo",
      "count": 2,
      "label": "Frente en reposo",
      "instructions": "Mirada al frente, labios juntos",
      "match": { "threshold": 0.9, "autoCapture": true, "gaze": true },
      "lighting": { "min": 0.4, "max": 0.85, "maxAsymmetry": 0.15 },
      "camera": { "lens": "back" },
      "reference": {
        "points": { "eye_l": [0.61, 0.42], "eye_r": [0.39, 0.42] },
        "yaw": 1.5, "pitch": -2.0, "roll": 0.3,
        "gaze": [0.01, -0.02],
        "hairline": [[0.30, 0.20], [0.70, 0.21]],
        "profile": { "punta nasal": [0.8, 0.5] },
        "lines": [ { "layer": "FOLDS", "name": "Nasogeniano derecho", "points": [[0.42, 0.60], [0.40, 0.70]] } ],
        "distanceCm": 110, "zoom": 2.0,
        "sourceTakenAt": 1763400000000
      }
    }
  ]
}
```

| Campo | Significado |
|---|---|
| `zone` | Identificador de la zona (ángulo), p. ej. `rostro_frontal`, `rostro_oblicuo_der`, `tercio_superior`, `cuero_cabelludo_vertice` (ver `Zone.kt`). |
| `expression` | Gesto: `reposo`, `sorpresa`, `enojo`, `sonrisa`, `ojos_apretados`, `nariz`, `beso`, `tristeza`, `menton`, `cuello`. |
| `count` | Fotos de la toma (1–50). |
| `match.threshold` | % mínimo de puntos coincidentes (0,3–1). Sin él, el del teléfono. |
| `lighting.min` / `max` | Luminancia media del rostro (0–1). |
| `lighting.maxAsymmetry` | Diferencia máxima de luz entre mejillas (0–1). |
| `camera.lens` | `back` o `front`. |
| `reference` | Características de la foto de referencia en su encuadre 3:4 (x, y en unidades del ancho). Opcional. |

Los campos ausentes usan el ajuste del teléfono.

## Paquete de resultados (`Resultados_<nombre>.zip`)

- `protocolo.photomed.json`: el protocolo.
- `fotos/NN_<archivo>.jpg`: las fotos tomadas con él (sin metadatos del paciente si se eligió).
- `resultados.json`:

```json
{
  "format": "photomed.results", "version": 1,
  "protocolId": "9b1f…", "protocol": "Toxina: control a 15 días",
  "requested": 3, "taken": 3,
  "steps": [ { "index": 1, "id": "a1b2c3d4", "title": "Frente en reposo", "requested": 2, "taken": 2 } ],
  "shots": [
    { "file": "fotos/01_….jpg", "zone": "rostro_frontal", "expression": "reposo", "takenAt": 1763400000000,
      "step": "a1b2c3d4", "stepIndex": 1, "shot": 1,
      "conditions": { "MatchPercent": "93", "LightFace": "0.55", "LightAsymmetry": "0.06", "DistanceCm": "108", "Zoom": "2.00" } }
  ],
  "patient": { "name": "…", "record": "…" }
}
```

`patient` solo va si se comparte con datos del paciente.
