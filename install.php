<?php
/**
 * Script d'installation — Crée les tables MySQL nécessaires
 * SUPPRIMEZ CE FICHIER APRÈS L'INSTALLATION !
 */

require_once __DIR__ . '/config.php';

$errors = [];
$success = false;

try {
    $dsn = 'mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=' . DB_CHARSET;
    $pdo = new PDO($dsn, DB_USER, DB_PASS, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    ]);

    // Table des URLs raccourcies
    $pdo->exec('CREATE TABLE IF NOT EXISTS ' . DB_PREFIX . 'urls (
        id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        slug VARCHAR(100) NOT NULL UNIQUE,
        original_url TEXT NOT NULL,
        clicks INT UNSIGNED DEFAULT 0,
        created_at DATETIME NOT NULL,
        last_clicked_at DATETIME DEFAULT NULL,
        INDEX idx_slug (slug)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci');

    // Table des clics (analytics)
    $pdo->exec('CREATE TABLE IF NOT EXISTS ' . DB_PREFIX . 'clicks (
        id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
        url_id INT UNSIGNED NOT NULL,
        clicked_at DATETIME NOT NULL,
        ip_address VARCHAR(45) DEFAULT NULL,
        referrer TEXT DEFAULT NULL,
        user_agent TEXT DEFAULT NULL,
        INDEX idx_url_id (url_id),
        INDEX idx_clicked_at (clicked_at),
        FOREIGN KEY (url_id) REFERENCES ' . DB_PREFIX . 'urls(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci');

    $success = true;
} catch (PDOException $e) {
    $errors[] = 'Erreur de connexion à la base de données : ' . htmlspecialchars($e->getMessage());
}
?>
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Installation — kev.ovh</title>
    <style>
        body { font-family: system-ui, -apple-system, sans-serif; background: #0f172a; color: #e2e8f0; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }
        .card { background: #1e293b; border-radius: 12px; padding: 2rem; max-width: 500px; width: 90%; box-shadow: 0 4px 24px rgba(0,0,0,0.3); }
        h1 { margin-top: 0; color: #f8fafc; font-size: 1.5rem; }
        .success { background: #065f46; border: 1px solid #10b981; border-radius: 8px; padding: 1rem; margin: 1rem 0; }
        .error { background: #7f1d1d; border: 1px solid #f87171; border-radius: 8px; padding: 1rem; margin: 1rem 0; }
        .warning { background: #78350f; border: 1px solid #f59e0b; border-radius: 8px; padding: 1rem; margin: 1rem 0; }
        code { background: #334155; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }
        a { color: #60a5fa; }
    </style>
</head>
<body>
    <div class="card">
        <h1>Installation de kev.ovh</h1>
        <?php if ($success): ?>
            <div class="success">
                <strong>Installation réussie !</strong><br>
                Les tables <code><?= DB_PREFIX ?>urls</code> et <code><?= DB_PREFIX ?>clicks</code> ont été créées.
            </div>
            <div class="warning">
                <strong>Important :</strong> Supprimez ce fichier (<code>install.php</code>) immédiatement pour des raisons de sécurité !
            </div>
            <p><a href="/admin/">Accéder au panneau d'administration &rarr;</a></p>
        <?php else: ?>
            <?php foreach ($errors as $error): ?>
                <div class="error"><?= $error ?></div>
            <?php endforeach; ?>
            <p>Vérifiez les paramètres dans <code>config.php</code> et réessayez.</p>
        <?php endif; ?>
    </div>
</body>
</html>
