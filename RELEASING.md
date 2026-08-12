# Release (CI)

Der Workflow [`play-release.yml`](.github/workflows/play-release.yml) baut bei einem
Tag-Push `v*` (oder manuell über *Run workflow*) ein signiertes App Bundle und lädt es
in den **Internal-Track** der Play Console hoch. Die Promotion zu Beta/Produktion
bleibt ein manueller Schritt in der Play Console.

Zusätzlich entsteht ein **GitHub-Release als Versionsmarke** mit den
Änderungsnotizen — ohne Artefakt, siehe unten.

## Ablauf pro Release

1. `versionCode` erhöhen und `versionName` setzen in `app/build.gradle.kts`
1. **Versionshinweise in beiden Sprachen** aktualisieren (`distribution/whatsnew/`,
   siehe unten) — sie sind Pflicht, nicht Kür: fehlen sie, zeigt Play den Text
   der Vorversion
2. Committen, Tag `vX.Y.Z` setzen und pushen
3. CI: Unit-Tests → `bundleRelease` (signiert) → Upload in den Internal-Track
   samt Versionshinweisen → GitHub-Release als Versionsmarke
4. In der Play Console testen und manuell promoten

Bewusst **kein APK**: Getestet wird über das Play-Testprogramm, und nur eine
Installation von dort zählt dafür. Ein direkt verteiltes APK trüge zudem den
Upload-Key statt des von Google vergebenen App-Signing-Keys — wer es installiert
hat, müsste vor dem Wechsel auf die Play-Fassung deinstallieren und verlöre dabei
seine lokale Sammlung.

## Versionshinweise ("Was ist neu")

Die Texte je Sprache liegen unter `distribution/whatsnew/` — eine Datei pro
Locale, ohne Endung:

```
distribution/whatsnew/whatsnew-de-DE
distribution/whatsnew/whatsnew-en-US
```

Der Workflow reicht das Verzeichnis über `whatsNewDirectory` an die Play-API
weiter; die Hinweise gehen also zusammen mit dem Bundle hoch und müssen nicht
mehr von Hand in die Console getippt werden. **Vor jedem Release aktualisieren** —
sonst erscheint der Text der Vorversion.

Play begrenzt jeden Text auf 500 Zeichen. Der Dateiname muss exakt der Locale
in der Console entsprechen (`de-DE`, `en-US`), sonst lehnt die API den Upload ab.

## Neue App in der Play Console (einmalig)

Die Anwendungs-ID ist `info.movieshelf`. Weil eine geänderte ID für Google eine
komplett neue App bedeutet, gilt vor dem ersten Tag-Push:

1. App mit dem Package-Namen `info.movieshelf` anlegen
2. **Erste AAB von Hand hochladen** (Internal Testing), falls die API die frisch
   angelegte App noch nicht annimmt. Das Bundle liegt danach nicht mehr als
   Workflow-Artefakt bereit — in einem öffentlichen Repository könnte es sonst
   jeder herunterladen. Für einen Notfall lässt es sich lokal bauen
   (`gradlew bundleRelease` mit gesetzten `ANDROID_*`-Variablen).
3. Play App Signing aktivieren. Der **bestehende Keystore kann weiterverwendet
   werden** — er ist nur der Upload-Key und nicht an eine Anwendungs-ID gebunden.
   Es müssen also keine Secrets neu erzeugt werden.
4. Den Service-Account in der neuen App berechtigen (siehe unten) — Berechtigungen
   vererben sich nicht.

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
   der App `info.movieshelf` die Berechtigung **Releases in Tests-Tracks verwalten**
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
