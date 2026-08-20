# MovieShelf Android – Roadmap

## Version 2.2.0 (in Arbeit)

Aktueller Stand: 2.1.0 (versionCode 31), der Versionsbump auf 2.2.0 steht noch aus.
Die Punkte stammen aus dem Funktionsvergleich mit **MovieShelf Desktop**
(`versions/desktop`) — beide Apps sollen nahezu identisch funktionieren, siehe
[CLAUDE.md](CLAUDE.md).
Priorität: 🔴 hoch · 🟡 mittel · ⚪ optional.

**Erledigt für 2.2.0:** Jellyfin-Import · eigene Sternbewertung · Zeilenansicht ·
überarbeitete Statistik · Vollsync-Fehler behoben. Alles offen in
[PR #30](https://github.com/lunasans/MovieShelf-Android/pull/30) und
[PR #31](https://github.com/lunasans/MovieShelf-Android/pull/31), noch nicht
auf `main`.

> Nicht auf der Liste, weil bereits vorhanden: Serien mit Staffeln/Episoden inkl.
> Season-Backfill, Boxset-Anzeige, TMDb-Import, Schauspieler-Detailseiten, Listen,
> Statistiken, physische Sammlungsdaten (Edition, Region, Regalstandort, Zustand,
> Kaufdatum/-preis), Standalone- und Online-Modus mit Sync, Deutsch/Englisch,
> Hell/Dunkel, Update-Hinweis über Play.
> Nur mobil: 2FA-Verwaltung, Wunschlisten-Toggle, Cover-/Backdrop-Upload, OAuth-Login.

### Funktionslücken gegenüber der Desktop-App

- [x] 🔴 **Jellyfin-Import** — **umgesetzt.** Anmeldung am Server (Token im Keystore),
  Bibliotheksauswahl, Import von Filmen und Serien samt Staffeln, Episoden, Covern,
  Backdrops, Besetzung, Trailern und Gesehen-Status, optional mit TMDb-Abgleich.
  Schreibt lokal in die Room-Datenbank; im Shelf-Modus geht das Ergebnis über den
  normalen Abgleich zum Server — genau wie in der Desktop-App.

- [x] 🔴 **Sortierung und Genre-Filter** — **umgesetzt.** Fünf Sortierschlüssel
  (Titel, Jahr, Bewertung, Laufzeit, Zugang) mit Richtungsumschalter und ein
  Genre-Filter, beide als Chips über der Liste. Die Regeln liegen als reine
  Funktionen in `MovieSorting.kt` und sind geprüft.

- [x] 🔴 **Massenbearbeitung** — **umgesetzt.** Langes Tippen startet die Auswahl,
  danach wählt ein Tippen aus. Sammelaktionen: gesehen/ungesehen setzen, alle
  auswählen, löschen (Admin). Ohne Sammel-Endpunkt — jeder Titel geht einzeln
  über den bestehenden Weg, nacheinander statt nebenläufig.
  *Offen: Wunschliste als Sammelaktion (braucht PR #41) und Sammelfelder wie
  Regalstandort oder Zustand.*

- [x] 🔴 **Eigene Bewertung (`user_rating`)** — **umgesetzt.** Fünf Sterne in der
  Detailansicht, erneutes Tippen nimmt die Bewertung zurück. Liegt lokal in
  `userRating`/`syncedUserRating` und geht über `POST /api/movies/{id}/rate` raus —
  sofort, und beim nächsten Abgleich noch einmal, falls der erste Versuch scheiterte.

- [x] 🟡 **Zufallsauswahl** — **umgesetzt.** Würfel-Symbol in der Kopfzeile des
  Dashboards; das Ergebnis kommt als Blatt von unten mit Cover, Titel, Eckdaten
  und Inhalt, dazu „Nochmal" und „Ansehen". Die Auslosung folgt der geöffneten
  Kategorie (Serien bleiben Serien), lässt Boxsets, gelöschte und nicht
  gesammelte Titel aus und läuft rein lokal.

- [x] 🟡 **Besetzung manuell pflegen** — **umgesetzt.** Abschnitt „Besetzung" im
  Bearbeiten-Formular: suchen, hinzufügen, Rolle eintragen, Hauptrolle
  markieren, entfernen. Ein Name ohne Treffer lässt sich trotzdem aufnehmen —
  die Shelf legt die Person an. **Braucht MovieShelf-SaaS#85.**

- [x] 🟡 **Boxset-Zuordnung bearbeiten** — **umgesetzt.** Auswahlfeld im
  Bearbeiten-Formular; die Kandidaten kommen aus der lokalen Sammlung, damit die
  Auswahl auch ohne Netz steht. Ist der Titel selbst ein Boxset, steht statt der
  Auswahl ein Satz. **Braucht MovieShelf-SaaS#85.**

- [x] ⚪ **Ansichtsmodi der Sammlung** — **umgesetzt.** Umschalter als Chip über der
  Liste zwischen Poster-Raster und kompakter Zeilenansicht mit Regalstandort; die
  Wahl liegt im DataStore. Die Tabellenansicht des Desktops bleibt bewusst aus —
  siehe unten.

### Neu aufgenommen (aus der Arbeit an 2.2.0)

- [x] 🔴 **Zugriffe verwalten** — **umgesetzt.** Server: `GET /api/user/tokens`,
  `DELETE /api/user/tokens/{id}` und `/others` ([MovieShelf-SaaS#83](https://github.com/lunasans/MovieShelf-SaaS/pull/83)).
  App: Profil → Angemeldete Geräte, mit getrennten Abschnitten für Geräte und
  verbundene Apps, Kennzeichnung des eigenen Zugangs und „alle anderen abmelden".
  Browser-Sitzungen und Freigabe-Links bleiben der Weboberfläche vorbehalten.

- [x] 🟡 **Gesehen-Stand von Episoden** — **umgesetzt.** Häkchen je Folge in der
  Staffelliste, lokal gespeichert und über `POST /api/episodes/{id}/watched`
  übertragen; eigener bestätigter Stand (`syncedWatched`) und eigener
  Push-Schritt, wie bei Film und Bewertung. Room 12 → 13.

- [x] ⚪ **Vollständige Sync-Vorschau** — **umgesetzt.** Die Vorschau zählt jetzt
  auch offene Bewertungen und Folgen-Markierungen; vorher meldete sie „nichts zu
  tun", während beides anstand.

### Offen, unabhängig vom Desktop-Vergleich

Im Code nachgeprüft, nicht aus dem Archiv übernommen — die 1.7.0-Liste dort ist
teils überholt.

- [x] 🟡 **Wunschlisten-Ansicht** — **umgesetzt.** Profil → Wunschliste zeigt die
  vorgemerkten Titel als Raster; das Herz im Detail füllt sie. Die erste Fassung
  las `inCollection` statt der Vormerkung und zeigte damit die falsche Menge —
  behoben, samt lokalem Speichern und eigenem Push-Schritt.

- [x] 🟡 **Fehler-/Retry-Zustand im Detail-Screen** — **umgesetzt.** Schlägt das Laden
  fehl, steht jetzt ein Fehlerbild mit „Erneut versuchen" da statt einer leeren
  Seite; die flüchtige Meldung bleibt für Fehler, bei denen der Film sichtbar ist.

- [x] 🟡 **Typisierte TMDb-DTOs im Anlegen-Weg** — **umgesetzt.** `AddMovieViewModel`
  und `AddMovieScreen` arbeiten jetzt mit `TmdbSearchItem` statt mit
  `Map<String, Any>`; die Rückverwandlung in eine Map ist entfallen. In der
  gesamten Oberfläche gibt es keine ungeprüften Map-Zugriffe mehr.

- [x] 🟡 **Tests für die Sortierlogik** — **umgesetzt** zusammen mit der Sortierung:
  Bewertungen mit Komma, fehlende Werte in beiden Richtungen, stabile Reihenfolge
  bei Gleichstand, Genre-Vergleich der Einzelteile.

- [x] ⚪ **Geteilte Bausteine statt eigener Kacheln** — **umgesetzt.** `MovieRowItem`
  (Filmografie) und `BoxsetMovieItem` (Teile eines Boxsets) waren derselbe
  Baustein, zweimal geschrieben — und beide dasselbe wie `MovieListRow`. Jetzt
  eine Fassung; der Gesehen-Schalter ist optional, weil er nicht überall
  hingehört. Bewusst nicht angetastet: Schauspieler-Portraits, TMDb-Suchtreffer
  und die Backdrop-Banner — das sind keine Poster-Karten.

### Bewusst nicht übernommen

- **Statistik-Fenster** — Desktop kann die Statistiken in ein eigenes Fenster
  auslösen. Auf Mobil ohne Entsprechung; der bestehende `StatsScreen` deckt den
  Inhalt ab.
- **Tabellenansicht** — Desktop bietet neben Karten und Zeilen eine Tabelle. Auf
  einem Telefonschirm bleibt von den Spalten nichts Lesbares übrig; die
  Zeilenansicht deckt den Zweck ab.

- **Backup als `.ms`-Archiv** — Desktop exportiert Datenbank plus Cover in eine
  Datei. Auf Android ist die Room-DB an die App gebunden; ein Austauschformat wäre
  nur sinnvoll, wenn es zwischen beiden Apps kompatibel ist. Zurückgestellt, bis
  klar ist, ob das Archiv geräteübergreifend gelten soll.

---

## Archiv

<details>
<summary>Roadmap 1.7.0 (abgeschlossen)</summary>

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
- ✅ **Nav-Leiste beim Scrollen ausblenden** — `NestedScrollConnection` + `AnimatedVisibility`; blendet beim Runter-Scrollen aus, beim Hoch-Scrollen ein
- ✅ **Typisierte TMDb-DTOs** — `TmdbSearchResponse`/`TmdbSearchItem` statt `Map<String,Any>` für `api/tmdb/search` (UI unverändert)
- ✅ **Cover/Backdrop-Upload** — im Edit-Screen Bild via Photo-Picker auswählen → Multipart-Upload an `POST /api/admin/movies/{id}/cover|backdrop`, mit Fortschritt + Snackbar
- ✅ **Listen / Wunschliste (ansehen)** — Server: neuer `GET /api/lists/{list}` (volle Filme, da Android keinen Komplett-Sync hat) + `movie_count` im Index. Android: „Meine Listen" im Profil → Listen-Übersicht → Filme einer Liste. *Listen anlegen/bearbeiten/löschen noch offen.* **Server-Teil (v2-saas) muss deployt werden.**
- ✅ **Wishlist-Toggle** — Server: neuer `POST /api/movies/{id}/wishlist` (Toggle) + `is_wishlisted` im Detail. Android: Herz-Toggle in der Film-Detailansicht (optimistisch). **Server-Teil (v2-saas) muss deployt werden.**
- ✅ **Listen anlegen/bearbeiten/löschen** — Android: Liste anlegen (FAB), umbenennen/löschen (Overflow), Film aus Liste entfernen, „Zu Liste hinzufügen" im Film-Detail (Bottom-Sheet). Nutzt bestehende `POST/PUT/DELETE /api/lists`.
- ✅ **Film manuell anlegen** — Admin: Formular-Screen (`CreateMovieScreen`), erreichbar über „Film hinzufügen" → ✏️. Nutzt `POST /api/admin/movies`.
- ✅ **Trailer von TMDb speichern** — Server: neuer Admin-Endpunkt `POST /api/admin/movies/{id}/fetch-trailer` (TMDb-Videos, Fallback YouTube-Suche). Android: „Trailer von TMDb holen"-Button im Detail (Admin, wenn kein Trailer). **Server-Teil muss deployt werden.**
- ✅ **Echtes 2FA** — Server: neue Endpunkte `POST /api/user/2fa/enable|confirm|disable` (Google2FA, Secret + otpauth + Recovery-Codes). Android: 2FA-Verwaltungs-Screen im Profil (einrichten/bestätigen/deaktivieren). **Server-Teil muss deployt werden.**
- ✅ **Akzent vereinheitlicht** — drei verschiedene Rottöne → ein kohärentes Marken-Rot, Theme-Sekundärtöne harmonisiert.
- ✅ **Material You** — Dynamic-Color-Schalter im Profil (ab Android 12), Einstellung in DataStore, Theme respektiert sie.
- ✅ **In-App-Update-Hinweis** — Google Play In-App-Updates (flexibel) beim Start; no-op außerhalb von Play.
- ✅ **Sortierung auf Background-Dispatcher** — Filter+Sortierung läuft jetzt auf `Dispatchers.Default` (thread-sicherer Collator), nicht mehr auf dem Main-Thread.
- ✅ **EncryptedPrefs-I/O** — Keystore-Init wird beim Start im Hintergrund vorgewärmt (kein Main-Thread-Block beim ersten Token-Zugriff).
- ✅ **Barrierefreiheit** — `contentDescription`-Durchsicht der Hauptscreens (fehlende bei bedienbaren Icons ergänzt, dekorative korrekt `null`).
- ✅ **Version 1.7.0** (versionCode 19)

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

- ✅ **"Shelf"-Look übernommen** — Layout/Design an die MovieShelf-Web-Oberfläche angeglichen: fast-schwarzer Hintergrund (`#0C0C0E`), blauer Haupt-Akzent (statt Amber/Pink-Rot), Rosé-Rot als Sekundärakzent, volles M3-Token-Set in `Color.kt`/`Theme.kt`, Outfit (Headlines) + Plus Jakarta Sans (Fließtext) als gebündelte Fonts (`Font.kt`/`Type.kt`), zentrale Shape-Skala (`Shape.kt`, sehr runde Ecken), neue geteilte Komponenten (`PosterCard`, `MediaBadge`, `GlassSurface`, `ShelfButton`, `HeadingText`), `FloatingNavBar` als Frosted-Glass-Pill mit Blau-Akzent statt Rot-Verlauf.
- ⚪ **Light-Theme finalisieren oder Dark-only** — Light-Schema nutzt jetzt ebenfalls Blau/Rosé, ist aber weiterhin nicht so durchgetestet wie Dark (Haupt-Look der App).
- ⚪ **Restliche Screens auf neue Komponenten migrieren** — Dashboard und Detail-Screen nutzen bereits `PosterCard`/`MediaBadge`/`GlassSurface`; Lists/ActorDetail/Stats/Profile/About/Auth/Add/Edit erben Farben/Fonts/Shapes global, könnten aber ihre Poster-Grids ebenfalls auf `PosterCard` umstellen.
- ⚪ **Material You (optional)** — Dynamic-Color-Schalter (aktuell bewusst deaktiviert).

---

### Vorgeschlagener Fokus für 1.7.0
1. **Film löschen** (#schneller Abschluss der Admin-Bearbeitung)
2. **Tests** für die in 1.6.1 überarbeitete Sortier-/Repository-Logik
3. Ein „echtes" Feature: **Wunschliste** oder **Listen**

</details>
