<?php
/**
 * Moteur de redirection public
 * Reçoit un slug via .htaccess et redirige vers l'URL originale
 */

$slug = $_GET['slug'] ?? '';

if ($slug === '') {
    // Page d'accueil — rediriger vers l'admin ou afficher une page vide
    header('Location: /admin/', true, 302);
    exit;
}

require_once __DIR__ . '/db.php';

$url = findUrlBySlug($slug);

if ($url === null) {
    http_response_code(404);
    echo '<!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"><title>404 - Lien introuvable</title>';
    echo '<style>body{font-family:system-ui,sans-serif;background:#0f172a;color:#e2e8f0;display:flex;justify-content:center;align-items:center;min-height:100vh;margin:0}';
    echo '.box{text-align:center;padding:2rem}.box h1{font-size:4rem;margin:0;color:#f87171}.box p{color:#94a3b8;margin-top:1rem}</style></head>';
    echo '<body><div class="box"><h1>404</h1><p>Ce lien court n\'existe pas.</p></div></body></html>';
    exit;
}

// Enregistrer le clic
$ip = $_SERVER['REMOTE_ADDR'] ?? '';
$referrer = $_SERVER['HTTP_REFERER'] ?? '';
$userAgent = $_SERVER['HTTP_USER_AGENT'] ?? '';
recordClick($url['id'], $ip, $referrer, $userAgent);

// Redirection 301
header('Location: ' . $url['original_url'], true, 301);
header('Cache-Control: no-cache, no-store, must-revalidate');
exit;
