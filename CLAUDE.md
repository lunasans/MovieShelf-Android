# CLAUDE.md

Leitfaden für die Arbeit an MovieShelf Android.

## Parität mit der Desktop-App

**MovieShelf Android und MovieShelf Desktop sollen nahezu identisch funktionieren.**
Beide sind Oberflächen auf dieselbe Sammlung. Wer zwischen ihnen wechselt, soll
nichts neu lernen müssen — und ein Titel darf nicht anders aussehen, je nachdem,
welche der beiden ihn importiert hat. Die Desktop-Quellen liegen unter
`../desktop`, die Shelf (Server) unter `../v2-saas`.

Was das im Alltag bedeutet:

- **Eine neue Funktion auf einer Seite ist eine Lücke auf der anderen.** Sie
  gehört in die Roadmap der jeweils anderen App ([ROADMAP.md](ROADMAP.md) bzw.
  `../desktop`), statt die beiden stillschweigend auseinanderlaufen zu lassen.
- **Gemeinsame Regeln werden portiert, nicht neu erfunden.** Wo beide Apps
  dieselben Daten auslegen — Jellyfins Laufzeit-Ticks und Freigabe-Alter, die
  Auswahl eines TMDb-Treffers, Duplikaterkennung, Sortierschlüssel —, ist die
  Logik bewusst eine Übertragung der anderen Seite, damit dieselbe Bibliothek
  dieselbe Sammlung ergibt. [JellyfinMapping.kt](app/src/main/java/info/movieshelf/data/jellyfin/JellyfinMapping.kt)
  und `../desktop/electron/handlers/jellyfin.ts` sind ein solches Paar: eine
  Regel dort zu ändern ist nur die halbe Änderung.
- **Zahlen müssen übereinstimmen.** Serien zählen getrennt von Filmen
  (`totalSeries` neben `totalFilms`), Boxsets zählen als ihre Teile. Weicht eine
  Zahl hier von derselben Zahl im Desktop oder in der Web-Shelf ab, ist eine der
  drei falsch.
- **Keine der Apps ist der Maßstab für das Verhalten der API.** Desktop bezieht
  Bewertungen ausschließlich aus `/admin/export`, Android liest zusätzlich
  `GET /movies/{id}`. Dieser Unterschied hat einen Server-Fehler lange verdeckt
  (`user_rating` fehlte in der Einzelantwort). Funktioniert ein Client und der
  andere nicht, ist zuerst der Endpunkt verdächtig, nicht der Client.

Bewusste Unterschiede sind in Ordnung, wo die Plattform es verlangt: ein eigenes
Statistik-Fenster ergibt auf dem Telefon keinen Sinn, eine Tabellenansicht passt
nicht auf einen kleinen Schirm, und die Felder zur physischen Sammlung wiegen am
Desktop schwerer. Solche Fälle gehören als **Entscheidung** festgehalten, damit
sie nicht wie ein Versehen aussehen — siehe den Abschnitt „Bewusst nicht
übernommen" in der [Roadmap](ROADMAP.md).

## Betriebsarten

Die App läuft in zwei Modi, hinterlegt als `SettingKeys.MODE` in der Datenbank
(nicht im DataStore: wird die Datenbank verworfen, ist auch die Frage nach dem
Modus wieder offen):

- **`standalone`** — eigener Bestand, kein Konto, keine Shelf. Alles liegt in der
  lokalen Room-Datenbank.
- **`shelf`** — an eine Shelf gebunden, mit Anmeldung und Abgleich.

Geschrieben wird **immer zuerst lokal**, in beiden Modi. Der Versand zum Server
wird danach versucht und beim nächsten Abgleich nachgeholt, wenn er scheitert.
Das gilt für neue Filme, Bilder, den Gesehen-Stand, Bewertungen und den
Jellyfin-Import gleichermaßen.

## Abgleich

`SyncEngine` holt (`pull`) und schiebt (`push`). Eine Zeile gilt als abweichend,
wenn `syncedAt` fehlt oder `updatedAt` jünger ist.

Zwei Dinge hängen **nicht** an diesem Vergleich, weil sie am Benutzer hängen und
eigene Endpunkte haben — der Gesehen-Stand und die eigene Bewertung. Beide führen
deshalb einen eigenen bestätigten Wert mit (`syncedWatched`, `syncedUserRating`)
und einen eigenen Schritt im Push. Wer hier etwas Drittes dieser Art hinzufügt,
braucht dasselbe Muster: sonst setzt der Film-Push `syncedAt`, die Zeile gilt als
sauber, und die offene Änderung ist beim nächsten Pull still verschwunden.

`NULL` ist bei der Bewertung ein gültiger Wert: „noch nicht bewertet" ist etwas
anderes als null Sterne. In SQL fällt jeder Vergleich mit NULL durch — die
Abfrage offener Bewertungen prüft die NULL-Fälle deshalb ausdrücklich.

## Tests

`./gradlew :app:testDebugUnitTest` — Robolectric plus echte In-Memory-Room-DB.
Getestet wird vor allem, wo ein Fehler **still** wäre: Sortierung, Sync-Erkennung,
Bild-Hosts, die Übersetzung fremder Daten. Ein Absturz fällt von allein auf, eine
falsch übernommene Sammlung nicht.

## Bauen

```bash
./gradlew :app:assembleDebug          # Debug-APK
./gradlew :app:testDebugUnitTest      # Unit-Tests
```
