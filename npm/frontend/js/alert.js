/**
 * Flash / toast style alerts rendered into #alert-root.
 */

const DEFAULT_TIMEOUT_MS = 4000;

function ensureRoot() {
    let root = document.getElementById('alert-root');
    if (!root) {
        root = document.createElement('div');
        root.id = 'alert-root';
        const main = document.querySelector('main') || document.body;
        main.prepend(root);
    }
    return root;
}

function show(type, message, timeoutMs = DEFAULT_TIMEOUT_MS) {
    const root = ensureRoot();
    const el = document.createElement('div');
    el.className = `alert alert-${type} alert-dismissible fade show`;
    el.setAttribute('role', 'alert');
    el.innerHTML = `
        <span></span>
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="关闭"></button>
    `;
    el.querySelector('span').textContent = message;
    root.appendChild(el);

    if (timeoutMs > 0) {
        setTimeout(() => {
            el.classList.remove('show');
            el.addEventListener('transitionend', () => el.remove(), { once: true });
            // Fallback removal if transition doesn't fire
            setTimeout(() => el.remove(), 300);
        }, timeoutMs);
    }
    return el;
}

export const alert = {
    success: (message, timeoutMs) => show('success', message, timeoutMs),
    error: (message, timeoutMs) => show('danger', message, timeoutMs),
    warning: (message, timeoutMs) => show('warning', message, timeoutMs),
    info: (message, timeoutMs) => show('info', message, timeoutMs),
    clear() {
        const root = document.getElementById('alert-root');
        if (root) {
            root.innerHTML = '';
        }
    },
};

export default alert;
