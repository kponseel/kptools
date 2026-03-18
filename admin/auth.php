<?php
/**
 * Gestion de l'authentification admin
 */

require_once __DIR__ . '/../config.php';

function startAdminSession(): void
{
    if (session_status() === PHP_SESSION_NONE) {
        session_name(SESSION_NAME);
        session_start();
    }
}

function isLoggedIn(): bool
{
    startAdminSession();
    if (!isset($_SESSION['admin_logged_in']) || $_SESSION['admin_logged_in'] !== true) {
        return false;
    }
    if (isset($_SESSION['admin_last_activity']) && (time() - $_SESSION['admin_last_activity']) > SESSION_TIMEOUT) {
        logoutAdmin();
        return false;
    }
    $_SESSION['admin_last_activity'] = time();
    return true;
}

function loginAdmin(string $password): bool
{
    startAdminSession();
    if (password_verify($password, ADMIN_PASSWORD_HASH)) {
        session_regenerate_id(true);
        $_SESSION['admin_logged_in'] = true;
        $_SESSION['admin_last_activity'] = time();
        return true;
    }
    return false;
}

function logoutAdmin(): void
{
    startAdminSession();
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $p = session_get_cookie_params();
        setcookie(session_name(), '', time() - 42000, $p['path'], $p['domain'], $p['secure'], $p['httponly']);
    }
    session_destroy();
}

function requireAuth(): void
{
    if (!isLoggedIn()) {
        if (!empty($_SERVER['HTTP_X_REQUESTED_WITH']) && strtolower($_SERVER['HTTP_X_REQUESTED_WITH']) === 'xmlhttprequest') {
            http_response_code(401);
            echo json_encode(['error' => 'Session expirée. Veuillez vous reconnecter.']);
            exit;
        }
        header('Location: /admin/');
        exit;
    }
}
