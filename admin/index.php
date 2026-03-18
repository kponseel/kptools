<?php
/**
 * Panneau d'administration — kev.ovh
 */

require_once __DIR__ . '/auth.php';

// Gestion de la déconnexion
if (isset($_GET['logout'])) {
    logoutAdmin();
    header('Location: /admin/');
    exit;
}

// Gestion du formulaire de connexion
$loginError = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['password'])) {
    if (loginAdmin($_POST['password'])) {
        header('Location: /admin/');
        exit;
    }
    $loginError = 'Mot de passe incorrect.';
}

$loggedIn = isLoggedIn();
?>
<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Admin — kev.ovh</title>
    <link rel="stylesheet" href="/admin/style.css">
</head>
<body>
<?php if (!$loggedIn): ?>
    <div class="login-container">
        <div class="login-card">
            <h1>kev.ovh</h1>
            <p class="subtitle">Raccourcisseur d'URL</p>
            <?php if ($loginError): ?>
                <div class="alert alert-error"><?= htmlspecialchars($loginError) ?></div>
            <?php endif; ?>
            <form method="POST" autocomplete="off">
                <div class="form-group">
                    <label for="password">Mot de passe</label>
                    <input type="password" id="password" name="password" required autofocus placeholder="Entrez le mot de passe admin">
                </div>
                <button type="submit" class="btn btn-primary btn-full">Se connecter</button>
            </form>
        </div>
    </div>
<?php else: ?>
    <div class="app">
        <header class="header">
            <div class="header-left">
                <h1>kev.ovh</h1>
                <span class="badge">Admin</span>
            </div>
            <a href="/admin/?logout" class="btn btn-ghost">Déconnexion</a>
        </header>

        <main class="main">
            <!-- Formulaire de création -->
            <section class="card card-create">
                <h2>Nouveau lien court</h2>
                <form id="createForm" class="create-form">
                    <div class="form-row">
                        <div class="form-group flex-grow">
                            <label for="original_url">URL originale</label>
                            <input type="url" id="original_url" name="original_url" required placeholder="https://exemple.com/page-tres-longue">
                        </div>
                        <div class="form-group">
                            <label for="custom_slug">Slug personnalisé <span class="optional">(optionnel)</span></label>
                            <div class="slug-input">
                                <span class="slug-prefix">kev.ovh/</span>
                                <input type="text" id="custom_slug" name="custom_slug" placeholder="mon-lien" pattern="[a-zA-Z0-9_-]+">
                            </div>
                        </div>
                    </div>
                    <button type="submit" class="btn btn-primary">Créer le lien</button>
                </form>
                <div id="createResult" class="create-result hidden"></div>
            </section>

            <!-- Recherche et liste -->
            <section class="card">
                <div class="list-header">
                    <h2>Liens existants</h2>
                    <div class="search-box">
                        <input type="text" id="searchInput" placeholder="Rechercher un slug ou une URL..." autocomplete="off">
                    </div>
                </div>
                <div id="linksTable" class="table-container">
                    <p class="loading">Chargement...</p>
                </div>
                <div id="pagination" class="pagination"></div>
            </section>
        </main>
    </div>

    <!-- Modal QR Code -->
    <div id="qrModal" class="modal hidden">
        <div class="modal-backdrop"></div>
        <div class="modal-content">
            <div class="modal-header">
                <h3>QR Code</h3>
                <button class="modal-close" onclick="closeQrModal()">&times;</button>
            </div>
            <div class="modal-body">
                <div id="qrImage"></div>
                <p id="qrSlug" class="qr-slug"></p>
                <a id="qrDownload" class="btn btn-primary" download="qrcode.png">Télécharger le QR Code</a>
            </div>
        </div>
    </div>

    <!-- Toast notification -->
    <div id="toast" class="toast hidden"></div>

    <script>
    const SITE_URL = '<?= SITE_URL ?>';
    let currentPage = 1;
    let searchTimeout = null;

    // --- Création de lien ---
    document.getElementById('createForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const url = document.getElementById('original_url').value.trim();
        const slug = document.getElementById('custom_slug').value.trim();
        const resultDiv = document.getElementById('createResult');

        try {
            const resp = await fetch('/admin/api.php', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'},
                body: JSON.stringify({action: 'create', original_url: url, slug: slug})
            });
            const data = await resp.json();

            if (data.success) {
                resultDiv.className = 'create-result success';
                resultDiv.innerHTML = `
                    <span class="result-url">${escapeHtml(data.short_url)}</span>
                    <button class="btn btn-sm" onclick="copyToClipboard('${escapeHtml(data.short_url)}')">Copier</button>
                `;
                document.getElementById('createForm').reset();
                loadLinks();
            } else {
                resultDiv.className = 'create-result error';
                resultDiv.textContent = data.error;
            }
        } catch (err) {
            resultDiv.className = 'create-result error';
            resultDiv.textContent = 'Erreur de connexion au serveur.';
        }
    });

    // --- Recherche ---
    document.getElementById('searchInput').addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            currentPage = 1;
            loadLinks();
        }, 300);
    });

    // --- Chargement des liens ---
    async function loadLinks(page = 1) {
        currentPage = page;
        const search = document.getElementById('searchInput').value.trim();
        const container = document.getElementById('linksTable');

        try {
            const params = new URLSearchParams({action: 'list', page: page, search: search});
            const resp = await fetch('/admin/api.php?' + params, {
                headers: {'X-Requested-With': 'XMLHttpRequest'}
            });
            const data = await resp.json();

            if (!data.urls || data.urls.length === 0) {
                container.innerHTML = '<p class="empty">Aucun lien trouvé.</p>';
                document.getElementById('pagination').innerHTML = '';
                return;
            }

            let html = `<table>
                <thead><tr>
                    <th>Slug</th>
                    <th>URL originale</th>
                    <th>Clics</th>
                    <th>Créé le</th>
                    <th>Dernier clic</th>
                    <th>Actions</th>
                </tr></thead><tbody>`;

            data.urls.forEach(link => {
                const shortUrl = SITE_URL + '/' + link.slug;
                const origUrl = link.original_url.length > 60
                    ? link.original_url.substring(0, 60) + '...'
                    : link.original_url;
                const lastClick = link.last_clicked_at || '—';
                html += `<tr>
                    <td class="slug-cell"><code>${escapeHtml(link.slug)}</code></td>
                    <td class="url-cell" title="${escapeHtml(link.original_url)}"><a href="${escapeHtml(link.original_url)}" target="_blank" rel="noopener">${escapeHtml(origUrl)}</a></td>
                    <td class="clicks-cell">${link.clicks}</td>
                    <td class="date-cell">${formatDate(link.created_at)}</td>
                    <td class="date-cell">${lastClick !== '—' ? formatDate(lastClick) : '—'}</td>
                    <td class="actions-cell">
                        <button class="btn btn-sm" onclick="copyToClipboard('${escapeHtml(shortUrl)}')" title="Copier le lien court">Copier</button>
                        <button class="btn btn-sm btn-qr" onclick="showQr('${escapeHtml(link.slug)}')" title="QR Code">QR</button>
                        <button class="btn btn-sm btn-danger" onclick="deleteLink(${link.id}, '${escapeHtml(link.slug)}')" title="Supprimer">Suppr.</button>
                    </td>
                </tr>`;
            });

            html += '</tbody></table>';
            container.innerHTML = html;

            // Pagination
            let pagHtml = '';
            if (data.pages > 1) {
                for (let i = 1; i <= data.pages; i++) {
                    pagHtml += `<button class="btn btn-sm ${i === data.page ? 'btn-primary' : ''}" onclick="loadLinks(${i})">${i}</button> `;
                }
            }
            document.getElementById('pagination').innerHTML = pagHtml;
        } catch (err) {
            container.innerHTML = '<p class="error">Erreur lors du chargement des liens.</p>';
        }
    }

    // --- Suppression ---
    async function deleteLink(id, slug) {
        if (!confirm(`Supprimer le lien "${slug}" ?`)) return;

        try {
            const resp = await fetch('/admin/api.php', {
                method: 'POST',
                headers: {'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest'},
                body: JSON.stringify({action: 'delete', id: id})
            });
            const data = await resp.json();

            if (data.success) {
                showToast('Lien supprimé.');
                loadLinks(currentPage);
            } else {
                showToast(data.error || 'Erreur lors de la suppression.', true);
            }
        } catch (err) {
            showToast('Erreur de connexion.', true);
        }
    }

    // --- Copier dans le presse-papiers ---
    function copyToClipboard(text) {
        navigator.clipboard.writeText(text).then(() => {
            showToast('Lien copié !');
        }).catch(() => {
            // Fallback
            const ta = document.createElement('textarea');
            ta.value = text;
            ta.style.position = 'fixed';
            ta.style.opacity = '0';
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            document.body.removeChild(ta);
            showToast('Lien copié !');
        });
    }

    // --- QR Code ---
    function showQr(slug) {
        const modal = document.getElementById('qrModal');
        const img = document.getElementById('qrImage');
        const dl = document.getElementById('qrDownload');
        const slg = document.getElementById('qrSlug');

        const qrUrl = '/admin/qr.php?slug=' + encodeURIComponent(slug);
        img.innerHTML = `<img src="${qrUrl}" alt="QR Code" width="250" height="250">`;
        slg.textContent = SITE_URL + '/' + slug;
        dl.href = qrUrl + '&download=1';
        dl.download = 'qr-' + slug + '.png';
        modal.classList.remove('hidden');
    }

    function closeQrModal() {
        document.getElementById('qrModal').classList.add('hidden');
    }

    document.querySelector('.modal-backdrop')?.addEventListener('click', closeQrModal);

    // --- Toast ---
    function showToast(message, isError = false) {
        const toast = document.getElementById('toast');
        toast.textContent = message;
        toast.className = 'toast' + (isError ? ' toast-error' : '');
        setTimeout(() => toast.classList.add('hidden'), 3000);
    }

    // --- Utilitaires ---
    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function formatDate(dateStr) {
        const d = new Date(dateStr);
        return d.toLocaleDateString('fr-FR', {day: '2-digit', month: '2-digit', year: 'numeric'})
             + ' ' + d.toLocaleTimeString('fr-FR', {hour: '2-digit', minute: '2-digit'});
    }

    // Chargement initial
    loadLinks();
    </script>
<?php endif; ?>
</body>
</html>
