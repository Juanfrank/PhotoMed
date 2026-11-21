# Internacionalización (i18n)

El texto de origen (español) queda en el código dentro de `tr("…")` (o `trn("…")` para etiquetas que se
guardan y se traducen al mostrarlas). `python3 tools/i18n/extract.py` genera los recursos Android:

- `app/src/main/res/values/strings_i18n.xml`: español (idioma por defecto).
- `app/src/main/res/values-<idioma>/strings_i18n.xml`: traducciones de `tools/i18n/<idioma>/*.json`
  (objeto JSON: texto español exacto → traducción).

Añadir un idioma: crear `tools/i18n/<idioma>/` con sus catálogos, añadirlo a
`res/xml/locales_config.xml` y a `AppLanguage`, y ejecutar el extractor (`--check` falla si falta
alguna traducción).

## Reglas

- Datos con formato posicional: `tr("Foto %1$d de %2$d", n, total)`; con datos, un `%` literal es `%%`.
- No se traducen claves internas, nombres de archivo, preferencias ni los metadatos que se guardan
  en las fotos (condiciones de la toma): son datos, no interfaz.
- La salida en español no cambia (las pruebas comparan textos en español).

## Glosario (español → inglés)

| Español | English |
|---|---|
| expediente | patient record (record) |
| paciente | patient |
| toma | shot |
| protocolo | protocol |
| zona | area |
| ángulo (vista) | view |
| referencia | reference |
| coincidencia | match |
| disparo automático | auto-capture |
| encuadre | framing |
| alineación / alinear | alignment / align |
| comparar / comparación | compare / comparison |
| anotaciones | annotations |
| diagrama facial | facial diagram |
| contorno | contour |
| surco | fold |
| arruga | wrinkle |
| surco nasogeniano | nasolabial fold |
| líneas de marioneta | marionette lines |
| patas de gallo | crow's feet |
| ceño (líneas del entrecejo) | glabellar lines (frown lines) |
| frente | forehead |
| líneas infraorbitarias | infraorbital lines |
| líneas periorales | perioral lines |
| papada | double chin (submental) |
| línea mandibular | jawline |
| óvalo facial | facial oval |
| línea de implantación del pelo | hairline |
| tercio facial | facial third |
| rostro frontal / oblicuo / perfil | face front / oblique / profile |
| cuero cabelludo | scalp |
| relieve | relief (surface depth) |
| luz pareja entre mejillas | even lighting across cheeks |
| mirada | gaze |
| gesto (expresión) | expression |
| reposo | at rest |
| consentimiento | consent |
| galería / álbum | gallery / album |
| ajustes | settings |
| sin datos del paciente | without patient data (anonymized) |
| ráfaga | burst |
| modo experto | expert mode |
