# Novedades de PhotoMed

Registro de cambios de cada versión, de la más reciente a la más antigua.

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
