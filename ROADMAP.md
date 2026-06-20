# MovieShelf Android – Roadmap

## Version 1.7.0 (geplant)

Aktueller Stand: 1.6.1. Die folgenden Punkte sind für 1.7.0 vorgemerkt.
Priorität: 🔴 hoch · 🟡 mittel · ⚪ optional.

### ✅ Bereits umgesetzt (Richtung 1.7.0 — noch nicht committet/getestet)

- ✅ **Film löschen** (Edit-Screen + Bestätigungsdialog, Cache-Cleanup, Reload)
- ✅ **2FA-Schalter entschärft** (deaktiviert + Hinweis „über Web verwaltet")
- ✅ **Trailer suchen** → YouTube-Suche, wenn kein Trailer vorhanden *(TMDb-Variante noch offen)*
- ✅ **Farbschemata vervollständigt** (volles M3-Token-Set, Light-`onSecondary`-Bug gefixt)
- ✅ **Typografie-Skala ausgebaut**
- ✅ **Fehler-/Retry-Zustände** auf Stats- & Actor-Screen *(Detail-Screen noch offen)*
- ✅ **Karten-Politur** (runder Watched-Button + Rating-Badge)
- ✅ **„Ungespeicherte Änderungen"-Warnung** im Edit-Screen
- ✅ **Logout-Bestätigung**
- ✅ **Autofill/Passwort-Manager im Login** — `ContentType.Username/EmailAddress/Password/SmsOtpCode` auf den Login-Feldern (inkl. Compose-BOM-Anhebung 2024.09 → 2025.06.01 für das `ContentType`-API)

### ✨ Features (Backend-Endpunkte existieren bereits)

- 🔴 **Film löschen** — `DELETE /api/admin/movies/{id}` (Admin). Lösch-Button im Edit-Screen mit Bestätigungsdialog; rundet die in 1.6.1 ergänzte Bearbeitung ab. *Aufwand: klein.*
- 🟡 **Cover/Backdrop-Upload** — `POST /api/admin/movies/{id}/cover` & `/backdrop`. Bewusst aus der 1.6.1-Edit-Funktion ausgelassen. Bildauswahl + Multipart-Upload. *Aufwand: mittel.*
- 🟡 **Wunschliste** — Server unterscheidet via `in_collection` zwischen Sammlung und Wunschliste (`MovieWishlistController`). App zeigt aktuell nur `in_collection=true`. Eigene Wunschlisten-Ansicht ergänzen. *Aufwand: mittel.*
- ⚪ **Eigene Listen/Sammlungen** — vollständige Lists-API (`GET/POST/PUT/DELETE /api/lists`) ist vorhanden, in der App fehlt jede UI. Größtes ungenutztes Feature. *Aufwand: groß.*
- ⚪ **Film manuell anlegen** — `POST /api/admin/movies` (ohne TMDb-Import). *Aufwand: klein–mittel.*
- 🟡 **Trailer suchen, wenn keiner vorhanden** — heute erscheint der Trailer-Button nur bei gesetzter `trailerUrl`. Zwei Stufen:
  - **Schnell (nur App):** Button „Trailer suchen" öffnet YouTube-Suche (`<Titel> <Jahr> Trailer`). Kein Server nötig, speichert nichts. *Aufwand: klein.*
  - **Richtig (App + Server):** Trailer per `tmdb_id` von TMDb holen & speichern. Logik existiert als Command `movies:smart-trailer` ([SmartTrailerSync](../../v2-saas/app/Console/Commands/SmartTrailerSync.php)); fehlt nur ein API-Endpunkt (z. B. `POST /api/movies/{id}/fetch-trailer`). *Aufwand: mittel.*

### 🔧 Code-Qualität & Robustheit

- 🔴 **Unit-Tests** — App hat aktuell keine Tests. Abdecken: Sortier-Logik (Collator/Artikel/Rating-Komma), `parseRating`, Offline-Pagination im Repository. *Hoher Wert, geringes Risiko.*
- 🟡 **Typisierte TMDb-DTOs** — `AddMovieViewModel`/`MovieDetailViewModel` nutzen `Map<String,Any>` + unchecked Casts. Durch Data-Classes ersetzen.
- ⚪ **Sortierung bei großen Sammlungen** — `applyFiltersAndSort` läuft auf dem Main-Thread; Sort-Keys einmalig vorberechnen und auf `Dispatchers.Default` auslagern.
- ⚪ **EncryptedSharedPreferences-I/O** — erste Entschlüsselung läuft synchron auf dem Main-Thread; auf IO-Dispatcher verschieben.

### 🎨 UX-Feinschliff

- 🔴 **2FA-Schalter im Profil funktionslos (Bug)** — `PUT /api/user` ignoriert `two_factor_enabled` (validiert/speichert nur name/email/password), der Schalter springt nach dem Speichern zurück. **Sofortmaßnahme:** Schalter deaktivieren/ausblenden oder als „nur über Web verwaltbar" kennzeichnen, damit er nicht in die Irre führt.
- 🟡 **2FA per App verwalten (Feature)** — echter TOTP-Enrollment-Flow: Secret + QR-Code anzeigen, mit Code bestätigen, deaktivieren. **Braucht neue Server-Endpunkte** (z. B. `POST /api/user/2fa/enable|confirm|disable`) — in der aktuellen API nicht vorhanden. Aufwand: groß (Server + App).
- 🟡 **Passwort-Manager / Autofill im Login** — Unterstützung für Passwort-Tresore (Google Password Manager, Bitwarden, 1Password …). E-Mail-/Passwort-Felder mit Autofill-Hints versehen (`ContentType.Username`/`Password` bzw. `autofillHints`), damit Tresore Zugangsdaten vorschlagen und speichern können. Betrifft `LoginScreen` (Passwort-Login) und den 2FA-Code (`ContentType.SmsOtpCode`). *Aufwand: klein–mittel.*
- 🟡 **Nav-Leiste beim Scrollen ausblenden (Filmliste)** — im Dashboard die `FloatingNavBar` bei Scroll nach unten aus-, bei Scroll nach oben wieder einblenden. Über eine `NestedScrollConnection` am Dashboard-Grid + `AnimatedVisibility` (`slideInVertically`/`slideOutVertically`). *Aufwand: klein.*
  - In der **Film-Übersicht** (Detail-Screen) ist die Nav-Leiste bereits komplett ausgeblendet (`showNavBar` umfasst nur dashboard/profile/stats) — kein Handlungsbedarf.
- 🟡 **„Ungespeicherte Änderungen"-Warnung** im Edit-Screen beim Zurück-Tippen.
- 🟡 **Fehler-/Retry-Zustände** auf Detail-, Stats- und Actor-Screens (Dashboard hat bereits Offline-Banner + Pull-to-Refresh).
- ⚪ **Logout-Bestätigung** in der Navigationsleiste.
- ⚪ **In-App-Update-Hinweis** — `ServerInfo.version` ist verfügbar; auf neuere Version hinweisen.
- ⚪ **Barrierefreiheit** — fehlende `contentDescription` an Icons ergänzen (TalkBack).

### 🖌️ Design & Theme

- 🔴 **Farbschemata vervollständigen** — in `Theme.kt` werden nur wenige M3-Tokens gesetzt; `onSurface`, `onBackground`, `surfaceVariant`, `onSurfaceVariant`, `primaryContainer`, `secondaryContainer`, `outline` u. a. fehlen → Material nutzt seine Default-Baseline (lila-ish), die überall in der UI durchschlägt (Karten/Chips/Suchfeld/Metadaten). Volles Token-Set passend zu Amber/Navy bzw. Rot definieren. Größter Hebel, nur Theme.kt/Color.kt. *Aufwand: klein–mittel.*
- 🟡 **Akzent vereinheitlichen** — `primary` (Amber/Pink-Rot) vs. Nav-Leiste (`#CC1111`) sind drei verschiedene Akzente; kohärentes Akzent-System festlegen.
- 🟡 **Typografie ausbauen** — `Type.kt` definiert nur `bodyLarge`; vollständige Typo-Skala + Marken-/Display-Font für Titel/Headlines („Kino"-Look).
- 🟡 **Light-Theme finalisieren oder Dark-only** — Light-Schema ist unvollständig und hat einen Copy-Paste-Bug (`onSecondary = OnSecondaryDark`). Entweder sauber ausbauen oder bewusst nur Dark.
- ⚪ **Karten-Politur** — Watched-Button von `RectangleShape` auf abgerundet; optional Rating-/FSK-Badge aufs Poster; einheitliche Eckenradien/Elevation.
- ⚪ **Material You (optional)** — Dynamic-Color-Schalter (aktuell bewusst deaktiviert).

---

### Vorgeschlagener Fokus für 1.7.0
1. **Film löschen** (#schneller Abschluss der Admin-Bearbeitung)
2. **Tests** für die in 1.6.1 überarbeitete Sortier-/Repository-Logik
3. Ein „echtes" Feature: **Wunschliste** oder **Listen**
