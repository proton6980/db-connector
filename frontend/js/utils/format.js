/** Minimal date/number format helpers (stubs for Step 3 UI). */

export function formatDateTime(value) {
    if (value == null || value === '') {
        return '-';
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
