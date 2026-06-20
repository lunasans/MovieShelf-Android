# MovieShelf Android – Roadmap

## Version 1.7.0 (geplant)

Aktueller Stand: 1.6.1. Die folgenden Punkte sind für 1.7.0 vorgemerkt.
Priorität: 🔴 hoch · 🟡 mittel · ⚪ optional.

### ✨ Features (Backend-Endpunkte existieren bereits)

- 🔴 **Film löschen** — `DELETE /api/admin/movies/{id}` (Admin). Lösch-Button im Edit-Screen mit Bestätigungsdialog; rundet die in 1.6.1 ergänzte Bearbeitung ab. *Aufwand: klein.*
- 🟡 **Cover/Backdrop-Upload** — `POST /api/admin/movies/{id}/cover` & `/backdrop`. Bewusst aus der 1.6.1-Edit-Funktion ausgelassen. Bildauswahl + Multipart-Upload. *Aufwand: mittel.*
- 🟡 **Wunschliste** — Server unterscheidet via `in_collection` zwischen Sammlung und Wunschliste (`MovieWishlistController`). App zeigt aktuell nur `in_collection=true`. Eigene Wunschlisten-Ansicht ergänzen. *Aufwand: mittel.*
- ⚪ **Eigene Listen/Sammlungen** — vollständige Lists-API (`GET/POST/PUT/DELETE /api/lists`) ist vorhanden, in der App fehlt jede UI. Größtes ungenutztes Feature. *Aufwand: groß.*
- ⚪ **Film manuell anlegen** — `POST /api/admin/movies` (ohne TMDb-Import). *Aufwand: klein–mittel.*

### 🔧 Code-Qualität & Robustheit

- 🔴 **Unit-Tests** — App hat aktuell keine Tests. Abdecken: Sortier-Logik (Collator/Artikel/Rating-Komma), `parseRating`, Offline-Pagination im Repository. *Hoher Wert, geringes Risiko.*
- 🟡 **Typisierte TMDb-DTOs** — `AddMovieViewModel`/`MovieDetailViewModel` nutzen `Map<String,Any>` + unchecked Casts. Durch Data-Classes ersetzen.
- ⚪ **Sortierung bei großen Sammlungen** — `applyFiltersAndSort` läuft auf dem Main-Thread; Sort-Keys einmalig vorberechnen und auf `Dispatchers.Default` auslagern.
- ⚪ **EncryptedSharedPreferences-I/O** — erste Entschlüsselung läuft synchron auf dem Main-Thread; auf IO-Dispatcher verschieben.

### 🎨 UX-Feinschliff

- 🟡 **„Ungespeicherte Änderungen"-Warnung** im Edit-Screen beim Zurück-Tippen.
- 🟡 **Fehler-/Retry-Zustände** auf Detail-, Stats- und Actor-Screens (Dashboard hat bereits Offline-Banner + Pull-to-Refresh).
- ⚪ **Logout-Bestätigung** in der Navigationsleiste.
- ⚪ **In-App-Update-Hinweis** — `ServerInfo.version` ist verfügbar; auf neuere Version hinweisen.
- ⚪ **Barrierefreiheit** — fehlende `contentDescription` an Icons ergänzen (TalkBack).

---

### Vorgeschlagener Fokus für 1.7.0
1. **Film löschen** (#schneller Abschluss der Admin-Bearbeitung)
2. **Tests** für die in 1.6.1 überarbeitete Sortier-/Repository-Logik
3. Ein „echtes" Feature: **Wunschliste** oder **Listen**
