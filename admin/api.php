<?php
/**
 * Endpoints AJAX pour les opérations CRUD
 */

require_once __DIR__ . '/auth.php';
require_once __DIR__ . '/../db.php';

requireAuth();

header('Content-Type: application/json; charset=utf-8');

// GET — Liste des liens
if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    $action = $_GET['action'] ?? '';

    if ($action === 'list') {
        $search = $_GET['search'] ?? '';
        $page = max(1, (int)($_GET['page'] ?? 1));
        echo json_encode(listUrls($search, $page));
        exit;
    }

    http_response_code(400);
    echo json_encode(['error' => 'Action inconnue.']);
    exit;
}

// POST — Création / Suppression
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $input = json_decode(file_get_contents('php://input'), true);

    if (!$input || !isset($input['action'])) {
        http_response_code(400);
        echo json_encode(['error' => 'Requête invalide.']);
        exit;
    }

    switch ($input['action']) {
        case 'create':
            $originalUrl = trim($input['original_url'] ?? '');
            $slug = trim($input['slug'] ?? '');
            echo json_encode(createShortUrl($originalUrl, $slug));
            break;

        case 'delete':
            $id = (int)($input['id'] ?? 0);
            if ($id <= 0) {
                echo json_encode(['success' => false, 'error' => 'ID invalide.']);
                break;
            }
            $deleted = deleteShortUrl($id);
            echo json_encode(['success' => $deleted, 'error' => $deleted ? null : 'Lien introuvable.']);
            break;

        default:
            http_response_code(400);
            echo json_encode(['error' => 'Action inconnue.']);
    }
    exit;
}

http_response_code(405);
echo json_encode(['error' => 'Méthode non autorisée.']);
