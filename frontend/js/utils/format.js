/** Date/number/text helpers for SPA pages. */

export function formatDateTime(value) {
    if (value == null || value === '') {
        return '-';
    }
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) {
        return value.replace('T', ' ').slice(0, 19);
    }
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
        return String(value);
    }
    return date.toLocaleString('zh-CN', { hour12: false });
}

export function formatNumber(value, digits = 0) {
    if (value == null || value === '') {
        return '-';
    }
    const num = Number(value);
    if (Number.isNaN(num)) {
        return String(value);
    }
    return num.toLocaleString('zh-CN', {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
    });
}

export function formatDurationMs(value) {
    if (value == null || value === '') {
        return '-';
    }
    const num = Number(value);
    if (Number.isNaN(num)) {
        return String(value);
    }
    return `${formatNumber(num, num % 1 === 0 ? 0 : 1)}ms`;
}

export function formatPercent(value, digits = 2) {
    if (value == null || value === '') {
        return '-';
    }
    const num = Number(value);
    if (Number.isNaN(num)) {
        return String(value);
    }
    return `${formatNumber(num, digits)}%`;
}

export function abbreviate(text, maxLen = 80) {
    if (text == null) {
        return '';
    }
    const s = String(text);
    if (s.length <= maxLen) {
        return s;
    }
    return `${s.slice(0, Math.max(0, maxLen - 3))}...`;
}

/** Append `:00` so datetime-local values satisfy ISO_LOCAL_DATE_TIME. */
export function toApiDateTime(value) {
    if (!value) {
        return '';
    }
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(value)) {
        return value.slice(0, 19);
    }
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
        return `${value}:00`;
    }
    return value;
}

export function statusBadgeClass(status) {
    switch (status) {
        case 'SUCCESS':
            return 'badge bg-success';
        case 'ERROR':
            return 'badge bg-danger';
        case 'BLOCKED':
            return 'badge bg-warning text-dark';
        default:
            return 'badge bg-secondary';
    }
}
