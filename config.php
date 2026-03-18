<?php
/**
 * Configuration du raccourcisseur d'URL kev.ovh
 *
 * Modifiez les valeurs ci-dessous avant le déploiement.
 */

// --- Connexion MySQL ---
define('DB_HOST', 'localhost');           // Hôte MySQL (fourni par OVH)
define('DB_NAME', 'votre_base');         // Nom de la base de données
define('DB_USER', 'votre_utilisateur');   // Utilisateur MySQL
define('DB_PASS', 'votre_mot_de_passe'); // Mot de passe MySQL
define('DB_CHARSET', 'utf8mb4');

// --- Préfixe des tables ---
define('DB_PREFIX', 'short_');

// --- Mot de passe admin ---
// Pour générer un nouveau hash, exécutez dans un fichier PHP temporaire :
//   echo password_hash('votre_mot_de_passe', PASSWORD_DEFAULT);
// Puis collez le résultat ci-dessous.
define('ADMIN_PASSWORD_HASH', '$2y$10$PLACEHOLDER_HASH_CHANGE_ME');

// --- Paramètres de session ---
define('SESSION_TIMEOUT', 3600); // Durée de session en secondes (1 heure)
define('SESSION_NAME', 'kev_ovh_admin');

// --- URL du site ---
define('SITE_URL', 'https://kev.ovh');

// --- Longueur du slug auto-généré ---
define('SLUG_LENGTH', 6);

// --- Slugs réservés (ne peuvent pas être utilisés) ---
define('RESERVED_SLUGS', ['admin', 'install', 'lib', 'config', 'db']);
