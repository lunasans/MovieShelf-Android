<?php
/**
 * Test-Shelf: ein Mindest-Nachbau der MovieShelf-API zum Ausprobieren am
 * eigenen Rechner.
 *
 * Zweck ist nicht, die Shelf zu ersetzen, sondern den Abgleich der App
 * beobachtbar zu machen — vor allem den "gesehen"-Stand, dessen Uebertragung
 * gegen die echte Shelf nur schwer nachzuvollziehen ist.
 *
 * Wichtig fuer die Aussagekraft: `POST /api/movies/{id}/watched` **schaltet
 * um**, genau wie das Original (MovieController::toggleWatched). Ein Nachbau,
 * der stattdessen setzt, wuerde den Fehler verstecken, um dessentwillen dieses
 * Werkzeug entstanden ist.
 *
 * Start (siehe README.md):
 *   php -S 0.0.0.0:8000 server.php
 *
 * Gearbeitet wird auf einer **Kopie** der Tenant-Datenbank; das Original
 * bleibt unangetastet.
 */

declare(strict_types=1);

const DB_FILE   = __DIR__ . '/shelf.sqlite';
const USER_ID   = 1;
const TOKEN     = 'testshelf-token';
const LOG_FILE  = __DIR__ . '/requests.log';

// ── Grundgeruest ─────────────────────────────────────────────────────────────

$method = $_SERVER['REQUEST_METHOD'];
$path   = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?? '/';
$body   = json_decode(file_get_contents('php://input') ?: '[]', true) ?: [];

if (! file_exists(DB_FILE)) {
    fail(500, 'Keine Datenbank. Zuerst "php seed.php <pfad-zur-tenant.sqlite>" ausfuehren.');
}

$db = new PDO('sqlite:' . DB_FILE);
$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$db->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);

// Jeder Aufruf wird protokolliert — das ist der halbe Sinn der Sache.
logLine(sprintf('%s %s %s', $method, $path, $body ? json_encode($body) : ''));

// ── Weiche ───────────────────────────────────────────────────────────────────

try {
    if ($path === '/api/info') {
        respond(['version' => 'testshelf', 'shelf_version' => '0.0.0-test']);
    }

    if ($path === '/api/login' && $method === 'POST') {
        respond([
            'token' => TOKEN,
            'user'  => ['id' => USER_ID, 'name' => 'Test', 'email' => $body['email'] ?? 'test@example.com'],
        ]);
    }

    requireToken();

    if ($path === '/api/user') {
        respond(['id' => USER_ID, 'name' => 'Test', 'email' => 'test@example.com']);
    }

    if ($path === '/api/admin/export' && $method === 'GET') {
        exportMovies($db);
    }

    // Umschalter — bewusst mit demselben Verhalten wie die echte Shelf.
    if (preg_match('#^/api/movies/(\d+)/watched$#', $path, $m) && $method === 'POST') {
        toggleWatched($db, (int) $m[1]);
    }

    if (preg_match('#^/api/movies/(\d+)$#', $path, $m) && $method === 'GET') {
        $movie = movieById($db, (int) $m[1]);
        $movie ? respond(['data' => $movie]) : fail(404, 'Nicht gefunden');
    }

    if (preg_match('#^/api/admin/movies/(\d+)$#', $path, $m) && $method === 'PUT') {
        updateMovie($db, (int) $m[1], $body);
    }

    if (preg_match('#^/api/admin/movies/(\d+)$#', $path, $m) && $method === 'DELETE') {
        $db->prepare('UPDATE movies SET is_deleted = 1, updated_at = ? WHERE id = ?')
           ->execute([now(), (int) $m[1]]);
        respond(['message' => 'geloescht']);
    }

    if ($path === '/api/admin/movies' && $method === 'POST') {
        createMovie($db, $body);
    }

    // Listen und Staffeln spielen fuer den "gesehen"-Test keine Rolle: leere
    // Antworten halten den Abgleich still, statt ihn mit 404 zu behelligen.
    if ($path === '/api/lists')  respond(['data' => []]);
    if ($path === '/api/tags')   respond(['data' => []]);
    if ($path === '/api/actors') respond(['data' => []]);
    if (preg_match('#^/api/admin/movies/\d+/seasons#', $path)) respond(['data' => []]);

    fail(404, "Kein Endpunkt fuer $method $path");
} catch (Throwable $e) {
    logLine('FEHLER: ' . $e->getMessage());
    fail(500, $e->getMessage());
}

// ── Endpunkte ────────────────────────────────────────────────────────────────

/**
 * Der Umschalter. Liefert wie das Original den **neuen** Stand zurueck.
 */
function toggleWatched(PDO $db, int $movieId): void
{
    $exists = $db->prepare('SELECT COUNT(*) FROM movie_user_watched WHERE user_id = ? AND movie_id = ?');
    $exists->execute([USER_ID, $movieId]);

    if ((int) $exists->fetchColumn() > 0) {
        $db->prepare('DELETE FROM movie_user_watched WHERE user_id = ? AND movie_id = ?')
           ->execute([USER_ID, $movieId]);
        $watched = false;
    } else {
        $db->prepare('INSERT INTO movie_user_watched (user_id, movie_id, created_at, updated_at) VALUES (?, ?, ?, ?)')
           ->execute([USER_ID, $movieId, now(), now()]);
        $watched = true;
    }

    // Genau hier sitzt die Eigenheit, die den Delta-Abgleich betrifft: die
    // Zwischentabelle aendert sich, `movies.updated_at` nicht. Wer das
    // nachstellen will, laesst diese Zeile aus (Standard); wer die Wirkung
    // eines serverseitigen Fixes sehen will, setzt TOUCH_ON_WATCHED=1.
    if (getenv('TOUCH_ON_WATCHED') === '1') {
        $db->prepare('UPDATE movies SET updated_at = ? WHERE id = ?')->execute([now(), $movieId]);
    }

    logLine(sprintf('  -> Film %d ist jetzt %s', $movieId, $watched ? 'GESEHEN' : 'ungesehen'));

    respond([
        'message'    => $watched ? 'Movie marked as watched' : 'Movie marked as unwatched',
        'is_watched' => $watched,
    ]);
}

function exportMovies(PDO $db): void
{
    $since      = $_GET['since'] ?? null;
    $exportedAt = now();

    $sql = 'SELECT * FROM movies';
    $args = [];
    if ($since) {
        $sql .= ' WHERE updated_at >= ?';
        $args[] = normalizeStamp($since);
    }
    $sql .= ' ORDER BY title';

    $rows = $db->prepare($sql);
    $rows->execute($args);
    $movies = array_map(fn($row) => presentMovie($db, $row), $rows->fetchAll());

    logLine(sprintf('  -> Export: %d Filme%s', count($movies), $since ? " seit $since" : ' (voll)'));

    respond([
        'exported_at' => $exportedAt,
        'is_delta'    => (bool) $since,
        'since'       => $since,
        'count'       => count($movies),
        'movies'      => $movies,
    ]);
}

function updateMovie(PDO $db, int $id, array $body): void
{
    $erlaubt = [
        'title', 'year', 'genre', 'overview', 'runtime', 'director', 'rating',
        'rating_age', 'tag', 'trailer_url', 'edition', 'region_code',
        'disc_location', 'purchase_date', 'condition', 'collection_type',
        'in_collection', 'view_count',
    ];

    $sets = [];
    $args = [];
    foreach ($erlaubt as $feld) {
        if (array_key_exists($feld, $body)) {
            $sets[] = "$feld = ?";
            $args[] = is_bool($body[$feld]) ? (int) $body[$feld] : $body[$feld];
        }
    }
    $sets[] = 'updated_at = ?';
    $args[] = now();
    $args[] = $id;

    $db->prepare('UPDATE movies SET ' . implode(', ', $sets) . ' WHERE id = ?')->execute($args);
    respond(['data' => movieById($db, $id)]);
}

function createMovie(PDO $db, array $body): void
{
    $db->prepare(
        'INSERT INTO movies (title, year, collection_type, in_collection, is_deleted, created_at, updated_at)
         VALUES (?, ?, ?, 1, 0, ?, ?)'
    )->execute([
        $body['title'] ?? 'Ohne Titel',
        $body['year'] ?? null,
        $body['collection_type'] ?? 'Film',
        now(),
        now(),
    ]);

    respond(['data' => movieById($db, (int) $db->lastInsertId())]);
}

// ── Darstellung ──────────────────────────────────────────────────────────────

function movieById(PDO $db, int $id): ?array
{
    $row = $db->prepare('SELECT * FROM movies WHERE id = ?');
    $row->execute([$id]);
    $found = $row->fetch();

    return $found ? presentMovie($db, $found) : null;
}

/** Nachbau von MovieResource — nur die Felder, die die App auswertet. */
function presentMovie(PDO $db, array $row): array
{
    $watched = $db->prepare('SELECT COUNT(*) FROM movie_user_watched WHERE user_id = ? AND movie_id = ?');
    $watched->execute([USER_ID, $row['id']]);

    $children = $db->prepare('SELECT COUNT(*) FROM movies WHERE boxset_parent = ? AND is_deleted = 0');
    $children->execute([$row['id']]);

    return [
        'id'               => (int) $row['id'],
        'item_type'        => 'movie',
        'title'            => $row['title'],
        'year'             => $row['year'] !== null ? (int) $row['year'] : null,
        'rating'           => $row['rating'],
        'rating_age'       => (int) ($row['rating_age'] ?? 0),
        'genre'            => $row['genre'],
        'tag'              => $row['tag'],
        'overview'         => $row['overview'],
        'runtime'          => $row['runtime'] !== null ? (int) $row['runtime'] : null,
        'director'         => $row['director'],
        // Bilder bleiben aus: sie sind fuer den "gesehen"-Test ohne Belang und
        // wuerden nur ins Leere zeigen.
        'cover_url'        => null,
        'backdrop_url'     => null,
        'trailer_url'      => $row['trailer_url'],
        'edition'          => $row['edition'],
        'region_code'      => $row['region_code'],
        'disc_location'    => $row['disc_location'],
        'purchase_date'    => $row['purchase_date'],
        'purchase_price'   => $row['purchase_price'] !== null ? (float) $row['purchase_price'] : null,
        'condition'        => $row['condition'],
        'view_count'       => (int) ($row['view_count'] ?? 0),
        'is_watched'       => ((int) $watched->fetchColumn()) > 0,
        'is_boxset'        => ((int) $children->fetchColumn()) > 0,
        'boxset_parent_id' => $row['boxset_parent'] !== null ? (int) $row['boxset_parent'] : null,
        'tmdb_id'          => $row['tmdb_id'],
        'collection_type'  => $row['collection_type'],
        'is_deleted'       => (bool) $row['is_deleted'],
        'in_collection'    => (bool) ($row['in_collection'] ?? 1),
        'created_at'       => stamp($row['created_at']),
        'updated_at'       => stamp($row['updated_at'] ?: $row['created_at']),
    ];
}

// ── Kleinkram ────────────────────────────────────────────────────────────────

function requireToken(): void
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (! str_contains($header, TOKEN)) {
        // Die App darf ruhig irgendeinen Token schicken; abgelehnt wird nur,
        // wer gar keinen mitbringt — sonst laesst sich schwer ausprobieren.
        if (trim($header) === '') {
            fail(401, 'Nicht angemeldet');
        }
    }
}

function now(): string
{
    return gmdate('Y-m-d\TH:i:s.000000\Z');
}

/** Laravel liefert ISO-8601; SQLite haelt "Y-m-d H:i:s". */
function stamp(?string $value): ?string
{
    if (! $value) return null;
    $time = strtotime($value);

    return $time ? gmdate('Y-m-d\TH:i:s.000000\Z', $time) : $value;
}

function normalizeStamp(string $value): string
{
    $time = strtotime($value);

    return $time ? gmdate('Y-m-d H:i:s', $time) : $value;
}

function respond(array $payload): void
{
    header('Content-Type: application/json');
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function fail(int $status, string $message): void
{
    http_response_code($status);
    header('Content-Type: application/json');
    echo json_encode(['message' => $message]);
    exit;
}

function logLine(string $line): void
{
    file_put_contents(LOG_FILE, date('H:i:s') . ' ' . $line . PHP_EOL, FILE_APPEND);
}
