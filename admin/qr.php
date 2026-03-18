<?php
/**
 * Génération de QR Code pour un slug donné
 */

require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/../lib/phpqrcode.php';

requireAuth();

$slug = $_GET['slug'] ?? '';

if ($slug === '') {
    http_response_code(400);
    echo 'Slug manquant.';
    exit;
}

$url = SITE_URL . '/' . $slug;
$download = isset($_GET['download']);

if ($download) {
    header('Content-Type: image/png');
    header('Content-Disposition: attachment; filename="qr-' . preg_replace('/[^a-zA-Z0-9_-]/', '', $slug) . '.png"');
} else {
    header('Content-Type: image/png');
    header('Cache-Control: public, max-age=86400');
}

QRcode::png($url, false, QR_ECLEVEL_M, 8, 2);
