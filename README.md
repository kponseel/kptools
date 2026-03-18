# kev.ovh — Raccourcisseur d'URL personnel

Raccourcisseur d'URL personnel hébergé sur OVH Shared Hosting (PHP + MySQL).

## Fonctionnalités

- Raccourcir des URLs longues en liens courts (`kev.ovh/abc123`)
- Slugs personnalisés (`kev.ovh/mon-projet`) ou auto-générés (6 caractères)
- Redirection 301 vers l'URL originale
- Tableau de bord admin protégé par mot de passe
- Suivi des clics (compteur, horodatage, referrer, IP, user-agent)
- Génération de QR codes (bibliothèque PHP intégrée, sans API externe)

## Déploiement

### 1. Configuration

Modifiez le fichier `config.php` avec vos paramètres :

```php
define('DB_HOST', 'votre-serveur.mysql.db');  // Hôte MySQL OVH
define('DB_NAME', 'votre_base');               // Nom de la base
define('DB_USER', 'votre_utilisateur');         // Utilisateur MySQL
define('DB_PASS', 'votre_mot_de_passe');       // Mot de passe MySQL
```

### 2. Mot de passe admin

Générez un hash de mot de passe en créant un fichier PHP temporaire :

```php
<?php echo password_hash('votre_mot_de_passe_secret', PASSWORD_DEFAULT);
```

Accédez-y via votre navigateur, copiez le hash affiché, puis collez-le dans `config.php` :

```php
define('ADMIN_PASSWORD_HASH', '$2y$10$votre_hash_ici');
```

Supprimez le fichier temporaire après utilisation.

### 3. Upload FTP

Uploadez tous les fichiers vers la racine web de votre hébergement OVH :

```
/www/                         ← racine web OVH
├── .htaccess
├── index.php
├── config.php
├── db.php
├── install.php
├── admin/
│   ├── index.php
│   ├── auth.php
│   ├── api.php
│   ├── qr.php
│   └── style.css
└── lib/
    └── phpqrcode.php
```

### 4. Installation des tables MySQL

Accédez à `https://kev.ovh/install.php` dans votre navigateur.

Le script créera automatiquement les tables nécessaires dans votre base de données.

**Important : supprimez le fichier `install.php` après l'installation !**

### 5. Vérification

Accédez à `https://kev.ovh/admin/` et connectez-vous avec votre mot de passe.

## Utilisation du panneau admin

1. **Créer un lien** : Entrez l'URL originale et optionnellement un slug personnalisé, puis cliquez sur "Créer le lien"
2. **Copier un lien** : Cliquez sur "Copier" à côté d'un lien existant
3. **Voir le QR code** : Cliquez sur "QR" pour afficher et télécharger le QR code
4. **Supprimer un lien** : Cliquez sur "Suppr." pour supprimer un lien
5. **Rechercher** : Utilisez la barre de recherche pour filtrer par slug ou URL

## Changer le mot de passe admin

1. Générez un nouveau hash avec `password_hash('nouveau_mdp', PASSWORD_DEFAULT)`
2. Remplacez la valeur de `ADMIN_PASSWORD_HASH` dans `config.php`
3. Uploadez le fichier modifié via FTP

## Schéma de la base de données

### Table `short_urls`

| Colonne          | Type             | Description                        |
|------------------|------------------|------------------------------------|
| `id`             | INT UNSIGNED     | Identifiant unique (auto-increment)|
| `slug`           | VARCHAR(100)     | Slug du lien court (unique)        |
| `original_url`   | TEXT             | URL originale                      |
| `clicks`         | INT UNSIGNED     | Nombre total de clics              |
| `created_at`     | DATETIME         | Date de création                   |
| `last_clicked_at`| DATETIME         | Date du dernier clic               |

### Table `short_clicks`

| Colonne      | Type            | Description                          |
|--------------|-----------------|--------------------------------------|
| `id`         | BIGINT UNSIGNED | Identifiant unique (auto-increment)  |
| `url_id`     | INT UNSIGNED    | Référence vers `short_urls.id`       |
| `clicked_at` | DATETIME        | Horodatage du clic                   |
| `ip_address` | VARCHAR(45)     | Adresse IP du visiteur               |
| `referrer`   | TEXT            | Page d'origine (referrer HTTP)       |
| `user_agent` | TEXT            | Agent utilisateur du navigateur      |

## Contraintes techniques

- PHP 8.x compatible
- MySQL avec InnoDB
- mod_rewrite activé (`.htaccess`)
- Aucune dépendance externe (pas de Composer, pas de CDN)
- Interface admin 100% autonome et responsive
