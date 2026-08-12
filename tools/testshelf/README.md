# Test-Shelf

Ein Mindest-Nachbau der MovieShelf-API zum Ausprobieren am eigenen Rechner —
gedacht für Fälle, in denen der Abgleich gegen die echte Shelf nicht
nachvollziehbar ist.

Entstanden ist sie an einem Fehler, der ohne sie kaum zu fassen war: „gesehen"
kam nicht auf der Shelf an, obwohl der Aufruf mit `200` quittiert wurde. Der
Grund war, dass `POST /api/movies/{id}/watched` **umschaltet, statt zu setzen**
— stand die Shelf schon auf dem Zielwert, drehte der Aufruf davon weg.

Der Nachbau verhält sich deshalb bewusst genauso. Ein Test-Server, der
stattdessen setzt, würde genau den Fehler verstecken, für den er gebaut wurde.

## Einrichten

Die Tenant-Datenbank wird **kopiert**, nie direkt benutzt — der Abgleich
schreibt, und das darf das Original nie treffen.

```sh
cd tools/testshelf
php seed.php ../../../tenant_rene_11.08.2026.sqlite
```

Das Skript meldet, wie viele Filme drin sind, wie viele davon als gesehen
gelten, und nennt ein paar noch ungesehene Titel zum Ausprobieren.

## Starten

```sh
php -S 0.0.0.0:8000 server.php
```

`0.0.0.0` statt `127.0.0.1`, damit auch ein echtes Gerät im WLAN herankommt.

In der App als Shelf-Adresse eintragen:

| Wo die App läuft | Adresse |
|---|---|
| Android-Emulator | `http://10.0.2.2:8000` (ist bereits der Standardwert) |
| Echtes Gerät im WLAN | `http://<LAN-IP-des-Rechners>:8000` |

Klartext-HTTP ist erlaubt (`network_security_config.xml`), es braucht also
kein Zertifikat. Angemeldet wird mit beliebiger E-Mail und beliebigem
Passwort — `POST /api/login` gibt immer einen Token zurück.

## Was mitläuft

Jeder Aufruf landet in `requests.log`, Umschaltvorgänge mit ihrem Ergebnis:

```
10:32:15 POST /api/movies/631/watched
10:32:15   -> Film 631 ist jetzt ungesehen
```

Das ist der Punkt, an dem sich ablesen lässt, ob die App den Zielzustand
durchsetzt oder nur blind umschaltet.

## Den Delta-Fehler nachstellen

Der Umschalter ändert nur die Zwischentabelle und fasst `movies.updated_at`
nicht an. Der Delta-Export liefert eine geänderte Markierung deshalb nie nach
— was im Web als gesehen markiert wird, erreicht die App nur bei einem
Voll-Abgleich. Der Nachbau macht es standardmäßig genauso.

Um zu sehen, wie es sich mit einem serverseitigen Fix verhielte:

```sh
TOUCH_ON_WATCHED=1 php -S 0.0.0.0:8000 server.php
```

## Grenzen

Bilder, Listen, Darsteller und Staffeln sind nicht nachgebildet — sie
antworten leer, damit der Abgleich durchläuft. Für alles jenseits des
Gesehen-Standes ist die echte Shelf die Referenz.

`shelf.sqlite` und `requests.log` sind Arbeitsdateien und nicht versioniert.
