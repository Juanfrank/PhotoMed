# PhotoMed: informe consolidado del panel de expertos

*Para: responsable del producto · Fecha: 28 nov 2026*
*Fuentes: revisiones de 10 expertos (interfaz, usabilidad, estética, dermatología, medicina estética, tecnología médica y regulación, biometría, algoritmos faciales, fotografía clínica y defensa del paciente) sobre el código, la documentación, el CHANGELOG y unas 70 capturas de la app; verificación cruzada con el código y el CHANGELOG.*

---

## 1. Resumen ejecutivo

**Qué está bien**
- **La captura estandarizada está muy por encima de la media.** La cámara guía la pose (giro, cabeceo, plano de Frankfort), estima la distancia y avisa del «efecto selfie». Tiene zoom asistido, disparo automático con ráfaga, protocolos con «Saltar/Terminar», revisión Guardar/Repetir y guarda las condiciones de toma en el XMP.
- **Las medidas automáticas son honestas.** Los trazos quedan «sin confirmar» hasta que el médico los valida, se indica que son «orientativas» y la validación está publicada (BENCHMARK.md, cifras en el CHANGELOG). Es una buena base de control de riesgo.
- **La privacidad está en el diseño.** Todo se procesa en el dispositivo, sin servidores ni cuentas. Existe «Compartir sin datos del paciente», activado por defecto al exportar.
- **La interfaz sigue Material 3 con coherencia.** Tokens propios, tema claro/oscuro, visor inmersivo, NavigationRail en horizontal y una cámara accesible con TalkBack.

**Los 7 cambios de mayor impacto**
1. **Consentimiento real.** Hoy es un interruptor sí/no. Debe registrar finalidades, fecha, firma y revocación, y la cámara y la exportación deben comprobarlo antes de actuar. Lo piden 7 de los 10 expertos.
2. **Aviso de «¿es el mismo paciente?».** Las capturas de prueba muestran a dos personas distintas comparadas como Antes/Después sin ninguna advertencia. Hace falta un aviso al guardar o comparar, y una acción para mover la foto a otro expediente. Lo piden 6 expertos.
3. **Anonimizar la imagen, no solo los metadatos.** Recorte a la zona, banda ocular o difuminado, más una limpieza real del archivo: hoy `strip` conserva la miniatura EXIF, Make/Model y DateTimeOriginal. Además, el álbum público no debe llevar el nombre del paciente.
4. **Protección local de la app.** Bloqueo con biometría o PIN, FLAG_SECURE y miniaturas discretas en zonas íntimas. Es de bajo esfuerzo y hoy no existe nada de esto.
5. **Medidas con incertidumbre y comparabilidad.** Mostrar «± error», marcar los Δ «dentro del error», ampliar el aviso de Comparar a luz, flash, lente y expresión, y bloquear AE/AWB al disparar.
6. **Arreglos rápidos de interfaz.** No permitir «Confirmar» con el análisis vacío y explicar la causa. Que la acción principal del expediente sea capturar. Dejar de truncar chips y segmentos. Sustituir la pantalla negra de carga por la miniatura.
7. **Una salida clínica y para el paciente.** Un informe PDF por sesión o comparación (leyenda, tabla, aviso, trazabilidad) y un resumen en lenguaje llano para el paciente. Hacen falta además decisiones de producto: uso previsto y clasificación regulatoria de los hallazgos de piel y de los grados (ver §5).

---

## 2. Recomendaciones de cada experto

### 2.1 Diseño de interfaz (UI, Material 3, Android)
**Valoración:** la base M3 es sólida. Los problemas se concentran en el ancho compacto (chips y segmentos truncados, una tabla de 5 columnas comprimida), en estados mal resueltos (carga en negro, «Confirmar» activo sin análisis) y en la jerarquía de acciones.

**Fortalezas**
- Tokens M3 propios (Md3Tokens.kt) y tema claro/oscuro; visor sobre fondo negro, adecuado para valorar la piel.
- Cámara accesible: contentDescription, liveRegion y role Switch (CameraAssist.kt).
- Modo simple/experto y franja de progreso «puntos 42 % / 80 %».
- Disposición horizontal adaptada (NavigationRail; visor y Comparar en dos paneles).
- Galería de zona con «basal / día 30» y estado de los trazos siempre a la vista.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Expediente: ExtendedFAB «Iniciar sesión» como acción principal y Comparar en segundo plano; corregir «1 zonas más» y la fecha de alta | doctor, centro | alta | bajo | 03_expediente.png; ExpedienteScreen.kt |
| Chips de capas: degradado en el borde o chip «Capas (3/5)» que abra una hoja agrupada | doctor | alta | medio | 05, 06, 09, 21, 22; CompareScreen.kt:865, 879 |
| No mostrar «Confirmar» si no hay trazos; estado de error con causa y acciones «Reintentar» / «Trazar a mano» | doctor, centro | alta | bajo | 06, 11; MetricsUi.kt:322-329 |
| Carga del visor: miniatura desenfocada, barra lineal con «Cargando foto…» y estado de error | doctor, paciente | media | bajo | 05; PhotoViewerScreen.kt:841 |
| SegmentedButton de Comparar con icono y etiqueta corta; probar con fontScale 1,3 | doctor | media | bajo | 09, 11; CompareScreen.kt:559-564 |
| Expedientes en ancho compacto: ListItem de dos líneas en lugar de tabla de 5 columnas | centro, doctor | media | medio | 01; ExpedientesScreen.kt:239, 366 |
| Consentimiento como badge en errorContainer y aviso al pulsar «Tomar foto» | centro, paciente | alta | bajo | 01, 20 |
| Inicio en horizontal: FAB en la cabecera del rail y miniaturas visibles | centro, doctor | media | bajo | 20; ExpedientesScreen.kt:204, 342 |
| Tabla de medidas: columna D/I para el lado, unidades en subtítulo, borrar con «Deshacer», Δ con flecha, decimales según el idioma de la app | doctor, paciente | alta | medio | cam_paparazzi_23; MetricsUi.kt:65-67, 554-559 |
| Objetivos táctiles de al menos 48 dp en la cámara y en las medidas | doctor | media | bajo | CameraScreen.kt:1347, 1350, 1428; MetricsUi.kt:275 |
| Indicación en texto imperativo para el paciente (reutilizando HintText.display) y CoachMark con la leyenda de iconos | paciente, doctor | media | bajo | HintCues.kt:70, 209 |
| Anotación: barra inferior en vertical, sliders rotulados, «Borrar todo» separado de «Deshacer» | doctor, paciente | media | medio | 07, 08; InkTools.kt |
| Galería: cuadrícula adaptativa con badges (coincidencia, confirmada, anotada) | doctor, centro | media | medio | 04; PhotoMetadata.kt:27 |
| Ajustes: encabezados uniformes, preajustes Rápido/Estándar/Estricto y perfil del centro | centro, doctor | baja | medio | 14_ajustes.png |

### 2.2 Usabilidad y UX clínica
**Valoración:** el flujo principal está bien encadenado. Sobran toques por sesión, hay errores evitables que hoy no se detectan (paciente equivocado, fechas) y funciones avanzadas que no se descubren. Las indicaciones de la cámara se dirigen al operador, no al paciente.

**Fortalezas**
- Protocolo → secuencia con «Saltar/Terminar» y «Secuencia i/N».
- La ráfaga y la revisión Guardar/Repetir permiten recuperar errores al capturar.
- Lista de expedientes ordenable, con filtros de pendientes y consentimiento.
- Indicaciones de una en una, con magnitud, vibración y tono.
- Editor de protocolos comprensible y bienvenida en lenguaje llano.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Opción por protocolo para guardar sin revisar si la coincidencia supera el umbral, con «Repetir» durante 3 s y resumen al final | doctor, paciente, centro | alta | medio | CameraScreen.kt:274, 908; CHANGELOG 2.22 |
| Aviso de cara distinta a las fotos anteriores y acción «Mover a otro expediente» | centro, doctor, paciente | alta | medio | 04, 09; no hay reasignación en ui/ |
| Indicaciones habladas para el paciente (TTS) y disparo con los botones de volumen o un disparador Bluetooth | paciente, doctor | alta | medio | No hay TextToSpeech ni onKeyDown (verificado) |
| Sujeto explícito en las indicaciones («Incline el teléfono» / «Paciente: gire la cabeza a SU izquierda») | doctor, paciente | media | bajo | cam_indicaciones; CHANGELOG ~360 |
| Selección múltiple de zonas con casillas visibles; indicar que la fila de plantillas se desplaza | doctor, centro | media | bajo | cam_expediente_seleccion |
| Chips y segmentos truncados; placeholder del buscador más corto | doctor | media | bajo | 05, 06, 09, 12, 01 |
| «Confirmar» desactivado si no hay análisis; enlace a «Trazar surco» | doctor, centro | media | bajo | 06, 12 |
| Fechas coherentes: alta del expediente frente a la primera foto; no mostrar nunca «1 ene 1970» | doctor, centro | media | bajo | 03, cam_expediente_seleccion |
| Pantalla de cierre de sesión (hechas, saltadas, bajo umbral) con acciones Repetir / Comparar / Exportar y próximo control | centro, doctor, paciente | alta | medio | CameraScreen.kt:1018-1022 |
| Modo «Mostrar al paciente» bloqueado en Comparar | paciente, centro | media | medio | 09; PRIVACY.md:27 |
| Alta más completa (edad, fototipo, fecha basal) y consentimiento con evidencia | centro, paciente, doctor | media | medio | cam_paparazzi_1 |
| Barra de anotación usable con guantes (56 dp, papelera separada, grosor S/M/L) | doctor | baja | bajo | 07 |
| Prueba de usabilidad con tareas cronometradas y telemetría local de fricción | centro, doctor | baja | medio | docs/BENCHMARK.md |

### 2.3 Estética visual y marca en salud
**Valoración:** la paleta de la app es correcta y accesible. Lo que resta confianza va encima de la foto: 25 colores de capa en pastel neón con repeticiones exactas, rojos que suenan a alarma, un semáforo rojo-verde y exportaciones sin maquetar.

**Fortalezas**
- Paletas tonales derivadas del logo, con contraste AA.
- Visor y cámara inmersivos.
- Comparar con estética profesional.
- Logo configurable en todas las exportaciones.
- Bienvenida sobria.
- Surcos numerados de forma coherente entre el visor y la tabla.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Paleta semántica de 6–7 familias con halo oscuro; quitar colores duplicados (EYES/FINGERS, PERIORAL/labio…); validar sobre fototipos I–VI | doctor, paciente | alta | medio | GuideOverlay.kt:685-730; diag_eye_diagram |
| Sacar el rojo de las papeleras y de las regiones neutras | paciente, doctor | alta | bajo | MetricsUi.kt:577; cam_paparazzi_23 |
| Semáforo de la guía apto para daltónicos (azul → ámbar → verde azulado, trazo discontinuo o continuo, check) | doctor, centro | media | bajo | GuideOverlay.kt:61-85 |
| Paleta de anotación unificada con la de las capas | doctor, centro | media | bajo | 07, 08 |
| Lámina de exportación «Informe del centro» (PDF/JPG) con leyenda, tabla y aviso | centro, paciente, doctor | alta | alto | CompareExport.kt:161-172; no hay PdfDocument (verificado) |
| Logo con placa de contraste, opacidad y tamaño ajustables | centro | media | bajo | Watermark.kt:85-105 |
| Consentimiento por finalidad y anonimización visual para divulgación | centro, paciente | alta | medio | Models.kt:12 |
| Δ con flecha y tono neutro; ocultar los «+0,00» | paciente, doctor | media | bajo | cam_paparazzi_23 |
| Estados de carga y vacío dignos de una app clínica | doctor, paciente | media | bajo | 05, 06, 11 |
| Documentar roles de color: primary para acción, secondary para selección | centro, doctor | baja | bajo | 01, 05, 09 |
| Pulido tipográfico (Ajustes, «Lado a», «1 zonas», «2 9 nov») | centro, doctor | baja | bajo | 14, 09, 03, 01 |
| Modo presentación al paciente con marca | paciente, doctor, centro | media | medio | 09, 11 |

### 2.4 Dermatología clínica
**Valoración:** la base fotográfica es muy buena. La debilidad está en el seguimiento lesión a lesión: las manchas se renumeran en cada foto, de modo que los Δ pueden comparar lesiones distintas. Faltan además un registro de lesiones, el mapeo corporal total (TBP), una escala en mm en el cuerpo y el filtrado de ropa y tatuajes.

**Fortalezas**
- Plausibilidad anatómica de los hallazgos.
- Validación con máscaras de dermatólogo.
- Avisos de no-diagnóstico.
- Indicaciones de toma clínicamente correctas.
- Exclusión de ombligo, areolas y axilas como falsos hallazgos.
- Escalas tricológicas reconocidas.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| ID persistente de cada lesión entre visitas, emparejado por posición; ocultar el Δ de las manchas mientras no exista | doctor, centro | alta | alto | SkinLesions.kt:161-176; MetricsUi.kt:332-337 |
| Registro de lesiones con mapa corporal y vínculo regional ↔ primer plano ↔ dermatoscopia | doctor, centro, paciente | alta | alto | Zone.kt:35-101 |
| Protocolos de mapeo corporal total y venoso (vistas posterior y medial de las piernas) | doctor, centro | alta | medio | Zone.kt:88-89 |
| Capa «Cambios desde la basal» sin el límite de las 25 mayores | doctor, paciente | alta | alto | SkinLesions.kt:41-42, 161 |
| Excluir ropa, estampados, tatuajes y fotos en blanco y negro; máscara de exclusión | doctor, paciente | alta | medio | diag_skin_dbg; SkinLesions.kt:68-74 |
| Revisión lesión a lesión; capas de hallazgos desmarcadas al exportar, con marca de no-diagnóstico | paciente, doctor, centro | alta | medio | MetricsUi.kt:325-330 |
| Escala en mm en el cuerpo (regla o ArUco) y tarjeta de color | doctor, centro | alta | medio | SkinLesions.kt:166-172, 285-286 |
| Terminología clínica para el profesional y llana para el paciente; ayuda por capa | doctor, paciente | media | bajo | FaceAnnotations.kt:23 |
| Grados alineados con escalas publicadas o rotulados como índice propio; mMASI | doctor | media | medio | SkinLesions.kt:292-321 |
| Ficha dermatológica: fototipo, antecedentes, diagnóstico CIE y tratamiento | doctor, centro | media | medio | Models.kt:8-16 |
| Informe PDF y CSV con trazabilidad | centro, doctor | media | medio | CompareExport.kt:115-121 |
| Consentimiento granular y protocolo para zonas sensibles (acompañante) | paciente, centro | media | medio | Models.kt:12; Zone.kt:82-87 |
| Aviso de integridad del expediente (posible otro paciente) | centro, doctor | media | medio | 04, 11 |

### 2.5 Medicina estética
**Valoración:** muy buena para documentar el antes y después. Falta la capa clínico-comercial: el tratamiento como evento, el mapa de inyección, los protocolos por procedimiento, el consentimiento desglosado, los avisos de comparabilidad y un informe para el paciente.

**Fortalezas**
- Expresiones como condición de disparo.
- Tabla de medidas con Δ.
- Honestidad de las medidas.
- Guía que evita el antes y después tramposo.
- Exportación completa (GIF/MP4/progresivo).
- «Basal / día N».

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Tratamiento como evento (producto, dosis, lote) y días contados desde el procedimiento | doctor, centro, paciente | alta | medio | Models.kt:6-17; ZoneGalleryScreen.kt:216 |
| Mapa de puntos de inyección con dosis y totales, anclado a la malla | doctor, centro | alta | medio | 08; CHANGELOG 671-713 |
| Protocolos predefinidos por procedimiento, con calendario de controles | doctor, centro | alta | bajo | BuiltInProtocols.kt:19-27 |
| Consentimiento por uso, con fecha y revocación, comprobado al exportar | centro, paciente, doctor | alta | medio | ExpedienteForm.kt:109-121; ExportSheet.kt:219-232 |
| Anonimización visual al exportar | centro, paciente | alta | bajo | ExportSheet.kt:219-220; Share.kt |
| Avisos de comparabilidad (otra persona, expresión, maquillaje, luz) | doctor, centro, paciente | alta | medio | 09, 10; CompareScreen.kt:285-287 |
| Informe de evolución de una página para el paciente | paciente, doctor, centro | media | medio | ExportSheet.kt:143-232 |
| Índices de simetría izquierda/derecha y resumen por tercio | doctor, paciente | media | medio | FeatureMetrics.kt |
| Registro de escalas validadas (GAIS, WSRS, Glogau) | doctor, centro | media | bajo | FeatureMetrics.kt |
| Mensaje útil cuando el análisis falla | doctor | media | bajo | 06, 11, 12 |
| Celulitis con contracción, perímetros, peso e IMC, y exigencia de luz | doctor, paciente | media | medio | SkinLesions.kt:24-25; PROTOCOLOS.md:34 |
| Satisfacción del paciente (GAIS del paciente, 0–10) | paciente, centro | baja | bajo | Models.kt |
| Recordatorio de control y checklist de preparación | centro, paciente, doctor | baja | bajo | ExpedientesScreen.kt:246 |

### 2.6 Tecnología médica y regulación (SaMD, MDR/FDA, ISO, protección de datos)
**Valoración:** la base técnica es buena: integridad de datos, confirmación por el médico y verificación documentada. Pero el producto dice «no es un dispositivo médico» mientras ofrece funciones de carácter diagnóstico. Le faltan los controles de un sistema que guarda datos de salud: auditoría, cifrado, bloqueo, roles, copia de seguridad e interoperabilidad.

**Fortalezas**
- Procesamiento 100 % local.
- Escrituras atómicas y bloqueo por foto.
- Procedencia de las importaciones.
- Confirmación por el profesional.
- Verificación transparente.
- Controles de privacidad visibles y aceptación explícita de los términos.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Uso previsto formal y elección de vía: «no dispositivo» (quitar grados y hallazgos) o SaMD IIa; separar módulos por compilación | centro, doctor | alta | alto | TERMS §3 frente a CHANGELOG 2.62/2.64; MetricsUi.kt:87 |
| Incertidumbre por medida y Δ «dentro del error»; revisar la profundidad fija de 0,40 mm | doctor, paciente | alta | medio | cam_paparazzi_23; BENCHMARK.md:254-275 |
| Franja fija de aviso y versión; pie de trazabilidad en las exportaciones; pm:AlgoVersion y pm:MeasureStatus en el XMP | doctor, paciente, centro | alta | bajo | MetricsUi.kt:113; PhotoMetadata.kt:149-155 |
| Consentimiento por finalidades, comprobado en la cámara y al exportar | paciente, centro | alta | medio | No hay «consent» en ui/camera (verificado) |
| Segundo identificador del paciente y aviso de paciente equivocado | doctor, centro, paciente | alta | medio | 03, 09, 11 |
| Modo clínico: almacenamiento privado cifrado con Keystore y carpeta seudónima | paciente, centro | alta | alto | PhotoRepository.kt:50; sin EncryptedFile/Keystore (verificado) |
| Bloqueo con biometría o PIN y FLAG_SECURE | centro, paciente | alta | bajo | Sin BiometricPrompt ni FLAG_SECURE (verificado) |
| Registro de auditoría encadenado por hash, SHA-256 del original y autor/fecha/versión de las confirmaciones | centro, doctor | alta | medio | CHANGELOG:719-721; ConfirmedTraces.kt:175-190 |
| Perfiles y roles (médico, fotógrafo, administrador) con política del centro | centro, doctor | media | alto | PRIVACY §1; 14_ajustes |
| DICOM VL Photographic con SNOMED CT, FHIR R4 y PDF por sesión | centro, doctor | media | alto | Sin DICOM/FHIR (verificado) |
| Copia de seguridad cifrada completa y restauración verificada | centro, doctor | media | medio | AndroidManifest.xml:17 (allowBackup=false) |
| Derechos del paciente: acceso y portabilidad, justificante de borrado y política de conservación | paciente, centro | media | medio | PRIVACY §8 |
| Anonimización real y confirmación al enviar con datos | paciente, centro | media | medio | Share.kt:18-20, 36 |
| Archivo de usabilidad IEC 62366 (parámetros fijados por protocolo, equivalencia entre tomas, prueba sumativa) | doctor, centro | media | medio | 14_ajustes; PhotoMetadata.kt:28 |

### 2.7 Biometría y metrología de imagen
**Valoración:** la estandarización geométrica y la documentación son excelentes. Las cifras, en cambio, se muestran sin incertidumbre ni MDC. La escala usa un iris poblacional aunque hay un factor del sensor por paciente que no se reutiliza. No hay estudio test-retest ni registro de la luz y el color.

**Fortalezas**
- Escala con respaldo y plausibilidad.
- Benchmark público y reproducible.
- Pose y distancia estandarizadas.
- Condiciones de toma en el XMP y aviso en Comparar.
- Corrección de color Bradford/ΔE2000 y control de luz en la toma.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Incertidumbre combinada «18 ± 1,5 mm» y decimales honestos; profundidad «≈ (no calibrada)» | doctor, paciente | alta | medio | LineMetrics.kt:86-94 (SHADING_GAIN pendiente) |
| Δ frente al MDC95 (generalizar el patrón de hairlineSummary) | doctor, paciente | alta | medio | CompareScreen.kt:1339-1348 |
| Estudio test-retest (ICC, SEM, MDC, Bland-Altman) publicado en BENCHMARK | centro, doctor | alta | alto | BENCHMARK §3 |
| HVID calibrado por paciente (mediana de irisFactor) y misma fuente de escala en el antes y el después | doctor, paciente | alta | medio | LineMetrics.kt:66; ReliefCapture.kt:157-170 |
| Guardar en el XMP luz, exposición y balance de blancos (ISO, CCT, flash) y avisar en Comparar | doctor, centro | alta | medio | PhotoViewerScreen.kt:670-672; Standardization.kt |
| Bloquear AE/AWB en la toma de seguimiento (modo sesión clínica) | doctor, centro | media | medio | Solo existe en ReliefCapture.kt (verificado) |
| Detección automática de la carta de color y perfil por sesión, con ΔE residual | doctor, centro | media | alto | ColorMatchDialog.kt:242-244 |
| Marcar como no comparables las tomas «casi alineadas» y corregir el escorzo | doctor | media | medio | 14_ajustes; 21 |
| Comprobación biométrica de identidad entre el antes y el después | centro, doctor | media | medio | 11, 12, cam_compare_progresivo |
| Indicador por foto de si es «medible», con el motivo cuando no hay análisis | doctor, centro | media | medio | 06, 21; LineMetrics.kt:79-83 |
| Informe de medidas en CSV/PDF con metadatos metrológicos | centro, doctor | media | bajo | Sin CSV (verificado) |
| Vista para el paciente con banda de error | paciente, doctor | baja | medio | cam_paparazzi_23 |
| Registrar dispositivo y lente; aviso de fondo no uniforme | centro, doctor | baja | bajo | pm:FocalNorm sin modelo ni lente |

### 2.8 Algoritmos de análisis facial
**Valoración:** técnica sólida: malla redundante, pose calibrada con LFW, ojo compuesto con su espejo. Las carencias están en identidad y desidentificación, en medir en 2D sin corregir la guiñada y en la falta de evaluación por fototipo, edad y sexo.

**Fortalezas**
- meshRobust (MediaPipe contrastado con ML Kit).
- HeadPose calibrada.
- EyeComposite validado.
- Avisos de condiciones distintas.
- Atención a la diversidad de piel en cejas y luz.
- Anonimización activada por defecto.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Verificación de identidad en el dispositivo con descriptor efímero (no persistente); solo como aviso, con umbral calibrado por grupo | centro, doctor, paciente | alta | alto | 11, 12, cam_compare_lado_a_lado |
| Desidentificar la imagen: recorte, banda ocular, difuminado, fondo segmentado | paciente, centro | alta | medio | ExportSheet.kt:216-224; SubjectSegmenter sin usar al exportar |
| Volver a codificar el JPEG (sin APP1/miniatura) y usar fecha relativa en el nombre | centro, paciente | alta | bajo | PhotoMetadata.kt:63-71 (verificado); Share.kt:18-20 |
| Álbum sin nombre del paciente, .nomedia o almacenamiento privado (evita la agrupación por caras de Google Fotos) | paciente, centro | alta | medio | PhotoRepository.kt:50; sin .nomedia (verificado) |
| Conservar la z de la malla o frontalizar; marcar «no comparable» si la guiñada supera 8° | doctor | alta | medio | FaceEngines.kt:107-112 |
| Pose calculada a posteriori (MeshYaw) y aviso de vista mal clasificada | doctor, centro | media | bajo | PhotoImport.kt:43-67, 131 |
| Ojo en primer plano: enderezar el alabeo y detectar la lateralidad | doctor | media | medio | EyeComposite.kt:49-63 |
| Gafas: condición de captura en periocular y exclusión en los hallazgos | doctor, paciente | media | bajo | diag_eye_diagram |
| Benchmark separado por fototipo, edad y sexo, con avisos en la app | doctor, paciente, centro | alta | medio | BENCHMARK §1; HeadPose.kt:15-17 |
| HVID calibrado por paciente | doctor, paciente | media | medio | LineMetrics.kt:66 |
| Consentimiento por usos ligado a la desidentificación forzada | paciente, centro | media | medio | Models.kt:12; ExportSheet.kt:113 |
| setNumFaces(3) y aviso de otra persona en el encuadre | doctor, paciente | media | bajo | FaceEngines.kt:93 (verificado) |

### 2.9 Fotografía clínica
**Valoración:** muy buena en geometría. Floja en fotometría y repetibilidad: AE/AWB no se bloquean, el flash no se registra, la luz solo se mide en el rostro, no se controla el fondo, el zoom digital llega a 8x y el aviso de Comparar es solo geométrico.

**Fortalezas**
- Control de la distorsión por distancia (Ward 2018).
- Guías por zona, lado y vista.
- Indicaciones cuantificadas con vibración y tono.
- Captura robusta (ráfaga, laplaciano, oclusiones).
- Trazabilidad XMP.
- Punto neutro Bradford.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| AE/AWB lock durante la ráfaga y balance de blancos fijo opcional por protocolo | doctor, centro | alta | medio | ReliefCapture.kt:100-101; CameraScreen.kt:555-566 |
| Registrar flash, lente, ISO, exposición y CCT | doctor, centro | alta | bajo | CameraScreen.kt:655-688; ScreenFlash.kt:129-132 |
| Ampliar el aviso de Comparar (luz, zoom, lente, flash, expresión) en chips | doctor, paciente | alta | bajo | CompareScreen.kt:1350-1365 |
| Medir y registrar la luz en cuerpo y cuero cabelludo; umbrales por toma | doctor, centro | media | medio | Protocol.kt:41; PROTOCOLOS.md:34 |
| Comprobación de fondo uniforme | doctor, centro | media | medio | cam_indicaciones; Zone.kt:95 |
| Preferir la lente tele óptica y limitar el zoom digital por resolución efectiva | doctor | media | medio | CameraAssist.kt:409; CameraScreen.kt:1621 |
| Distancia en tomas corporales con el esqueleto | doctor, centro | media | medio | CameraScreen.kt:684-686 |
| Nitidez mínima en la zona y enfoque dirigido | doctor, centro | media | bajo | CameraScreen.kt:732-738 |
| Checklist de preparación por zona y hoja para el paciente | paciente, centro, doctor | media | bajo | PROTOCOLOS.md:46 |
| Indicaciones habladas para el paciente y cuenta atrás | paciente, doctor | media | medio | Sin TTS (verificado) |
| Guía de iluminación y «perfil de puesto» en el protocolo | centro, doctor | baja | bajo | BUENAS_PRACTICAS.md |
| Detección guiada de carta gris o regla en la toma basal | doctor, centro | baja | alto | ColorMatch.kt:112, 304 |
| Siluetas ajustadas de manos y pies suavizadas; revisar el encuadre de «muslos» | doctor, paciente | baja | medio | cam_extremidades_individual |

### 2.10 Defensa del paciente, ética, accesibilidad y comunicación
**Valoración:** la base ética es buena. Los puntos débiles son el consentimiento sin efecto real, la privacidad de las zonas íntimas (álbum con nombre, nube, sin bloqueo), el lenguaje alarmista de los hallazgos, la falta de una salida para el paciente, el daltonismo y la validación por tono de piel.

**Fortalezas**
- Privacidad por diseño.
- Bienvenida en lenguaje llano.
- Estado del consentimiento visible (lista, filtro e icono en el expediente).
- Comunicación prudente de los hallazgos.
- Accesibilidad de base en la cámara.
- Ropa interior mínima admitida.

| Recomendación | Para quién | Prioridad | Esfuerzo | Evidencia |
|---|---|---|---|---|
| Consentimiento con alcance, fecha, firma, representante y revocación; alcance en el XMP | centro, paciente | alta | medio | Models.kt:12; PhotoMetadata.kt:155 |
| Que el consentimiento condicione la cámara y la exportación (diálogo con 3 opciones) | centro, paciente, doctor | alta | bajo | Sin «consent» en ui/camera (verificado) |
| Zonas íntimas fuera del álbum público con nombre; aviso de copia en la nube | paciente, centro | alta | medio | PhotoRepository.kt:50; PhotoStore.kt:53-68 |
| Bloqueo de la app, FLAG_SECURE y miniaturas difuminadas en zonas sensibles | paciente, centro | alta | bajo | Verificado: no existen |
| Protocolo de pudor (checklist, acompañante, aviso antes del disparo, textos respetuosos) | paciente, doctor, centro | media | bajo | ZoneCriteria.kt:822, 914, 1146 |
| Hallazgos de piel desactivados por defecto, etiquetas descriptivas y confirmación obligatoria | paciente, doctor | alta | medio | SkinLesions.kt:46-47, 319-321 |
| Resumen para el paciente y exportación íntegra del expediente (acceso y portabilidad) | paciente, centro | media | medio | PRIVACY §8 |
| Daltonismo: estilo de trazo, patrones por capa y paleta Okabe-Ito | doctor | media | bajo | GuideOverlay.kt:684-710 |
| TalkBack en el visor, las capas y los nombres de color | doctor, centro | baja | bajo | ZoomableImage.kt:38 y ExpedienteScreen.kt:482 (verificado) |
| Validación y umbrales relativos por fototipo (V-VI) | paciente, doctor | media | alto | SkinLesions.kt:40-41; Periorbital.kt:36 |
| Retirada del consentimiento con registro de exportaciones y justificante | centro, paciente | media | medio | PRIVACY §7-8 |
| «Explicar al paciente» en pantalla completa y letra grande antes de la sesión | paciente, centro | media | bajo | cam_paparazzi_19; 03 |

---

## 3. Visión transversal por perspectiva

### Centro médico
- **Consentimiento por finalidades, fechado, revocable y que condicione cámara y exportación.** Proponen: Ética, Regulación, Estética médica, Dermatología, Marca, Facial, UX y UI.
- **Seguridad de datos.** Bloqueo con biometría o PIN y FLAG_SECURE (Regulación, Ética); álbum seudónimo o almacenamiento privado cifrado (Regulación, Facial, Ética); copia de seguridad cifrada (Regulación).
- **Trazabilidad.** Registro de auditoría, autor y versión de las confirmaciones, versión del algoritmo en exportaciones y XMP (Regulación, Metrología).
- **Integridad del expediente.** Aviso de paciente equivocado y «Mover a otro expediente». Proponen: UX, Dermatología, Estética médica, Regulación, Metrología, Facial.
- **Informe PDF/CSV y, a medio plazo, DICOM/FHIR** para archivar en la historia clínica (Dermatología, Regulación, Metrología, Marca).
- **Decisión regulatoria sobre uso previsto y clasificación** (Regulación; refuerzo de Dermatología y Ética).
- **Estandarizar el centro.** Perfiles y roles, preajustes bloqueables, perfil de puesto (Regulación, UI, Fotografía).

### Doctor
- **Fiabilidad de las cifras.** Incertidumbre ±, Δ frente al MDC, escala calibrada por paciente, corrección de la guiñada, profundidad no calibrada (Metrología, Regulación, Facial).
- **Comparabilidad.** Aviso ampliado con luz, flash, lente, expresión e identidad, y AE/AWB bloqueados (Fotografía, Metrología, Estética médica).
- **Flujo de sesión.** Acción principal «Iniciar sesión», guardado automático con resumen final, disparo con volumen o Bluetooth, selección múltiple visible (UI, UX).
- **Estados del análisis.** «Confirmar» solo si hay contenido, causa explícita y acceso a «Trazar a mano» (UI, UX, Marca, Estética médica, Metrología).
- **Seguimiento clínico.** Identidad de las lesiones entre visitas, registro y mapa corporal, protocolos de mapeo total y venoso (Dermatología); tratamiento como evento, mapa de inyección y protocolos por procedimiento (Estética médica).
- **Legibilidad.** Chips y segmentos sin truncar, tabla de medidas más clara, paleta de capas semántica y apta para daltónicos (UI, UX, Marca, Ética).

### Paciente
- **Privacidad de su imagen.** Desidentificación visual, sin nombre en el álbum, zonas íntimas protegidas, miniaturas discretas (Facial, Ética, Regulación, Estética médica, Marca).
- **Indicaciones dirigidas a él.** Voz, frase imperativa, cuenta atrás y sujeto explícito (UX, Fotografía, UI).
- **Comunicación prudente.** Hallazgos con lenguaje descriptivo, desmarcados al exportar y con confirmación obligatoria; colores sin rojo de alarma (Ética, Dermatología, Marca).
- **Una salida comprensible.** Resumen o informe de evolución en lenguaje llano con margen de error, y modo presentación (Estética médica, Metrología, Ética, Marca, UX).
- **Derechos.** Acceso, portabilidad y retirada del consentimiento con justificante (Regulación, Ética).
- **Dignidad en la sesión.** Protocolo de pudor y «Explicar al paciente» (Ética, Dermatología).

---

## 4. Plan priorizado (deduplicado)

### Tanda 1: rápidas (alto impacto, bajo esfuerzo)
| # | Acción | Apoyan | Público |
|---|---|---|---|
| 1 | Bloqueo opcional con biometría o PIN y FLAG_SECURE en visor, Comparar, cámara y galería; miniaturas difuminadas en zonas sensibles | Regulación, Ética | centro, paciente |
| 2 | Comprobar el consentimiento al abrir la cámara o iniciar sesión (diálogo: registrar / verbal con testigo / continuar con motivo) y al exportar con datos; badge visible en la lista | Ética, Regulación, UI, Estética médica | centro, paciente |
| 3 | Anonimizar de verdad: volver a codificar el JPEG sin APP1/miniatura, fecha relativa en el nombre y aviso de que no oculta la cara | Facial, Regulación | paciente, centro |
| 4 | Panel de medidas: «Confirmar» solo con trazos; estado de error con causa (sin rostro, barba, resolución, iris) y acciones Reintentar / Trazar a mano / Puntuar con escala | UI, UX, Marca, Estética médica, Metrología | doctor |
| 5 | Ampliar `captureConditionsWarning` con luz (LightFace), expresión, zoom, lente y flash, en chips | Fotografía, Metrología, Estética médica | doctor, paciente |
| 6 | Registrar en el XMP flash, lente, ISO, exposición, CCT, versión del algoritmo y estado y autor de la confirmación | Fotografía, Metrología, Regulación | doctor, centro |
| 7 | Franja fija «orientativo · escala · versión» y pie de trazabilidad en las exportaciones con capas o medidas | Regulación | todos |
| 8 | Expediente: ExtendedFAB «Iniciar sesión» y Comparar en segundo plano; corregir plurales y fechas («1 ene 1970», fecha de alta) | UI, UX, Marca | doctor, centro |
| 9 | Truncados: chip «Capas (n/m)» o degradado, segmentos con icono y etiqueta corta, placeholder más corto, FAB del rail | UI, UX, Marca | doctor |
| 10 | Carga del visor con la miniatura y barra lineal | UI, Marca | doctor, paciente |
| 11 | Protocolos predefinidos por procedimiento (toxina, labios, nasogeniano, celulitis, PRP) | Estética médica | doctor, centro |
| 12 | Color: sin rojo en papeleras ni regiones neutras; semáforo de guía apto para daltónicos (trazo y check); paleta de anotación unificada | Marca, Ética | doctor, paciente |
| 13 | Indicación principal en texto imperativo con sujeto explícito; CoachMark con la leyenda | UI, UX | paciente, doctor |
| 14 | Objetivos táctiles de al menos 48 dp; TalkBack en el visor y la paleta | UI, Ética | doctor |
| 15 | setNumFaces > 1 con aviso de otra persona; gafas como condición en periocular; pose calculada a posteriori para fotos importadas | Facial | doctor |
| 16 | Checklist de preparación y de pudor por zona, «Explicar al paciente» y aviso audible antes del disparo en zonas íntimas | Fotografía, Ética, Estética médica | paciente, centro |

### Tanda 2: siguientes (alto impacto, esfuerzo medio)
| # | Acción | Apoyan |
|---|---|---|
| 17 | Registro de consentimiento: finalidades, zonas íntimas, fecha, versión del texto, firma o foto, representante, revocación; en el XMP | Ética, Regulación, Estética médica, Dermatología, Marca, Facial, UX |
| 18 | Aviso «¿mismo paciente?» (descriptor efímero o firma de la malla) al guardar y al comparar, y acción «Mover a otro expediente» | UX, Dermatología, Estética médica, Regulación, Metrología, Facial |
| 19 | Desidentificación visual al exportar (recorte a la zona, banda ocular, difuminado, fondo), forzada si falta consentimiento de uso identificable | Facial, Estética médica, Regulación, Marca |
| 20 | Álbum seudónimo (sin nombre) y zonas íntimas fuera de MediaStore; aviso de copia en la nube | Facial, Ética, Regulación |
| 21 | Incertidumbre ± por medida, Δ frente al MDC (gris = «dentro del error»), profundidad marcada como no calibrada | Metrología, Regulación |
| 22 | AE/AWB lock al disparar (ampliar lo de ReliefCapture) y balance de blancos fijo opcional por protocolo | Fotografía, Metrología |
| 23 | Hallazgos de piel: desactivados por defecto y al exportar, etiquetas descriptivas, revisión lesión a lesión, filtrado de ropa, tatuajes y blanco y negro | Dermatología, Ética, Marca |
| 24 | Guardado automático por umbral con «Repetir» y pantalla de cierre de sesión (saltadas, bajo umbral, Comparar/Exportar, próximo control) | UX, Estética médica |
| 25 | Tratamiento como evento (producto, dosis, lote), «día N post-tratamiento» y recordatorio de control | Estética médica, UX |
| 26 | Informe PDF por sesión o comparación (leyenda, tabla, aviso, trazabilidad), versión para el paciente en lenguaje llano y CSV de medidas | Marca, Dermatología, Estética médica, Regulación, Metrología, Ética |
| 27 | Registro de auditoría encadenado, SHA-256 del original y «fecha corregida» | Regulación |
| 28 | Paleta semántica de capas, validada sobre fototipos | Marca, Ética |
| 29 | Escala calibrada por paciente (HVID) y misma fuente de escala en el par; corrección del escorzo o «no comparable» por guiñada | Metrología, Facial |
| 30 | Voz para el paciente (TTS) y disparo con volumen o Bluetooth | UX, Fotografía |
| 31 | Protocolos de mapeo corporal total y venoso; escala en mm en el cuerpo (regla o ArUco) | Dermatología, Fotografía |
| 32 | Copia de seguridad cifrada y exportación íntegra para el paciente | Regulación, Ética |
| 33 | Modo presentación al paciente bloqueado y con marca | UX, Marca |
| 34 | Mapa de inyección con dosis; registro de escalas clínicas (GAIS, WSRS) | Estética médica |
| 35 | Lista compacta de expedientes, tabla de medidas rediseñada, galería con badges, barra de anotación | UI, UX |

### Tanda 3: medio plazo (esfuerzo alto o estratégico)
| # | Acción | Apoyan |
|---|---|---|
| 36 | Uso previsto y clasificación regulatoria (vía «no dispositivo» o SaMD IIa), con módulos separables | Regulación (Dermatología, Ética) |
| 37 | Identidad persistente de las lesiones, registro con mapa corporal y capa «Cambios desde la basal» | Dermatología |
| 38 | Estudio test-retest y benchmark separado por fototipo, edad y sexo, con umbrales relativos al tono | Metrología, Facial, Ética |
| 39 | Modo clínico cifrado (Keystore) | Regulación |
| 40 | Perfiles y roles con política del centro | Regulación, UI |
| 41 | DICOM VL Photographic y FHIR | Regulación |
| 42 | Detección automática de carta de color o regla y perfil por sesión | Metrología, Fotografía |
| 43 | Fondo, lente tele, nitidez mínima, luz y distancia en el cuerpo | Fotografía, Metrología |
| 44 | Simetría, ficha dermatológica, celulitis con contracción, satisfacción del paciente | Estética médica, Dermatología |
| 45 | Prueba de usabilidad sumativa (IEC 62366) y telemetría local de fricción | UX, Regulación |

---

## 5. Desacuerdos y tensiones

1. **Hallazgos y grados: quitarlos o darles más valor.** Dermatología y Estética médica piden más análisis: lesiones nuevas, mMASI, escalas, medidas en mm. Regulación advierte que eso convierte la app en SaMD de clase IIa mientras se declara «no dispositivo», y Ética pide desactivarlos por defecto.
   *Resolución:* decidir primero el uso previsto. En la versión actual: hallazgos desactivados por defecto, etiquetas descriptivas, confirmación obligatoria y fuera de las exportaciones. Las funciones diagnósticas nuevas se desarrollan en un módulo separable a la espera de la vía regulatoria.

2. **Aviso de identidad frente a dato biométrico.** Seis expertos piden comparar caras. Facial y Regulación advierten del art. 9 del RGPD.
   *Resolución:* descriptor efímero que se recalcula y no se guarda; solo aviso, nunca bloqueo; umbral calibrado por grupo; documentarlo en PRIVACY.md.

3. **Menos toques frente a más control.** UX pide guardar sin revisar. Regulación quiere fijar los parámetros críticos por protocolo y que nadie los relaje, y Ética pide un aviso audible antes del disparo en zonas íntimas.
   *Resolución:* el guardado automático solo si la coincidencia supera el umbral del protocolo. El rol administrador fija los umbrales. En zonas sensibles se mantiene el aviso y no se muestra la vista previa grande.

4. **Consentimiento bloqueante o informativo.** Regulación propone un aviso bloqueante; UI y Ética, un diálogo con alternativas (verbal con testigo, continuar justificando el motivo).
   *Resolución:* diálogo con justificación registrada en la auditoría. Bloqueo duro solo para exportaciones identificables sin el alcance correspondiente.

5. **Almacenamiento privado cifrado frente a la comodidad de la galería.** El modo clínico rompe el flujo actual del profesional individual.
   *Resolución:* modo seleccionable, «clínico» por defecto para centros. Como paso intermedio inmediato, un álbum seudónimo.

6. **Banda ocular.** Estética médica la propone como solución; Facial y Ética advierten que no anonimiza.
   *Resolución:* el recorte a la zona por defecto; la banda ocular con una advertencia explícita y no ofrecida en periocular.

7. **Modo presentación.** UX lo quiere bloqueado; Marca, con logo y leyenda; Regulación pide FLAG_SECURE, que impediría proyectar la pantalla.
   *Resolución:* un único modo con todo ello y una excepción explícita de FLAG_SECURE mientras está activo.

---

## Nota de verificación

Comprobé con Grep, en `app/src/main` y `CHANGELOG.md`, las afirmaciones de «no existe». Se confirman: TextToSpeech, teclas de volumen, FLAG_SECURE, BiometricPrompt, EncryptedFile/Keystore, PdfDocument, CSV, DICOM/FHIR, reasignar a otro expediente, comprobación de identidad, .nomedia, auditoría, eventos de tratamiento, resumen de sesión, «Seleccionar varias», incertidumbre ± en las medidas, consentimiento en la cámara y la exportación, fototipo en el expediente, y descripción en el visor y las miniaturas (`contentDescription = null`). También se confirma que `setNumFaces(1)` está activo, que AE/AWB lock solo existe en ReliefCapture y que `strip()` borra solo 8 etiquetas.

Reformulé como mejora 5 recomendaciones porque la base ya existe:
- Badge de consentimiento: ya hay un icono de error en el expediente y un filtro en la lista.
- Modo presentación: ya hay pantalla completa y «Ocultar controles».
- Bloqueo de AE/AWB: ya existe en la captura de relieve.
- Anonimización: ya existe «sin datos del paciente», pero solo actúa sobre los metadatos.
- Aviso de comparabilidad: ya existe, pero es solo geométrico.

**Recomendaciones descartadas por ya existir: 0.**

---

## Decisiones del responsable del producto (28 nov 2026)

Pendiente de implementar: aún no se ha empezado ninguna.

**Descartadas:** 2 (comprobar el consentimiento al abrir la cámara o al exportar), 11 (protocolos predefinidos por procedimiento), 18 (aviso de «¿mismo paciente?» y mover a otro expediente) y 21 (margen de error ± por medida y cambios dentro del error).

**Aprobadas:** 1, 3–10, 12–17, 19, 20, 22–45.

**Aprobadas con nota:**
- **19. Desidentificación visual al exportar:** no se impide nunca exportar ni compartir. Solo se avisa al exportar o al compartir cuando la foto es identificable, y se ofrece la opción de desidentificar.
- **20. Álbum sin nombre del paciente:** el álbum se llama por el número de expediente. Si el expediente no tiene número, se usa un identificador del sistema o un número secuencial.

**Ajustes que implican las decisiones:**
- 17 (registro de consentimiento) se mantiene como registro e información, sin bloquear la cámara ni la exportación, porque la 2 se descarta.
- 29 (escala calibrada por paciente y aviso de «no comparable» por giro) se mantiene sin el margen de error ± de la 21.
