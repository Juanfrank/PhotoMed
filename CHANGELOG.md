# Novedades de PhotoMed

Registro de cambios de cada versión, de la más reciente a la más antigua.

## 2.67 — 28 de noviembre de 2026

Recomendaciones pequeñas del panel de expertos (ver docs/REVISION_EXPERTOS.md):

- **Compartir sin datos del paciente, de verdad** (3): la foto se vuelve a codificar solo con sus píxeles (con la
  orientación aplicada): sin XMP ni comentario, pero tampoco la miniatura EXIF, la marca y el modelo de la cámara,
  las fechas originales ni el GPS. El nombre lleva el día relativo («dia0», «dia30») en lugar de la fecha, y el
  selector avisa de que la cara sigue visible.
- **Panel de medidas** (4): no se ofrece «Confirmar» si el análisis no encontró nada (se habría guardado como
  confirmado un análisis vacío); se explica el motivo y se ofrece «Trazar a mano».
- **Exportaciones con trazos o medidas** (7): franja debajo de la foto (sin taparla) con «Trazos y medidas
  orientativos, no diagnósticos · escala · versión de PhotoMed».
- **Expediente** (8): la acción principal es «Iniciar sesión» (abre los protocolos) y Comparar pasa a la barra
  superior; plurales correctos («1 zona más», «1 foto»); fechas coherentes: «alta» y, si hay fotos anteriores
  importadas, «primera foto»; nunca una fecha cero.
- **Sin textos cortados** (9): los chips de capas indican con un degradado y una flecha que hay más a la derecha;
  en el móvil en vertical, «Lado a lado» se muestra como «Lado» (TalkBack lee el nombre completo); buscador con
  un texto más corto.
- **Visor** (10): mientras carga la foto se ve su miniatura con «Cargando foto…», en lugar de la pantalla negra.
- **Indicaciones de la cámara con sujeto** (13): la corrección principal, en una frase con quién la hace
  («Paciente: baje la barbilla 5°», «Operador: incline la cámara hacia abajo»; los lados, de la imagen), también
  para TalkBack; y una ayuda que explica los iconos.
- **Accesibilidad** (14): botones de giro y espejo de la referencia y colores de la paleta de anotación de 48 dp.

## 2.66 — 26 de noviembre de 2026

- **Ojo solo = el segmento del ojo del diagrama de la cara**: se comprobó frente al análisis de la cara entera en
  la foto original (23 ojos): contorno del ojo a 0,03 anchos de ojo, ceja 0,05, ojera 0,04, bolsa 0,04, valle
  lagrimal 0,02, patas de gallo 0,13, con el mismo número de hallazgos. El relleno que completa la «cara» bajo el
  ojo ya no cuenta como piel (ninguna referencia, como la mejilla de la ojera, ni línea lo usa) y el surco
  nasogeniano ya no sale en el ojo solo (no es del ojo).

## 2.65 — 26 de noviembre de 2026

- **Hallazgos de la piel solo donde son posibles médicamente**: las arañitas (telangiectasias, vasos superficiales
  de una dermis fina) ya no se buscan en las plantas ni en las palmas (piel gruesa sin pelo), en el cuero cabelludo
  con pelo ni en las uñas (no son piel: el esmalte o el lecho rosado parecían manchas o arañitas, en pies y manos);
  las várices, solo en el miembro inferior y nunca en la planta; la celulitis, solo en glúteos y muslos. Dentro del
  ojo y en el bermellón de los labios no se buscaban ya.

## 2.64 — 26 de noviembre de 2026

- **Ombligo y pezones también en primeros planos** del abdomen o del tórax (sin hombros ni caderas a la vista, lo
  habitual en la consulta): se buscan solo con la piel, como manchas redondas más oscuras que su alrededor en todas
  las direcciones (los surcos abdominales, alargados, no cuentan), a varias escalas; el ombligo, junto a la línea
  media si el tronco entra entero; los pezones, la pareja simétrica a la altura que da la anatomía por encima del
  ombligo. Frente a marcas manuales en 11 fotos: ombligo bien en 7, mal en 1; los pezones solo cuando son claros
  (sin falsos, a costa de no darlos en algunas fotos).
- **Hallazgos de la piel sin falsos anatómicos**: el ombligo, las areolas y las axilas ya no salen como manchas ni
  arañitas, y tampoco las sombras pegadas al borde de la piel (bajo la mandíbula, el pliegue de la axila) ni las
  bandas alargadas. Las manchas falsas en fotos del cuerpo bajan a la mitad o menos; frente a máscaras de
  dermatólogo el acierto mejora (IoU mediana 0,73).
- **Pie con esqueleto como el de la mano**: articulación metatarsofalángica de cada dedo repartida por el ancho del
  antepié, una interfalángica en el dedo gordo y dos en los demás, metatarsianos hacia el mediopié y la «planta».
- **Cejas mejor ajustadas**: la malla las dejaba finas y altas (su borde inferior pasaba por el medio del pelo) y
  con la cola larga; se corrige su forma y se ajustan al pelo por columnas. Solapamiento con la ceja real (29
  cejas, máscaras por píxel): 0,41 → 0,44, mejor en 19 de 29. Afecta también a la altura de la ceja y de la frente.
- **Ojo en primer plano**: los párpados y el iris se afinan con el modelo de puntos del ojo de MediaPipe (Apache
  2.0) en el recorte que da la malla. Error del contorno (anchos de ojo): encuadre normal 0,046 → 0,042, primer
  plano 0,071 → 0,048, muy cerrado 0,108 → 0,063. Si el ojo llena el encuadre y no hay malla, el ojo se busca
  igualmente y se dibujan los párpados y el iris (sin medidas).

## 2.63 — 25 de noviembre de 2026

- **Tronco: axilas, pezones y ombligo como puntos de referencia** (abdomen frontal y tórax / mamas; las axilas,
  también de espaldas y de lado): se usan para alinear la toma con la referencia, se ven en el diagrama y miden:
  inclinación y separación de los pezones (respecto del ancho de hombros) y su altura, altura del ombligo y su
  desvío de la línea media, inclinación, altura y separación de las axilas. La axila es el vértice del hueco
  brazo–tronco en la silueta; ombligo y pezones, manchas redondas más oscuras (los pezones, también más rojas)
  que la piel en todas las direcciones, donde la anatomía los espera. Los pezones, solo si se ven los dos a una
  altura parecida y sobre piel (no ropa): mejor sin punto que con uno falso.
- **Uñas de las manos por el esqueleto y el color**: cada uña se busca en la falange distal (de la articulación a
  la punta) como la placa más clara y menos roja que la piel del propio dedo (o de otro color, con esmalte), con
  umbral adaptativo, y se dibuja con su forma (elipse orientada con el dedo) en la foto a más resolución. Frente
  al detector anterior, el mismo número de uñas por el dorso y la mitad de falsas en la palma (manos de palma con
  2 uñas o más: de 3 a 0).

## 2.62 — 25 de noviembre de 2026

- **Esqueleto del cuerpo al estilo de las manos** en pies, brazos, piernas, tronco (frente, lado, espalda, mamas,
  glúteos) y cuello: articulaciones, ejes, falanges transversales (anchos de la piel a alturas fijas de cada
  segmento) y medidas en grados y proporciones: ángulo del codo y de la rodilla, grosor / largo de cada segmento,
  inclinación de hombros, caderas, tronco y cuello, cintura / tórax y cintura / caderas, antepié largo / ancho y
  eje del pie. Brazo sin puntos del cuerpo: eje por la piel. En 335 fotos reales, 334 medidas en rangos anatómicos.
  Todas las zonas del cuerpo tienen ahora la tabla de medidas.
- **Alineador de color**: la referencia se toma solo de la piel (segmentación) y se descartan ropa, brillos,
  sombras, rojeces y tonos no naturales (rechazo de atípicos por la mediana); si el punto elegido a mano no
  parece piel natural, se avisa.
- **Ojo individual**: se mide con la sección del ojo de la malla facial (la foto y su espejo forman una cara):
  párpados, cejas, patas de gallo, ojeras, bolsas e infraorbitarias con escala por el iris.
- **Hallazgos de la piel (orientativos, capas nuevas)**: **manchas** (más oscuras y con más color que la piel
  vecina y con borde propio; frente a 80 lesiones con máscara de dermatólogo, IoU mediana 0,66), **arañitas**
  (trazos finos rojos), **várices** (muslos, piernas y pies: trazos azulados alargados) y **celulitis** (glúteos y
  muslos: superficie con hoyuelos y grado 0–3 por la textura). En la cara y el ojo, manchas y arañitas con medidas
  en mm (sin ojos, cejas ni labios). Arañitas, várices y celulitis son heurísticas, sin datos etiquetados para
  validarlas; la celulitis depende mucho de la luz. La **fibrosis no se detecta**: es un cambio de la dermis que
  una foto no muestra de forma fiable (necesita palpación o ecografía).

## 2.61 — 25 de noviembre de 2026

- **La pantalla de la cámara ya no se redibuja entera con cada cuadro analizado** (unas 5 veces por segundo). El
  cuerpo de la pantalla lee solo valores que cambian poco (coincide o no, pulsos, aviso del detector, disparo
  listo), y lo que cambia en cada cuadro (guía y su color, contorno del cuerpo, línea del pelo, puntos,
  flechas, correcciones, anillo del disparador, aviso de luz) se dibuja en ámbitos propios. La vista previa, los
  controles, la barra de zoom y el resto de la pantalla ya no se recomponen ni relanzan sus tareas por cuadro (el
  zoom asistido y la guía adaptada leen el análisis fuera de la composición). Menos trabajo en el hilo principal
  mientras se encuadra: menos tirones.

## 2.60 — 25 de noviembre de 2026

Revisión integral, tanda 4: **escalabilidad, recursos y errores**.

- **Lista de expedientes**: una sola consulta a la galería para todos (antes una por expediente) y los protocolos
  leídos una vez; el recuento de tomas por protocolo se guarda por expediente, así un arranque en frío no abre
  todas las fotos para leer sus metadatos.
- **Espacio**: al borrar un expediente se borran también sus exportaciones y las imágenes de nombre no reconocido,
  y los datos privados de sus fotos aunque el sistema pida confirmación; una vez al día se apartan los datos de
  fotos que ya no existen y se borran a los 30 días; las cachés de análisis tienen límite y las cargas grandes no
  desplazan las miniaturas de la memoria.
- **Errores que ya no se pierden ni cierran la app**: importar sigue con las demás fotos si una falla (y avisa si
  se interrumpe); la migración de la versión 1 sigue con las demás; el análisis en vivo deja rastro de sus
  errores; las lecturas de metadatos de Comparar no cierran la app; un XMP demasiado grande no se intenta
  escribir.
- **Método**: la indicación de enderezar la cabeza en los oblicuos salía al revés (signo del alabeo del modelo 3D,
  comprobado con 274 caras) y la semilla del ajuste de la pose también; el arco de Cupido se mide en el eje
  vertical de la cara (antes incluía el desplazamiento lateral de los picos); dos pies en la misma mitad de la
  imagen ya no mezclan sus puntos; la igualación de color se quita al cambiar la selección (podía aplicarse una
  hecha contra otra referencia).
- **Pruebas**: procesos de prueba más cortos y sin el compilador residente (agotaban la memoria).

## 2.59 — 25 de noviembre de 2026

Revisión integral, tanda 3: **rendimiento de la cámara, el visor y Comparar** (mismos resultados, menos trabajo).

- **Cámara**: la guía de la cara (deformación de ~140 puntos) solo se recalcula si la cara se mueve, no con cada
  cuadro; los pies de la máscara se analizan una vez (antes hasta tres por cuadro) y la máscara girada con la guía
  se gira una vez; la comprobación de la mano recorre cajas precalculadas.
- **Comparar**: el color igualado se aplica con tablas (y una tabla 3D con la curva de tono) en lugar de ~12
  potencias por píxel; las fotos corregidas se decodifican más pequeñas cuantas más se ven (1080 → 540 px) y de dos
  en dos (con 12 fotos había riesgo de quedarse sin memoria); tocar la pantalla ya no la recompone entera.
- **Números de las medidas**: se colocan una vez por conjunto de líneas y zoom (antes en cada cuadro de dibujo,
  también al hacer zoom), sin esquivar las celdas del mapa del cuero cabelludo.
- **Trazo a mano**: se mide solo esa línea (antes se repetía el análisis completo de arrugas de la cara).
- **Visor y arranque**: giro, recorte y lecturas de archivos fuera del hilo principal; las cachés de la versión
  anterior se apartan al instante y se borran en segundo plano, y los cierres registrados por el sistema se leen
  en segundo plano (el aviso sigue apareciendo).

## 2.58 — 25 de noviembre de 2026

Revisión integral (10 revisores), tanda 1: **integridad de los datos clínicos**.

- **Los modelos nativos ya no se quedan bloqueados**: si se salía de una pantalla justo cuando un análisis
  conseguía su turno, la puerta de los modelos quedaba tomada y ningún análisis, alineación ni medida volvía a
  ejecutarse hasta reiniciar la app.
- **Ediciones simultáneas de la misma foto, de una en una**: trazos, anotaciones, metadatos XMP y fecha de
  captura se leen, cambian y guardan bajo un bloqueo por foto. Antes dos toques rápidos (o el visor y Comparar
  a la vez) perdían el primer cambio o reescribían el JPEG a la vez.
- **Archivo del expediente atómico y recuperable**: se escribe en un temporal con fsync y se renombra; si uno
  antiguo está dañado, se reconstruye con los datos de sus fotos (antes el expediente desaparecía de la lista).
  Igual para protocolos, memoria de líneas y relieve.
- **La toma no se pierde si falla el guardado en la galería**: se conserva en la app y se guarda sola al volver a
  abrir la cámara del expediente (antes se borraba).
- **Borrar fotos**: sus datos privados (relieve, trazos, anotaciones, ediciones) se borran solo cuando el borrado
  se ha confirmado (antes se perdían si se cancelaba; en Android 10 la foto ni se borraba). Al borrar un
  expediente se limpian también (antes quedaban huérfanos).
- **«Regenerar» y «Borrar anotaciones» ya no se deshacen solos** cuando no se puede reescribir la foto.
- Los fallos al escribir el XMP quedan en el registro de acciones.

Tanda 2: **modelos nativos y cámara**.

- **La cámara abre sin congelarse**: la malla facial se carga en el primer análisis (con la puerta de los
  modelos), no al dibujar la pantalla; y los motores en vivo se cierran también con la puerta.
- **Referencia en la cámara sin bloqueos**: el análisis en vivo ya no se para hasta 30 s esperando turno, y
  mientras se aplica la referencia nueva no se compara con la anterior (antes podía dispararse alineado con la
  foto equivocada).
- **La mejor foto de la ráfaga se juzga sola**: segmentación, manos y rostro de la propia foto, sin los votos ni
  la segmentación del vídeo en vivo (que además quedaba alterado). Si alguna no se puede puntuar, se elige la más
  nítida (antes, la primera).
- **El flash ya no cancela la foto**: durante la toma con flash solo se exige que el paciente siga en el cuadro.
- **ML Kit no se cierra con una tarea en curso** (se libera al terminar) y deja de ejecutarse en cada cuadro sin
  cara (uno de cada cinco).
- **Menos memoria y trabajo**: una sola malla facial y un solo segmentador de pelo y piel compartidos para las
  fotos (antes uno nuevo en cada análisis, alineación, relieve o importación), y la línea del pelo en vivo
  reutiliza la segmentación del cuadro. Un análisis cuya pantalla ya se cerró suelta el turno sin ejecutarse.

## 2.57 — 25 de noviembre de 2026

- **Capilar frontal (cabeza inclinada): la distancia punta nasal–mentón es criterio de alineación con
  peso.** Relativa a la distancia interpupilar, con la cabeza flexionada se acorta mucho más que la anchura
  de los ojos (modelo 3D: 1,42 a nivel, 0,97 a 30°, ≈ 0,023 por grado). Sin referencia se pide ≈ 30°
  (±0,12); con la toma anterior del paciente, su mismo valor ±6 % (≈ ±2,5°). Si no se cumple la foto no se
  dispara, la calidad baja según el desvío y se indica «inclinar más hacia delante» o «inclinar menos».
- **Cuero cabelludo: las líneas amarillas** eran los bordes de la franja central donde se mide la raya. Ahora
  es una banda azul clara («Franja de la raya») que al tocarla explica qué es y muestra el cuero visible en el
  centro, a los lados y la diferencia; solo en el vértice y en la frontal inclinada (en la coronilla y los
  parietales no hay raya central).
- **Cierre al analizar una foto del rostro** (p. ej. frente y glabela) corregido: una medida degenerada (NaN)
  cerraba la app al guardar los trazos confirmados. Ahora esas medidas no se dan, los puntos no finitos se
  descartan y, si el guardado falla por cualquier otra causa, se registra sin cerrar la app.
- **Cierre en código nativo tras el relieve** (SIGBUS en los hilos de MediaPipe y ML Kit): el análisis en
  vivo de la cámara seguía usando los modelos mientras se analizaba la foto recién tomada. Ahora también
  pasa por la puerta de los modelos nativos: mientras se analiza una foto, la cámara se salta cuadros en
  lugar de correr dos modelos a la vez (y la referencia de la cámara espera su turno).

## 2.56 — 24 de noviembre de 2026

- **Comparar**: las tomas capilares (vértice, coronilla, parietales) tienen la tabla de medidas de la densidad
  capilar aparente, foto a foto, como el visor.
- **Tipo básico BASP de la línea frontal recalibrado con retratos reales** (CelebA, línea retrocedida
  etiquetada por personas): la altura de la línea en el centro sobre las pupilas, en distancias
  interpupilares, separa las líneas retrocedidas con una exactitud equilibrada del 77 % (antes, con la
  altura sobre las cejas, el 50 %). Nueva medida «Pupilas → pelo (centro) / interpupilar». La forma en M
  (entradas) sigue con los umbrales de la escala: esas fotos no resuelven las sienes.
- **Densidad de la vista frontal inclinada validada** con las máscaras anotadas de 5 fotos de hombres con
  alopecia frontal: error medio de 6 puntos.

## 2.55 — 24 de noviembre de 2026

- **Densidad capilar aparente** en las tomas capilares (vértice, coronilla, parietales y frontal con la cabeza
  inclinada), en el visor:
  - **Cuero cabelludo visible** entre el pelo, en toda la zona, en el centro y en la periferia: cada píxel de
    la cabeza combina la segmentación (pelo o piel) con su color entre el del pelo y el de la piel del propio
    paciente, como HairComb (JAMA Dermatology 2023) y la automatización de la escala SALT. Validado con las
    máscaras anotadas de 10 fotos cenitales de hombres y mujeres con alopecia: correlación 0,93 con la
    cobertura anotada, error medio ≈ 10 puntos.
  - **Raya**: cuero visible en la franja central frente a los lados (lo que valoran Sinclair y Olsen). El
    ancho de la raya en mm no se da: con pelo claro o brillos salía varias veces el real.
  - **Grados orientativos**: BASP específico V (vértice) y F (frontal), Hamilton‑Norwood de la coronilla,
    Ludwig y Sinclair; y en la línea del pelo del rostro frontal y de la toma inclinada, el **tipo básico
    BASP** (L, M1–3, C1–3, por el retroceso de las entradas frente al centro) con su Norwood aproximado.
  - Capas «Cuero cabelludo visible» (contorno y zonas donde se ve más cuero que pelo) y «Raya», y la ficha
    de medidas. No son pelos por cm² (eso es tricoscopia) ni un diagnóstico: sirven para seguir al mismo
    paciente con tomas iguales (luz, peinado, cabello seco).

## 2.54 — 24 de noviembre de 2026

- **Toma capilar «Frontal (cabeza inclinada)» con la malla facial completa**:
  - **Guía adaptada a la cara**: el contorno del modelo 3D se encaja en la cara del paciente por los ojos
    de la malla (escala, giro y posición), como las guías del rostro: en la primera toma, en cuanto la cara
    está dentro del marco; en las siguientes, sobre la cara de la toma anterior. La frente, la línea de
    implantación y la raya siguen la proyección a 30°: si la cabeza no está bastante inclinada, la marca
    de la línea de implantación no coincide con la del paciente.
  - **Distancia en cm** (por la distancia interpupilar), como en las tomas del rostro, y la de la toma
    anterior como objetivo.
  - **Visor y Comparar**: solo contorno, línea del pelo, ojos y cejas; ya no se miden arrugas, surcos ni
    proporciones sobre la cara escorzada. Las alturas frente → pelo y las entradas se corrigen por el
    cabeceo medido en la malla, para que sean comparables con las del rostro frontal.

## 2.53 — 24 de noviembre de 2026

- **Nueva toma capilar: «Frontal (cabeza inclinada)»**: de frente, con la cabeza inclinada hacia delante
  (≈ 30°, barbilla al pecho) y la cámara a la altura de la cara, para ver la línea de implantación frontal,
  las entradas, la raya y la mitad anterior del cuero cabelludo. La guía se proyecta desde el mismo modelo
  3D de la cabeza que las demás vistas capilares (línea frontal en la marca, raya en la línea media, cara
  escorzada abajo). La cámara comprueba con la malla facial que la cara esté de frente, sin girar ni
  ladear, y que la inclinación hacia delante sea la pedida (pide «bajar» o «subir» la barbilla); no exige
  mirada al frente ni expresión (los ojos miran abajo), y avisa si una gorra o un pañuelo tapan la frente.
  Se mide la línea de implantación como en el rostro frontal. Es la primera toma del protocolo de
  Tricología.

## 2.52 — 24 de noviembre de 2026

- **Pies**: el suelo (arena, alfombra o baldosas que la segmentación toma por ropa u otros) ya no se
  confunde con calzado que tapa los dedos. Dorso de los pies de 60 % a 78 % con fotos verificadas.
- **Espalda**: una espalda musculosa con los brazos ya no se toma por un primer plano de pies (83 %).

## 2.51 — 24 de noviembre de 2026

- **Glúteos**: con las rodillas fuera del cuadro (lo habitual en esta toma) la región se calcula con la
  altura del tronco; la ropa interior mínima (tanga) se acepta, una braga o un bañador no; la nuca a la
  vista indica que la persona está de espaldas. Con fotos verificadas, de 49 % a 80 %.
- **Cuero cabelludo**: si el detector de caras ve los ojos, no es una vista del cuero cabelludo (antes un
  retrato con mucho pelo en el centro podía dispararla). Coronilla 86 %, vértice 83 %.
- **Espalda**: sin la cabeza en el cuadro no se pide «De espaldas a la cámara» (el detector supone la
  orientación); hombros y caderas nivelados con 10° de tolerancia. Espalda 81 %.

## 2.50 — 24 de noviembre de 2026

- **Precisión por toma medida frente a la verificación de la IA**: cada foto de referencia (de internet y de
  conjuntos clínicos públicos: dermatología, alopecia de Norwood, manos 11k) se revisa a mano según la toma
  pedida, y se mide para cada toma la exactitud equilibrada (fotos correctas aceptadas y fotos incorrectas o
  de otras tomas rechazadas). Superan el 80 %: rostro frontal, oblicuos, perfiles, tercio superior,
  periocular, ojos, labios, cuello, vértice del cuero cabelludo, abdomen frontal, tórax, espalda, muslos y
  manos.
- **Oblicuo del rostro**: se acepta con la punta nasal alineada con la mejilla (la definición clínica del
  oblicuo de 45°) aunque la malla estime menos giro; antes casi ningún oblicuo real se aceptaba.
- **Cabeceo (plano de Frankfort)** calibrado con retratos frontales (el cero del modelo 3D no estaba en el
  plano de Frankfort) y tolerancias de giro e inclinación ajustadas al ruido de la medida.
- **Nada tapa la cara**: «Quítese las gafas», «Descubra la frente (sin gorra, sombrero ni pañuelo)» y
  «Retire la mano de la cara».
- **Expresión neutra**: una sonrisa o la boca abierta impiden el disparo (también con «casi alineada»); las
  cejas de reposo ya no se toman por cejas levantadas.
- **Cuello y escote**: distancia de la toma (acérquese / aléjese), mentón al frente y escote descubierto por
  debajo de los hombros (una camiseta ya no pasa).
- **Espalda y tórax**: la espalda con un brazo tocando el borde vale; el tórax se puede tomar sin las
  caderas en el cuadro.

## 2.49 — 24 de noviembre de 2026

- **Corregido: cierre en código nativo** (tras el relieve y el análisis de una foto). Cada análisis de una
  foto, alineación o medida de la línea del pelo creaba y cerraba a la vez los detectores de cara y de
  cuerpo de ML Kit aunque no se usaran, y si una detección tardaba más de la cuenta el detector se cerraba
  mientras ML Kit seguía trabajando en sus hilos. Ahora solo se crean si se usan y no se cierran con una
  tarea en curso.
- **Contorno real en las zonas del cuerpo**: la silueta de la persona (borde de la piel o de la ropa)
  recortada a la zona, con el eje de cada miembro y sus articulaciones. Antes era un trapecio entre hombros
  y caderas con bloques en los miembros, y de espaldas sin puntos no se dibujaba nada. El contorno del pie
  se corta a la altura del pie (no sube por la pierna).
- **Más puntos de referencia**: en las manos, el fondo de cada espacio entre dedos y los bordes de la
  muñeca; en los pies, la punta de cada dedo (con los cinco distinguidos); el tronco (abdomen, tórax,
  espalda) a seis alturas; en el ojo, cuatro regiones más (iris y pupila, párpado inferior y ojera, canto
  interno y pliegue del párpado superior).
- **Diagrama del cuerpo en el visor y en Comparar** (antes solo la cara tenía diagrama): capas de
  contorno, ejes y articulaciones, dedos (esqueleto de cada dedo y la palma, o la separación entre los
  dedos del pie), uñas y puntos de referencia, en el desplegable «Diagrama del cuerpo».

## 2.48 — 24 de noviembre de 2026

- **Captura más rápida**:
  - La espera de quietud antes de la primera foto baja de 0,7 a 0,5 s y mide la velocidad del movimiento
    (según el tiempo real entre cuadros), no el desplazamiento entre cuadros: en las zonas del cuerpo, con
    el análisis más lento, el temblor normal de la mano reiniciaba la cuenta una y otra vez. Un golpe de
    temblor suelto (que vuelve a su sitio) tampoco la reinicia. Moverse de verdad sigue impidiendo la foto.
  - La ráfaga de 3 tomas (para evitar repetir por un parpadeo) solo en las zonas del rostro: en el cuerpo
    se hace una toma, y hay que estar quieto mucho menos tiempo.
  - En cuanto termina la exposición se detiene el análisis en vivo, así la foto se elige y se guarda antes.

## 2.47 — 23 de noviembre de 2026

- **Corregido: palma y dorso de las manos estaban invertidos** con los puntos del detector de manos. Con
  fotos reales (11k Hands y Pexels) la etiqueta derecha/izquierda del detector, en los cuadros sin espejar
  de la cámara, es la mano real; la app la interpretaba como si viniera espejada, así que pedía «muestre el
  dorso» enseñando el dorso. Ahora, con 14 pares de dorsos y 8 de palmas, se aceptan 12 y 6 (las demás por
  encuadre) y ninguna palma pasa por dorso ni al revés.
- **Manos que no pasan por pies ni por brazo**: los surcos hondos entre dedos (o una mano que domina el
  cuadro) descartan las zonas de pies y el brazo en primer plano.

- **De frente o de espaldas, fiable**: se decide por el lado de cada hombro que da el detector del cuerpo
  (de frente el hombro izquierdo queda a la derecha de la imagen; de espaldas, a la izquierda) y, si están
  superpuestos, por la cabeza en la segmentación (cara o pelo). Antes se usaba la «nariz», que el detector
  supone también de espaldas: las fotos de espalda se rechazaban con «De espaldas a la cámara» y el tórax
  las aceptaba. Con fotos reales de culturistas, ningún error.
- **Brazo**: exige la vista posterior del protocolo (de frente avisa «De espaldas a la cámara").
- **Tórax**: un sujetador o un bikini a la altura de las areolas pide descubrir el tórax.
- **Espalda**: la cintura del pantalón ya no cuenta como ropa sobre la espalda.
- **Pies juntos por las piernas** (rodillas o tobillos que se tocan): se separan en dos pies. El margen
  lateral se comprueba en el pie, no en la pierna que sale por el borde.
- **Menos disparos por error**: un primer plano de una mano o de unos pies ya no se toma como espalda,
  glúteos o brazo. Entre zonas, de 63 a 57 falsos disparos en 3974 casos.

## 2.46 — 21 de noviembre de 2026

- **Manos sin el detector de manos**: cuando no encuentra las manos (primeros planos, algunas luces), se
  evalúan por la piel: mano entera, dedos extendidos (contorno en nube con surcos hondos, que la distinguen
  de un pie) y palma o dorso por las uñas. Antes la toma se quedaba en «Buscando las manos…».
- **Brazo en primer plano sin puntos del cuerpo**: se evalúa por la piel (brazo entero, codo recto, sin
  manga). Antes pedía siempre «Encuadre el brazo entero».
- **Primeros planos del rostro**: sin el aviso de luz desigual entre mejillas (solo en el rostro frontal) y,
  en los labios, sin exigir la mirada al frente.
- **Más precisión en las zonas que no son el rostro frontal**: el ojo, el cuero cabelludo, los labios, la
  zona periocular y la frente disparaban con casi cualquier escena quieta (también con fotos de manos o
  pies). Ahora se comprueba el contenido (piel de la cara que llena el cuadro, pelo en el cuero cabelludo,
  un solo ojo, labios sin los ojos debajo; cuello centrado del mentón a los hombros; espalda y glúteos sin
  cara). Falsos disparos con fotos de otras zonas: de 1174 a 63 de 3974, con pocas pérdidas en las fotos
  propias. Nuevos avisos: «Acérquese: encuadre solo la zona», «Acérquese: encuadre un solo ojo», «Encuadre
  los labios», «Encuadre el cuero cabelludo…».
- **Luz en oblicuos y perfiles**: se mide el lado de la cara que mira a la cámara (la sombra del lado lejano
  es propia de la vista y no indica poca luz).
- **Avisos de luz insuficiente o excesiva** que faltaban: en el rostro con foto anterior de referencia (antes
  la luz solo se medía en los protocolos) y en manos, pies y cuerpo (no se medía nunca; ahora sobre la piel
  de la zona: «Poca luz en la zona», «Demasiada luz en la zona: sin flash», también con mucha piel
  quemada). Con poca o demasiada luz no dispara la captura automática.
- **Mensajes de corrección revisados**:
  - En el modo simple solo se veían las indicaciones con icono: las de manos, pies y cuerpo (falanges,
    dedos, uñas, codo, rodillas, ropa, centrado…) no se mostraban y la cámara no disparaba sin decir por
    qué. Ahora la principal se muestra como texto corto sobre la imagen (y «+N» con las demás).
  - «Mano / Pie a la izquierda de la imagen» en lugar de «Mano de la izquierda» (se confundía con la mano
    izquierda del paciente, que con la cámara trasera sale a la derecha).
  - «Centre la zona» enciende las flechas del borde hacia donde mover el teléfono y tiene su icono.
  - Mientras se confirma que la zona ya cumple se muestra «Comprobando la zona…» (antes seguía el último
    aviso, ya resuelto); las correcciones van antes que los mensajes de estado.
- **Captura más rápida**: la ráfaga de 3 tomas se pide de golpe (antes cada toma esperaba a que la anterior
  se guardara) y solo hay que estar quieto hasta que se hacen las exposiciones (una vibración corta avisa);
  la elección de la mejor y el guardado siguen después. El disparo automático espera menos (0,15 s) y no
  vuelve a enfocar si ya enfocó hace menos de 2 s (antes hasta 2,5 s cada vez); el sensor de profundidad
  se lee a la vez que la toma.
- **Protocolos con el contorno del cuerpo**: la referencia de una toma de manos, pies o miembros guarda
  su contorno (silueta, separación entre los dedos del pie, contorno de cada miembro) y la cámara lo usa
  como guía en lugar de la ilustración de stock. Ya no se analizan surcos faciales en esas fotos.
- **La guía es la captura anterior**: tras cada toma, la referencia pasa a ser la foto recién tomada (antes
  se quedaba en la primera) y su contorno es la guía aunque la foto no esté superpuesta o la asistencia esté
  apagada (antes volvía al contorno de stock).
- **Contorno del pie alineado con lo que se ve**: el contorno dibujado usa la segmentación más reciente
  (cada 0,2 s en los pies) y la fusión de máscaras solo se aplica con la escena quieta (al mover la cámara
  el contorno iba hasta 1,5 s por detrás). Con la foto anterior superpuesta se dibuja el contorno de esa foto
  (blanco, el mismo detector que en vivo) en lugar de la guía deformada por una silueta que, con fondos no
  lisos, no coincidía con la foto; se alinea haciendo coincidir los dos contornos.
- **Uñas como referencia** en pies y manos (dorso): las uñas claramente visibles confirman cada dedo del
  pie, distinguen el dorso de la planta («se ven las uñas; muestre la planta») y el dorso de la palma de la
  mano, y se marcan en el esquema. Las uñas naturales, poco contrastadas, no se usan para decidir (con
  fotos reales se confunden con las yemas).
- **Comparar fotos tomadas con la referencia girada**: cada toma guarda la foto de referencia usada y su
  giro (y espejo). Al alinear, el giro entre dos fotos se calcula por esa cadena y se deshace antes (la
  alineación solo busca giros pequeños); la foto se muestra alineada con el giro incluido.
- **Alineación en zonas del cuerpo verificada** con fotos reales de manos, pies, brazo, piernas, espalda,
  abdomen, glúteos y tórax: 44 de 44 alineadas (error < 1 % del ancho), también con la referencia girada.
- **Detección del pie estable entre cuadros** (la captura automática del pie casi nunca disparaba): la piel
  se decide por mayoría de las últimas tres segmentaciones (también la de la referencia, con tres luces);
  los puntos del pie se miden desde la punta y por la mitad de la imagen de cada pie; los bordes de contorno
  tienen una tolerancia algo mayor que los puntos anatómicos. Con cuadros casi iguales de fotos reales: el
  veredicto ya no cambia entre cuadros (antes 5 de 70) y coinciden 38 de 50 (antes 42 de 70 cuadro a
  cuadro, con saltos de puntos de hasta 1,6 anchos de pie).
- **Análisis facial solo en el rostro**: en Comparar y en el visor ya no se buscan surcos, arrugas ni
  rasgos faciales en fotos de manos, pies o cuerpo (ni se espera ese análisis para alinear).
- **Guía con la foto anterior girada**: la guía se gira con la referencia también cuando la foto anterior
  está superpuesta (antes se quedaba vertical o se deformaba con los puntos de la foto girada y no
  coincidía). En manos, pies y miembros, el contorno de lo que capta la cámara se ve también sobre la
  foto anterior, para comprobar que cuadran.
- **Dedos del pie**: se delimitan con el contorno «en nube» (una curva por dedo y un valle entre cada
  dos), el surco entre dedos que nace de cada valle con su inclinación (el borde oscuro de cada dedo) y,
  desde el dedo gordo, los surcos de dedos muy juntos. La guía dibuja la separación y la punta de cada
  dedo, y se pide «que se vean los dedos» si se distinguen menos de 3. Con fotos reales de pies: 5 dedos
  exactos en 12 de 17.
- **Pies: dedos** comprobados de forma fiable: el extremo de cada pie entero y al aire (sin calcetín,
  ropa ni calzado); ya no se exige contar cada dedo en la silueta, que con fotos reales fallaba.
- **Veredicto estable**: los criterios de la zona deciden por mayoría de los últimos análisis; las
  indicaciones ya no cambian una y otra vez con la misma imagen, y los pies se calculan una vez por
  máscara.
- **Zona centrada** en el cuadro (manos, pies, brazo, piernas, tronco…): si no, «Centre la zona».
- **Más puntos de referencia** en manos (21 por mano), pies (punta, centro y bordes) y brazos, piernas
  y tronco (bordes a ¼, ½ y ¾): la alineación con la referencia usa muchos más puntos.
- **Giros de 90° de la guía**: el contorno ajustado ya no se gira con la guía (quedaba atravesado sobre
  la mano) y los criterios de manos, pies y cuerpo se miden en la orientación de la guía girada.
- **Una sola mano, pie o pierna**: con el selector Derecha / Izquierda ya no se piden las dos. El
  brazo se reconoce también de espaldas (el detector invierte los lados) y en primer plano (sin cadera).
- **Botón de disparo automático** (barra superior): ya funciona también en la primera toma, sin foto
  previa ni referencia (antes quedaba desactivado).
- **Contorno de ajuste de la mano**: sigue el borde real de la mano que se ve (antes iba por el eje de
  los dedos y quedaba más estrecho); el grosor de brazos y piernas se mide en la piel.
- **Siluetas flexibles en manos, pies y cuerpo**: la guía ya no es un dibujo fijo; se ajusta a la mano,
  los pies o el cuerpo detectados (largo y ángulo de cada dedo, giro de la mano, ángulo del brazo).
  Solo se exige lo clínico: que se vean **todas las falanges** (sin dedos recogidos, puño ni dedos
  ocultos), que se vean **los dedos de los pies** (no un calcetín) y la postura general (márgenes más
  amplios para hombros, codo, rodillas y separación del brazo).
- **Pantallas principales** (Expedientes y Protocolos) con el logo y el nombre de la app y debajo la
  sección. El buscador de expedientes pasa bajo la barra: ya no queda recortado por arriba.
- **Protocolos: luz por defecto** en cada toma nueva de rostro (luz mínima 35 % y diferencia entre
  mejillas máx. 15 %). Al usar una foto como referencia se mide su luz y la toma pide esa misma luz
  (±15 puntos; mejillas con 8 puntos de margen).
- **Miniatura de la referencia** en cada toma: la foto si está en el expediente elegido o, si no
  (protocolo importado), un esquema con sus contornos y surcos.
- **Tomas plegables** en el editor de protocolos, con un resumen de una línea.
- **Selector de expediente** de las fotos de referencia con la miniatura de cada paciente (la misma de
  la pestaña Expedientes).
- **Disparo automático activado por defecto** (Ajustes, cámara y tomas nuevas de protocolos).
- **Disparo automático más seguro**: desde el disparo hasta que la foto está lista para guardarse se
  vigila que la cara siga en el encuadre y la posición; si se pierde, la foto se cancela y se pide al
  paciente que se mantenga quieto. El aviso de enfoque ya no retrasa el disparo.
- **Plantillas de sesión → protocolos**: todo es un protocolo. Las plantillas predefinidas aparecen
  como protocolos (editables) y las propias se convierten solas; las zonas elegidas manteniendo
  pulsado se guardan como protocolo.
- **GIF**: ahora hay cuadros 100 % «antes» y 100 % «después» (sin franja ni línea) y se reproduce de
  ida y vuelta: al repetirse en bucle no se nota el salto. La vista previa sigue el mismo guion.
- **Exportar en MP4** (H.264, 30 cuadros/s), con la misma ida y vuelta y los mismos tiempos; cada
  cuadro con el avance exacto de su instante (movimiento continuo).
- **Transiciones más fluidas**: el GIF pasa de ~14 a 25 cuadros/s y el movimiento acelera y frena
  suavemente (antes arrancaba y paraba de golpe y el MP4 repetía los pasos del GIF de forma desigual).
- **Menú de exportación**: cambiar de opción ya no lo devuelve arriba.
- **Comparar**: interruptor para encender y apagar el ajuste de color junto al de alineación, y
  botón «Confirmar surcos» bajo las fotos (antes solo dentro de la tabla de medidas).
- **Criterios clínicos por zona del cuerpo** (la toma no se dispara sin cumplirlos): manos completas
  con los cinco dedos extendidos y la cara pedida (palma o dorso), los dos pies completos, abdomen,
  tórax, espalda, glúteos, muslos, brazo y cuello descubiertos, brazo con el codo extendido y separado
  ≈ 20° del cuerpo, rodillas extendidas, de lado / de espaldas cuando toca. Nuevo detector de manos
  (MediaPipe Hand Landmarker). Probado con dibujos propios de cada toma correcta e incorrecta.
- **Captura automática verificada en las 30 zonas** con fotos reales (ver `docs/CAPTURA_AUTOMATICA.md`),
  con estas correcciones:
  - Espalda, glúteos, manos, pies, cuero cabelludo, ojo, modo libre y primeros planos sin cara
    detectada: la primera foto se dispara con el encuadre quieto (antes nunca se disparaba).
  - Con referencia: en zonas sin puntos coincide por la geometría de los bordes (antes casi nunca en
    piel, pelo o manos) y ya no espera puntos que la referencia no tiene.
  - Mirada medida respecto del centro de los párpados (antes una mirada al frente salía «hacia arriba»)
    y exigida solo de frente; perfil a partir de 55° (MediaPipe no estima más) y del lado correcto; una
    foto frontal ya no se acepta como perfil; luz pareja entre mejillas solo de frente.
- **Luz del rostro**: ya no se queda «midiendo». En primeros planos (labios, ojo, frente) o con la
  cara parcial se mide por regiones; sin pupilas se usan los contornos de los ojos. La luz pareja entre
  mejillas solo se pide en la vista frontal (en oblicuas y perfiles cada mejilla recibe luz distinta).
- **Logo en las exportaciones**: se adjunta en Ajustes (PNG con transparencia recomendado) y la
  esquina se elige al exportar (se recuerda); va en fotos, cuadrículas, GIF y MP4.
- **Colores de la marca**: paletas tonales calculadas a partir de los colores del logo (verde
  azulado, azul y azul marino), con contraste WCAG AA comprobado en claro y oscuro.
- **Modo oscuro** elegible en Ajustes: según el sistema, claro u oscuro.
- **Idiomas: español e inglés** (Ajustes > Idioma, o el idioma de la app de Android 13+). Toda la
  interfaz, las indicaciones de la cámara y los documentos legales; los nombres de líneas y medidas
  se guardan en español y se muestran traducidos.
- **Tema**: solo la referencia oficial de Material Design 3 (tokens de Material Web).
- **Cámara**: ya no vibra sin parar en la revisión de la foto recién tomada.

## 2.45 — 21 de noviembre de 2026

- **Sensibilidad exhaustiva** de la detección de arrugas (Ajustes): además busca surcos accesorios junto
  al nasogeniano y la marioneta, más líneas en la frente y hasta 5 infraorbitarias por lado. En la
  partición de prueba de FFHQ-Wrinkle: exhaustividad 0,484 → 0,515 (precisión 0,625 → 0,580). La
  sensibilidad equilibrada (de serie) no cambia: ninguna de estas búsquedas sube la exhaustividad sin
  bajar algo la precisión (ver `docs/BENCHMARK.md`).

## 2.44 — 21 de noviembre de 2026

- **Arrugas**: la detección libre genera sus candidatos con el mapa de textura del artículo de
  FFHQ-Wrinkle (escala fina), sin entrenamiento. En la partición de prueba del artículo: F1 estricto
  0,280 → 0,294, F1 de 6 regiones 0,52 → 0,55, líneas infraorbitarias 0,61 → 0,67, con la misma
  precisión; análisis algo más rápido (425 → 393 ms por cara).

## 2.43 — 21 de noviembre de 2026

- **Arrugas: detección libre** además de los pasillos fijos: mapa de crestas en toda la piel (sin ojos,
  cejas ni labios), con la luz normalizada, en cualquier orientación.
- **Puntuación de cada línea candidata** (contraste, fuerza, longitud, orientación esperada en la zona y
  rectitud) antes de elegir las de cada zona.
- **Ceño** más preciso: fuera del borde de las cejas y solo líneas casi verticales.
- **Capas nuevas**: líneas infraorbitarias (bajo el ojo) y periorales (labio superior e inferior).
- **Sensibilidad de la detección** en Ajustes: conservadora, equilibrada o exhaustiva.
- **Benchmark repetido** en la partición de prueba del artículo de FFHQ-Wrinkle: F1 estricto 0,234 →
  0,280 (+20 %), ceño 0,24 → 0,30, patas de gallo 0,40 → 0,45, infraorbitarias 0,61 (ver
  `docs/BENCHMARK.md`).

## 2.42 — 21 de noviembre de 2026

- **Visor**: en la barra, Anotar, Pantalla completa, Compartir y ⋯ (Comparar, Girar / espejo /
  recorte, Información, Eliminar); el título ya no se aplasta. Al anotar se cierra la tabla de medidas
  y la barra de herramientas se desplaza si no cabe (nunca queda cortada).
- **Inicio**: buscador en la barra superior (sin el título grande); en horizontal, menú lateral en lugar
  de la barra inferior (antes no se veía ningún expediente).
- **Expediente**: ficha del paciente en una línea (al tocarla, notas y álbum); plantillas y protocolos
  en «Iniciar sesión de fotos», plegado si ya hay fotos: las fotos aparecen arriba.
- **Opacidad** de trazos y números como chip («50 %») en la fila de capas, en el visor y en Comparar
  (una fila menos sobre la foto).
- **Benchmark** también con la partición de prueba del artículo de FFHQ-Wrinkle, experimentos de
  resolución y sensibilidad, brechas del conjunto y recomendaciones (ver `docs/BENCHMARK.md`).

## 2.41 — 21 de noviembre de 2026

- **Anotaciones con iconos en lugar de textos**: símbolos (punto, círculo, triángulo, cuadrado, X, cruz,
  estrella, línea, flecha, ángulos), lado del texto (auto, arriba, abajo, izquierda, derecha), texto,
  tamaño y grosor; sin la línea de ayuda. Cada icono conserva su nombre para TalkBack. El panel ocupa
  menos alto y deja más foto.
- **Benchmark del motor** frente a mediciones manuales publicadas (ver `docs/BENCHMARK.md`): arrugas y
  surcos con FFHQ-Wrinkle (100 fotos) y puntos y proporciones con AFW / 300-W (178 caras).
- Recorrido de capturas de todas las pantallas (prueba `ScreenTourTest`) para revisar el diseño.

## 2.40 — 21 de noviembre de 2026

- **Línea mandibular** (capa nueva, en «Contorno externo»), como la línea del pelo:
  - de frente u oblicua: el borde inferior del contorno de ángulo a ángulo, con **nitidez del borde**
    (contraste cara / cuello y ancho de la transición del 10 al 90 %: menor = más definido),
    **rectitud** de cada lado (desviación hacia abajo respecto a la recta ángulo → mentón) y **ángulo
    goníaco** en el lado visible cuando la cara está girada;
  - de perfil (por la silueta del paciente): **ángulo cervicomentoniano** (rectas del submentón y del
    cuello; referencia ≈ 90–120°).
- **Papada** (capa aparte de la línea mandibular): de frente, el pliegue bajo el mentón con sus medidas
  de surco; de perfil, el contorno submentoniano con su convexidad. El análisis llega ahora más abajo
  del mentón.
- **Pestañas Expedientes y Protocolos**: la biblioteca de protocolos se reutiliza con cualquier
  paciente: crear, importar, editar, duplicar, compartir, eliminar y **Aplicar** (se elige el paciente y
  la cámara empieza). Al editar desde la biblioteca se elige de qué expediente salen las fotos de
  referencia.
- **Lista de expedientes en tabla**: miniatura con la última foto de la cara (solo ángulos faciales),
  nombre y número, fotos, ángulos, última toma y tomas pendientes de protocolos; se ordena tocando la
  cabecera de cada columna, se busca por nombre o número y se filtra (con / sin fotos, con tomas
  pendientes, últimos 30 días, sin fotos en 6 meses, sin consentimiento).

## 2.39 — 21 de noviembre de 2026

- **Ángulos (línea multipunto)** en el menú de símbolos: cada toque añade un punto y la línea sigue al
  siguiente; arrastrar un punto lo mueve; arriba, «Quitar el último punto», «Descartar» y
  «Confirmar». En cada punto interior se muestra el **ángulo interno** entre un segmento y el
  siguiente. Ya guardada, con «Seleccionar» se mueve cada punto por separado (los ángulos se
  recalculan). Se exporta igual que en pantalla.
- **Proporciones faciales en el contorno**: tres tercios (pelo → cejas → subnasal → mentón, % de la
  altura, ideal 33 % cada uno; el superior si se ve la línea del pelo), subdivisión del tercio inferior
  (subnasal → estomion : estomion → mentón, ideal 1 : 2), índice facial aproximado (altura nasion →
  mentón / ancho facial × 100, con su tipo según Martin–Saller) y, de frente, quintos faciales (ideal
  20 % cada uno) y ancho mandibular (bigonial aproximado) frente al bicigomático. Cada medida con su
  segmento punteado.

## 2.38 — 21 de noviembre de 2026

- **Protocolos de fotos** (ver `docs/PROTOCOLOS.md`): listas ordenadas de tomas que se crean, se
  comparten como archivo (`.photomed.json`) y se importan («Abrir con PhotoMed» o *Importar* en el
  expediente). Cada toma define zona (ángulo), gesto facial, cantidad de fotos, % mínimo de
  coincidencia, disparo automático, luz mínima y luz pareja entre mejillas, mirada al frente, cámara e
  indicaciones.
- **Referencia sin imagen**: una toma puede exigir coincidir con una foto existente guardando solo sus
  características (puntos anatómicos, pose, mirada, línea del pelo, perfil, contornos y surcos,
  distancia y zoom), sin enviar la foto. Se puede crear un protocolo entero desde fotos del expediente
  (una toma por foto).
- **La cámara ejecuta el protocolo** paso a paso: fija el gesto, exige la coincidencia, la luz y la
  referencia (sus contornos y surcos se dibujan como guía), pide la cantidad de fotos de cada toma y
  guarda en cada foto el protocolo, la toma y su número, la coincidencia y la luz medida.
- **Resultados de vuelta**: un `.zip` con el protocolo, las fotos tomadas con él y `resultados.json`
  (toma, coincidencia, luz, distancia… de cada foto), con o sin datos del paciente. Quien creó el
  protocolo lo importa en su expediente; reimportarlo no duplica fotos.

## 2.37 — 21 de noviembre de 2026

- **Tabla de medidas compacta** (visor y Comparar): sin interlineado ni relleno entre filas; cada
  fila con el alto justo de su texto (una línea, o dos con varias fotos: línea y medida); letra un punto
  menor; los nombres largos acaban en «…» en vez de partirse; cabecera del panel más baja.
- **Altura máxima de la tabla: 20 % de la pantalla** (antes 42–45 %), en vertical. En horizontal sigue
  ocupando la columna de la izquierda.

## 2.36 — 21 de noviembre de 2026

- **La tabla de medidas se filtra a lo seleccionado en la foto**: al tocar una línea, la tabla muestra
  solo esa línea; con la línea ya elegida, tocar uno de sus tramos punteados (ancho alar, largo…) la
  deja solo con ese tramo. «Ver todas» vuelve a la tabla completa. Lo elegido desde la propia tabla no
  la filtra.
- **Deslizar vuelve a funcionar**: el toque para seleccionar una línea se quedaba el gesto y bloqueaba
  el deslizador de Comparar. Ahora un toque solo cuenta si el dedo no se mueve; el deslizador, el zoom y
  el paso de una foto a otra reciben el gesto. En el visor, con un dedo y sin zoom se pasa de foto.
- **Botón de pantalla completa en lugar del de capas** (visor y Comparar). Las capas se eligen siempre
  con los chips de encima de la foto. En Comparar, pantalla completa también (solo las fotos).
- **Horizontal y tableta apaisada**: los controles y la tabla en la mitad izquierda y la foto en la
  derecha, en el visor y en Comparar (antes la foto ocupaba todo el ancho y en Comparar la tabla quedaba
  debajo).

## 2.35 — 21 de noviembre de 2026

- **Varios núcleos en el análisis de arrugas y rasgos**: cada candidato a arruga o surco (frente, ceño,
  raíz nasal, patas de gallo, surcos, búsqueda en una zona elegida) se rastrea en un núcleo distinto, y
  los filtros de imagen (desenfoque, Hessiano, color Lab, limpieza de máscaras, FFT del relieve) se
  reparten por filas. Se deja un núcleo libre para la interfaz. El resultado es idéntico al de un solo
  núcleo (comprobado con 18 retratos: mismas líneas y medidas). En la prueba (3 núcleos de cálculo),
  de 869 a 342 ms por cara; en un teléfono de 8 núcleos, más.
- **Varias fotos a la vez**: los modelos nativos (malla facial, ML Kit, segmentación) siguen de uno en
  uno, pero solo mientras se usan; el cálculo de las arrugas queda fuera de esa espera, así que en
  Comparar una foto calcula sus arrugas mientras la siguiente pasa por los modelos.
- La barba y el bigote se detectan una sola vez por foto (antes tres).

## 2.34 — 21 de noviembre de 2026

- **La expresión pedida es condición para capturar.** Con sonrisa, cejas arriba, ojos apretados,
  labios fruncidos, ceño o tristeza, la captura automática (también la de «casi alineada») y el botón
  en verde esperan a que se haga el gesto; antes, con una foto de referencia bastaba con parecerse a
  ella. Si la cara está de perfil y no se puede medir, no se da por cumplida. Nuevas comprobaciones
  para el **ceño** (cejas bajas) y la **tristeza** (comisuras hacia abajo), calibradas con caras en
  reposo para que no se cumplan sin el gesto.
- **Línea del pelo: nunca se omite.** Se mide sobre la foto ya guardada, en segundo plano (antes se
  medía antes de guardar y, si el modelo estaba ocupado, se saltaba).
- **Comparar: tocar una línea en cualquiera de las fotos** la selecciona en todas, abre la tabla de
  medidas en su fila y muestra sus tramos punteados (como en el visor).
- **Horizontal y tabletas**: la app ya gira (la cámara sigue en vertical). En pantallas anchas la
  tabla de medidas va a la derecha de la foto, en el visor y en Comparar, sin reducirla.
- **Rendimiento**: lecturas de trazos y correcciones fuera del hilo principal en Comparar y el visor
  (nota de la tabla, restaurar líneas, correcciones del usuario).
- **Revisión con lint**: anotación de API experimental de la cámara, carácter BOM del XMP escrito
  como escape.

## 2.33 — 21 de noviembre de 2026

- **Comparar se alinea solo al abrir** (interruptor en los ajustes, activo por defecto); antes solo
  lo hacía con fotos tomadas «casi alineadas». La alineación espera al análisis de arrugas y usa los
  surcos con el mismo nombre en las dos fotos (nasogeniano, marioneta, valle lagrimal) como puntos de
  correspondencia, además de los de la cara; los que no encajan se descartan.
- **Símbolos** (las formas pasan a ser símbolos): punto, círculo, triángulo, cuadrado, X, cruz,
  estrella, línea y flecha. Un toque crea un marcador del tamaño elegido; arrastrando ocupa de un
  extremo al otro (cuadrado → rectángulo, círculo → elipse…). Las formas guardadas en 2.32 se
  convierten solas.
- **Texto de los símbolos**: sin fondo (con un contorno fino para leerse), más pequeño y en la misma
  línea que el símbolo, pegado al borde del lado elegido sin cortarlo; **Auto** elige el lado que no se
  superpone con otras anotaciones. En las flechas va en el origen; en las líneas, encima o debajo con
  su mismo ángulo.
- **Herramientas de anotación en una barra flotante vertical** sobre la foto (un icono por opción):
  lápiz, símbolo, **seleccionar y mover** (arrastrar un trazo o símbolo), borrar trazo, goma,
  deshacer, borrar todo (pide confirmación) y terminar. Los ajustes de cada herramienta, abajo.
- **Borrador por trazo** corregido: mide contra la forma completa (segmentos y contorno, y el
  interior de los símbolos con área) y toma el recorrido del borrador como segmentos (antes solo sus
  puntos, y un trazo fino podía quedar sin borrar).
- **Visor**: el botón de las capas de la vista sale de la barra (que tapaba el de regresar) y pasa a
  la fila de capas (chip «Vista»); botón de **pantalla completa** semitransparente arriba a la derecha
  de la foto (oculta las barras de la app y del sistema).
- **Exportar**: vista previa de cualquier opción (foto, cuadrícula, lado a lado, GIF), con las capas
  elegidas.
- **Corregido**: al guardar una anotación o un trazo confirmado, el archivo quedaba vacío un instante
  y una lectura simultánea los daba por perdidos; ahora se escriben de forma atómica.
- **Contorno sin orejas**: a la altura de las orejas (de los ojos a la base de la nariz) el borde se
  limita a un 1,5 % del ancho de la cara hacia fuera; las sienes, solo por encima de los ojos.

## 2.32 — 21 de noviembre de 2026

- **Anotaciones: símbolos y formas**. *Símbolo*: un toque coloca un punto, círculo, triángulo, cuadrado,
  X, cruz o estrella, con un texto opcional arriba, abajo, a la izquierda o a la derecha (el lado se
  conserva al girar o espejar la foto). *Formas*: línea, flecha, círculo, rectángulo o elipse,
  arrastrando de un extremo al otro. El borrador por trazo los quita tocando su contorno.
- **Exportar con capas**: casillas para la imagen, el diagrama facial, las anotaciones y el overlay
  (fechas). Corregido: las líneas del diagrama no se exportaban (se buscaban con una clave de caché
  antigua); ahora se exporta lo mismo que se ve, también las anotaciones.
- **Deslizador sin fechas superpuestas**: «antes» arriba a la izquierda y «después» abajo a la
  derecha (también en el GIF de cortinilla).
- **Igualar el color de la piel al alinear** (interruptor en los ajustes de Comparar, encendido por
  defecto): tras la alineación automática se iguala el color de cada foto a la referencia.
- **Tabla de medidas**: cada fila de un rasgo es un tramo y al tocarla se selecciona solo ese tramo
  (resaltado en la foto y centrado); en arrugas y surcos, la celda del ancho selecciona el tramo del
  ancho. Las columnas se reparten según el ancho disponible (la primera hasta el 40 %).
- **Contorno**: en las sienes el borde de la piel se busca en horizontal (antes el contorno se metía
  hacia dentro); en la mandíbula, menos suavizado para conservar su ángulo y las formas irregulares.
- **Icono de las capas de la vista** con el color del tema (antes era invisible sobre fondo oscuro).
- **Rendimiento y errores**: lecturas de trazos confirmados (y del XMP) fuera del hilo principal en
  el visor y en Comparar; Atrás sale primero de los modos anotar y trazar surco.
- **Accesibilidad**: filas de la tabla con alto según el tamaño de letra del sistema (no se recortan),
  colores de anotación y asa de Comparar con área táctil suficiente, celdas con rol de botón.

## 2.31 — 21 de noviembre de 2026

- **Comparar sin cierres por modelos simultáneos**: la alineación automática y el análisis de cada
  foto creaban a la vez varios detectores y segmentadores nativos (MediaPipe, ML Kit); eso agotaba la
  memoria o fallaba en código nativo, y Android cerraba la app sin pasar por el registro. Ahora todos
  los modelos nativos de la app se ejecutan de uno en uno (análisis, alineación, contornos, importación,
  relieve, línea del pelo al capturar).
- **Registro también de los cierres sin traza de Java**: al volver a abrir la app se consulta a Android
  el motivo del último cierre (fallo nativo, falta de memoria con la app a la vista, bloqueo) y se
  guarda con la memoria usada, un extracto del volcado y las últimas acciones de la app (Android 11+).
  Los registros de errores incluyen también las últimas acciones.

## 2.30 — 21 de noviembre de 2026

- **Cuatro capas en cada foto** (botón de la vista 👁 en el visor y en Comparar): la **imagen**, el
  **diagrama facial** (contorno interno y externo con su numeración), las **anotaciones** y el
  **overlay** (fechas y rótulos). Cada una se muestra u oculta por separado; se recuerda.
- **Anotaciones** para planificar el tratamiento, en una capa transparente que no modifica la foto:
  lápiz con 8 colores y grosor ajustable, borrador por trazo (quita trazos enteros) y borrador tipo
  pintura, deshacer y borrar todo. Se guardan en la app y en el XMP de la foto (`pm:Ink`), en
  coordenadas de la original (siguen en su sitio al girar, espejar, recortar o ampliar) y se ven también
  en Comparar.
- **Sin datos flotantes**: al tocar una línea se abre la tabla de medidas en su fila, resaltada y
  desplazada hasta verse (con la muestra del trazo de cada tramo). Tocar una fila de la tabla, en el
  visor y en Comparar, selecciona la línea y desplaza (y amplía si es pequeña) la foto hasta ella.
- **Comparar**: con la tabla de medidas abierta se oculta el selector de fotos.
- **Fecha de captura editable** en la información de la foto (calendario y hora): cambia el nombre del
  archivo (lleva la fecha), la fecha de la galería y los metadatos EXIF/XMP; los trazos, anotaciones,
  ediciones y relieve de la foto se conservan.
- **Registro de errores**: ningún cierre es silencioso. Cada error se guarda (versión, dispositivo y
  traza, sin fotos ni datos del paciente); al volver a abrir la app se avisa y se puede compartir.
  También en Ajustes → Registro de errores. Los errores de tareas en segundo plano se registran sin
  cerrar la app.
- **Ojeras**: altura máxima del 12 % del ancho de la cara (~15 mm) y umbral de 4 L*: ya no bajan por la
  mejilla.
- **Surco nasogeniano** sin curvas irregulares: suavizado y corte del final donde se aparta más de 40°
  de la dirección del surco.
- **Trazar surco**: busca en un pasillo más ancho alrededor del trazo (±15 % de su longitud).

## 2.29 — 21 de noviembre de 2026

- Los tramos de los rasgos de los trazos confirmados en 2.23–2.24 se recuperan también al abrir la
  foto en **Comparar** (no solo en el visor). Una sola recuperación por foto, aunque estén abiertas
  las dos pantallas; queda guardada.

## 2.28 — 21 de noviembre de 2026

- **Trazos confirmados de versiones anteriores, con tramo y fila**: al leerlos, cada tramo se enlaza
  con su medida en la ficha (por su nombre corto o por su valor). Los confirmados en 2.23–2.24, que no
  guardaban tramos, los recuperan: arrugas y surcos (ancho), ojeras (alto y ancho) y bolsas (altura y
  surco) de su propio trazo; los rasgos (ojos, cejas, nariz, labios, contorno, frente), una sola vez al
  abrirlos en el visor, de la cara detectada en la foto. Los trazos y las cifras confirmadas no cambian.

## 2.27 — 21 de noviembre de 2026

- **Actualizar sin cierres**: al abrir una versión nueva se borran las cachés de la anterior (imágenes,
  temporales, memoria de líneas y análisis guardados), cuyo formato puede haber cambiado. Se conservan
  los expedientes, las fotos, las ediciones, las correcciones, los trazos confirmados, los relieves y
  las preferencias.
- **Ficha de medidas más compacta**: la muestra del trazo de cada tramo va delante de su medida, en
  la misma fila (sin leyenda aparte).

## 2.26 — 21 de noviembre de 2026

- **Segmentos de medida sin texto sobre la foto**: cada tramo se dibuja con un estilo de trazo propio
  (puntos, raya y punto, rayas espaciadas, triángulos, cuadros, círculos, raya y dos puntos), con
  marcas en los extremos. La ficha de la medida muestra la leyenda: la muestra de cada trazo con lo que
  mide y su valor. Los conectores (p. ej. pupila → ceja) van en punteado fino, sin leyenda.

## 2.25 — 21 de noviembre de 2026

- **Ojeras y bolsas con forma de media luna**: el borde inferior es una curva ajustada (cuadrática
  robusta) que se cierra hacia los cantos, sin los picos de columnas sueltas; la profundidad no pasa
  de 1,35 veces la mediana.
- **Línea del pelo medida en rectas**: desde el centro de cada pupila, perpendicular a la línea de los
  ojos, pasando por la ceja hasta el pelo (y en el centro y en la vertical de cada canto externo). Se
  dibuja punteada al seleccionarla. La altura de la ceja también se mide en la vertical de la pupila.
- **Nariz**: ancho alar, ancho del puente (raíz), ancho del dorso (mitad), longitud del dorso (raíz →
  punta) y alto. Tocar cualquier trazo de la nariz (dorso, alas) la selecciona (antes no hacía nada).
- **Cada medida con su segmento**: al seleccionar una línea (en la foto o tocando su fila en la tabla)
  se dibujan punteados los segmentos que se miden, con su valor: anchos y altos de ojos, labios,
  nariz, contorno y tercios, frente, altura de la ceja, ancho de arrugas y surcos, alto y ancho de la
  ojera, altura y surco de la bolsa. Se guardan con los trazos confirmados.
- **Tabla de medidas**: cada tabla se desplaza en horizontal por su cuenta (compartían el
  desplazamiento y la más estrecha impedía ver las columnas de las demás).

## 2.24 — 21 de noviembre de 2026

- **Ojeras mejoradas**: la zona se segmenta por columnas bajo el párpado (sigue la parte oscura real,
  no un polígono fijo) y se compara con la mejilla de la misma columna, más abajo, lo que compensa la
  luz lateral. Las gafas, el pelo y el fondo cortan la zona. Medidas: área, ΔL*, ΔE*ab, ITA°, tipo
  probable (pigmentaria, vascular o estructural; con bolsa, «sombra de la bolsa») e intensidad 0–4.
- **Bolsas palpebrales** (capa nueva, en *Contorno interno*): relieve claro bajo el párpado con su
  surco inferior, aceptado solo si forma una curva suave casi paralela al párpado. Medidas: altura
  (párpado → surco), longitud del surco, relieve (ΔL*), área y grado orientativo 0–3.

## 2.23 — 21 de noviembre de 2026

- **Comparar, más espacio para las fotos**: los detalles de la alineación (método, escala, giro,
  coincidencia por rasgo, frente) y los avisos pasan a un botón de información (ⓘ, o ⚠ si hay avisos).
  Todo lo que hay bajo las fotos (alineación, filtros, miniaturas y tabla de medidas) se pliega con
  «Ocultar controles»; la línea de tiempo queda siempre visible. Se recuerda.
- **Capas en dos desplegables con casillas**: *Contorno externo* (contorno y línea del pelo) y
  *Contorno interno* (ojos y cejas, nariz, labios, surcos, frente, ceño, patas de gallo, ojeras), cada
  uno con «Seleccionar todos» como primera opción. En el visor y en Comparar.
- **Trazar surco a mano alzada**: en lugar del rectángulo, se dibuja sobre la foto (el trazo se ve):
  una **línea** por donde se ve el surco (se ajusta a la sombra de la piel; si no la hay, se usa el
  trazo) o se **rodea un área** (se busca dentro). Funciona con la foto ampliada.
- **Volver a trazar** una línea desde la tabla de medidas (✎) y **revertirlo** (↶).
- **Trazos confirmados**: con «Confirmar» (o al eliminar, añadir o volver a trazar) los trazos y sus
  medidas se guardan en los metadatos XMP de la propia foto (`pm:Traces`) y en la app; desde entonces
  se muestran tal cual, sin volver a analizar. Solo se recalculan con «Regenerar». Van en coordenadas de
  la foto original, así que siguen en su sitio al girar, espejar, recortar o ampliar, en el visor y en
  Comparar.

## 2.22 — 20 de noviembre de 2026

- **Revisar la foto al capturar**: tras el disparo se abre la foto en el visor (con capas, medidas y
  zoom), con **Guardar** y **Repetir**. Repetir la borra y vuelve a la cámara con los mismos ajustes;
  Guardar (o Atrás) la conserva y, en una secuencia, pasa a la zona siguiente. Mientras se revisa, el
  análisis en vivo y el disparo automático quedan en pausa.
- **Comparar desde el visor**: botón en la barra del visor (y en la revisión) que abre Comparar con la
  foto y la anterior de la misma expresión.
- **Cejas**: se ajustan a la franja de pelo de la ceja en la imagen cuando la malla las deja arriba o
  abajo (solo con evidencia clara y coherente; sin tocar gafas ni flequillo). Mejora la altura de la
  frente (cejas → línea del pelo) y la de la ceja sobre la pupila.
- **Barba y bigote poco poblados**: se detecta también el vello ralo (barba de pocos días, canas
  dispersas) y la piel de referencia se toma de puntos centrales que no caen en el fondo con la cara
  girada. En los retratos de prueba: 6 rostros con vello detectados (antes 5, y con más cobertura),
  ningún falso positivo en rostros sin vello.

## 2.21 — 20 de noviembre de 2026

- **Fotos por expresión facial (arrugas dinámicas)**: en la cámara, una fila *Expresión* elige el
  gesto de la toma con su instrucción y el músculo que lo produce: reposo, sorpresa (frente, frontal),
  enojo (ceño, corrugador y prócer), sonrisa máxima (patas de gallo y surcos), ojos apretados, arrugar
  la nariz (líneas nasales), fruncir los labios (código de barras), tristeza (comisuras), mentón tenso
  (mentoniano) y cuello tenso (bandas platismales). Solo se ofrecen las que tienen sentido en la zona.
- La comprobación en vivo pide el gesto elegido (cejas más arriba, sonreír más, apretar los ojos…) en
  lugar de pedir relajar la cara, y la foto fantasma es la última con la misma expresión.
- La expresión va en el nombre del archivo (`_exp-sonrisa_`) y en los datos de la foto; las fotos en
  reposo siguen igual. Galería con una etiqueta por expresión, visor con la expresión en el título.
- **Comparar** empieza con las fotos en reposo, ofrece filtrar por expresión y avisa si la secuencia
  mezcla expresiones distintas; la referencia de fotos anteriores solo usa la misma expresión.
- **Importar** permite elegir la expresión de cada foto.

## 2.20.1 — 20 de noviembre de 2026

- **Corrección**: el botón de eliminar un surco quedaba fuera de la pantalla (al final de una fila más
  ancha que el teléfono). Ahora va en una columna fija a la derecha de la tabla, siempre visible.
- **Corrección**: *Trazar surco* buscaba en otro sitio si la foto estaba ampliada o desplazada (no se
  deshacía el zoom al convertir el rectángulo a la foto). Ahora se usa la misma conversión que al tocar
  una línea, y el rectángulo empieza en el primer toque.

## 2.20 — 20 de noviembre de 2026

- **Eliminar falsos positivos**: botón al final de cada fila de arrugas, surcos y ojeras en la tabla
  de medidas (en Comparar, en una foto o en todas); *Restaurar líneas eliminadas* los recupera.
- **Trazar surco en una zona**: chip *Trazar surco* en el visor; se arrastra un rectángulo sobre el
  surco que falta y se busca con un umbral más concesivo (12 orientaciones, valle más marcado que su
  entorno aunque sea tenue). Se guarda como *Surco añadido n*, con sus medidas.
- **Referencia de fotos anteriores**: cada foto recuerda sus arrugas y surcos en coordenadas de la
  cara; al analizar una foto posterior del paciente (misma zona), los que no aparecen se vuelven a
  buscar donde estaban, con umbrales menores, y conservan su nombre (la comparación los empareja).
  En 4 retratos con menos contraste: se perdían 21 líneas; con la referencia se recuperan 19.

## 2.19.2 — 20 de noviembre de 2026

- **Quijada real**: el contorno lateral e inferior sigue ahora el borde de la cara segmentada (piel
  del rostro, que incluye barba y bigote), con voto de vecinos para no seguir pelos sueltos y la
  transición más cercana a la malla; el ajuste por color queda de respaldo. Bajo el mentón, si la
  segmentación baja más de 2,5 % del ancho de la cara (cuello sin barba), se usa el borde de sombra.
  En el retrato marcado, el contorno va sobre el borde de la barba en ambos lados (≤ 1 % del borde
  medido a mano).

## 2.19.1 — 20 de noviembre de 2026

- **Corrección del contorno lateral** (no se ajustaba al borde de la cara): cuando la malla cae fuera
  de la cara, sobre ropa o un pañuelo, se tomaba como algo delante de la cara y no se movía. Ahora la
  oclusión se comprueba dentro de la cara, y el borde buscado es el de la cara (dentro parecido al
  color de la propia cara junto a ese punto, fuera no), no un borde del fondo. En el retrato marcado,
  el contorno se mueve 2,6 % y 1,5 % del ancho de la cara hacia dentro, sobre el borde de la barba
  (medido a mano: 2,7 % y 1,4 %).

## 2.19 — 20 de noviembre de 2026

- **Números fuera de los trazos**: cada número va a un lado de su línea, con una guía fina, sin
  tapar el trazo ni la piel (en 4 retratos de ejemplo, 82 de 83 sin tocar ninguna línea).
- **Opacidad de trazos y números** ajustable con un control deslizante (empieza en 50 %), en el visor
  y en Comparar; se recuerda y se aplica al exportar.
- **Mejillas, mandíbula y mentón más precisos**: del nivel de los ojos hacia abajo, el contorno
  parte de la malla y se ajusta al borde visible de la cara (mayor cambio de color L*a*b* entre cara
  y fondo, en ambos sentidos); bajo el mentón, al borde de sombra, para no bajar al cuello (mentón:
  antes hasta 6 % del ancho de la cara por debajo, ahora < 1 %). Corrige los huecos (trazo separado de
  la mejilla) y los trazos por dentro del borde de la mandíbula.

## 2.18 — 20 de noviembre de 2026

- **Líneas numeradas**: cada línea medida lleva en la foto el mismo número que su fila en la tabla
  (también en Comparar, igual en todas las fotos, y al exportar), colocado sin solaparse.
- **Colores por tipo**: nasogeniano, marioneta, valle lagrimal, frente, ceño, raíz nasal, patas de
  gallo, ojeras, cejas y labios con colores distintos.
- **Tabla de medidas debajo de la foto** (no superpuesta) en el visor y en Comparar, con cifras de
  mayor contraste y secciones (arrugas y surcos, ojeras, rasgos).
- **Selector de capas encima de la foto**, chip **Contornos internos** (ojos y cejas, nariz y labios
  de una vez) y **barra de progreso** visible mientras se analizan arrugas y rasgos.
- **Medidas de los rasgos**: labio superior (ancho, alto, área, filtrum, arco de Cupido) e inferior
  (alto, área, proporción), ojos (ancho, alto, inclinación cantal), cejas (longitud, altura), nariz
  (ancho alar, alto) y contorno (ancho, tercios medio e inferior).
- **Línea del pelo**: altura de la frente (cejas → pelo) en el centro, sobre cada ceja y su cola, y
  entradas temporales, para seguir los patrones de calvicie.
- **Surco nasogeniano más sensible** (umbrales menores y pasillo más ancho): en 18 retratos, en los 18
  (antes 16).
- **Idempotencia**: girar 4 veces conserva el recorte (antes se perdía); importar dos veces la misma
  foto no la duplica; pruebas de repetición de ediciones, plantillas, recortes, numeración y análisis.

## 2.17 — 20 de noviembre de 2026

- **MediaPipe como motor estándar** de puntos faciales; ya no se elige en Ajustes. ML Kit queda
  como respaldo (si MediaPipe falla o no encuentra la cara, en el mismo cuadro) y como comprobación
  de la malla en las fotos (los ojos de ambos deben coincidir; si no, la malla se rehace).
- **Surcos visibles junto a barba o bigote**: se descartaban si el bigote tapaba más de la mitad de
  su recorrido. Ahora la búsqueda se limita primero a la parte visible y se trazan hasta el borde del
  vello (en 18 retratos: los 9 nasogenianos visibles en rostros con vello, ninguno sobre el vello).

## 2.16 — 20 de noviembre de 2026

- **Barba y bigote ya no distorsionan los surcos.** La segmentación los clasificaba como piel del
  rostro, así que el nasogeniano y la marioneta se trazaban sobre el vello y se desviaban a sus
  bordes oscuros. Ahora se detecta el vello facial (textura fina y color sin croma frente a la piel de
  mejillas y frente) y los surcos se trazan y miden solo sobre la piel visible; las líneas
  parcialmente cubiertas llevan una nota (✱ en la tabla) y la foto, un aviso. En 18 retratos: trazos
  sobre vello del 28 % al 0 % en los 5 con barba o bigote, sin falsos positivos en los demás.

## 2.15.5 — 20 de noviembre de 2026

- **Corrección de raíz: MediaPipe no se iniciaba en la app instalada.** La minimización (R8)
  renombraba Flogger, el registro que MediaPipe busca por nombre al iniciarse, así que la malla
  facial (arrugas de la frente, ceño, patas de gallo, ojeras), la segmentación pelo / piel, la
  medición de relieve y la sugerencia de ángulo al importar fallaban siempre en el APK, aunque la
  cara estuviera completa. Ahora se conservan, y la compilación falla si R8 vuelve a renombrar
  clases de MediaPipe, protobuf o Flogger.
- El aviso muestra el primer error real (no su repetición).
- Las llamadas a ML Kit tienen un límite de 10 s: una que no responda ya no bloquea para siempre
  un hilo de fondo.

## 2.15.4 — 20 de noviembre de 2026

- **Corrección**: el aviso «no se detectó la cara entera» salía cuando lo que fallaba era la malla
  facial de MediaPipe en el teléfono, aunque la cara estuviera completa. Ahora la malla se reintenta
  (foto reducida, recorte alrededor de la cara, marco ampliado) con una sola instancia compartida;
  si aun así falla, se miden igualmente los surcos (nasogeniano, marioneta, valle lagrimal) con los
  puntos de ML Kit y el aviso muestra el error real de MediaPipe.

## 2.15.3 — 20 de noviembre de 2026

- **Corrección: no se mostraban las medidas de las arrugas.** Las capas de arrugas vienen apagadas
  y la foto solo se analizaba con alguna capa encendida, así que *Medidas* salía vacía. Ahora, con el
  panel de capas abierto, la foto se analiza siempre; la tabla lista todas las líneas medidas (estén
  o no visibles), indica «Analizando…» mientras calcula, ofrece *Mostrar en la foto* y, si no hay
  medidas, explica por qué.
- **Más arrugas detectadas en fotos con la cara pequeña**: se analiza un recorte de la cara a
  resolución fija (≈ 400 px de ancho de cara) en vez de la foto entera a 480 px.
- **Primeros planos** (frente, ojos): si la cara no se detecta entera, se reintenta dentro de un
  marco más amplio.

## 2.15.2 — 20 de noviembre de 2026

- **Corrección**: la página de importación ya no es una ventana de diálogo aparte (en algunos
  teléfonos no recibía la altura de la barra de navegación y el botón de abajo quedaba debajo); va en
  la ventana de la app como el resto de pantallas, y el botón *Importar* está también arriba.
  Pruebas: barra de navegación simulada en la ventana de la app y regla que impide diálogos a
  pantalla completa.

## 2.15.1 — 20 de noviembre de 2026

- **Corrección**: el botón *Importar al expediente* quedaba bajo los botones de navegación del
  teléfono; ahora se aparta de ellos (y el título, de la barra de estado). Prueba automática que lo
  comprueba con una barra de navegación simulada.

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
