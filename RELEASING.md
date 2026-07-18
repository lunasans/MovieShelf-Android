# Play-Store-Release (CI)

Der Workflow [`play-release.yml`](.github/workflows/play-release.yml) baut bei einem
Tag-Push `v*` (oder manuell über *Run workflow*) ein signiertes App Bundle und lädt es
in den **Internal-Track** der Play Console hoch. Die Promotion zu Beta/Produktion
bleibt ein manueller Schritt in der Play Console.

## Ablauf pro Release

1. `versionCode` erhöhen und `versionName` setzen in `app/build.gradle.kts`
2. Committen, Tag `vX.Y.Z` setzen und pushen
3. CI: Unit-Tests → `bundleRelease` (signiert) → Upload in den Internal-Track
4. In der Play Console testen und manuell promoten

## Einmalige Einrichtung (GitHub-Secrets)

Der Workflow braucht fünf Repository-Secrets (*Settings → Secrets and variables → Actions*):

| Secret | Inhalt |
|--------|--------|
| `ANDROID_KEYSTORE_BASE64` | Upload-Keystore, base64-kodiert: `base64 -w0 upload.keystore` (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.keystore"))`) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore-Passwort |
| `ANDROID_KEY_ALIAS` | Alias des Upload-Keys im Keystore |
| `ANDROID_KEY_PASSWORD` | Passwort des Keys |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON-Key des Google-Cloud-Service-Accounts (kompletter Dateiinhalt) |

### Service-Account für die Play-API anlegen

1. In der [Google Cloud Console](https://console.cloud.google.com/) ein Projekt wählen/anlegen
   und die **Google Play Android Developer API** aktivieren
2. Service-Account erstellen (*IAM & Verwaltung → Dienstkonten*), JSON-Key herunterladen
3. In der [Play Console](https://play.google.com/console) unter
   *Nutzer und Berechtigungen → Nutzer einladen* die Service-Account-E-Mail einladen und
   der App `at.neuhaus.movieshelf` die Berechtigung **Releases in Tests-Tracks verwalten**
   (oder *Releases verwalten*) geben
4. Kompletten JSON-Inhalt als Secret `PLAY_SERVICE_ACCOUNT_JSON` hinterlegen

### Keystore

Es muss derselbe **Upload-Key** sein, der in der Play Console für die App registriert ist
(bei aktiviertem Play App Signing signiert Google das finale Artefakt). Der Keystore wird
nur base64-kodiert als Secret gespeichert und liegt nie im Repo.

## Hinweise

- Die Signier-Konfiguration in `app/build.gradle.kts` greift nur, wenn die CI
  `ANDROID_KEYSTORE_FILE` setzt — lokale Builds über den Android-Studio-Wizard
  funktionieren unverändert.
- Track ändern: `track: internal` im Workflow auf `beta`/`production` stellen
  (empfohlen: bei `internal` bleiben und manuell promoten).
- Release-Notes können später über ein `whatsNewDirectory` ergänzt werden
  (siehe Doku von `r0adkll/upload-google-play`).
- Voraussetzung: Die App muss einmal manuell in der Play Console angelegt und ein
  erstes Bundle hochgeladen worden sein — die API kann keine neue App erstellen.
