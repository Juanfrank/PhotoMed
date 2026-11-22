# Novedades de PhotoMed

Registro de cambios de cada versión, de la más reciente a la más antigua.

## 2.46 — 21 de noviembre de 2026

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
