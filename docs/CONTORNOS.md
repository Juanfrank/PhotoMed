# Contornos anatómicos de referencia

Las guías de la cámara se generan a partir de ilustraciones anatómicas de línea (rostro de frente,
perfiles, cuerpo de frente y de espalda, manos y pies) guardadas en `art/source/`, aportadas por el
autor del proyecto. **Fuente y licencia de estas ilustraciones: pendiente de documentar** (completar
aquí y en “Acerca de y legal” la atribución que corresponda).

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

Los oblicuos 45° y el abdomen lateral aún usan el contorno vectorial propio (no hay ilustración de
esas vistas); se dibujan con el mismo grosor y color para mantener la coherencia.

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
