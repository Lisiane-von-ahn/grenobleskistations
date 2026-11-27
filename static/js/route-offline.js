(() => {
    const storageKey = 'grenobleski-saved-routes-v1';
    function read() {
        try { return JSON.parse(localStorage.getItem(storageKey) || '[]'); }
        catch (_) { return []; }
    }
    const save = document.getElementById('save-route');
    if ('serviceWorker' in navigator) navigator.serviceWorker.register('/route-offline-worker.js').catch(() => {});
    if (save) save.addEventListener('click', async () => {
        try {
            const snapshot = document.documentElement.cloneNode(true);
            snapshot.querySelectorAll('script, form, button, input, select, .planning-filters, #save-route-status, .route-back, .route-view-switch').forEach(node => node.remove());
            for (const link of snapshot.querySelectorAll('link[rel="stylesheet"]')) {
                if (new URL(link.href).origin !== location.origin) { link.remove(); continue; }
                const response = await fetch(link.href);
                if (!response.ok) throw new Error();
                const style = document.createElement('style');
                style.textContent = (await response.text()).replace(/url\((['"]?)([^)'"]+)\1\)/g, (match, quote, url) => {
                    return url.startsWith('data:') ? match : 'url("' + new URL(url, link.href).href + '")';
                });
                link.replaceWith(style);
            }
            snapshot.querySelectorAll('a').forEach(link => link.removeAttribute('href'));
            const entry = {title: document.title, savedAt: new Date().toISOString(), html: '<!doctype html>' + snapshot.outerHTML};
            const routes = read();
            routes.unshift(entry);
            localStorage.setItem(storageKey, JSON.stringify(routes.slice(0, 5)));
            document.getElementById('save-route-status').textContent = save.dataset.success;
        } catch (_) {
            document.getElementById('save-route-status').textContent = save.dataset.failure;
        }
    });
    const list = document.getElementById('offline-list');
    if (list) {
        const routes = read();
        if (!routes.length) list.textContent = list.dataset.empty;
        routes.forEach((entry, index) => {
            const row = document.createElement('p');
            const button = document.createElement('button');
            button.className = 'btn btn-outline-primary';
            button.textContent = entry.title + ' · ' + new Date(entry.savedAt).toLocaleString();
            button.onclick = () => {
                const frame = document.getElementById('offline-preview');
                frame.srcdoc = entry.html;
                frame.hidden = false;
            };
            const remove = document.createElement('button');
            remove.className = 'btn btn-outline-secondary ms-2';
            remove.textContent = list.dataset.remove;
            remove.onclick = () => {
                const remaining = read().filter(item => item.savedAt !== entry.savedAt);
                localStorage.setItem(storageKey, JSON.stringify(remaining));
                row.remove();
                document.getElementById('offline-preview').hidden = true;
                if (!remaining.length) list.textContent = list.dataset.empty;
            };
            row.appendChild(button);
            row.appendChild(remove);
            list.appendChild(row);
        });
    }
})();
