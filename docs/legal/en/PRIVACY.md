_This is a convenience translation. In case of discrepancy, the Spanish version prevails._

# PhotoMed Privacy Policy

**Effective as of:** November 18, 2026
**Party responsible for the application:** Juan Medina — juanfrank11@gmail.com

PhotoMed is an application that allows healthcare and medical aesthetics professionals to take,
organize and compare clinical photographs. This policy explains what data the application processes,
where it is stored and what control you have over it.

## 1. Summary

- PhotoMed **has no servers and no user accounts**. The author does not receive, see or store your photographs or your patients' data.
- Everything is stored **on your phone**: photos in the gallery (album `Pictures/PhotoMed/<paciente>`) and patient record data in the application's private storage.
- Photographs **have the patient's data embedded** (name, patient record number, area, date, consent) as metadata. To send them without that data, use **“Share without patient data”**.
- Image analysis for aligning photographs is performed **on the device itself**.

## 2. Who is responsible for patient data

The professional or institution using PhotoMed is the **data controller** for the data of
its patients. PhotoMed is merely a tool that runs on your device. You must:

- obtain the patient's **informed consent** for taking and using clinical photographs
  (the application allows you to record it in the patient record, but does not replace it);
- comply with the personal data and health data protection laws and regulations applicable in your
  jurisdiction (for example, Law 172-13 of the Dominican Republic, the European GDPR, HIPAA in
  the U.S., or other equivalent laws), as well as the rules of professional secrecy;
- protect the device (screen lock, encryption, secure backups).

## 3. Data processed by the application

| Data | Purpose | Where it is stored |
|---|---|---|
| Patient name, patient record number, notes, consent | Organizing photographs by patient record | The app's private storage (`expediente.json`) |
| Clinical photographs | Before/after documentation and comparison | Phone gallery, album `Pictures/PhotoMed/<expediente>` |
| Metadata of each photo (patient, patient record, area, date, consent) | Identifying the photo even when viewed outside the app, and recovering patient records after reinstalling | Inside the image file (EXIF and XMP) |
| Skin relief map and depth sensor distance (only with relief measurement or a phone with a depth sensor) | Measuring the depth and length of wrinkles and folds | The app's private storage (`relieve/`); deleted when the photo is deleted from within the app |
| Accelerometer readings | Displaying the level indicator in the camera | In memory only; not stored |

PhotoMed does not collect location, contacts, advertising identifiers or its own usage data, and
does not include advertising or analytics tools.

## 4. Permissions

- **Camera:** taking photographs. Used only while the camera screen is open.
- **Save to gallery** (Android 8 and 9): writing to `Pictures/PhotoMed`. On Android 10 or higher no permission is required.
- **Read images** (optional): only if you use “Recover from gallery”, to read PhotoMed photos created by a previous installation.
- **Internet:** declared by the Google ML Kit libraries (see section 6). PhotoMed does not
  send photos or data: all models are bundled with the application.

## 5. Visibility in the gallery and in backups

Because photographs are stored in the gallery, **other applications with permission to access photos
can see them**, and if you have a cloud backup enabled (for example, Google Photos),
**those photos and their metadata may be uploaded to that service** depending on your account settings.
Review those settings and exclude the PhotoMed album if your applicable regulations so require.

## 6. Third-party components

To detect anatomical landmarks (eyes, ear, shoulders, hips, etc.) and separate the patient's
silhouette from the background (contour measurement), PhotoMed uses **Google ML Kit** (face detection, pose
detection and selfie segmentation) and, if selected in Settings, **MediaPipe Face Landmarker** (Apache 2.0),
and **MediaPipe Selfie Multiclass** (Apache 2.0; hair, facial skin and body skin), all with
models bundled with the application:
images are processed on the device and **are not sent to Google**. According to Google's documentation, ML Kit may send anonymous technical
information about API usage and performance (not images or results). More information at
<https://developers.google.com/ml-kit/terms>.

## 7. Sharing

Photographs leave the phone only when you decide so (Share button). There are two options:

- **Without patient data:** a copy is sent without the clinical or location metadata and with a neutral file name.
- **With patient data:** the photo is sent exactly as stored.

## 8. Retention and deletion

Data is retained until you delete it. You can delete a photo, or an entire patient record
with all its photos, from within the application. Uninstalling PhotoMed deletes the patient record
data, but **the photographs remain in the gallery** (with their metadata) until you
delete them; you can rebuild the patient records with “Recover from gallery”.

## 9. Minors

The application is intended for professionals. Photographs of patients who are minors require
the consent of their legal representative in accordance with applicable law.

## 10. Changes and contact

If this policy changes, the new version will be published together with the application, with its effective
date. For privacy questions, write to **juanfrank11@gmail.com**.
