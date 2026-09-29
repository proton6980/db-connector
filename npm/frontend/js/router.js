/**
 * Hash-based SPA router.
 * Routes: #/dashboard, #/connections, #/connections/new,
 *         #/connections/:id/edit, #/logs, #/logs/:id
 */

import { render as renderDashboard } from './pages/dashboard.js';
import { render as renderConnections } from './pages/connections.js';
import { render as renderConnectionForm } from './pages/connection-form.js';
import { render as renderLogs } from './pages/logs.js';
import { render as renderLogDetail } from './pages/log-detail.js';

const routes = [
    { pattern: /^\/dashboard\/?$/, name: 'dashboard', handler: renderDashboard },
    { pattern: /^\/connections\/new\/?$/, name: 'connections', handler: (el) => renderConnectionForm(el, { mode: 'new' }) },
    { pattern: /^\/connections\/([^/]+)\/edit\/?$/, name: 'connections', handler: (el, m) => renderConnectionForm(el, { mode: 'edit', id: m[1] }) },
    { pattern: /^\/connections\/?$/, name: 'connections', handler: renderConnections },
    { pattern: /^\/logs\/([^/]+)\/?$/, name: 'logs', handler: (el, m) => renderLogDetail(el, { id: m[1] }) },
    { pattern: /^\/logs\/?$/, name: 'logs', handler: renderLogs },
];

let appEl = null;
let navGeneration = 0;

function currentPath() {
    const hash = window.location.hash || '';
    const raw = hash.startsWith('#') ? hash.slice(1) : hash;
    const pathOnly = raw.split('?')[0];
    return pathOnly.startsWith('/') ? pathOnly : `/${pathOnly}`;
}

export function setActiveNav() {
    const path = currentPath();
    let active = 'dashboard';
    if (path.startsWith('/connections')) {
        active = 'connections';
    } else if (path.startsWith('/logs')) {
        active = 'logs';
    }
    document.querySelectorAll('[data-nav]').forEach((link) => {
        link.classList.toggle('active', link.getAttribute('data-nav') === active);
    });
}

function showLoadError(message) {
    appEl.innerHTML = '<div class="alert alert-danger" role="alert"></div>';
    appEl.querySelector('.alert').textContent = message || '页面加载失败';
}

async function navigate() {
    if (!appEl) {
        return;
    }
    const token = ++navGeneration;
    let path = currentPath();
    if (!path || path === '/') {
        window.location.hash = '#/dashboard';
        return;
    }

    for (const route of routes) {
        const match = path.match(route.pattern);
        if (match) {
            setActiveNav();
            const mount = document.createElement('div');
            try {
                await route.handler(mount, match);
            } catch (err) {
                if (token !== navGeneration) {
                    return;
                }
                showLoadError(err.message || '页面加载失败');
                return;
            }
            if (token !== navGeneration) {
                return;
            }
            // Keep mount as live parent so page closures over `container` stay valid.
            appEl.replaceChildren(mount);
            return;
        }
    }

    if (token !== navGeneration) {
        return;
    }
    setActiveNav();
    appEl.innerHTML = `
        <div class="alert alert-warning">
            页面不存在：<code></code>
            <a href="#/dashboard" class="alert-link ms-2">返回仪表盘</a>
        </div>
    `;
    appEl.querySelector('code').textContent = path;
}

export function startRouter(container) {
    appEl = container;
    window.addEventListener('hashchange', navigate);
    if (!window.location.hash || window.location.hash === '#') {
        window.location.hash = '#/dashboard';
    } else {
        navigate();
    }
}

export default { startRouter, setActiveNav };
