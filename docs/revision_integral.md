# Revisión integral de PhotoMed (10 revisores)

Versión 2.57, 25 de noviembre de 2026. Revisión de solo lectura del código; cada hallazgo cita archivo y línea. **118 hallazgos** de 10 revisores, **92** tras agrupar los repetidos (mismo archivo y línea). Confianza: «confirmado» = verificado leyendo el código; «probable» = deducido sin reproducirlo.

Ámbitos: Cámara y análisis en vivo, Modelos nativos, Análisis de una foto, Comparar y alineación, Persistencia, Manejo de errores, Concurrencia, Método de los criterios, Interfaz Compose, Recursos y escalabilidad.

## Estado tras las correcciones (versiones 2.58–2.60)

- **Tanda 1 – integridad de datos (2.58)**: corregidos A10, A11, A12, A13, A14, A18, A21 y los medios de
  persistencia (escrituras atómicas con fsync, caché de trazos, recuperación del expediente, memoria de líneas y
  relieve atómicos).
- **Tanda 2 – modelos nativos y cámara (2.58)**: corregidos A1, A2, A3, A4, la ráfaga juzgada con el estado en vivo,
  la segmentación duplicada, ML Kit en cada cuadro sin cara, la malla y el segmentador compartidos, y el flash que
  cancelaba la foto.
- **Tanda 3 – rendimiento (2.59)**: corregidos A5, A6, A7, A8, A9, A15, A16, A17, handLike, cargas en el hilo
  principal del visor y de Comparar, y el arranque.
- **Tanda 4 – escalabilidad, recursos y errores (2.60)**: corregidos A19, A20, los fallos silenciosos de importación,
  migración y análisis en vivo, el signo del alabeo (comprobado con 274 caras), el arco de Cupido, los pies en la
  misma mitad, la igualación de color al cambiar la selección, el tope del XMP, las cachés sin límite y el barrido de
  datos huérfanos.
- **Descartado con datos**: «la densidad capilar cuenta la piel de la cara como cuero visible»: en las 10 fotos
  anotadas el cuero calvo se etiqueta como piel de la cara o del cuerpo; excluirla rompería la medida.
- **Pendiente (mejoras estructurales mayores)**: reescritura del XMP mediante copia y reemplazo, ediciones por foto
  fuera de SharedPreferences, importación sin recodificar el JPEG, alineación de N fotos preparando la referencia una
  sola vez y recomposición de CameraScreen por estados derivados.

## Corregido ya

- **NativeGate.run quedaba tomada para siempre** si la corrutina se cancelaba justo al conseguir el turno (lo encontraron cuatro revisores; crítico): desde ese momento ningún análisis, alineación ni medida se ejecutaba hasta reiniciar la app. Corregido en `NativeGate.kt`, con prueba (`NativeGateTest.cancellationNeverLeavesTheGateTaken`).

## Alto (21)

### A1. El motor MediaPipe (FaceLandmarker) se crea en el hilo principal, durante la composición, y fuera de NativeGate

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:341` · Rendimiento · confirmado · Concurrencia, Cámara y análisis en vivo, Modelos nativos

- **Evidencia:** `val matcher = remember(zone, engine) { LiveMatcher(zone, context.applicationContext, engine) }` → LiveMatcher.kt:62 `private val detector = LandmarkDetector(stream = true, context, engine)` → LandmarkDetector.kt:41-44 crea `MediaPipeFace(context.applicationContext)` en el constructor → FaceEngines.kt:88 `FaceLandmarker.createFromOptions(...)` carga `face_landmarker.task`.
- **Impacto:** Cada vez que se abre la cámara o se cambia de zona (Saltar/Siguiente en una secuencia o protocolo), el hilo de la interfaz carga y arranca el grafo nativo de MediaPipe, lo que congela la pantalla (tirones o casi-ANR en gamas bajas). Esa inicialización nativa no pasa por NativeGate. Puede coincidir con un modelo nativo que sí la tiene tomada, como HairlineMeasure.measureAndStore lanzado en AppScope justo después de guardar la toma anterior, que es justo la concurrencia que causó los SIGBUS.
- **Corrección:** Crear el LiveMatcher fuera de la composición: en el hilo del pipeline, con `pipeline.post { NativeGate.tryRunBlocking("crear motor", 30_000) { pipeline.swap(LiveMatcher(...)) } }` desde un LaunchedEffect(zone, engine). O bien hacer perezoso el MediaPipeFace dentro de LandmarkDetector (lazy, como ya se hace con ML Kit), para que se cree en el primer analyze, que ya corre bajo la puerta.

### A2. setReference bloquea el hilo de análisis hasta 30 s y, si se agota la espera, deja en silencio la referencia anterior

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:382` · Fiabilidad · confirmado · Concurrencia, Cámara y análisis en vivo, Modelos nativos

- **Evidencia:** `pipeline.post { NativeGate.tryRunBlocking("referencia en la cámara", 30_000) { runCatching { m.setReference(bmp) } ... }; refPoints = m.referencePoints ... }`. Si tryRunBlocking devuelve null no se avisa de nada y refPoints/refSilhouette se toman del matcher, que conserva `ref` y `refGray` de la foto anterior.
- **Impacto:** Tras una toma, `reload++` cambia el fantasma a la foto recién tomada mientras HairlineMeasure o el relieve tienen la puerta. Mientras tanto el único hilo de análisis queda parado: sin cuadros en vivo y con `live` congelado. Las puntuaciones de la ráfaga (scoreOf, línea 690) caducan a los 4 s y devuelven 0, así que `maxBy` se queda con la primera foto y no con la mejor. Si la espera pasa de 30 s, la coincidencia sigue contra la referencia ANTERIOR mientras la pantalla muestra la nueva, y el disparo automático puede saltar alineado con la foto equivocada.
- **Corrección:** No bloquear el hilo del pipeline. Reintentar setReference con espera corta (p. ej. tryRunBlocking(…, 0) y volver a publicarlo con un retraso). Mientras la referencia no esté aplicada, marcar el matcher como «sin referencia» (useReference=false, o anular ref y refGray antes de esperar) para que nunca se compare contra la anterior, y avisar si no se pudo aplicar.

### A3. El cierre de los modelos del LiveMatcher (swap y shutdown) corre fuera de NativeGate, a la vez que otros modelos

`app/src/main/java/com/photomed/app/align/AnalysisPipeline.kt:28` · Fiabilidad · confirmado · Cámara y análisis en vivo, Modelos nativos

- **Evidencia:** `executor.execute { runCatching { old.close() } }` (línea 28) y `executor.execute { last?.let { runCatching { it.close() } } }` (línea 49) llaman a LiveMatcher.close(), que cierra FaceLandmarker, ImageSegmenter, HandLandmarker y los clientes de ML Kit (LiveMatcher.kt:498) sin tomar la puerta.
- **Impacto:** Al cambiar de zona o salir de la cámara justo después de una toma, el cierre (destrucción del grafo y liberación de memoria nativa) coincide con el análisis de la foto recién tomada o con el relieve, que sí tienen la puerta. Es la misma concurrencia nativa que la puerta pretende evitar y abre otra vía de SIGBUS que no pasa por el registro de Java.
- **Corrección:** Cerrar dentro de la puerta: `executor.execute { NativeGate.tryRunBlocking("cerrar análisis en vivo", 30_000) { old.close() } ?: old.close() }`. También se puede encolar el cierre en una corrutina con NativeGate.run.

### A4. Si ML Kit agota el tiempo, la tarea nativa sigue corriendo con la puerta ya liberada, y después se cierra el cliente debajo de ella

`app/src/main/java/com/photomed/app/align/LandmarkDetector.kt:73` · Manejo de errores · confirmado · Modelos nativos

- **Evidencia:** `Tasks.await(t, ML_KIT_TIMEOUT_S, SECONDS)` lanza TimeoutException y la tarea sigue en curso. En close() (línea 200) se espera como mucho CLOSE_WAIT_S=10 s y después se ejecuta `runCatching { face.close() }` aunque la tarea no haya terminado. SubjectSegmenter hace lo mismo (líneas 15-16 y 37-38).
- **Impacto:** Con una tarea colgada (memoria agotada, foto enorme), la puerta queda retenida hasta 20 s (10 de espera más 10 al cerrar), así que el vivo se congela y Comparar espera. Luego la puerta se libera con ML Kit todavía trabajando en sus hilos, y el modelo siguiente corre a la vez que él. Por último, close() libera el cliente con la tarea viva, que vuelve a usarlo después de cerrado en código nativo.
- **Corrección:** Si se agota el tiempo, no cerrar el cliente: dejarlo vivo (fuga controlada) y cerrarlo con `t.addOnCompleteListener { client.close() }`. Además, señalar en NativeGate que hay una tarea nativa pendiente para que el siguiente usuario espere a que termine (por ejemplo, liberar la puerta desde el listener de compleción).

### A5. measureTrace repite el análisis facial completo (malla, ML Kit, segmentación y todas las arrugas) para medir un solo trazo a mano

`app/src/main/java/com/photomed/app/align/FaceAnnotations.kt:1097` · Rendimiento · confirmado · Análisis de una foto

- **Evidencia:** measureTrace = analyze(bmp, zone, extras, user = AnnotUserEdit(added = listOf(pts))).first.firstOrNull { it.metrics?.name == "Surco añadido 1" }. No usa la caché. PhotoViewerScreen.tracePath (línea 227) lo llama en cada trazo con una foto de 1600 px recién cargada.
- **Impacto:** Cada trazo cuesta varios segundos de CPU y ocupa la puerta nativa (meshRobust, ML Kit, HairSkinSegmenter), mientras que la medida solo necesita la escala (malla e iris) y la luminancia del recorte. Además bloquea la cámara y las demás fotos de Comparar. En tomas SCALP_FRONT nunca devuelve medida, porque scalpFrontLines quita la capa FOLDS.
- **Corrección:** Añadir una ruta ligera: obtener la malla (o reutilizar la de LineMemory o la del último análisis en caché), calcular FaceCrop.crop y lum(b) solo de la región del trazo y llamar a LineMeasure.line(name, pts, lum, scaleOf(mesh, extras), halfMm = 4f, relief). Así se evitan folds() y wrinkles().

### A6. MeasureNumbering.place se recalcula en cada cuadro de dibujo, en el hilo principal, con coste O(líneas × candidatos × segmentos)

`app/src/main/java/com/photomed/app/align/LineMetrics.kt:323` · Rendimiento · probable · Análisis de una foto

- **Evidencia:** place() reconstruye `segs` (todas las aristas de todas las líneas visibles) y, por cada línea numerada, prueba hasta 48 candidatos. Cada uno llama a lineClear (minOf sobre TODOS los segmentos) y, si ninguno cabe, maxByOrNull los recorre otra vez. Se llama desde DrawScope.drawLineNumbers (GuideOverlay.kt:747) sin memorizar el resultado.
- **Impacto:** Una cara con unas 40–60 líneas medidas y unos 2–3 mil segmentos supone millones de hypot por cuadro, más la creación de listas de pares y de un Paint en cada draw. Al hacer zoom o arrastrar en el visor o en Comparar da tirones visibles. En vistas capilares, las miles de celdas SCALP_MAP inflan `segs`.
- **Corrección:** Memorizar la colocación por (identidad de lines, numbers, rUnits cuantizado), con remember o derivedStateOf, y calcularla fuera de DrawScope. Indexar los segmentos en una rejilla espacial para que lineClear solo mire las celdas vecinas, y excluir de `all` las líneas filled (celdas y puntos).

### A7. Cada foto corregida (perspectiva/color) se decodifica a 1080×1440 y se procesa a la vez; con 12 fotos hay riesgo de OOM

`app/src/main/java/com/photomed/app/ui/compare/CompareScreen.kt:1297` · Recursos · confirmado · Comparar y alineación

- **Evidencia:** rememberCorrected: `ImageOps.correctPerspective(ImageOps.load34(context, uri, 1080), key, focalNorm)` y luego `IntArray(w*h)` + `color.apply(px)` (otro IntArray) + un Bitmap nuevo, en Dispatchers.Default. Se llama por cada foto de seq (hasta Timeline.MAX_PHOTOS = 12) desde `models` (línea 506), sin límite de concurrencia, y el bitmap resultante queda retenido en el estado.
- **Impacto:** Tras alinear e igualar el color de 12 fotos, cada produceState reserva unos 30 MB transitorios (5 búferes de 6,2 MB) y se ejecutan hasta tantos como núcleos haya: picos de 200–300 MB más unos 75 MB retenidos, además de la caché de Coil. En equipos de gama media (heap de 256–384 MB) acaba en OutOfMemoryError (que runCatching atrapa: la foto vuelve a la URI sin corregir pero con la transformación corregida, es decir, mal alineada) o en GC continuo.
- **Corrección:** Decodificar al tamaño de la celda: 1080 solo en SLIDER/FADE con 2 fotos, y unos 540 px o menos en cuadrícula o lado a lado. Limitar la concurrencia con un Semaphore(2). Reciclar los intermedios (la carga y la corrección de perspectiva) cuando no sean el resultado. Aplicar el color con un ColorMatrix/LUT en el Paint de dibujado o en un RenderEffect, en lugar de copiar píxeles.

### A8. ColorAdjust.apply calcula unas 12 potencias o raíces y reserva un DoubleArray por píxel, y se recalcula cada vez que se activa el color

`app/src/main/java/com/photomed/app/align/ColorMatch.kt:126` · Rendimiento · confirmado · Comparar y alineación

- **Evidencia:** Por píxel: `ColorSpace.lin` (pow 2.4) ×3, `ColorSpace.mulv` (devuelve `DoubleArray(3)` nuevo), `gam` (pow 1/2.4) ×3 y, con lut, `Lab.fromRgb` (3 pow + 3 cbrt) y `Lab.toRgb` (3 pow). Se aplica a 1,55 M píxeles por foto en rememberCorrected y otra vez en CompareExport.render (línea 71). La clave del produceState es la instancia de ColorAdjust, así que apagar y encender el interruptor 'Color' (useColor) lo recalcula todo.
- **Impacto:** Del orden de 1–3 s de CPU por foto en ARM de gama media y 1,5 M de reservas (presión de GC). Con 11 fotos objetivo, decenas de segundos de CPU al terminar la alineación automática y en cada encendido del interruptor de color, además de la exportación.
- **Corrección:** Precalcular LUTs: lin[256] (sRGB→lineal), gamma con unas 4096 entradas para el lineal→sRGB, y la curva de L* combinada. Evaluar mulv en línea sin reservar memoria. Mejor aún: hornear un LUT 3D de 33³ o 64³ en RGB y aplicarlo por interpolación trilineal. Cachear los bitmaps corregidos por (uri, phi, ajuste) para que el interruptor solo cambie el modelo mostrado.

### A9. Cada evento táctil (incluidos los de movimiento) recompone toda CompareScreen y repite el trabajo derivado

`app/src/main/java/com/photomed/app/ui/compare/CompareScreen.kt:538` · Rendimiento · confirmado · Comparar y alineación

- **Evidencia:** `awaitPointerEvent(PointerEventPass.Initial); lastTouch = System.currentTimeMillis()` en cada evento. lastTouch se lee en composición como clave de `LaunchedEffect(lastTouch, ...)` (línea 427), que además se reinicia. En cada recomposición: derive() se llama varias veces, y resultOf(p) llama a derive() otra vez (línea 307) unas 5×N veces. derive hace `selected.mapNotNull { photos.firstOrNull }`, filtra todas las fotos y recorre Zone.entries. rememberPhotoInk ejecuta `Annotations.tag` (exists/lastModified/length del archivo) en composición por foto (PhotoLayers.kt:268), y ComparePhoto recalcula `MeasureNumbering.of(v.annots.values.toList())` por celda.
- **Impacto:** Al arrastrar el deslizador o hacer zoom con 12 fotos y un expediente de cientos de fotos, cada evento táctil (unos 120 Hz) recompone la pantalla entera con decenas de recorridos O(selección × fotos) y unas 36 llamadas al sistema de archivos en el hilo principal: cuadros perdidos y gestos a saltos.
- **Corrección:** Guardar el último toque en un objeto no observable (`remember { LongArray(1) }`) o en un Channel/MutableStateFlow con debounce que reinicie el temporizador de ocultación sin recomponer. Calcular `derive()` una vez con `remember(all, selected, reversed, timelineState, alignedFor...)` o derivedStateOf y pasar seqKeys a resultOf. Mover Annotations.tag y MeasureNumbering.of a estados recordados y calculados fuera del hilo principal.

### A10. Reescrituras concurrentes del XMP del mismo JPEG sin exclusión: pérdida de cambios o foto clínica corrupta

`app/src/main/java/com/photomed/app/data/XmpFile.kt:34` · Fiabilidad · probable · Persistencia

- **Evidencia:** XmpFile.update hace read-modify-write (`ExifInterface(pfd.fileDescriptor)` … `saveAttributes()`) sin lock. Lo llaman desde corrutinas independientes en Dispatchers.IO: PhotoViewerScreen.kt:207-210 (`scope.launch { withContext(Dispatchers.IO) { … ConfirmedTraces.save(...) } }` en cada toque), CompareScreen.kt:240-246, Annotations.kt:125 (Ink) y HairlineMeasure.kt:35 (Cond_HairlineIpd). Nada las serializa por URI.
- **Impacto:** Basta descartar dos surcos seguidos o guardar tinta mientras se mide la línea del pelo para que dos saveAttributes reescriban a la vez el mismo archivo (para un JPEG de 4-8 MB cada reescritura tarda cientos de ms). Lo más leve es perder una de las dos actualizaciones (pm:Traces o pm:Ink). Lo grave es que las escrituras se intercalen en el mismo descriptor y el JPEG original del paciente quede corrupto.
- **Corrección:** Serializar todas las escrituras de metadatos por URI con un Mutex o lock en un mapa (p. ej. `XmpFile.update` dentro de `locks.getOrPut(uri){Any()}`), incluidas CaptureDate.write y PhotoMetadata.write. Además, encolar las mutaciones de trazos en un único actor/canal por foto en vez de usar un scope.launch suelto por toque.

### A11. Los datos privados de la foto se borran antes de que se confirme el borrado; si se cancela, se pierden (y en Android 10 la foto ni siquiera se borra)

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:631` · Manejo de errores · confirmado · Persistencia

- **Evidencia:** `ReliefStore.delete(context, it); repo.deletePhotos(listOf(current))`: ReliefStore.delete (Relief.kt:197-201) borra el relieve, LineMemory, AnnotUserEdits y ConfirmedTraces antes de llamar a store.delete. Con fotos de otra instalación, este devuelve NeedsConfirmation (PhotoStore.kt:170-177). Lo mismo pasa en PhotoViewerScreen.kt:418 y ZoneGalleryScreen.kt:254. En Android 10 (minSdk 26), tras aceptar la RecoverableSecurityException, el callback de la línea 198 solo hace `reload++` y no reintenta el delete.
- **Impacto:** Tras reinstalar, el usuario pulsa Eliminar y luego Cancelar en el diálogo del sistema: la foto sigue, pero sus trazos confirmados, el relieve, la memoria de líneas y las correcciones ya no están. En Android 10, aunque acepte, la foto no se borra y sus datos sí. Además, en lotes de Android 10 solo se pide permiso para la primera foto no propia (`return` dentro del bucle).
- **Corrección:** Limpiar los datos privados solo cuando el resultado sea Done o el callback devuelva ok=true (pasar la lista de URIs a la lambda de rememberGalleryDelete). En Android 10, reintentar `resolver.delete` después de RESULT_OK y seguir con las URIs restantes.

### A12. expediente.json se escribe sin atomicidad; si se corrompe, el expediente desaparece y la recuperación lo ignora

`app/src/main/java/com/photomed/app/data/PhotoRepository.kt:93` · Fiabilidad · confirmado · Manejo de errores, Persistencia

- **Evidencia:** `File(dir, META).writeText(json.toString(2))` no pasa por AtomicFile. readExpediente (líneas 99-115) se traga cualquier excepción con `catch (e: Exception) { null }`, y recoverFromGallery (línea 72) hace `if (File(metaRoot, folder).exists()) continue`.
- **Impacto:** Si el proceso muere, se llena el disco o se va la batería mientras se edita un expediente (updateExpediente), queda un JSON truncado. El expediente deja de aparecer en la lista y en getExpediente sin ningún error. Como la carpeta existe, «recuperar desde la galería» tampoco lo reconstruye: el usuario pierde el acceso a todas las fotos del paciente desde la app.
- **Corrección:** Usar AtomicFile.write (con fsync, ver otro hallazgo) en writeMeta. Si el JSON no se puede leer, reconstruirlo desde el XMP de la primera foto (como hace recoverFromGallery) en vez de devolver null, o tratar como recuperable una carpeta con meta ilegible.

### A13. Si falla el guardado en la galería, la foto clínica recién tomada se borra y el fallo no queda registrado

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:826` · Manejo de errores · confirmado · Manejo de errores

- **Evidencia:** `val result = runCatching { repo.savePhoto(...) }.also { best.delete() }`. El archivo de caché se borra pase lo que pase. El fallo solo se comunica con `scope.launch { ... onFailure { snackbar.showSnackbar(...) } }`, un scope de la pantalla que ya está cancelado si el usuario salió de la cámara. No se llama a CrashLog.write. En reliefCapture ocurre lo mismo (líneas 725-727, `.also { file.delete() }`).
- **Impacto:** Si MediaStore falla (almacenamiento lleno, volumen no disponible, error al escribir el XMP en PhotoMetadata.write), la toma se pierde sin posibilidad de reintentar. Si el paciente o el usuario ya cerraron la cámara (el diseño de AppScope lo permite), nadie se entera de que la foto no se guardó.
- **Corrección:** Borrar `best` solo si el guardado sale bien (`onSuccess { best.delete() }`). Si falla, conservarlo en una carpeta de pendientes y reintentar o avisar en la siguiente apertura. Registrar siempre el fallo con `CrashLog.write("Guardar una captura", it)` y avisar al usuario de forma independiente del scope de la pantalla (notificación o Toast con applicationContext).

### A14. «Regenerar trazos» y «Borrar anotaciones» se deshacen solos cuando no se puede reescribir el XMP de la foto

`app/src/main/java/com/photomed/app/align/ConfirmedTraces.kt:215` · Manejo de errores · confirmado · Manejo de errores

- **Evidencia:** clear(): `XmpFile.update(context, Uri.parse(uri)) { PhotoMetadata.withTraces(it, null) }` ignora el resultado y luego borra la copia local. of() (línea 184) hace: si la copia local no existe, `PhotoMetadata.tracesOf(XmpFile.read(...))... AtomicFile.write(f, j)`, es decir, recupera los trazos desde el XMP y vuelve a escribir la copia. Annotations.save con trazos vacíos (Annotations.kt:119-121) borra el archivo y depende de XmpFile.update del mismo modo, y Annotations.of (línea 111) recupera desde el XMP. XmpFile.update (XmpFile.kt:19-32) devuelve false sin registrar la causa.
- **Impacto:** En fotos que la app no puede reescribir (de otra instalación o importadas, donde da RecoverableSecurityException, o en fotos de solo lectura), el usuario pulsa Regenerar o Borrar todo y los trazos confirmados o las anotaciones vuelven a aparecer en la siguiente lectura. Se siguen midiendo y exportando datos que el clínico creía eliminados, sin ningún aviso.
- **Corrección:** Comprobar el resultado de XmpFile.update. Si falla, conservar una marca local de borrado (tombstone) que of() respete antes de recurrir al XMP, e informar al usuario: «borrado en la app; la foto conserva una copia en sus metadatos». Registrar en CrashLog la excepción que hoy se traga runCatching dentro de XmpFile.update.

### A15. La memo de feetOf (un solo hueco, por identidad) se invalida varias veces por cuadro: computeFeet se repite hasta 3 veces por cuadro

`app/src/main/java/com/photomed/app/align/ZoneCriteria.kt:576` · Rendimiento · confirmado · Método de los criterios

- **Evidencia:** feetMemo es un único Pair<Classes,List<Foot>> comparado con ===. ClassesFusion.add (ClassesFusion.kt:32) devuelve SIEMPRE un Classes nuevo cuando hay ≥3 máscaras, así que lastClasses !== lastRawClasses. En cada cuadro LiveMatcher llama a BodyPoints.of(… lastClasses) (l.249) → feetOf(fusionada), a BodySchema.of(drawScene con lastRawClasses) (l.467) → feetOf(cruda) y a ZoneCriteria.hints(scene con lastClasses) (l.476) → feetOf(fusionada) otra vez.
- **Impacto:** En pies (y en manos sin puntos, vía skinHands) se rehace en cada cuadro, alternando, el etiquetado de componentes de 307k píxeles, splitColumn, ToeDetector y la búsqueda de uñas, aunque la máscara solo cambia cada 200 ms. Supone decenas de ms por cuadro en el hilo de análisis, presión de GC y retraso de la guía.
- **Corrección:** Memo con 2–3 entradas (LRU por identidad) o, mejor, guardar los pies dentro de Classes (campo lazy `val feet by lazy { computeFeet(this) }`), de modo que cada máscara se analice una sola vez sea cual sea el orden de las llamadas.

### A16. rotatedCcw copia la máscara y el RGB en cada cuadro y rompe toda memoización por identidad cuando la guía está girada

`app/src/main/java/com/photomed/app/align/ZoneCriteria.kt:61` · Rendimiento · confirmado · Método de los criterios

- **Evidencia:** Con q≠0 se construye en cada llamada un ByteArray(w2*h2) nuevo y un IntArray rgb nuevo (l.66-70). LiveMatcher.withCriteria lo llama dos veces por cuadro (scene l.463 y drawScene l.466), aunque lastClasses no haya cambiado.
- **Impacto:** Con «Girar 90°» activo, cada cuadro copia unos 307 KB más 1,2 MB por escena (unos 3 MB por cuadro en basura). Además feetOf/footLike nunca aciertan la memo, así que los pies y las manos por piel se recalculan enteros en cada cuadro: más caídas de FPS y más pausas de GC.
- **Corrección:** Cachear la escena girada por (identidad de Classes, q) en LiveMatcher, recalcularla solo cuando llegue una máscara nueva y reutilizar la misma instancia girada para scene y drawScene cuando raw===fused.

### A17. En la primera toma, la deformación TPS de ~140 puntos y dos ajustes HeadPose se recalculan en el hilo de UI con cada cuadro

`app/src/main/java/com/photomed/app/ui/camera/GuideOverlay.kt:186` · Rendimiento · confirmado · Interfaz Compose, Método de los criterios

- **Evidencia:** CameraScreen.kt:1068 pasa adaptTo = live?.currentPointsAll (un mapa nuevo por cuadro) cuando framedFace. GuideOverlay: remember(art, adaptTo){ FaceMeshWarp.fit(...) } (l.186-188) y remember(art, adaptTo, …){ GuideWarp.analyze(...) } (l.210-212). FaceMeshWarp.fit llama a HeadPose.fit y resuelve dos eliminaciones de Gauss (n+3)≈143 (FaceMeshWarp.kt:173-174, O(n³) cada una). GuideWarp.analyze llama otra vez a HeadPose.fit (GuideWarp.kt:132).
- **Impacto:** Unos 2M de multiplicaciones más dos búsquedas por coordenadas de HeadPose (cientos de solve sobre ~140 puntos con trigonometría) en la composición, en cada cuadro de análisis. Provoca tirones de UI o frames perdidos y, en gamas bajas, riesgo de jank sostenido mientras la cara está encuadrada.
- **Corrección:** Calcular el ajuste en el hilo de análisis (o con derivedStateOf + withContext(Default)) y limitarlo a unos 5 Hz o a cambios relevantes de los puntos. Factorizar la matriz TPS una vez (LU) y resolver x e y con ella. Reutilizar la pose ya calculada en Detection.pose en lugar de volver a llamar a HeadPose.fit.

### A18. Condición de carrera al guardar anotaciones y trazos confirmados: dos trazos o borrados rápidos se pierden (last-writer-wins)

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:261` · Fiabilidad · confirmado · Interfaz Compose

- **Evidencia:** inkPath, inkPoly, inkMove e inkVertex lanzan cada uno scope.launch { withContext(Dispatchers.IO) { val old = Annotations.of(context, u) ...; Annotations.save(context, u, next) } } sin Mutex. Lo mismo pasa en mutateTraces (l. 205-214): ConfirmedTraces.ensure(...) seguido de save(change(set)). inkUndo[u] = (inkUndo[u].orEmpty() + old) también se modifica de forma concurrente desde IO.
- **Impacto:** Dibujar dos trazos seguidos, o borrar dos líneas falsas en la tabla en menos de lo que tarda la E/S (que incluye escribir el XMP de la foto), ejecuta dos corrutinas en paralelo. Las dos leen el mismo 'old' y el segundo save sobrescribe el primero: se pierde una anotación o una eliminación sin ningún aviso. La pila de deshacer queda incoherente. Son datos clínicos persistidos en la foto.
- **Corrección:** Serializar las mutaciones por foto: un Mutex por URI (mapa de Mutex) o una cola de un solo consumidor (Channel o actor) con limitedParallelism(1). Hacer la lectura, la modificación y el guardado dentro del lock, y actualizar inkUndo en Main tras el guardado.

### A19. El listado de expedientes hace una consulta a MediaStore por expediente y abre todas las fotos para leer su XMP en cada arranque en frío

`app/src/main/java/com/photomed/app/data/ExpedienteSummaries.kt:26` · Escalabilidad · confirmado · Recursos y escalabilidad

- **Evidencia:** PhotoRepository.listExpedientes (l.34-37) llama a readExpediente por carpeta y cada una ejecuta photos(dir.name), es decir store.list(folder), una consulta por expediente. Después, ExpedientesScreen.kt l.100-104 llama a ExpedienteSummaries.of por cada expediente, que vuelve a llamar a repo.photos(exp) (otra consulta) y calcula el stamp con Protocols.list(context).size, que lee y parsea todos los JSON de protocolos. Si la caché falla, hace photos.forEach { repo.readInfo(p) } (l.33), que abre cada JPEG con ExifInterface. La caché es un HashMap en memoria y se pierde al cerrar el proceso.
- **Impacto:** Con 300 expedientes y unas 6.000 fotos: unas 600 consultas a MediaStore y unas 300 lecturas de todos los protocolos cada vez que se vuelve a la lista (reload++). Además, en cada arranque en frío se abren unas 6.000 fotos para leer el XMP. La lista sale vacía varios segundos y los resúmenes llegan uno a uno durante minutos, con E/S y batería sostenidas.
- **Corrección:** Hacer una sola consulta con RELATIVE_PATH LIKE 'Pictures/PhotoMed/%' y agrupar en memoria por carpeta para el número de fotos y la última toma. Leer Protocols.list una vez fuera del bucle. Guardar los resúmenes en disco (p. ej. files/expedientes/<id>/summary.json), invalidados por el número de fotos y la última toma, o guardar en expediente.json el contador por protocolo al capturar, para no abrir las fotos.

### A20. Al borrar un expediente quedan huérfanos el relieve, los trazos, las anotaciones, las correcciones y las ediciones de todas sus fotos

`app/src/main/java/com/photomed/app/data/PhotoRepository.kt:57` · Recursos · confirmado · Persistencia, Recursos y escalabilidad

- **Evidencia:** deleteExpediente solo hace store.delete(...) y metaDir.deleteRecursively(). No llama a ReliefStore.delete por foto, cosa que sí hacen ZoneGalleryScreen.kt:254 y PhotoViewerScreen.kt:418. Incluso ReliefStore.delete (Relief.kt:197-202) omite files/anotaciones (Annotations) y la preferencia photo_edits (PhotoEdits). AppUpgrade.DERIVED_DIRS solo limpia 'lineas', y no hay ningún barrido de huérfanos para fotos borradas desde la galería del sistema.
- **Impacto:** files/relieve/*.png (PNG sin pérdida del tamaño de la foto, unos 1-3 MB cada uno), files/trazos, files/anotaciones y las preferencias annot_user y photo_edits crecen sin límite. Además quedan datos clínicos (trazos, notas, anotaciones) de pacientes borrados en el almacenamiento privado, lo que supone un problema de privacidad y de cumplimiento al pedir la supresión.
- **Corrección:** En deleteExpediente, recorrer photos(exp) y llamar a una función PhotoData.deleteAll(uri) que borre relieve, trazos, líneas, anotaciones, annot_user y photo_edits, después de DeleteResult.Done. Añadir un barrido periódico, p. ej. en AppUpgrade o en WorkManager, que compare los SHA-1 de los archivos de cada carpeta con las URIs vivas de MediaStore y borre los que no existan.

### A21. Cada trazo de anotación y cada cambio de trazos reescribe el JPEG clínico completo, sin serializar las escrituras sobre la misma foto

`app/src/main/java/com/photomed/app/align/Annotations.kt:125` · Fiabilidad · probable · Recursos y escalabilidad

- **Evidencia:** Annotations.save llama a XmpFile.update(...) en cada guardado, y XmpFile.kt:25-28 abre openFileDescriptor(uri,"rw") y hace ExifInterface.saveAttributes(), que copia y reescribe todo el archivo. PhotoViewerScreen.kt:283 guarda después de cada trazo dentro de un scope.launch independiente en Dispatchers.IO, y mutateTraces (l.205-214) hace lo mismo con ConfirmedTraces.save. No hay mutex por URI: dos trazos rápidos, o un trazo y una confirmación, hacen leer-modificar-escribir a la vez.
- **Impacto:** Un JPEG de 3-6 MB se reescribe por cada trazo, con mucha E/S y batería. Las escrituras concurrentes sobre el mismo archivo pueden perder trazos (gana el último) o, peor, dejar truncada o corrupta la foto clínica original si el proceso muere a mitad de saveAttributes, porque la restauración desde el temporal solo ocurre si salta una excepción.
- **Corrección:** Serializar por URI (Mutex por clave, como GuideMigration.running) tanto la copia privada como el XMP. Guardar en la copia privada de inmediato y llevar el cambio al XMP con debounce, p. ej. al salir del modo de anotación o al cerrar el visor, en una sola escritura. Si es posible, escribir a un archivo temporal y reemplazar por MediaStore en lugar de reescribir el original en el sitio.

## Medio (47)

### M1. scoreOf (la mejor foto de la ráfaga) toca y usa el estado del análisis en vivo: la puntuación depende de cuadros anteriores

`app/src/main/java/com/photomed/app/align/LiveMatcher.kt:403` · Método · confirmado · Cámara y análisis en vivo

- **Evidencia:** scoreOf solo guarda y restaura `matched` y `last` (líneas 404 y 412). analyze(photo) → withCriteria (446-483) llama a `refreshScene`, limitado por tiempo (`now - lastClassesAt > 500`), y por eso reutiliza lastClasses/lastHands del cuadro en vivo. Además añade el voto de la foto a `votes` (2 de 3, mezclado con los votos en vivo) y modifica fusion, lastSchema, lastFace, frameBody, frameSil y `steady` (en el modo de estandarización).
- **Impacto:** En FACE_FRONT, OBLIQUE, SCALP_FRONT y los primeros planos (needsClasses es true y la ráfaga es de 3), los criterios de oclusión de cada foto (mano o gafas delante de la cara) se evalúan con la segmentación de OTRO cuadro. El veredicto «cumple 2 de 3» depende de los votos en vivo y del orden en que se puntúan las fotos. Se puede elegir la foto con un parpadeo o con una mano delante, y el estado en vivo queda contaminado cuando se reanuda el análisis.
- **Corrección:** Separar la evaluación de una foto del estado en vivo. Hacer una ruta sin estado (por ejemplo `evaluateStill(photo)`) que segmente y detecte siempre sobre la propia foto, sin throttle, sin votos, sin fusion y sin steady. O bien guardar y restaurar todo el estado mutable (votes, fusion, lastHands/lastClasses y sus marcas de tiempo, lastSchema, steady, lastFace).

### M2. Cada resultado de análisis recompone toda la CameraScreen y reinicia tres LaunchedEffect

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:570` · Rendimiento · confirmado · Concurrencia, Cámara y análisis en vivo, Manejo de errores

- **Evidencia:** `?.let { live = it }` (~5,5 Hz). `live` se lee en el cuerpo de CameraScreen (415 `val status = live?.eval?.status`, 434 degree, 460 tone, 911 `ready`) y es clave de `LaunchedEffect(live, refPoints, showGhost)` (443), `LaunchedEffect(live)` (456) y `LaunchedEffect(live, zoomAssist, manualZoom)` (874). Box y Column son inline: su contenido pertenece al mismo ámbito de recomposición.
- **Impacto:** La función composable de unas 1300 líneas (barra, overlay de la guía, controles, lambdas de captura) se vuelve a ejecutar entera con cada cuadro analizado, y además se cancelan y relanzan tres corrutinas. Eso cuesta CPU en el hilo principal, que compite con la vista previa y la animación del obturador, y produce tirones mientras se encuadra, justo cuando más se necesita fluidez.
- **Corrección:** Derivar estados estrechos con derivedStateOf (status, degree, ready, schema, hairline) y leer `live` solo dentro de los componentes que lo dibujan (pasar un `() -> LiveFrame?` o un State). Cambiar las claves `live` de los LaunchedEffect por valores derivados (`live?.eval?.scale` cuantizado, por ejemplo) o usar snapshotFlow con distinctUntilChanged.

### M3. Segmentación pelo/piel duplicada en las zonas con línea del pelo: dos inferencias del mismo modelo cada 500 ms

`app/src/main/java/com/photomed/app/align/LiveMatcher.kt:437` · Rendimiento · confirmado · Cámara y análisis en vivo

- **Evidencia:** refreshScene (267-269) llama a `hairSkin?.labels(frame)` cada 500 ms cuando `needsClasses(zone)`, que incluye FACE_VIEWS (FACE_FRONT, SCALP_FRONT) y CLOSEUP_FACE (FOREHEAD). hairlineOf (435-437) vuelve a llamar a `hairSkin?.labels(frame)` con su propio reloj (`liveHairlineAt`) para las mismas zonas (HairSkin.appliesTo: FACE_FRONT, FOREHEAD, SCALP_FRONT).
- **Impacto:** En la vista frontal, la frente y el capilar frontal, el modelo multiclase de MediaPipe (de lo más caro del cuadro) corre el doble de veces de lo necesario y sobre cuadros distintos. La línea del pelo dibujada y los criterios de oclusión pueden discrepar. La puerta nativa queda ocupada más tiempo, se saltan más cuadros y aumenta la latencia hasta el disparo.
- **Corrección:** Guardar las últimas FaceLabels (con su marca de tiempo) en refreshScene y que hairlineOf las reutilice si tienen menos de 500 ms. Solo debe segmentar por su cuenta cuando la zona no necesite clases.

### M4. Sin cara en el cuadro se ejecutan dos detectores de rostro (MediaPipe y luego ML Kit) por cuadro, y en ojo y cuero cabelludo uno más cada 500 ms

`app/src/main/java/com/photomed/app/align/LandmarkDetector.kt:109` · Rendimiento · confirmado · Cámara y análisis en vivo

- **Evidencia:** detectFaceAny: `if (mp != null && mp.points.isNotEmpty()) return ...`. Si no, `val d = detectFace(bmp)` (ML Kit), y con cara de ML Kit además `Gaze.fromImage(d.points, bmp.rgb(), ...)`. LiveMatcher.kt:457-459 llama a `detector.detectFull(Detector.FACE, frame)` cada 500 ms en EYE y SCALP_*.
- **Impacto:** En los primeros planos (FOREHEAD, PERIOCULAR, LIPS), donde lo normal es que no haya cara entera (SteadyFraming.CLOSE_UPS), y en el capilar con la cabeza inclinada, cada cuadro paga MediaPipe más ML Kit en modo rápido con contornos. Sube el coste del cuadro, baja la frecuencia efectiva de análisis (SteadyFraming necesita cuadros seguidos para medir la quietud) y aumenta el consumo de batería y el calor.
- **Corrección:** En el modo de flujo, usar ML Kit como respaldo solo cuando MediaPipe falle por excepción o no esté disponible, no cuando simplemente no encuentre cara. Como mínimo, limitar el respaldo en vivo (p. ej. uno de cada N cuadros) o quitarlo en las zonas de primer plano y cuero cabelludo.

### M5. Asignaciones grandes por cuadro: tres bitmaps intermedios a resolución completa y copias RGB completas para leer unos pocos píxeles

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:1528` · Recursos · confirmado · Cámara y análisis en vivo

- **Evidencia:** toUprightCrop: `toBitmap()` (640×480, o 1280×960 en relieve), `createBitmap(full, crop)`, rotación `createBitmap(cropped, …, m, true)` a resolución completa y después centerCrop34 (createScaledBitmap más otro createBitmap). LiveMatcher.kt:388 `Standardization.lighting(frame.rgb(), …)` y 471 `frame.rgb()` (360×480 IntArray ≈ 691 KB cada una), aunque bodyLightHints solo muestrea con paso w/120 y lighting solo lee tres círculos.
- **Impacto:** Del orden de 4-6 MB asignados por cuadro analizado (unos 20-30 MB/s a 5,5 Hz; mucho más con reliefHi a 1280×960). Eso provoca pausas frecuentes del recolector en el hilo de análisis y en la interfaz, y presión de memoria que, sumada a los modelos nativos, acerca un OOM. Un OutOfMemoryError no lo captura el `catch (_: Exception)` del analizador y tumba el hilo del executor.
- **Corrección:** Escalar antes de rotar: recortar y reducir a 360 px y después girar el bitmap pequeño (o usar un único Matrix con escala y rotación en un solo createBitmap). Reutilizar un IntArray por cuadro: calcular `frame.rgb()` una vez en analyze y pasarlo a lighting, bodyLightHints y Gaza, o bien leer con getPixel solo los puntos muestreados.

### M6. La vigilancia de quietud durante la toma usa el mismo criterio que el disparo y puede cancelar la foto por el propio flash

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:778` · Fiabilidad · especulativo · Cámara y análisis en vivo

- **Evidencia:** `capture(stillOk = { readyNow(live?.eval) })` (925). El watch cancela la toma si `!ok()` dura más de 300 ms antes de las exposiciones. El análisis sigue activo hasta onExposed. Standardization.lightHints añade «Demasiada luz: sin flash» si `l.face > MAX_FACE_LIGHT`, y SteadyFraming toma un salto de luminancia entre cuadros como movimiento (`meanAbsDiff/tex`), lo que da CLOSE 0,3.
- **Impacto:** Con el flash de pantalla (cámara frontal, en ráfaga dura de la primera a la última toma) o con el preflash AE de CAPTURE_MODE_MAXIMIZE_QUALITY, los cuadros en vivo durante la exposición cambian de luz. readyNow pasa a false más de 300 ms y la foto se descarta con «Foto cancelada: la cara se salió del encuadre», aunque el paciente no se haya movido. Esto alarga la sesión y obliga a repetir tomas.
- **Corrección:** Durante la toma, vigilar solo la geometría (puntos o posición, o quietud con la luminancia normalizada) e ignorar la luz y los criterios fotométricos. También se puede pausar la vigilancia mientras el flash o el screenFlash están activos.

### M7. Cada análisis de foto crea y cierra un FaceDetector de ML Kit en modo ACCURATE, a veces dos veces, aunque MediaPipe ya encontró la malla

`app/src/main/java/com/photomed/app/align/FaceAnnotations.kt:1145` · Recursos · confirmado · Análisis de una foto, Modelos nativos

- **Evidencia:** meshRobust siempre llama a `val ml = mlkitPoints(bmp)` (línea 1145), incluso cuando `first != null`. mlkitPoints hace `LandmarkDetector(stream = false, context, FaceEngine.ML_KIT).use { ... }` (línea 1127). Si no hay malla, analyzeSplit vuelve a llamar a mlkitPoints (línea 955). Aligner.autoMask y skinMaskGated crean otro LandmarkDetector por llamada.
- **Impacto:** Por foto: carga del modelo de contornos ACCURATE, inferencia y cierre, lo que suma cientos de ms a la puerta. En Comparar con N fotos, la cola de la puerta crece de forma lineal. El propio comentario de LandmarkDetector.kt:45-48 dice que crear y cerrar detectores de ML Kit seguidos provocó un cierre nativo en sus hilos, y aquí ese patrón sigue en el camino principal.
- **Corrección:** Usar un FaceDetector ACCURATE compartido de larga vida, como MediaPipeFace.withShared, protegido por NativeGate. En meshRobust, reutilizar `ml` en vez de llamar de nuevo a mlkitPoints en analyzeSplit (pasarlo dentro de NativeFace). Pedir ML Kit solo si hace falta contrastar, o hacerlo después de la malla.

### M8. Se crea un ImageSegmenter nuevo (HairSkinSegmenter) por llamada, varias veces en un mismo análisis

`app/src/main/java/com/photomed/app/align/FaceAnnotations.kt:951` · Rendimiento · confirmado · Análisis de una foto, Modelos nativos, Recursos y escalabilidad

- **Evidencia:** `HairSkinSegmenter.createOrNull(context)?.use { ... }` aparece en FaceAnnotations.kt:951, 956, 1066 (hairLines, al que llama frontHair tras el análisis principal) y 1080 (bodyDiagram), en Aligner.kt:127 y 153, y en HairlineMeasure.kt:22. Cada creación ejecuta `ImageSegmenter.createFromOptions` y carga selfie_multiclass.tflite.
- **Impacto:** En SCALP_FRONT se cargan dos segmentadores en un solo análisis, y en Comparar o al importar, uno por foto. Cada carga cuesta decenas o cientos de ms y genera picos de memoria nativa con la puerta tomada. Crear y destruir el grafo una y otra vez multiplica las oportunidades de fallo en código nativo y la fragmentación.
- **Corrección:** Instancia compartida perezosa (`HairSkinSegmenter.withShared(context) { }`), usada solo dentro de NativeGate, igual que MediaPipeFace.withShared. Cerrarla con onTrimMemory o tras un periodo sin uso.

### M9. El relieve y la importación crean su propio FaceLandmarker aunque ya hay uno compartido vivo: hasta 3 mallas en memoria

`app/src/main/java/com/photomed/app/ui/camera/ReliefCapture.kt:134` · Recursos · confirmado · Modelos nativos

- **Evidencia:** `MediaPipeFace(context).use { mp -> ...}` en process (línea 134) y `MediaPipeFace(context).use { it.mesh(photo) }` en saveDepthScaleGated (línea 163). PhotoImport.kt:59 hace `MediaPipeFace(context)`. Mientras tanto, `MediaPipeFace.shared` (FaceEngines.kt:118) no se cierra nunca y el LiveMatcher en vivo tiene otro más.
- **Impacto:** Tras una toma con relieve conviven el FaceLandmarker del vivo, el compartido y el del relieve. Junto con las imágenes de 1440 px de Relief, eso presiona la memoria nativa justo en el escenario donde hubo SIGBUS (relieve seguido del análisis de la foto). Además, cada relieve o escala de profundidad paga la carga del modelo.
- **Corrección:** Usar `MediaPipeFace.withShared(context) { mp -> ... }` en ReliefCapture y PhotoImport (ya están dentro de NativeGate). Cerrar la instancia compartida al recibir onTrimMemory(TRIM_MEMORY_RUNNING_LOW o mayor).

### M10. La puerta se retiene durante cálculo puro y E/S, y deja sin turno a la cámara y a Comparar

`app/src/main/java/com/photomed/app/data/PhotoImport.kt:55` · Escalabilidad · confirmado · Concurrencia, Modelos nativos

- **Evidencia:** `NativeGate.run("importar: ángulo sugerido") { prepareGated(...) }` abarca todo el `uris.map { exif + loadFull + detect }`. Igual con NativeGate.run en analyzeGated (FaceAnnotations.kt:939), que incluye HairDensity.analyze y BodyDiagram.lines, con Aligner.auto (Aligner.kt:90), que incluye alignCore completo, y con tryRunBlocking("análisis en vivo") alrededor de todo `it.analyze(frame)`.
- **Impacto:** Importar 50 fotos retiene la puerta durante decenas de segundos de decodificación y lectura de EXIF: el análisis en vivo salta todos los cuadros y los análisis de Comparar esperan. La alineación retiene la puerta durante el registro de imágenes (CPU pura) y bloquea los análisis de arrugas de otras fotos, con lo que se pierde el paralelismo que el diseño pretende (FaceAnnotations.kt:931-933).
- **Corrección:** Reducir el ámbito de la puerta a las llamadas nativas: en import, decodificar fuera y tomar la puerta por foto solo para `m.detect(b)`; en Aligner, liberar antes de alignCore (pasar las máscaras ya calculadas); en vivo, tomar la puerta solo alrededor de detectFull, labels, segment y detect.

### M11. El análisis no se puede cancelar: Par.map bloquea con Future.get y no hay comprobaciones de cancelación; además, al cancelar se guarda la nota de error en la caché

`app/src/main/java/com/photomed/app/align/FaceAnnotations.kt:936` · Recursos · confirmado · Análisis de una foto

- **Evidencia:** analyzeSplit ejecuta FaceCrop.finish → fromMesh → folds/wrinkles con Par.map (Par.kt: `futures.map { await(it) }` → f.get()), sin ensureActive ni runInterruptible. Los llamadores (PhotoViewerScreen.kt:761 y PhotoLayers.kt:252) envuelven el analyze suspendido en runCatching, que también captura CancellationException, y después hacen putNote(key, "No se pudo analizar la foto.").
- **Impacto:** Al salir del visor o desplazar Comparar, cada análisis cancelado sigue gastando 1–3 s de todos los núcleos del ForkJoinPool y retrasa los análisis visibles. La cancelación se registra como fallo («No se pudo analizar la foto.») y se llama a onAnalyzed desde una corrutina ya cancelada.
- **Corrección:** Llamar a coroutineContext.ensureActive() entre etapas (después de la puerta, antes de folds, antes de wrinkles y entre zonas), o ejecutar finish dentro de runInterruptible y comprobar Thread.interrupted en Par. En Par.map, cancelar los futures pendientes si falla uno o si se interrumpe. En los llamadores, volver a lanzar CancellationException (catch específico) antes del runCatching.

### M12. «Arco de Cupido (pico − centro)» está sesgado: mide distancias euclídeas al punto 17, que incluyen el desplazamiento lateral de los picos

`app/src/main/java/com/photomed/app/align/FeatureMetrics.kt:80` · Método · confirmado · Análisis de una foto

- **Evidencia:** value(..., ((d(mesh[37], mesh[17]) + d(mesh[267], mesh[17])) / 2 - d(mesh[0], mesh[17])) * mm). Los picos 37 y 267 están a los lados de la línea media y el 17 está en ella, así que d(37,17)=√(dx²+dy²) > dy aunque los picos estén a la misma altura que el centro.
- **Impacto:** Con un labio plano (picos a la altura del centro), alto de unos 20 mm y picos a unos 6 mm de la línea media, sale un arco de unos +0,9 mm inexistente. El valor también cambia con el ancho del labio y con el giro de la cabeza, así que no es comparable entre pacientes ni entre tomas.
- **Corrección:** Proyectar en el eje vertical de la cara como en el resto de FeatureMetrics: `val a = Axes(mesh); value(..., ((a.v(mesh[37]) + a.v(mesh[267])) / 2 - a.v(mesh[0])) * mm)`, y añadir su MeasureGuide.

### M13. La densidad capilar cuenta la piel de la cara, las orejas y el cuello como «cuero cabelludo visible»

`app/src/main/java/com/photomed/app/align/HairDensity.kt:80` · Método · probable · Análisis de una foto

- **Evidencia:** headCls incluye FACE_SKIN y BODY_SKIN, y en vis `seg = if (lab[i] == HAIR) 0f else 1f`: todo lo que no es pelo dentro de la región cuenta como cuero visible. En VERTEX no hay cutBelow, y en CROWN/PARIETAL solo se recorta la parte baja (las orejas y los laterales quedan).
- **Impacto:** En una toma cenital donde se ve la frente o en una parietal con la oreja, el porcentaje visible y los grados BASP, Ludwig, Sinclair y Norwood salen inflados, y varían de una toma a otra según cuánta cara entre en el encuadre. Eso falsea el seguimiento.
- **Corrección:** Para la visibilidad, usar como región solo el pelo y la piel rodeada de pelo: por ejemplo, la envolvente o cierre morfológico de la máscara HAIR intersecado con piel, y excluir FACE_SKIN conectada a los ojos o al borde inferior. Como mínimo, excluir FACE_SKIN en VERTEX, CROWN y PARIETAL y usarla solo como referencia de color.

### M14. La alineación de N fotos repite N−1 veces la detección y segmentación de la referencia y crea de nuevo los modelos nativos en cada par

`app/src/main/java/com/photomed/app/align/Aligner.kt:93` · Escalabilidad · confirmado · Comparar y alineación

- **Evidencia:** Aligner.auto se llama en un bucle por cada i (CompareScreen.kt:389) y en cada llamada hace `ImageOps.load34(context, before, DETECT_W)`, crea `LandmarkDetector(...).use { detectFull(b) ... }`, `HairSkinSegmenter.createOrNull(context)?.use { s.labels(bb) ...}` y `SubjectSegmenter().use { bodyOf(b) }`, todo para la misma referencia. Después, autoColorAdjust (ColorMatchDialog.kt:219-221) vuelve a cargar la referencia y a calcular `aligner.skinMask(ref.zone, b)` (otro LandmarkDetector y otro HairSkinSegmenter) por cada foto.
- **Impacto:** Con 12 fotos se crean y cargan unas 11×(3–5) instancias de modelos nativos (cientos de ms cada una) y se analiza la referencia 22 veces. Todo con NativeGate tomada, así que el análisis de arrugas y otras tareas nativas esperan. La alineación de una serie larga tarda varias veces lo necesario.
- **Corrección:** Separar una fase de preparación de la referencia (bitmap, Detection, máscara de piel/cuerpo y GrayImage de REFINE_W) que se calcule una vez por runAlign. Pasar al bucle detectores y segmentadores ya abiertos (un solo use por lote) y reutilizar la máscara de piel de la referencia en autoColorAdjust.

### M15. Cancelar la alineación (cambio de selección) no detiene el par en curso: el cálculo de CPU no tiene puntos de cancelación y retiene la puerta

`app/src/main/java/com/photomed/app/align/Aligner.kt:105` · Fiabilidad · confirmado · Comparar y alineación

- **Evidencia:** Aligner.auto tiene un solo `ensureActive()` tras la detección. alignCore (KeystoneFit con unas 90 evaluaciones, EdgeAligner.align hasta 2 veces, withoutOccluded, BodyRegion) es síncrono y no comprueba la cancelación. resetAlignment() hace `alignJob?.cancel(); alignJob = null`, así que el siguiente runAlign no puede hacer cancelAndJoin del trabajo anterior (prev es null).
- **Impacto:** Al tocar varias miniaturas seguidas, cada alineación cancelada sigue ejecutando su par completo (segundos) con NativeGate tomada. La nueva alineación y el análisis de arrugas de las fotos recién elegidas esperan detrás, y la barra de progreso parece congelada.
- **Corrección:** Añadir comprobaciones de cancelación (`coroutineContext.ensureActive()`, o pasar un `isActive: () -> Boolean` a alignCore, KeystoneFit y EdgeAligner) entre etapas y en los bucles de búsqueda. En resetAlignment, guardar el Job anterior para que runAlign pueda hacer cancelAndJoin.

### M16. ImageOps.load34 recorta, escala y gira bitmaps en el hilo del llamador, que en exportación y recorte manual es el principal

`app/src/main/java/com/photomed/app/ui/compare/CompareExport.kt:65` · Rendimiento · confirmado · Comparar y alineación

- **Evidencia:** `var bmp = ImageOps.load34(context, photo.uri, width)` está antes de `withContext(Dispatchers.Default)`. load34 ejecuta `centerCrop34` (createScaledBitmap + createBitmap) y, si la foto tiene edición, `loadFull(width*2)` + `e.apply` (rotación completa a unos 2160 px) en el contexto del llamador. build() se invoca desde `scope.launch` de ExportSheet (Main), y loadPreview desde produceState (Main). Lo mismo pasa en el editor de recorte (CompareScreen.kt:1081, produceState en Main con ancho 900).
- **Impacto:** Exportar 12 fotos giradas o recortadas hace en el hilo principal 12 rotaciones de bitmaps de unos 18 MB más escalados: de cientos de ms a más de 1 s de UI bloqueada en tramos y la barra de progreso a saltos. En equipos lentos, riesgo de ANR.
- **Corrección:** Envolver el cuerpo de load34 y loadFull en `withContext(Dispatchers.Default)` después del execute de Coil, o pedirle a Coil la transformación (Transformation) para que todo ocurra en sus hilos de decodificación.

### M17. runAlign espera hasta 25 s al análisis de arrugas consultando archivos cada 400 ms, y el resultado depende del momento

`app/src/main/java/com/photomed/app/ui/compare/CompareScreen.kt:374` · Método · confirmado · Comparar y alineación

- **Evidencia:** `withTimeoutOrNull(25_000) { while (true) { s.forEach { photoBaseLines(context, p.uri, prevOf(p)) } ...; delay(400) } }`. photoBaseLines → ConfirmedTraces.of (stat) + key34 → ReliefStore.tag, AnnotUserEdits.tag y `LineMemory.tag(context, previous)`, que hace un stat por cada foto anterior de la zona. Al vencer el plazo se alinea con `beforeLines/afterLines` nulos para las fotos no analizadas.
- **Impacto:** En series largas (100 fotos previas × 12 seleccionadas) son unas 1200 llamadas al sistema de archivos cada 400 ms durante hasta 25 s. Además, la misma selección puede alinearse con o sin puntos de surcos según cuánto tarde el análisis, con resultados distintos entre ejecuciones (poco reproducible en uso clínico).
- **Corrección:** Esperar a señales del análisis (un StateFlow por clave en PhotoAnnotator) en lugar de sondear, y calcular key34 una vez por foto. Indicar en AlignmentResult si se usaron surcos, o realinear de forma determinista cuando lleguen, para que el método sea reproducible.

### M18. Los ajustes de color sobreviven a los cambios de selección o de orden y pueden aplicar una igualación hecha contra otra referencia

`app/src/main/java/com/photomed/app/ui/compare/CompareScreen.kt:338` · Método · confirmado · Comparar y alineación

- **Evidencia:** resetAlignment() limpia alignments y alignedFor, pero no `colorAdjusts`, que va indexado solo por la clave de la foto objetivo (no por la referencia). El botón 'Todas (automático)' (líneas 1032-1038) lanza `scope.launch` sin token ni Job y asigna `colorAdjusts = out` al terminar, aunque la selección haya cambiado. En runAlign, si autoColor no encuentra piel (`adj` vacío), se conservan los ajustes anteriores.
- **Impacto:** Con A,B,C igualados a A, al invertir el orden o quitar A la referencia pasa a ser otra foto, pero B y C siguen desplazadas hacia el color de A, y la nueva referencia puede aparecer recoloreada. Las diferencias de color que se ven y se exportan no corresponden a la referencia mostrada (interpretación clínica errónea).
- **Corrección:** Guardar el ajuste con la clave de la referencia (`Map<Pair<ref,target>, ColorAdjust>`) o limpiar colorAdjusts en resetAlignment. Controlar el trabajo de 'Todas (automático)' con el mismo alignToken/Job y descartarlo si la secuencia cambió.

### M19. El límite XMP_MAX mide el JSON sin escapar y por separado: Traces, Ink y EXIF superan juntos el APP1 de 64 kB y la escritura falla en silencio

`app/src/main/java/com/photomed/app/align/ConfirmedTraces.kt:208` · Método · probable · Persistencia

- **Evidencia:** `json.length <= XMP_MAX` (48 000) en ConfirmedTraces y `XMP_MAX = 40_000` en Annotations.kt:67/125. Pero PhotoMetadata.esc (PhotoMetadata.kt:116) convierte cada `"` del JSON en `&quot;` (6 caracteres) y lo no ASCII en `&#NNN;`. Además, ambos atributos más los datos del paciente van en el mismo XMP (y en el mismo segmento APP1 que el EXIF).
- **Impacto:** Con 40-48 kB de JSON (muchos surcos con `prev` y guías, o mucha tinta), el XMP escapado supera los ~64 kB de un segmento APP1. ExifInterface lanza la excepción, runCatching la convierte en false y los trazos se quedan solo en la copia privada: se pierden al reinstalar, aunque el diseño promete que viajan con la foto. Que se guarde o no depende del orden en que se escribieron.
- **Corrección:** Calcular el tamaño del XMP completo resultante (`PhotoMetadata.withAttr(...)` ya escapado, más los otros atributos) y compararlo con un presupuesto común (~60 kB menos el EXIF existente). Si no cabe, avisar al usuario o comprimir (Base64 de deflate) en vez de fallar en silencio.

### M20. Cambios de trazos con lectura-modificación-escritura sin serializar: los toques rápidos pierden ediciones

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:209` · Fiabilidad · probable · Persistencia

- **Evidencia:** `mutateTraces` lanza una corrutina por acción: `val set = ConfirmedTraces.ensure(...)` y luego `ConfirmedTraces.save(context, u, change(set))`. Dos lanzamientos seguidos leen el mismo `set` antes de que el primero guarde. Lo mismo en CompareScreen.kt:240-246 y en tracePath (líneas 232-245).
- **Impacto:** Si el usuario elimina dos falsos positivos con un intervalo menor que el save (que incluye reescribir el JPEG), el segundo guardado parte del estado anterior y reaparece la primera línea eliminada. En mediciones clínicas reproducibles, eso es un dato incorrecto que no se nota.
- **Corrección:** Serializar las mutaciones por foto (Mutex por URI alrededor de ensure+change+save, o un Channel/actor por pantalla) y aplicar `change` sobre el último estado guardado dentro del lock.

### M21. Condición de carrera en la caché de ConfirmedTraces.of: guarda datos viejos con la marca de tiempo nueva

`app/src/main/java/com/photomed/app/align/ConfirmedTraces.kt:189` · Fiabilidad · confirmado · Persistencia

- **Evidencia:** Se lee `TraceJson.read(f.readText())` fuera del lock y después `mem[uri] = f.lastModified() to s`, volviendo a consultar lastModified. Si save() (línea 204-207) sustituye el archivo entre las dos operaciones, la caché asocia el contenido anterior a la marca nueva. Además, la línea 206 lee `mem[uri]` fuera del `synchronized`.
- **Impacto:** Mientras se guardan trazos en un hilo, otra lectura (render de Comparar o del visor en otro hilo) puede dejar en la caché el conjunto anterior con el stamp vigente. El visor sigue mostrando y midiendo los trazos antiguos hasta la siguiente modificación, aunque el disco esté bien.
- **Corrección:** Tomar `val stamp = f.lastModified()` antes de leer y guardar ese stamp. Mejor aún, hacer lectura, escritura y actualización de mem bajo el mismo lock por URI. Hacer la lectura de mem en la línea 206 dentro de synchronized.

### M22. Listar expedientes cuesta O(expedientes × fotos): una consulta a MediaStore por expediente, abrir cada JPEG y releer todos los protocolos

`app/src/main/java/com/photomed/app/data/ExpedienteSummaries.kt:58` · Escalabilidad · confirmado · Persistencia

- **Evidencia:** Por cada expediente: `repo.photos(exp)` (otra query, además de la que hace listExpedientes en PhotoRepository.kt:101), `Protocols.list(context).size` (lee y parsea todos los JSON de protocolos) y, si cambia el stamp, `repo.readInfo(p)` abre y parsea el EXIF de cada foto por ContentResolver (línea 64). PhotoImport.importAll (PhotoImport.kt:332) hace lo mismo con todas las fotos del expediente en cada importación.
- **Impacto:** Con 200 expedientes y 3 000 fotos, el primer listado hace cientos de consultas binder y miles de aperturas de archivos. Tarda segundos y consume batería. La caché solo vive en memoria, así que se repite en cada arranque.
- **Corrección:** Una sola consulta a MediaStore con `RELATIVE_PATH LIKE base%` agrupada por carpeta para contar fotos y obtener la fecha máxima. Llamar a Protocols.list una vez fuera del bucle. Persistir el índice de ProtocolId y SourceHash por foto (archivo pequeño en filesDir) en lugar de releer el EXIF.

### M23. E/S sin protección dentro de scope.launch del hilo principal: una excepción cierra la app

`app/src/main/java/com/photomed/app/ui/compare/CompareScreen.kt:385` · Manejo de errores · probable · Manejo de errores

- **Evidencia:** `alignJob = scope.launch { ... val orientInfos = withContext(Dispatchers.IO) { allPhotos...associate { it.displayName to (repo.readInfo(it)?.conditions.orEmpty()) } } }`. Aquí scope es rememberCoroutineScope() (línea 159), sin CoroutineExceptionHandler. readInfo usa `contentResolver.openInputStream(uri)` (PhotoRepository.kt:214), que lanza FileNotFoundException o SecurityException. El mismo patrón aparece en PhotoViewerScreen.kt:402 (readInfo), en las líneas 207-213 (mutateTraces → ensure → originalAspect con `openInputStream(uri)!!`), en la 540 (Annotations.save) y en la 631 (ConfirmedTraces.clear), y en ExpedienteScreen.kt:213-216 (updateExpediente sin try).
- **Impacto:** Basta una sola foto de la zona borrada desde la galería del sistema, o sin permiso de lectura (expediente recuperado de otra instalación), para que al alinear en Comparar la app se cierre en el hilo principal. Lo mismo ocurre al confirmar trazos o ver la información de una foto que ya no existe.
- **Corrección:** Dar un manejador común a los scopes de pantalla, por ejemplo `rememberCoroutineScope()` envuelto en `scope + CoroutineExceptionHandler { _, e -> CrashLog.write(...); snackbar }`, o usar un helper `launchSafe`. En runAlign, envolver cada readInfo en runCatching (una foto ilegible solo debe perder su orientación). En ConfirmedTraces.originalAspect, sustituir `!!` por un error controlado.

### M24. Importar fotos: un fallo a mitad de la importación se informa como «0 importadas, N no se pudieron leer»

`app/src/main/java/com/photomed/app/ui/expediente/ExpedienteScreen.kt:182` · Manejo de errores · confirmado · Manejo de errores

- **Evidencia:** `val r = runCatching { PhotoImport.importAll(context, repo, e, chosen) }.getOrNull()`. Después: `val saved = r?.saved?.size ?: 0; val dup = r?.duplicates ?: 0; val failed = chosen.size - saved - dup`. No se llama a CrashLog. En prepare (línea 127) el fallo también se traga con getOrNull, y la importación sigue sin fechas ni análisis.
- **Impacto:** Si importAll lanza después de guardar algunas fotos, el usuario ve «Importadas 0 de 10 (10 no se pudieron leer)» aunque varias ya están en el expediente. Al reintentar, se crean duplicados o confusión, y la causa real no queda registrada.
- **Corrección:** Hacer que importAll acumule los errores por foto y devuelva siempre un resultado parcial, con try por elemento dentro de PhotoImport. En la pantalla, registrar la excepción global con CrashLog.write y mostrar un mensaje distinto («la importación se interrumpió: X guardadas»).

### M25. La migración de fotos de la versión 1 falla en silencio y se aborta en la primera foto que da error

`app/src/main/java/com/photomed/app/MainActivity.kt:64` · Manejo de errores · confirmado · Manejo de errores

- **Evidencia:** `lifecycleScope.launch(Dispatchers.IO) { runCatching { repository.migrateLegacy(legacy) } }`. No hay onFailure ni CrashLog. migrateLegacy (PhotoRepository.kt:178-195) recorre todas las fotos con savePhoto; una excepción interrumpe todo el bucle.
- **Impacto:** Las fotos clínicas antiguas siguen en el almacenamiento privado y no aparecen en la galería ni en el expediente. El usuario no recibe ningún aviso y en el registro de errores no hay nada. Se reintenta en cada arranque y vuelve a fallar en el mismo archivo, así que el resto nunca se migra.
- **Corrección:** Usar un try por foto dentro de migrateLegacy, continuar con las demás y apartar la que falla. Registrar cada fallo con CrashLog.write y mostrar un aviso persistente si quedan fotos sin migrar.

### M26. XmpFile.update reescribe la foto original en su sitio y oculta cualquier fallo

`app/src/main/java/com/photomed/app/data/XmpFile.kt:19` · Fiabilidad · probable · Manejo de errores

- **Evidencia:** `context.contentResolver.openFileDescriptor(uri, "rw")!!.use { pfd -> val e = ExifInterface(pfd.fileDescriptor); ...; e.saveAttributes() }` dentro de `runCatching { ... true }.getOrDefault(false)`. Se llama en cada confirmación o edición de trazos y en cada anotación (ConfirmedTraces.save, Annotations.save).
- **Impacto:** saveAttributes sobre un descriptor reescribe el JPEG completo desde el principio. Si el proceso muere a mitad, la foto clínica original puede quedar truncada. Además, cualquier fallo (permiso, espacio) solo se refleja como un false, sin causa en el registro, lo que impide diagnosticar los avisos «no se pudo escribir en la foto».
- **Corrección:** Para fotos propias (file://), escribir en un temporal y renombrarlo. Para MediaStore, valorar copiar a un temporal, editarlo y volcarlo con IS_PENDING, o al menos registrar la excepción con CrashLog.write antes de devolver false. Distinguir RecoverableSecurityException para pedir permiso al usuario.

### M27. La puntuación de la ráfaga se degrada sin aviso a 0 y alarga la captura hasta unos 12 s

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:690` · Método · confirmado · Concurrencia

- **Evidencia:** scoreOf: `withTimeoutOrNull(4000) { ... pipeline.post { NativeGate.tryRunBlocking("nitidez de la ráfaga", 3000) { ... } ?: 0f ... } } ?: 0f`, y en la línea 793 `files.maxBy { scoreOf(it) }` se llama en serie para las 3 tomas.
- **Impacto:** Si el hilo del pipeline está ocupado (espera de 30 s de la referencia) o la puerta la tiene HairlineMeasure o saveDepthScale de la toma anterior (AppScope), cada scoreOf espera de 3 a 4 s y devuelve 0. maxBy elige entonces la primera toma: se pierde el objetivo de la ráfaga, que es descartar parpadeos, y capturing queda en true hasta unos 12 s, con la interfaz bloqueada.
- **Corrección:** Puntuar las 3 tomas en una sola tarea con un único turno de la puerta (scoreOf sobre la lista) y con un plazo total. Distinguir «sin puntuación» (null) de 0: si falta alguna, usar un criterio de respaldo como la nitidez por Laplaciano (puro Kotlin, sin puerta) en vez de elegir la primera.

### M28. La cancelación no llega al análisis ni a Par: al salir de la pantalla el cálculo sigue y conserva la puerta nativa

`app/src/main/java/com/photomed/app/align/Par.kt:104` · Rendimiento · probable · Concurrencia

- **Evidencia:** Par.map y Par.rows envían a un ForkJoinPool y esperan con `f.get()`, sin comprobar la cancelación ni cancelar los futures restantes si uno falla (líneas 104-105 y 118-120). analyzeSplit (FaceAnnotations.kt:936-1000) no tiene ninguna llamada a ensureActive(); en todo align/ solo hay una, en Aligner.kt:105.
- **Impacto:** Al salir de Comparar o del visor con varias fotos analizándose (1080-1600 px, malla, segmentación y rastreo de surcos en paralelo), la foto en curso termina entera: la fase nativa retiene NativeGate y, si se abre la cámara a continuación, el análisis en vivo se salta cuadros durante segundos. runAlign hace prev?.cancelAndJoin(), que espera a que termine el Aligner.auto completo antes de empezar la nueva alineación. Si una tarea de Par.map lanza una excepción, las demás siguen consumiendo núcleos.
- **Corrección:** Añadir coroutineContext.ensureActive() entre las fases de analyzeSplit y de alignCore, sobre todo antes de NativeGate.run y después de cada bloque de Par. En Par.map, aceptar un `isActive: () -> Boolean` o capturar el Job y, ante un error o una cancelación, hacer futures.forEach { it.cancel(false) }.

### M29. E/S de disco y decodificación de relieve en el hilo principal dentro de produceState al montar la clave y los extras del análisis

`app/src/main/java/com/photomed/app/ui/compare/PhotoLayers.kt:251` · Rendimiento · confirmado · Comparar y alineación, Concurrencia

- **Evidencia:** produceState se ejecuta en el hilo principal. `key34(...)` (líneas 211-215) lee ReliefStore.meta (JSON de disco), AnnotUserEdits.tag y LineMemory.tag (stat de ficheros). `LineMemory.priors(context, previous)` lee y parsea los JSON, y `PhotoAnnotator.extrasFor(...)` hace ReliefStore.meta + load34 + `Relief.decode` (bucle sobre 1080×1440 píxeles, FloatArray de unos 6 MB). Todo se evalúa como argumentos antes del withContext(Default) de analyzeSplit. Lo mismo pasa en PhotoViewerScreen.kt:758-766 (1600 px). CrashLog.crumb hace writeText síncrono en el contexto de quien llama a NativeGate.run, que en Aligner.auto es el hilo principal (scope.launch de Comparar).
- **Impacto:** Al abrir Comparar con 2 a 4 fotos con relieve hay varias decodificaciones por píxel y lecturas de disco en el hilo principal: fotogramas perdidos y, en almacenamiento lento, riesgo de ANR. StrictMode lo detectaría.
- **Corrección:** Envolver en withContext(Dispatchers.IO/Default) el cálculo de la clave, de los priors y de extrasFor (o hacer que extrasFor y priors cambien ellos mismos de contexto). Hacer que CrashLog.crumb escriba de forma asíncrona, en un hilo de E/S propio o en un buffer con volcado diferido.

### M30. El etiquetado de componentes asigna objetos por píxel de piel; feet() lo ejecuta una vez más sin memo

`app/src/main/java/com/photomed/app/align/ZoneCriteria.kt:546` · Recursos · confirmado · Método de los criterios

- **Evidencia:** Dentro del bucle por píxel: `for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1))` crea un array y 4 Pair por píxel, y `members += k` en ArrayList<Int> crea un Integer por píxel. Además se asignan dos IntArray(w*h) por llamada. feet() llama a skinBlobs(c) (l.730), que repite labeled(c) completo además del que hace computeFeet. armFromSkin hace otro labeled (l.1100).
- **Impacto:** Con la máscara a la resolución del cuadro de análisis (480×640) y la piel ocupando un 30–60 %, son cientos de miles de asignaciones por etiquetado, varias veces por cuadro. Hay pausas de GC frecuentes en el hilo de la cámara.
- **Corrección:** Usar desplazamientos en IntArray (dx=[1,-1,0,0], dy=[0,0,1,-1]) y guardar los miembros en un IntArray preasignado (o reetiquetar en una segunda pasada con la pila). Reutilizar el resultado de computeFeet (cajas de manchas) en feet() en lugar de llamar a skinBlobs. Opcionalmente, submuestrear la máscara a ~240 px de ancho para los criterios.

### M31. handLike recalcula min/max de los 21 puntos de cada mano para cada píxel de piel del cuadro

`app/src/main/java/com/photomed/app/align/ZoneCriteria.kt:458` · Rendimiento · confirmado · Método de los criterios

- **Evidencia:** Dentro de `for (y in 0 until c.height) for (x in 0 until c.width)`: `big.any { h -> val m = 0.15f * (h.points.maxOf { it.y } - h.points.minOf { it.y }); px in h.points.minOf { it.x } - m..h.points.maxOf { it.x } + m && py in … }`, con 6 recorridos de 21 puntos más una lambda por píxel.
- **Impacto:** Con unos 100k píxeles de piel, más de 13M operaciones por llamada. handLike se ejecuta en cada cuadro en pies, espalda, glúteos y brazo (l.735, 915, 987, 1034).
- **Corrección:** Precalcular una vez por mano su caja con margen (FloatArray de 4) antes del bucle y comprobarla con comparaciones simples, o contar la piel dentro de las cajas recorriendo solo esos rectángulos.

### M32. El alabeo mezcla convenciones de signo: HeadRoll es horario y det.roll (Euler de MediaPipe/ML Kit) antihorario; la indicación sale invertida en oblicuos

`app/src/main/java/com/photomed/app/align/Standardization.kt:146` · Método · probable · Método de los criterios

- **Evidencia:** `val roll = (if (yawAbs < 30f && !oblique) HeadRoll.of(det.points)?.first else null) ?: det.roll`. HeadRoll documenta «positivo = horario en pantalla». det.roll viene de FaceEngines.euler: atan2(r10, r00) en el espacio de MediaPipe con y hacia arriba, es decir, positivo = antihorario. En ML Kit headEulerAngleZ también es positivo en sentido antihorario. No se invierte el signo en ningún punto (OneEuro.kt:69 lo copia tal cual).
- **Impacto:** En FACE_OBLIQUE (siempre oblicuo) y con giros ≥30° el mensaje «enderezar X° (inclinada a la derecha/izquierda)» indica el lado contrario. El fotógrafo corrige en sentido opuesto, el alabeo empeora y la toma no se dispara.
- **Corrección:** Normalizar a una sola convención: usar `-det.roll` en el fallback (o invertir el signo en FaceEngines.detectionOf y en LandmarkDetector para ML Kit) y documentar la convención en Detection.roll.

### M33. HeadPose inicia la búsqueda del alabeo con el signo invertido

`app/src/main/java/com/photomed/app/align/HeadPose.kt:91` · Método · confirmado · Método de los criterios

- **Evidencia:** `val rollInit = HeadRoll.of(points, 45f)?.first ?: 0f` se usa como a[2]. Con R=Rz(r) y la proyección v = ty − s·y' (l.34), un r>0 sube el lado derecho de la imagen (antihorario en pantalla). HeadRoll es positivo cuando el lado derecho está más bajo (horario), así que la semilla queda a −2·roll del óptimo.
- **Impacto:** Con la cabeza inclinada 20–40°, la búsqueda por coordenadas arranca a 40–80° del mínimo en los tres arranques de guiñada. Hacen falta muchas más evaluaciones de solve (coste por cuadro ×2–4, y HeadPose.fit se llama al menos 2 veces por cuadro: detector y OneEuro) y crece el riesgo de caer en un mínimo local acoplado con la guiñada.
- **Corrección:** Usar `-HeadRoll.of(points, 45f)?.first` como semilla y, de paso, no volver a canonicalizar dentro de fitRaw cuando el llamador ya pasa puntos canónicos.

### M34. BodyPoints nombra cada pie por la mitad de la imagen: dos pies en la misma mitad sobrescriben y mezclan sus puntos

`app/src/main/java/com/photomed/app/align/BodyPoints.kt:86` · Fiabilidad · confirmado · Método de los criterios

- **Evidencia:** `val side = if (tx < 0.5f) 'a' else 'b'; out.putAll(footPoints(f, c, if (s.single) 'a' else side))`. Si los dos pies tienen la punta en x≥0.5 (o en <0.5), ambos escriben las claves pie_b_*. Las claves que solo calcula uno (izq_k, der_k, dedo_k dependen de rowAt) quedan con los valores del otro pie.
- **Impacto:** Cuando el encuadre está descentrado, el mapa resultante combina puntos de dos pies distintos con el mismo nombre. La alineación con la referencia empareja puntos falsos, con escala o giro erróneos y coincidencias o rechazos espurios.
- **Corrección:** Con dos pies, asignar 'a' y 'b' por el orden de la punta (ya están ordenados por sortedBy en l.82) y no por la mitad de la imagen, o descartar el segundo si cae en la misma mitad.

### M35. handOutline y Nails.ofHand se calculan 2–3 veces por mano y por cuadro, con dos arrays w×h nuevos en cada traceComponent

`app/src/main/java/com/photomed/app/align/BodySchema.kt:38` · Recursos · confirmado · Método de los criterios

- **Evidencia:** BodySchema.hands llama a ZoneCriteria.handOutline y a Nails.ofHand (l.38-41). BodyPoints.handEdges llama otra vez a handOutline (BodyPoints.kt:149). ZoneCriteria.hands llama otra vez a Nails.ofHand (ZoneCriteria.kt:518). traceComponent asigna BooleanArray(w*h) e IntArray(w*h) por llamada (l.1195) y evalúa 24 distancias a segmento por píxel candidato.
- **Impacto:** En manos con detector: unos 4 trazados y 4 búsquedas de uñas por cuadro, con unos 1,5 MB de arrays temporales cada uno, en el hilo de análisis. Resulta en GC y latencia del esquema.
- **Corrección:** Memoizar por (Classes, Hand) el contorno y las uñas de cada mano en el cuadro (o calcularlos una vez en LiveMatcher y pasarlos en Scene), y trazar sobre la caja de la mano en lugar de sobre arrays del cuadro entero.

### M36. El tronco de lado mide la ropa en una franja fija de ±0,12 anchos de imagen, sin escalar al tamaño del cuerpo

`app/src/main/java/com/photomed/app/align/ZoneCriteria.kt:972` · Método · confirmado · Método de los criterios

- **Evidencia:** `val cx = (hl.x + hr.x) / 2; val half = 0.12f` y después skinClothes(it, cx - half, …, cx + half, …) (l.977). La misma constante filtra los brazos que tapan el abdomen (l.974).
- **Impacto:** Con el paciente lejos, la franja de 0,24 del ancho supera el grosor del abdomen de perfil: entra fondo, la piel queda por debajo de MIN_SKIN y aparece «Descubra el abdomen» aunque esté descubierto, así que la toma no se dispara. De cerca ocurre lo contrario: solo se mide el centro y un brazo junto al flanco no se detecta.
- **Corrección:** Escalar la franja con la longitud del tronco (p. ej. half = 0.12·|hy−sy|/0.35) o con el grosor real medido con halfWidth sobre la máscara, como ya se hace en BodySchema.body.

### M37. La guía deformada se redibuja entera en cada cambio de tono (cada cuadro): 1.813 evaluaciones TPS más drawBitmapMesh, o 320 drawImage por franjas, y un Paint nuevo

`app/src/main/java/com/photomed/app/ui/camera/GuideOverlay.kt:203` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** Dentro de Canvas: val verts = mesh.meshVertices(36, 48, size.width, size.height) (37×49 llamadas a map() de O(140)). En la rama por franjas: for (i in 0 until 160) { drawImage(...); drawImage(...) }. Además, paint = remember(tone) { Paint(...).apply { colorFilter = LightingColorFilter(...) } }. tone = GuideTone.of(live?.eval) (CameraScreen.kt:460) es un degradado continuo y cambia en cada cuadro.
- **Impacto:** Cada cuadro con un grado de coincidencia distinto recompone GuideLayer y redibuja el lienzo. Eso son unas 250.000 evaluaciones de núcleo con logaritmo, o 320 blits con filtro de color, más la asignación de Paint y ColorFilter. Esto se suma al coste del análisis en vivo en el mismo dispositivo.
- **Corrección:** Cachear los vértices con remember(mesh, size), por ejemplo en drawWithCache, y aplicar el color con un ColorFilter que lea el tono como estado solo en la fase de dibujo. Cuantizar el tono (p. ej. 16 pasos) para no recrear Paint ni ColorFilter. En la rama por franjas, precalcular los rectángulos en drawWithCache.

### M38. El análisis del visor lee ficheros, preferencias y JSON de las fotos anteriores en el hilo principal

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:752` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** Dentro de produceState, que se ejecuta en Main y fuera de withContext: val key = "full|$u|...|${ReliefStore.tag(context, u)}|${AnnotUserEdits.tag(context, u)}|${LineMemory.tag(context, previous)}". ReliefStore.meta hace f.readText() más JSON; LineMemory.tag hace un stat por cada foto anterior. En la l. 764, priors = LineMemory.priors(context, previous) lee y parsea el JSON de cada foto anterior antes de entrar en analyze(). PhotoLayers.kt:251/255 (rememberPhotoAnnotations, key34 y priors) repite el patrón para cada foto de Comparar.
- **Impacto:** Con series largas (decenas de fotos de la misma zona) se hacen decenas de E/S y parseos JSON en Main por cada página del pager y por cada foto de Comparar. Eso provoca tirones al pasar de foto y riesgo de ANR con almacenamiento lento. StrictMode lo marcaría.
- **Corrección:** Envolver en withContext(Dispatchers.IO) el cálculo de la clave (tags) y de priors, o mover todo el cuerpo de produceState a IO/Default y publicar el value en Main.

### M39. Giro, espejo y recorte de bitmaps de hasta 2048 px se aplican en el hilo principal al abrir cada foto y al recortar

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:731` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** produceState { value = runCatching { ImageOps.loadFull(context, photo.uri, 2048, edit).asImageBitmap() } } sin withContext. Coil decodifica fuera de Main, pero loadFull termina con (edit ?: ...).apply(bmp), que hace Bitmap.createBitmap(src, 0, 0, w, h, matrix, true) y el recorte en el hilo que llama (Main). Lo mismo en la l. 594 (editor de recorte, 1600 px).
- **Impacto:** En fotos con giro o espejo, cada página del pager (y las vecinas al deslizar) asigna y transforma un bitmap ARGB de unos 12 MB en Main, con filtrado. Son decenas de ms de bloqueo por foto y picos de memoria visibles como tirones al pasar de foto.
- **Corrección:** Envolver la carga en withContext(Dispatchers.Default) dentro de produceState, o hacer que ImageOps.loadFull ejecute apply() en Dispatchers.Default internamente, ya que es suspend.

### M40. En el visor, el zoom redibuja todas las anotaciones y vuelve a colocar los números con MeasureNumbering.place en cada cuadro del gesto

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:843` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** Dentro del Canvas: val k = (w / 360.dp.toPx()).coerceIn(...) / scale.coerceAtLeast(1f).let { sqrt(it) }. Leer 'scale' en la fase de dibujo invalida el dibujo en cada evento de pellizco. Luego: drawAnnotations(a, layers, k, numbers = MeasureNumbering.of(listOf(a)), ...) → drawLineNumbers → MeasureNumbering.place(...). place() evalúa ~66 candidatos por línea contra todos los segmentos de todas las líneas visibles (GuideOverlay.kt:747, LineMetrics.kt:323-348) y crea un Paint nuevo en cada dibujo (GuideOverlay.kt:739).
- **Impacto:** Con contornos de cientos de puntos y 15-25 líneas numeradas, cada cuadro del zoom hace del orden de millones de segDist/hypot más smoothPath de todas las líneas. Se notan tirones al hacer zoom sobre fotos analizadas, justo cuando el clínico inspecciona detalles.
- **Corrección:** Usar drawWithCache, o remember(annots, layers, numbers, bucket(k)), para cachear los Path suavizados y las posiciones de los números, y recalcular solo al cambiar las líneas, las capas o un escalón de k. Calcular MeasureNumbering.of fuera del lambda de dibujo y reutilizar un Paint recordado.

### M41. Comparar: LocalCompareView estático con un CompareView nuevo (lambda nueva) en cada recomposición invalida todo el subárbol de fotos y recalcula la numeración por foto

`app/src/main/java/com/photomed/app/ui/compare/PhotoLayers.kt:374` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** val LocalCompareView = staticCompositionLocalOf { CompareView() }. En CompareScreen.kt:526 se crea CompareView(..., onSelect = { l, key -> ... }) en cada recomposición: la lambda nueva rompe el equals del data class. En ComparePhoto (l. 386): numbers = MeasureNumbering.of(v.annots.values.toList()) sin remember, una vez por foto.
- **Impacto:** Cualquier recomposición de CompareScreen, por ejemplo al arrastrar el deslizador de opacidad (annotAlpha.value), cambiar la selección o recibir el resultado de una foto, recompone todas las celdas, sus AsyncImage y sus Canvas. Además ordena todas las líneas N veces. En series de 4-8 fotos el deslizador y la selección van a saltos.
- **Corrección:** Hacer estable el CompareView: remember con claves explícitas y onSelect recordado (rememberUpdatedState más una lambda estable), o usar compositionLocalOf en vez del estático. Calcular la numeración una sola vez en CompareScreen (remember(annots)) e incluirla en CompareView.

### M42. Las cargas grandes para análisis e importación entran en la caché de memoria de Coil y desplazan las miniaturas

`app/src/main/java/com/photomed/app/align/ImageOps.kt:37` · Recursos · confirmado · Recursos y escalabilidad

- **Evidencia:** loadFull y load34 usan context.imageLoader.execute(ImageRequest...allowHardware(false)) sin memoryCachePolicy(CachePolicy.DISABLED). PhotoImport.kt:95 y :112 decodifican con loadFull(..., 4096, ...), es decir un ARGB de unos 4096×3072×4 = 48 MB. El análisis usa 1440-1600 px (ReliefCapture.kt:140, GuideMigration.kt:130).
- **Impacto:** La caché fuerte de Coil (aprox. 25 % del heap) retiene bitmaps de 8-48 MB que no se volverán a pedir con la misma clave. Esto vacía la caché de miniaturas de las rejillas, que se vuelven a decodificar al desplazarse, y mantiene vivos decenas de MB después de importar. En gama baja aumenta el riesgo de OOM o de cierre por memoria (REASON_LOW_MEMORY) durante una importación en lote.
- **Corrección:** Añadir .memoryCachePolicy(CachePolicy.DISABLED) (y diskCachePolicy READ_ONLY o DISABLED) en las peticiones de ImageOps. Para importar, decodificar con BitmapFactory, inSampleSize y región, o copiar el archivo original sin decodificarlo.

### M43. La importación decodifica a 4096 px y vuelve a codificar como JPEG 95: pierde calidad y EXIF original y pide unos 48 MB por foto

`app/src/main/java/com/photomed/app/data/PhotoImport.kt:124` · Método · confirmado · Recursos y escalabilidad

- **Evidencia:** Cada foto pasa por decode(it.uri) (loadFull 4096) y luego bmp.compress(Bitmap.CompressFormat.JPEG, 95, o) a cacheDir/import_*.jpg. Además, para detectar duplicados se hace repo.photos(exp).forEach { repo.readInfo(p) } (l.99), que abre todas las fotos del expediente.
- **Impacto:** La foto clínica importada sufre una pérdida generacional por recompresión, reduce la resolución si supera 4096 px y pierde la EXIF original (cámara, exposición, perfil). Cada foto supone un pico de unos 48 MB más el JPEG. En expedientes con cientos de fotos, la comprobación de duplicados abre todas las fotos en cada importación.
- **Corrección:** Copiar el flujo original tal cual (las fotos ya guardadas tienen orientación EXIF) e incrustar el XMP con ExifInterface, o decodificar solo si hace falta enderezar. Guardar los SourceHash en expediente.json o en un índice en lugar de leerlos del XMP de cada foto.

### M44. En el arranque, el hilo principal hace E/S potencialmente larga: borrado recursivo de la caché al actualizar y lectura de los volcados de cierres

`app/src/main/java/com/photomed/app/MainActivity.kt:53` · Rendimiento · confirmado · Recursos y escalabilidad

- **Evidencia:** onCreate llama de forma síncrona a CrashLog.checkExitReasons (lee traceInputStream completo con readBytes, aplica un split con regex y escribe archivos), AppUpgrade.onStart (cacheDir.listFiles()?.forEach { it.deleteRecursively() }, que incluye la caché de disco de Coil de hasta 250 MB, más files/lineas, con prefs commit()) y BuiltInProtocols.seed (escrituras de archivos), todo antes de setContent.
- **Impacto:** Tras cada actualización, el primer arranque borra miles de archivos de la caché de Coil y de lineas en el hilo principal. Con un usuario veterano puede tardar segundos y provocar un ANR en el arranque. Los volcados de ANR o nativos (varios MB) también se leen y procesan en el hilo principal.
- **Corrección:** Mover checkExitReasons, la limpieza de AppUpgrade (dejando solo la marca de versión síncrona) y seed a lifecycleScope.launch(Dispatchers.IO). Para que la caché antigua no se use, renombrar primero el directorio (operación atómica y rápida) y borrarlo después en segundo plano.

### M45. Las ediciones y correcciones por foto viven en SharedPreferences sin límite: se parsean enteras en la composición y se reescribe el XML completo en cada cambio

`app/src/main/java/com/photomed/app/align/ImageEdit.kt:69` · Escalabilidad · confirmado · Recursos y escalabilidad

- **Evidencia:** PhotoEdits.all() carga getSharedPreferences("photo_edits").all y parsea un JSONObject por entrada la primera vez. Se invoca desde photoModel() (ui/PhotoModel.kt:20) durante la composición de cada miniatura, en el hilo principal. AnnotUserEdits (LineMemory.kt:120-124) guarda por URI JSON con los trazos añadidos a mano en la preferencia annot_user, con apply() en cada dismiss o add. Ninguna de las dos se limpia al borrar fotos.
- **Impacto:** Con miles de fotos editadas o corregidas, el XML de preferencias crece a cientos de KB o varios MB: la primera miniatura bloquea el hilo principal mientras se lee y parsea todo (riesgo de jank o ANR en la rejilla). Cada corrección reescribe el archivo entero, y QueuedWork puede bloquear en onPause/onStop esperando escrituras pendientes.
- **Corrección:** Pasar ambos almacenes a archivos por URI (como trazos o anotaciones) o a una pequeña base de datos (Room o SQLite) con clave por URI. Precalentar PhotoEdits en IO antes de mostrar las rejillas y borrar las entradas al eliminar la foto.

### M46. El análisis en vivo pide RGBA_8888 a CameraX y no detiene el analizador: conversión YUV a RGBA en cada cuadro aunque solo se analice uno cada 180 ms

`app/src/main/java/com/photomed/app/ui/camera/CameraScreen.kt:556` · Recursos · probable · Recursos y escalabilidad

- **Evidencia:** Se usa setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) con STRATEGY_KEEP_ONLY_LATEST. El analizador vuelve enseguida cuando matchingFlag es falso o el análisis está pausado, o si now - lastAnalysis < 180 (l.564). Además, toUprightCrop(360) (l.566; l.1528-1537: toBitmap, createBitmap del recorte y otro con rotación) se ejecuta antes de intentar NativeGate.tryRunBlocking, así que se calcula aunque la puerta esté ocupada y el cuadro se descarte.
- **Impacto:** Como el analizador queda libre casi siempre, CameraX convierte a RGBA unos 30 cuadros/s (640×480, o 1280×960 en modo relieve) para aprovechar unos 5. Con la puerta ocupada (análisis de la foto recién tomada) se crean 3 bitmaps por cuadro para nada. Se nota en batería y temperatura en sesiones largas de captura.
- **Corrección:** Desvincular el ImageAnalysis (o clearAnalyzer) mientras no se hace coincidencia. Comprobar la puerta antes de convertir, p. ej. tryRunBlocking { proxy.toUprightCrop(360) ... }, o usar YUV y convertir solo el cuadro elegido. Valorar setTargetFrameRate o Camera2Interop para limitar la tasa de análisis.

### M47. Al borrar fotos, los datos privados se eliminan antes de que la galería confirme el borrado

`app/src/main/java/com/photomed/app/ui/gallery/ZoneGalleryScreen.kt:254` · Fiabilidad · confirmado · Recursos y escalabilidad

- **Evidencia:** Se ejecuta list.forEach { ReliefStore.delete(context, it.uri.toString()) }; repo.deletePhotos(list), y lo mismo en PhotoViewerScreen.kt:418 y :631. MediaStorePhotoStore.delete (PhotoStore.kt:116-131) puede devolver DeleteResult.NeedsConfirmation (fotos de otra instalación, API 29+), que el usuario puede cancelar.
- **Impacto:** Si el usuario cancela el diálogo del sistema, la foto sigue en el expediente pero ha perdido de forma irreversible su relieve PNG, su escala del sensor de profundidad, la memoria de líneas y las correcciones manuales (annot_user).
- **Corrección:** Borrar los datos derivados solo después de DeleteResult.Done, o en el callback de rememberGalleryDelete cuando ok sea true, usando la lista de URIs confirmadas.

## Bajo (23)

### B1. Tras el shutdown, los cuadros ya encolados no se procesan y su ImageProxy nunca se cierra

`app/src/main/java/com/photomed/app/align/AnalysisPipeline.kt:36` · Recursos · confirmado · Cámara y análisis en vivo

- **Evidencia:** `fun post(block) { if (stopped) return; try { executor.execute { if (!stopped) block() } } catch (_: RejectedExecutionException) {} }`. El analizador de CameraX es `{ r -> pipeline.post(r::run) }` y el `proxy.close()` está dentro de ese bloque (CameraScreen.kt:574).
- **Impacto:** Si llega un cuadro entre shutdown y el desenlace de la cámara (o hay uno ya en cola), el Runnable de CameraX se descarta sin ejecutarse. Su ImageProxy queda sin cerrar y retiene un búfer del ImageReader. Con KEEP_ONLY_LATEST el analizador deja de recibir cuadros si el caso de uso se reutilizara, y queda un aviso de fuga de imagen.
- **Corrección:** Usar un executor propio para CameraX que, si el pipeline está detenido, ejecute de todos modos el Runnable (el analizador ya comprueba el motor con withEngine, que devuelve null), o que llame a close() sobre el proxy. Basta con cambiar `if (!stopped) block()` por la ejecución incondicional en el caso del analizador.

### B2. setReference y guideOf segmentan la misma imagen dos veces y crean un SubjectSegmenter extra por llamada

`app/src/main/java/com/photomed/app/align/LiveMatcher.kt:192` · Rendimiento · confirmado · Modelos nativos

- **Evidencia:** En setReference: `refBody = BodyRegion.of(zone, bmp) { b -> segmenter?.segment(b) }` (línea 182) y, sobre el mismo bmp, `SubjectSegmenter().use { s -> s.segment(bmp) }` (línea 192). guideOf (línea 152) crea otro aunque `segmenter` ya existe. Además, el getter `segmenter` reintenta `SubjectSegmenter()` en cada llamada si la creación falla (línea 134).
- **Impacto:** Una creación y un cierre de cliente ML Kit, más una inferencia duplicada, en cada cambio de referencia o de orientación. Con la referencia girada, guideOf añade otra creación más. Si el modelo no carga, se reintenta la creación cada 300-600 ms en el vivo.
- **Corrección:** Usar siempre el `segmenter` cacheado y reutilizar la máscara de silueta para refBody, en lugar de segmentar de nuevo. Recordar el fallo de creación (bandera) para no reintentarlo en cada cuadro.

### B3. El MediaPipeFace compartido nunca se recrea ni se libera, y solo se conserva el primer error

`app/src/main/java/com/photomed/app/align/FaceEngines.kt:128` · Fiabilidad · probable · Modelos nativos

- **Evidencia:** `val m = shared ?: runCatching { MediaPipeFace(...) }...also { shared = it }`. Si `block(mp)` lanza una excepción, solo se guarda lastError (`if (lastError == null)`) y la instancia sigue en `shared`. No hay close() ni respuesta a onTrimMemory.
- **Impacto:** Si el grafo de MediaPipe queda en mal estado tras un error nativo recuperable, todos los análisis de malla siguientes fallan hasta reiniciar la app y caen a ML Kit, con menos medidas (sin frente, ceño ni patas de gallo). Además, unos 10-30 MB de memoria nativa quedan retenidos durante toda la vida del proceso, aunque el usuario no vuelva a analizar.
- **Corrección:** Si block falla con IllegalStateException o RuntimeException de MediaPipe, cerrar y anular `shared` para recrearla en la siguiente llamada. Liberarla en onTrimMemory o tras un tiempo sin uso.

### B4. Conversión a CIELAB con un FloatArray por píxel y pow() por canal, y medianas con listas de Float en caja

`app/src/main/java/com/photomed/app/align/LineMetrics.kt:274` · Rendimiento · confirmado · Análisis de una foto

- **Evidencia:** srgbToLab devuelve `floatArrayOf(...)` en cada píxel y lin() hace `pow(2.4f)` por canal. LabImage.fromArgb lo llama sobre todo el recorte (unos 0,5 Mpx). HairDensity.analyze (línea 94) guarda un FloatArray por píxel en `Array(n)` y su median (línea 98) hace `idx.map { labOf[it][k] }.sorted()` tres veces con Float en caja.
- **Impacto:** Cientos de miles de objetos y millones de pow por análisis. Presión de GC y decenas a cientos de ms extra por foto en gama media, que se multiplican en Comparar.
- **Corrección:** Tabla de 256 entradas para lin(); escribir L, a y b directamente en los arrays de salida sin el FloatArray intermedio. En HairDensity, guardar tres FloatArray planos y calcular las medianas con FloatArray copiados más sort(), o con un histograma.

### B5. Caché del análisis: la clave toma WrinkleTuning al guardar y no al empezar, y el mapa de notas crece sin límite

`app/src/main/java/com/photomed/app/align/FaceAnnotations.kt:1268` · Fiabilidad · confirmado · Análisis de una foto, Concurrencia

- **Evidencia:** put() y putNote() anteponen `WrinkleTuning.key` cuando se guarda el resultado. Si la sensibilidad cambia durante el análisis (WrinkleTuning.apply modifica varios @Volatile sin atomicidad mientras las tareas de Par los leen), un resultado calculado con los parámetros viejos, o mezclados, se guarda con la clave nueva. `notes` es un HashMap sin removeEldestEntry, mientras que `cache` está limitado a 40.
- **Impacto:** Tras cambiar la sensibilidad se pueden ver líneas que no corresponden a la sensibilidad elegida hasta clearCache(). Las notas se acumulan durante toda la sesión, una por foto, edición y sensibilidad.
- **Corrección:** Tomar una instantánea inmutable de WrinkleTuning al empezar analyze (un data class), pasarla a wrinkles() y usar su clave al guardar. Guardar la nota dentro del mismo LinkedHashMap acotado, por ejemplo con `Pair<List<AnnotLine>, String?>`.

### B6. FaceCrop: el recorte del bitmap trunca x0/y0 a enteros pero el cambio de coordenadas usa los flotantes, y finish lee los píxeles del recorte dos veces

`app/src/main/java/com/photomed/app/align/FaceAnnotations.kt:858` · Método · confirmado · Análisis de una foto

- **Evidencia:** bitmap(): `val x = c.x0.toInt()...; createBitmap(src, x, y, w, h, m, true)`, mientras que toCrop/toPhoto usan `(p.x * srcW - x0) / w` con x0 flotante y c.w en lugar del w entero. finish() hace getPixels en la línea 897 y lum(b) repite getPixels con otro IntArray en la línea 865.
- **Impacto:** La malla queda desplazada respecto a la imagen analizada hasta 1 px de la foto (hasta 2 px del recorte con scale=2): los pasillos y los valles se buscan ligeramente desplazados y las líneas vuelven con ese sesgo. También se copia sin necesidad un IntArray del tamaño del recorte por análisis.
- **Corrección:** Redondear x0/y0 (y w/h) a enteros en crop() antes de construir Crop, para que bitmap() y toCrop usen exactamente el mismo origen. Calcular lum a partir del `px` ya leído (una función lum(px, w, h)).

### B7. La exportación retiene a resolución completa todas las fotos renderizadas y runCatching se traga la cancelación

`app/src/main/java/com/photomed/app/ui/compare/CompareExport.kt:384` · Recursos · confirmado · Comparar y alineación

- **Evidencia:** `val b = runCatching { render(...) }.getOrNull()` atrapa también CancellationException (y OOM), que cuenta como `failed++`. `rendered += (if (grid || side || gif != null) createScaledBitmap(...) else b)` guarda el bitmap de 1080×1440 aunque en modo individual no se vuelva a usar. render() encadena hasta 7 copias completas por foto (load, perspectiva, IntArray, apply, copia para anotaciones, transformed, orient).
- **Impacto:** En exportación individual de 12 fotos quedan unos 75 MB retenidos hasta el final, más picos de unos 50 MB por foto. Una cancelación (salir de la pantalla) se cuenta como fallo de lectura y el bucle sigue hasta el siguiente ensureActive.
- **Corrección:** No añadir a `rendered` si no hay cuadrícula, lado a lado ni GIF, y reciclar los intermedios de render. Volver a lanzar CancellationException en el runCatching (`.onFailure { if (it is CancellationException) throw it }`).

### B8. Caché `mem` de ConfirmedTraces sin límite: retiene un TraceSet por cada foto vista en toda la sesión

`app/src/main/java/com/photomed/app/align/ConfirmedTraces.kt:175` · Recursos · confirmado · Persistencia, Recursos y escalabilidad

- **Evidencia:** `private val mem = HashMap<String, Pair<Long, TraceSet?>>()` solo se limpia en forget/clear. Cada TraceSet incluye puntos, métricas, `previous` y `removed`.
- **Impacto:** Recorrer series largas en Comparar o la galería de un paciente con muchas fotos acumula conjuntos de trazos en el heap del proceso, que ya va justo por los bitmaps y los modelos nativos. Eso aumenta la presión de GC y el riesgo de OOM en sesiones largas.
- **Corrección:** Sustituirla por un `android.util.LruCache` con tamaño acotado (p. ej. 64 entradas o por peso aproximado de puntos).

### B9. Cambiar la fecha de captura no es transaccional: se ignora el fallo de EXIF y el de rename, y quedan nombre, EXIF y MediaStore desalineados

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:641` · Manejo de errores · confirmado · Persistencia

- **Evidencia:** `CaptureDate.write(context, ph.uri, t)` devuelve Boolean y se descarta. Luego `repo.setCaptureDate` → MediaStorePhotoStore.rename (PhotoStore.kt:157-161) ignora el número de filas que devuelve `resolver.update`, y la fecha se deduce del nombre (timestampOf).
- **Impacto:** Con fotos de otra instalación (sin permiso de escritura en Android 11+), o si el update devuelve 0, el EXIF/XMP puede quedar con una fecha y el nombre o DATE_TAKEN con otra. La serie temporal del paciente se ordena según el nombre mientras el metadato exportado dice otra cosa.
- **Corrección:** Comprobar el resultado de CaptureDate.write y de `resolver.update(...) == 1`. Si uno falla, revertir el otro (o no continuar) y mostrar un error. Pedir createWriteRequest en Android 11+ para fotos no propias.

### B10. AtomicFile no hace fsync y cae a una escritura no atómica; LineMemory y ReliefStore escriben directamente sobre el destino

`app/src/main/java/com/photomed/app/align/AtomicFile.kt:13` · Fiabilidad · confirmado · Persistencia

- **Evidencia:** `tmp.writeText(text)` sin `FileOutputStream.fd.sync()`, y si renameTo falla se hace `f.writeText(text)` directamente. LineMemory.kt:62 y Relief.kt:177-178 (PNG y JSON) escriben sobre el archivo final. LineMemory construye el JSON con NaN posibles: JSONArray.toString() devuelve null y la escritura falla en silencio dentro de runCatching.
- **Impacto:** Tras un corte de energía, f2fs/ext4 pueden dejar el archivo renombrado con longitud 0 (datos no volcados), y los trazos o protocolos se leen como null sin aviso. Un relieve PNG a medio escribir hace que las medidas de profundidad se carguen corruptas o desaparezcan.
- **Corrección:** En AtomicFile, escribir con FileOutputStream, llamar a `fd.sync()` antes del rename y, si el rename falla, lanzar una excepción en vez de escribir in situ (o usar androidx.core.util.AtomicFile). Usar AtomicFile en LineMemory y ReliefStore (PNG a tmp y rename) y filtrar los valores no finitos antes de serializar.

### B11. El trazo a mano informa «no se pudo trazar ahí» ante cualquier excepción, incluidas las de memoria, E/S y los modelos

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:224` · Manejo de errores · confirmado · Manejo de errores

- **Evidencia:** `withContext(Dispatchers.Default) { runCatching { ImageOps.loadFull(...1600...); FreehandTrace.trace(...); PhotoAnnotator(...).measureTrace(...) }.getOrNull() }`. Después: `if (out == null) snackbar.showSnackbar(tr("No se pudo trazar ahí; dibuje una línea sobre el surco o rodéelo"))`. No se llama a CrashLog. runCatching también captura OutOfMemoryError y CancellationException.
- **Impacto:** Un fallo técnico (OOM con el mapa de bits de 1600 px, foto ilegible, error del modelo) se presenta como un error de uso. El clínico repite el gesto una y otra vez sin éxito y el fallo no queda registrado.
- **Corrección:** Separar el null legítimo (FreehandTrace sin resultado) de las excepciones. Volver a lanzar CancellationException, registrar el resto con CrashLog.write y mostrar un mensaje distinto («error al analizar la foto»).

### B12. Alinear en Comparar: las fotos que fallan no se mencionan cuando otras se alinean bien

`app/src/main/java/com/photomed/app/ui/compare/CompareScreen.kt:417` · Manejo de errores · confirmado · Manejo de errores

- **Evidencia:** `r.onSuccess { out[s[i].key] = it }.onFailure { failed = it }`. El mensaje solo usa failed si `res.isEmpty()`. Con más de 2 fotos muestra `tr("Alineadas %1$d fotos a la referencia ...", res.size, ...)`. Ningún fallo se registra en CrashLog.
- **Impacto:** Con 4 fotos de las que 2 no se alinean, el usuario ve «Alineadas 2 fotos», sin saber que las otras se muestran sin alinear, y puede comparar series desalineadas como si fueran equivalentes. La causa del fallo se pierde.
- **Corrección:** Contar los fallos e indicarlos en el mensaje («2 de 4 sin alinear: <causa>»). Marcar visualmente las fotos no alineadas y registrar cada excepción con CrashLog.write.

### B13. CrashLog: lectura de los motivos de cierre y escritura de migas en el hilo principal

`app/src/main/java/com/photomed/app/CrashLog.kt:101` · Recursos · confirmado · Manejo de errores

- **Evidencia:** checkExitReasons se llama desde MainActivity.onCreate (línea 53) y hace `i.traceInputStream?.use { it.readBytes() }` y después `String(bytes, ISO_8859_1).split(Regex(...))` con hasta 5 volcados (los de ANR pueden ocupar varios MB). crumb() (línea 33) crea un SimpleDateFormat y reescribe migas.txt completo en cada llamada. NativeGate.run la llama después de runInterruptible, en el dispatcher del llamador, que suele ser el hilo principal (scope.launch de las pantallas). Además, write() cuenta migas.txt y migas_prev.txt dentro del límite MAX al podar.
- **Impacto:** Arranque lento o ANR justo después de un ANR o un cierre nativo, que es cuando más falta hace que la app abra. Hay E/S de disco en el hilo principal en cada entrada y salida de la puerta nativa. Los informes de errores conservados son, en realidad, 18 y no 20.
- **Corrección:** Ejecutar checkExitReasons en AppScope (IO). Limitar lo que se lee del volcado (`readNBytes(256 KB)`) antes de aplicar la expresión regular. Escribir las migas de forma asíncrona (un único hilo) con un formateador reutilizado. Excluir los archivos «migas» de la poda.

### B14. PhotoEdits.cache es un LinkedHashMap sin sincronizar que se modifica desde el hilo principal mientras otros hilos lo leen

`app/src/main/java/com/photomed/app/align/ImageEdit.kt:79` · Fiabilidad · confirmado · Concurrencia

- **Evidencia:** `val m = all(context) ... m.remove(key) / m[key] = edit` en set(), y `fun of(...) = all(context)[key]`, que se llama desde Dispatchers.Default/IO (ImageOps.load34 y loadFull dentro de los análisis, backfill, extrasFor). El mapa sale de `.toMap().toMutableMap()`: es un LinkedHashMap normal; @Volatile solo protege la referencia.
- **Impacto:** Editar (girar o recortar) una foto mientras otra se analiza en segundo plano puede provocar lecturas inconsistentes durante un rehash (edición nula, o sea foto sin girar, y análisis en el encuadre equivocado) o una ConcurrentModificationException si algún día se itera el mapa.
- **Corrección:** Usar ConcurrentHashMap (`ConcurrentHashMap(map)`) o un mapa inmutable que se sustituye entero (copy-on-write) en set().

### B15. La pausa del pipeline descarta ejecutables de CameraX sin cerrar el ImageProxy

`app/src/main/java/com/photomed/app/align/AnalysisPipeline.kt:158` · Recursos · probable · Concurrencia

- **Evidencia:** `if (stopped) return` y `executor.execute { if (!stopped) block() }`, con la RejectedExecutionException capturada y en silencio. El analizador se registra con `a.setAnalyzer({ r -> pipeline.post(r::run) })` (CameraScreen.kt:559). CameraX solo cierra la imagen si su executor lanza RejectedExecutionException; aquí el ejecutable desaparece sin más.
- **Impacto:** Al desmontar la pantalla, los cuadros ya entregados y encolados no llegan a ejecutar proxy.close(). Las imágenes del ImageReader de CameraX quedan retenidas hasta que se desvincula la cámara. Hoy el efecto está acotado porque clearAnalyzer y el unbind llegan después, pero si el pipeline se detuviera con la cámara aún enlazada, el análisis se quedaría congelado.
- **Corrección:** En post, cuando el pipeline está detenido, lanzar RejectedExecutionException en vez de ignorar el bloque, o ejecutar `r.run()` de todos modos en ese caso (el analizador comprueba el estado y cierra el proxy en su finally). No envolver los ejecutables de CameraX en un `if (!stopped)` que se salte el close.

### B16. footLike recalcula los pies de la máscara girada 180° sin memo y copia máscara y RGB en cada llamada

`app/src/main/java/com/photomed/app/align/ZoneCriteria.kt:438` · Rendimiento · confirmado · Método de los criterios

- **Evidencia:** `(toes(c, 1) || toes(rotated180(c), 2))`: rotated180 hace reversedArray de data y rgb, y toes() llama a computeFeet(k) sin memo cuando k !== c. footMemo es de un solo hueco y se invalida con cada Classes nuevo (que llega en cada cuadro por los dos hallazgos anteriores).
- **Impacto:** En espalda, glúteos y brazo sin puntos del cuerpo, cuando no hay pies hacia abajo (el caso normal), cada cuadro hace un etiquetado completo extra con ToeDetector y unos 1,5 MB de copias.
- **Corrección:** Guardar el resultado en Classes (lazy) también para la versión girada, o detectar los dedos hacia arriba recorriendo el borde superior de las manchas ya etiquetadas sin rotar la máscara.

### B17. La indicación de giro frontal pide «a la derecha» por defecto cuando la geometría no da el lado

`app/src/main/java/com/photomed/app/align/Standardization.kt:124` · Método · confirmado · Método de los criterios

- **Evidencia:** `(if (sideSign > 0) "Cabeza: girar a la izquierda %.0f°" else "Cabeza: girar a la derecha %.0f°")`: con sideSign == 0 (|side| ≤ 0,08, la nariz centrada en el óvalo por ruido o con un óvalo incompleto) y yawAbs > 8 siempre se pide girar a la derecha, sin consultar el signo de det.yaw.
- **Impacto:** En el límite de tolerancia la indicación puede ser la contraria a la real, y el paciente oscila sin llegar a cumplir.
- **Corrección:** Si sideSign == 0, usar el signo de det.yaw (con su convención) para el sentido, o mostrar «Cabeza: de frente» sin sentido.

### B18. rememberPhotoInk hace un stat del fichero de anotaciones en el cuerpo del composable en cada recomposición

`app/src/main/java/com/photomed/app/ui/compare/PhotoLayers.kt:267` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** val tag = com.photomed.app.align.Annotations.tag(context, uri.toString()) en el cuerpo del composable. Annotations.tag hace file(...).exists(), lastModified() y length(). Se llama por cada foto de la serie (CompareScreen.kt:523).
- **Impacto:** Hay E/S de disco en Main durante la composición, multiplicada por el número de fotos y por cada recomposición de Comparar (deslizador de opacidad, selección…). Añade latencia y lo marca StrictMode. Además, el valor solo cambia si alguien escribe, así que es una invalidación por sondeo frágil.
- **Corrección:** Sustituir el tag por un contador de versión de anotaciones (como inkVersion en el visor) o por un Flow que emita al guardar, y leer el fichero solo dentro de produceState en IO.

### B19. La guía libre crea un IntArray y un Bitmap nuevos en Main con cada contorno en vivo, aunque no los use

`app/src/main/java/com/photomed/app/ui/camera/GuideOverlay.kt:629` · Recursos · probable · Interfaz Compose

- **Evidencia:** val edgeBmp = remember(free) { val f = free?.takeIf { it.edges != null } ?: ...; IntArray(f.edgeW * f.edgeH) {...}; Bitmap.createBitmap(...) }. Se construye aunque luego se dibuje el outline (free.outline.size >= 3) y no el mapa de bordes. En CameraScreen.kt:1094, free = ... else live?.freeContour cambia con cada cuadro analizado.
- **Impacto:** En el modo libre sin referencia superpuesta, cada cuadro asigna un array y un bitmap ARGB en el hilo principal, sin reutilizarlos. Genera presión de GC y tirones en la vista previa.
- **Corrección:** Construir el bitmap solo si outline.size < 3 y reutilizar un único Bitmap mutable de tamaño fijo (setPixels), o generar la ImageBitmap en el analizador fuera de Main.

### B20. El estado del visor (foto seguida tras cambiar la fecha, modos de edición y anotación, línea multipunto) usa remember y se pierde al recrear la Activity

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:182` · Fiabilidad · probable · Interfaz Compose

- **Evidencia:** var trackName by remember { mutableStateOf(initialName) }; inking, editing, fullscreen, polyDraft, metricsSheet también con remember. El manifiesto declara configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden" pero no uiMode ni locale. Tras setCaptureDate el fichero cambia de nombre (l. 646), mientras que initialName sigue siendo el antiguo.
- **Impacto:** La rotación está cubierta, pero un cambio de tema claro/oscuro, de idioma o la muerte del proceso recrean la pantalla. Si antes se cambió la fecha, initialName ya no existe: en modo revisión se ejecuta onBack() (l. 186) y en el modo normal se abre la primera foto en vez de la que se veía. También se pierde la línea multipunto en curso.
- **Corrección:** Usar rememberSaveable para trackName, los modos y polyDraft (con un Saver de Pt), y/o actualizar el argumento de navegación al renombrar.

### B21. La tabla de medidas se reconstruye por completo (agrupar, numerar, ordenar, filas) en cada recomposición del visor o de Comparar

`app/src/main/java/com/photomed/app/ui/compare/MetricsUi.kt:332` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** MetricsBody construye byName, MeasureNumbering.of(columns.map{...}), ordered y specs sin remember. En MetricsCard (l. 206), metricRows(m) se recalcula dentro del bucle de guides: g.key.isNotEmpty() && metricRows(m).any { ... }. La tabla no es lazy (Column con verticalScroll).
- **Impacto:** Mover el deslizador de opacidad, seleccionar líneas o cualquier cambio de estado en PhotoViewerScreen recompone el panel y rehace la tabla. Con varias fotos y 20-30 líneas son cientos de Text por recomposición. El coste es moderado, pero se suma al de los Canvas.
- **Corrección:** Envolver la preparación de datos en remember(columns, layers, filtering, selName, selectedKey). Calcular metricRows(m) una vez fuera del bucle. Valorar LazyColumn si crece el número de filas.

### B22. El trazo en curso (anotar y trazar surco) reconstruye el Path completo con drop(1) en cada evento de puntero

`app/src/main/java/com/photomed/app/ui/gallery/PhotoViewerScreen.kt:972` · Rendimiento · confirmado · Interfaz Compose

- **Evidencia:** path es un mutableStateListOf que crece con path += ch.position en cada evento de arrastre. En el dibujo: Path().apply { moveTo(path[0]...); path.drop(1).forEach { lineTo(...) } }. Lo mismo en la l. 999 (trazar surco).
- **Impacto:** Cada evento de movimiento copia la lista y reconstruye el Path completo, así que un trazo largo cuesta O(n²) en asignaciones. Con trazos lentos y largos (rodear un área) aparece latencia en el lápiz.
- **Corrección:** Mantener un Path recordado al que se añada solo el último segmento (lineTo) y un contador de versión como estado para invalidar el dibujo. Iterar por índices sin drop(1).

### B23. APK pesado: unos 27 MB de modelos en assets sin comprimir más modelos de ML Kit integrados que duplican funciones

`app/build.gradle.kts:104` · Recursos · probable · Recursos y escalabilidad

- **Evidencia:** assets: selfie_multiclass.tflite 16 MB, hand_landmarker.task 7,5 MB y face_landmarker.task 3,6 MB, con noCompress para 'task' y 'tflite' (l.68-71). Dependencias integradas: com.google.mlkit:face-detection, pose-detection y segmentation-selfie, aunque la cara principal ya va con MediaPipe (ML Kit Face solo es respaldo). La regla -keep class com.google.protobuf.** { *; } de proguard-rules.pro conserva todo protobuf.
- **Impacto:** Descarga e instalación de más de 50 MB por ABI, aunque la mayoría de usuarios no usa nunca las zonas de manos (hand_landmarker) ni el respaldo de ML Kit. La regla keep amplia reduce lo que R8 puede eliminar.
- **Corrección:** Mover hand_landmarker y selfie_multiclass a Play Asset Delivery (on-demand o fast-follow), o descargarlos la primera vez que se usen. Valorar las variantes no integradas de ML Kit (play-services-mlkit-*) para el respaldo. Limitar la regla de protobuf a las clases GeneratedMessageLite que usa MediaPipe.
