# Política de privacidad de PhotoMed

**Vigente desde:** 18 de noviembre de 2026
**Responsable de la aplicación:** Juan Medina — juanfrank11@gmail.com

PhotoMed es una aplicación para que profesionales de la salud y de la estética médica tomen,
organicen y comparen fotografías clínicas. Esta política explica qué datos trata la aplicación,
dónde se guardan y qué control tiene usted sobre ellos.

## 1. Resumen

- PhotoMed **no tiene servidores ni cuentas de usuario**. El autor no recibe, ve ni almacena sus fotografías ni los datos de sus pacientes.
- Todo se guarda **en su teléfono**: las fotos en la galería (álbum `Pictures/PhotoMed/<paciente>`) y los datos de los expedientes en el almacenamiento privado de la aplicación.
- Las fotografías **llevan incrustados los datos del paciente** (nombre, número de expediente, zona, fecha, consentimiento) como metadatos. Para enviarlas sin esos datos use **“Compartir sin datos del paciente”**.
- El análisis de imágenes para alinear fotografías se hace **en el propio dispositivo**.

## 2. Quién es responsable de los datos de los pacientes

El profesional o la institución que usa PhotoMed es el **responsable del tratamiento** de los datos
de sus pacientes. PhotoMed es solo una herramienta que se ejecuta en su dispositivo. Usted debe:

- obtener el **consentimiento informado** del paciente para la toma y el uso de fotografías clínicas
  (la aplicación permite registrarlo en el expediente, pero no lo sustituye);
- cumplir la normativa de protección de datos personales y de datos de salud aplicable en su
  jurisdicción (por ejemplo, la Ley 172-13 de la República Dominicana, el RGPD europeo, la HIPAA en
  EE. UU. u otras equivalentes), así como las normas de secreto profesional;
- proteger el dispositivo (bloqueo de pantalla, cifrado, copias de seguridad seguras).

## 3. Datos que trata la aplicación

| Dato | Para qué | Dónde se guarda |
|---|---|---|
| Nombre del paciente, número de expediente, notas, consentimiento | Organizar las fotografías por expediente | Almacenamiento privado de la app (`expediente.json`) |
| Fotografías clínicas | Documentación y comparación antes/después | Galería del teléfono, álbum `Pictures/PhotoMed/<expediente>` |
| Metadatos de cada foto (paciente, expediente, zona, fecha, consentimiento) | Identificar la foto aunque se vea fuera de la app y recuperar expedientes tras reinstalar | Dentro del archivo de imagen (EXIF y XMP) |
| Lecturas del acelerómetro | Mostrar el nivel en la cámara | Solo en memoria; no se guardan |

PhotoMed no recopila ubicación, contactos, identificadores publicitarios ni datos de uso propios, y
no incluye publicidad ni herramientas de analítica.

## 4. Permisos

- **Cámara:** tomar las fotografías. Solo se usa con la pantalla de cámara abierta.
- **Guardar en la galería** (Android 8 y 9): escribir en `Pictures/PhotoMed`. En Android 10 o superior no se necesita permiso.
- **Leer imágenes** (opcional): solo si usa “Recuperar desde la galería”, para leer fotos de PhotoMed creadas por una instalación anterior.

## 5. Visibilidad en la galería y en copias de seguridad

Como las fotografías se guardan en la galería, **otras aplicaciones con permiso de acceso a fotos
pueden verlas**, y si tiene activada una copia de seguridad en la nube (por ejemplo, Google Fotos),
**esas fotos y sus metadatos pueden subirse a ese servicio** según la configuración de su cuenta.
Revise esa configuración y excluya el álbum PhotoMed si su normativa lo exige.

## 6. Componentes de terceros

Para detectar puntos anatómicos (ojos, oreja, hombros, caderas, etc.) y separar la silueta del
paciente del fondo (medición de contornos) PhotoMed usa **Google ML Kit** (detección de rostro, de
pose y segmentación de personas) con modelos incluidos en la aplicación: las imágenes se procesan en el dispositivo y **no se
envían a Google**. Según la documentación de Google, ML Kit puede enviar información técnica
anónima de uso y rendimiento de la API (no imágenes ni resultados). Más información en
<https://developers.google.com/ml-kit/terms>.

## 7. Compartir

Las fotografías solo salen del teléfono cuando usted lo decide (botón Compartir). Hay dos opciones:

- **Sin datos del paciente:** se envía una copia sin los metadatos clínicos ni de ubicación y con un nombre de archivo neutro.
- **Con datos del paciente:** se envía la foto tal como está guardada.

## 8. Conservación y eliminación

Los datos se conservan hasta que usted los elimine. Puede borrar una foto, o un expediente completo
con todas sus fotos, desde la aplicación. Al desinstalar PhotoMed se borran los datos de los
expedientes, pero **las fotografías permanecen en la galería** (con sus metadatos) hasta que las
elimine; puede reconstruir los expedientes con “Recuperar desde la galería”.

## 9. Menores de edad

La aplicación está dirigida a profesionales. Las fotografías de pacientes menores de edad requieren
el consentimiento de su representante legal conforme a la ley aplicable.

## 10. Cambios y contacto

Si esta política cambia, se publicará la nueva versión con su fecha de vigencia junto con la
aplicación. Para preguntas sobre privacidad escriba a **juanfrank11@gmail.com**.
