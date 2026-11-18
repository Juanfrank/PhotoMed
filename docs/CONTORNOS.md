# Contornos anatómicos de referencia

Las guías de la cámara se generan a partir de ilustraciones anatómicas de línea (rostro de frente,
perfiles, cuerpo de frente y de espalda, manos y pies) guardadas en `art/source/`.

Ilustraciones anatómicas base: **Open Pain Illustration** — Digital Health Zurich (DIZH) / Universidad de Zúrich, licencia [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Descripción: JMIR Medical Informatics 2026;e75737 (doi:10.2196/75737). Cambios: recorte por zona, extracción y recoloreado de trazos; las vistas oblicua 45° y lateral del tronco son derivadas (proyección 3D de las vistas frontal/lateral y reconstrucción a partir de las vistas frontal/posterior).

### Vistas derivadas

- **Oblicuos 45°** (`tools/guides/synth_oblique.py`): modelo 3D simplificado de la cabeza. El
  semiancho de cada altura sale de la vista frontal y la profundidad (frente, nariz, labios, mentón,
  nuca) del perfil, alineados por vértice, ojos y mentón. Cada sección es una superelipse; se rota 45°
  y se proyecta: silueta (mín./máx. de la proyección + nariz), rasgos frontales escorzados y ocultos
  cuando la superficie deja de mirar a la cámara, oreja cercana tomada del perfil y dorso nasal.
- **Abdomen lateral** (`tools/guides/synth_side.py`): alturas de las referencias y anchos del tronco
  medidos en las vistas frontal y posterior; profundidad anteroposterior por proporciones
  antropométricas (≈ 0,7 del ancho en tórax y cintura, ≈ 0,85 en caderas), con mama, pliegue
  inframamario, ombligo, lordosis lumbar, glúteo y pliegue glúteo.

`tools/guides/build_guides.py` recorta cada zona en 3:4, extrae los trazos negros, los esqueletiza y
los vuelve a engrosar de forma uniforme, y los colorea (trazo cian con halo oscuro) en
`app/src/main/res/drawable-nodpi/guide_<zona>.webp` (60 KB en total). También genera `GuideArt.kt`
con las líneas y marcas de referencia, definidas sobre la anatomía de la ilustración:

| Zona | Fuente | Referencias |
|---|---|---|
| Rostro frontal | rostro de frente | línea media, cejas, línea pupilar, base nasal, mentón, medio-pupilares, pupilas |
| Frente y glabela · Periocular · Labios | rostro de frente (recortes) | línea media; cejas / pupilas / comisuras |
| Perfiles derecho e izquierdo | perfiles | plano de Frankfort, vertical por glabela, tragion y orbitale |
| Cuello y escote | cuerpo de frente | línea media, mentón, escotadura yugular |
| Tórax / mamas · Abdomen frontal · Muslos | cuerpo de frente | línea media, pezones, pliegue inframamario, ombligo, ingle, rótulas |
| Espalda · Glúteos · Brazos | cuerpo de espalda | línea media, C7, escápulas, crestas ilíacas, pliegue interglúteo y glúteo, codo |
| Manos (dorso, palma) · Pies (dorso, planta) | manos y pies | línea media |

| Oblicuos 45° | derivada de frontal + perfil | cejas, línea pupilar, base nasal, vertical por la punta nasal, pupilas |
| Abdomen lateral | derivada de frente + espalda | eje acromion–trocánter, ombligo, pliegue glúteo |

![Guías](../app/src/test/snapshots/images/com.photomed.app_GuideScreenshotTest_allGuides.png)

## Historial de búsqueda de referencias

### Referencias libres consultadas (no usadas)

Se buscaron siluetas de dominio público **sin requisito de atribución** como inspiración para las
proporciones generales:

| Referencia | Licencia (según la página) | Uso |
|---|---|---|
| [Female head profile silhouette — FreeSVG #71117](https://freesvg.org/females-head-profile-silhouette) (publicado por OpenClipart) | Public Domain (CC0), “No attribution required” | Proporción del perfil facial |
| [Male body silhouette — FreeSVG #7918](https://freesvg.org/male-body-silhouette-vector-clip-art) (OpenClipart) | Public Domain (CC0), “No attribution required” | Proporción del tronco y miembros |
| [Silhouette of man standing and facing forward — Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Silhouette_of_man_standing_and_facing_forward.svg) | CC0 1.0 (según la búsqueda; verificar en la página antes de reutilizar el archivo) | Proporción corporal frontal |

**Por qué no se incorporaron sus trazados:** son siluetas rellenas de carácter artístico (cabello,
ropa, pose), sin puntos anatómicos ni líneas clínicas, y su escala no corresponde a los encuadres
estandarizados de fotografía clínica. Se usaron solo como referencia visual de proporciones; ningún
trazado de terceros forma parte del código.

## Criterios anatómicos usados

- **Rostro frontal:** cánones neoclásicos de proporción facial — tercios faciales iguales (implantación
  capilar – glabela – subnasal – mentón), línea pupilar, líneas medio-pupilares y línea media.
- **Perfiles:** plano horizontal de Frankfort (tragion – orbitale) y vertical verdadera por glabela,
  según los estándares habituales de fotografía clínica en cirugía plástica y estética.
- **Oblicuo 45°:** punta nasal alineada con el contorno de la mejilla.
- **Corporal:** línea media, ombligo, pliegue inframamario, pliegue glúteo y rodillas como
  referencias de reposicionamiento, con encuadres de cuerpo estandarizados.
