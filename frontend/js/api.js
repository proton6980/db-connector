/**
 * Fetch wrapper for session-cookie auth.
 * CSRF token is stored in sessionStorage under key "csrf".
 */

const CSRF_KEY = 'csrf';
const BASE_URL = '';

function getCsrf() {
    return sessionStorage.getItem(CSRF_KEY);
}

export function setCsrf(token) {
    if (token) {
        sessionStorage.setItem(CSRF_KEY, token);
    } else {
        sessionStorage.removeItem(CSRF_KEY);
    }
}

export function clearAuth() {
    sessionStorage.removeItem(CSRF_KEY);
}

function redirectToLogin() {
    clearAuth();
    if (!window.location.pathname.endsWith('/login.html')) {
        window.location.href = '/login.html';
    }
}

async function parseBody(response) {
    const text = await response.text();
    if (!text) {
        return null;
    }
    try {
        return JSON.parse(text);
    } catch (_) {
        return text;
    }
}

function errorMessage(body, fallback) {
    if (body && typeof body === 'object') {
        return body.error || body.message || fallback;
    }
    if (typeof body === 'string' && body.trim()) {
        return body;
    }
    return fallback;
}

async function request(method, path, body) {
    const headers = {
        Accept: 'application/json',
    };

    const upper = method.toUpperCase();
    const mutating = upper === 'POST' || upper === 'PUT' || upper === 'DELETE' || upper === 'PATCH';
    if (mutating) {
        headers['Content-Type'] = 'application/json';
        const csrf = getCsrf();
        if (csrf) {
            headers['X-CSRF-TOKEN'] = csrf;
        }
    }

    const response = await fetch(BASE_URL + path, {
        method: upper,
        credentials: 'include',
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (response.status === 401) {
        redirectToLogin();
        const data = await parseBody(response);
        const err = new Error(errorMessage(data, '未登录或会话已过期'));
        err.status = 401;
        err.body = data;
        throw err;
    }

    const data = await parseBody(response);
    if (!response.ok) {
        const err = new Error(errorMessage(data, `请求失败 (${response.status})`));
        err.status = response.status;
        err.body = data;
        throw err;
    }
    return data;
}

async function getBlob(path) {
    const response = await fetch(BASE_URL + path, {
        method: 'GET',
        credentials: 'include',
        headers: { Accept: '*/*' },
    });

    if (response.status === 401) {
        redirectToLogin();
        const data = await parseBody(response);
        const err = new Error(errorMessage(data, '未登录或会话已过期'));
        err.status = 401;
        err.body = data;
        throw err;
    }

    if (!response.ok) {
        const data = await parseBody(response);
        const err = new Error(errorMessage(data, `请求失败 (${response.status})`));
        err.status = response.status;
        err.body = data;
        throw err;
    }

    const contentType = (response.headers.get('Content-Type') || '').toLowerCase();
    // 200 with HTML/JSON is usually a login page or error payload, not a file download.
    if (contentType.includes('application/json') || contentType.includes('text/html')) {
        const data = await parseBody(response);
        const err = new Error(errorMessage(data, '导出失败：服务器返回了非文件响应'));
        err.status = response.status;
        err.body = data;
        throw err;
    }

    return response.blob();
}

export const api = {
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body ?? {}),
    put: (path, body) => request('PUT', path, body ?? {}),
    delete: (path, body) => request('DELETE', path, body),
    getBlob,
    setCsrf,
    clearAuth,
    getCsrf,
};

export default api;
