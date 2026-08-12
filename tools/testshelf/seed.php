<?php
/**
 * Legt die Arbeitskopie fuer die Test-Shelf an.
 *
 * Die Tenant-Datenbank wird kopiert, nicht benutzt: der Abgleich schreibt,
 * und das darf niemals das Original treffen.
 *
 *   php seed.php ..\..\..\tenant_rene_11.08.2026.sqlite
 */

declare(strict_types=1);

$quelle = $argv[1] ?? null;
$ziel   = __DIR__ . '/shelf.sqlite';

if (! $quelle || ! file_exists($quelle)) {
    fwrite(STDERR, "Aufruf: php seed.php <pfad-zur-tenant.sqlite>\n");
    exit(1);
}

if (realpath($quelle) === realpath($ziel)) {
    fwrite(STDERR, "Quelle und Ziel sind dieselbe Datei — abgebrochen.\n");
    exit(1);
}

if (! copy($quelle, $ziel)) {
    fwrite(STDERR, "Kopieren fehlgeschlagen.\n");
    exit(1);
}

$db = new PDO('sqlite:' . $ziel);
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);

$zahl = fn(string $sql) => (int) $db->query($sql)->fetchColumn();

echo "Kopie angelegt: shelf.sqlite\n";
echo '  Filme:              ' . $zahl('SELECT COUNT(*) FROM movies WHERE is_deleted = 0 AND in_collection = 1') . "\n";
echo '  davon gesehen (U1): ' . $zahl('SELECT COUNT(*) FROM movie_user_watched WHERE user_id = 1') . "\n";

// Ein paar ungesehene Filme nennen: an ihnen laesst sich das Markieren
// ausprobieren, ohne erst suchen zu muessen.
$offen = $db->query(
    'SELECT m.id, m.title FROM movies m
     WHERE m.is_deleted = 0 AND m.in_collection = 1
       AND NOT EXISTS (SELECT 1 FROM movie_user_watched w WHERE w.movie_id = m.id AND w.user_id = 1)
     ORDER BY m.title LIMIT 5'
)->fetchAll();

if ($offen) {
    echo "\nNoch nicht gesehen — gut zum Ausprobieren:\n";
    foreach ($offen as $film) {
        echo "  #{$film['id']}  {$film['title']}\n";
    }
} else {
    echo "\nAlles bereits als gesehen markiert.\n";
}

echo "\nWeiter mit: php -S 0.0.0.0:8000 server.php\n";
