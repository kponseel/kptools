<?php
/**
 * Connexion à la base de données et fonctions utilitaires
 */

require_once __DIR__ . '/config.php';

function getDB(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=' . DB_CHARSET;
        $pdo = new PDO($dsn, DB_USER, DB_PASS, [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

/**
 * Trouve une URL par son slug
 */
function findUrlBySlug(string $slug): ?array
{
    $stmt = getDB()->prepare(
        'SELECT * FROM ' . DB_PREFIX . 'urls WHERE slug = :slug LIMIT 1'
    );
    $stmt->execute(['slug' => $slug]);
    $row = $stmt->fetch();
    return $row ?: null;
}

/**
 * Incrémente le compteur de clics et met à jour le dernier clic
 */
function recordClick(int $urlId, string $ip, string $referrer, string $userAgent): void
{
    $db = getDB();

    $stmt = $db->prepare(
        'UPDATE ' . DB_PREFIX . 'urls SET clicks = clicks + 1, last_clicked_at = NOW() WHERE id = :id'
    );
    $stmt->execute(['id' => $urlId]);

    $stmt = $db->prepare(
        'INSERT INTO ' . DB_PREFIX . 'clicks (url_id, clicked_at, ip_address, referrer, user_agent) VALUES (:url_id, NOW(), :ip, :ref, :ua)'
    );
    $stmt->execute([
        'url_id' => $urlId,
        'ip'     => $ip,
        'ref'    => $referrer,
        'ua'     => $userAgent,
    ]);
}

/**
 * Génère un slug aléatoire unique
 */
function generateSlug(int $length = SLUG_LENGTH): string
{
    $chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    $max = strlen($chars) - 1;
    do {
        $slug = '';
        for ($i = 0; $i < $length; $i++) {
            $slug .= $chars[random_int(0, $max)];
        }
    } while (findUrlBySlug($slug) !== null);
    return $slug;
}

/**
 * Valide une URL
 */
function isValidUrl(string $url): bool
{
    return filter_var($url, FILTER_VALIDATE_URL) !== false
        && preg_match('/^https?:\/\//', $url);
}

/**
 * Vérifie si un slug est valide
 */
function isValidSlug(string $slug): bool
{
    return preg_match('/^[a-zA-Z0-9_-]{1,100}$/', $slug) === 1
        && !in_array(strtolower($slug), array_map('strtolower', RESERVED_SLUGS), true);
}

/**
 * Crée un lien court
 */
function createShortUrl(string $originalUrl, string $slug = ''): array
{
    if (!isValidUrl($originalUrl)) {
        return ['success' => false, 'error' => 'URL invalide. Elle doit commencer par http:// ou https://'];
    }

    if ($slug === '') {
        $slug = generateSlug();
    } else {
        if (!isValidSlug($slug)) {
            return ['success' => false, 'error' => 'Slug invalide. Utilisez uniquement des lettres, chiffres, tirets et underscores (max 100 caractères).'];
        }
        if (findUrlBySlug($slug) !== null) {
            return ['success' => false, 'error' => 'Ce slug est déjà utilisé. Choisissez-en un autre.'];
        }
    }

    $stmt = getDB()->prepare(
        'INSERT INTO ' . DB_PREFIX . 'urls (slug, original_url, created_at) VALUES (:slug, :url, NOW())'
    );
    $stmt->execute(['slug' => $slug, 'url' => $originalUrl]);

    return [
        'success'   => true,
        'slug'      => $slug,
        'short_url' => SITE_URL . '/' . $slug,
    ];
}

/**
 * Supprime un lien court
 */
function deleteShortUrl(int $id): bool
{
    $db = getDB();
    $db->prepare('DELETE FROM ' . DB_PREFIX . 'clicks WHERE url_id = :id')->execute(['id' => $id]);
    $stmt = $db->prepare('DELETE FROM ' . DB_PREFIX . 'urls WHERE id = :id');
    $stmt->execute(['id' => $id]);
    return $stmt->rowCount() > 0;
}

/**
 * Liste tous les liens avec pagination et recherche
 */
function listUrls(string $search = '', int $page = 1, int $perPage = 20): array
{
    $db = getDB();
    $offset = ($page - 1) * $perPage;

    $where = '';
    $params = [];
    if ($search !== '') {
        $where = ' WHERE slug LIKE :search OR original_url LIKE :search2';
        $params['search'] = '%' . $search . '%';
        $params['search2'] = '%' . $search . '%';
    }

    $countStmt = $db->prepare('SELECT COUNT(*) FROM ' . DB_PREFIX . 'urls' . $where);
    $countStmt->execute($params);
    $total = (int)$countStmt->fetchColumn();

    $stmt = $db->prepare(
        'SELECT * FROM ' . DB_PREFIX . 'urls' . $where . ' ORDER BY created_at DESC LIMIT :limit OFFSET :offset'
    );
    foreach ($params as $k => $v) {
        $stmt->bindValue($k, $v);
    }
    $stmt->bindValue('limit', $perPage, PDO::PARAM_INT);
    $stmt->bindValue('offset', $offset, PDO::PARAM_INT);
    $stmt->execute();

    return [
        'urls'     => $stmt->fetchAll(),
        'total'    => $total,
        'page'     => $page,
        'per_page' => $perPage,
        'pages'    => (int)ceil($total / $perPage),
    ];
}
