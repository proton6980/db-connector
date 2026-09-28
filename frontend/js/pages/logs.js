import { api } from '../api.js';
import { alert } from '../alert.js';
import {
    abbreviate,
    formatDateTime,
    formatDurationMs,
    statusBadgeClass,
    toApiDateTime,
} from '../utils/format.js';

const PAGE_SIZE = 20;

function parseHashQuery() {
    const hash = window.location.hash || '';
    const qIndex = hash.indexOf('?');
    if (qIndex < 0) {
        return new URLSearchParams();
    }
    return new URLSearchParams(hash.slice(qIndex + 1));
}

function currentFilters() {
    const qs = parseHashQuery();
    return {
        connectionId: qs.get('connectionId') || '',
        accountId: qs.get('accountId') || '',
        status: qs.get('status') || '',
        from: qs.get('from') || '',
        to: qs.get('to') || '',
        page: Math.max(0, Number(qs.get('page') || 0) || 0),
    };
}

function buildQuery(filters, { forExport = false } = {}) {
    const params = new URLSearchParams();
    if (filters.connectionId) {
        params.set('connectionId', filters.connectionId);
    }
    if (filters.accountId) {
        params.set('accountId', filters.accountId);
    }
    if (filters.status) {
        params.set('status', filters.status);
    }
    const from = toApiDateTime(filters.from);
    const to = toApiDateTime(filters.to);
    if (from) {
        params.set('from', from);
    }
    if (to) {
        params.set('to', to);
    }
    if (!forExport) {
        params.set('page', String(filters.page || 0));
        params.set('size', String(PAGE_SIZE));
    }
    return params;
}

function updateHash(filters) {
    const params = buildQuery(filters);
    const qs = params.toString();
    window.location.hash = qs ? `#/logs?${qs}` : '#/logs';
}

function fillSelect(select, options, selected, emptyLabel, valueKey, labelKey) {
    select.replaceChildren();
    const empty = document.createElement('option');
    empty.value = '';
    empty.textContent = emptyLabel;
    select.appendChild(empty);
    for (const opt of options || []) {
        const option = document.createElement('option');
        if (typeof opt === 'string') {
            option.value = opt;
            option.textContent = opt;
        } else {
            option.value = opt[valueKey];
            option.textContent = opt[labelKey];
        }
        if (option.value === selected) {
            option.selected = true;
        }
        select.appendChild(option);
    }
}

function statusBadge(status) {
    const span = document.createElement('span');
    span.className = statusBadgeClass(status);
    span.textContent = status || '-';
    return span;
}

function renderTable(tbody, logs) {
    tbody.replaceChildren();
    if (!logs || logs.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 7;
        td.className = 'text-center text-muted';
        td.textContent = '暂无日志';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }

    for (const log of logs) {
        const tr = document.createElement('tr');

        const idTd = document.createElement('td');
        const link = document.createElement('a');
        link.href = `#/logs/${encodeURIComponent(log.id)}`;
        link.textContent = String(log.id);
        idTd.appendChild(link);

        const connTd = document.createElement('td');
        connTd.textContent = log.connectionId || '';

        const accountTd = document.createElement('td');
        accountTd.textContent = log.accountId || '';

        const sqlTd = document.createElement('td');
        sqlTd.textContent = abbreviate(log.sqlText, 60);
        sqlTd.title = log.sqlText || '';

        const statusTd = document.createElement('td');
        statusTd.appendChild(statusBadge(log.status));

        const durTd = document.createElement('td');
        durTd.textContent = formatDurationMs(log.durationMs);

        const timeTd = document.createElement('td');
        timeTd.textContent = formatDateTime(log.executedAt);

        tr.append(idTd, connTd, accountTd, sqlTd, statusTd, durTd, timeTd);
        tbody.appendChild(tr);
    }
}

function renderPagination(nav, pageData, filters) {
    nav.replaceChildren();
    const totalPages = pageData.totalPages || 0;
    if (totalPages <= 1) {
        return;
    }

    const page = pageData.page || 0;
    const ul = document.createElement('ul');
    ul.className = 'pagination';

    function addItem(label, targetPage, disabled, active) {
        const li = document.createElement('li');
        li.className = 'page-item';
        if (disabled) {
            li.classList.add('disabled');
        }
        if (active) {
            li.classList.add('active');
        }
        if (active || disabled) {
            const span = document.createElement('span');
            span.className = 'page-link';
            span.textContent = label;
            li.appendChild(span);
        } else {
            const a = document.createElement('a');
            a.className = 'page-link';
            a.href = '#';
            a.textContent = label;
            a.addEventListener('click', (e) => {
                e.preventDefault();
                updateHash({ ...filters, page: targetPage });
            });
            li.appendChild(a);
        }
        ul.appendChild(li);
    }

    addItem('首页', 0, page === 0, false);
    addItem('上一页', page - 1, page <= 0, false);
    addItem(`${page + 1}/${totalPages}`, page, true, true);
    addItem('下一页', page + 1, page >= totalPages - 1, false);
    addItem('末页', totalPages - 1, page >= totalPages - 1, false);

    nav.appendChild(ul);
}

async function downloadCsv(filters) {
    try {
        const params = buildQuery(filters, { forExport: true });
        const qs = params.toString();
        const blob = await api.getBlob(`/api/logs/export${qs ? `?${qs}` : ''}`);
        const text = await blob.text();
        const truncated = text.includes('# 结果已达上限');
        const downloadBlob = new Blob([text], { type: blob.type || 'text/csv;charset=UTF-8' });
        const url = URL.createObjectURL(downloadBlob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'audit_log.csv';
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(url);
        if (truncated) {
            alert.warning('CSV 已下载，但结果已达上限，请缩小筛选范围');
        } else {
            alert.success('CSV 已开始下载');
        }
    } catch (err) {
        alert.error(err.message || '导出失败');
    }
}

export async function render(container) {
    const filters = currentFilters();

    container.innerHTML = `
        <h2 class="mb-3">审计日志</h2>
        <form class="row g-2 mb-3" data-filter-form>
            <div class="col-auto">
                <select class="form-select" name="connectionId" data-filter="connectionId">
                    <option value="">全部连接</option>
                </select>
            </div>
            <div class="col-auto">
                <select class="form-select" name="accountId" data-filter="accountId">
                    <option value="">全部账号</option>
                </select>
            </div>
            <div class="col-auto">
                <select class="form-select" name="status" data-filter="status">
                    <option value="">全部状态</option>
                    <option value="SUCCESS">SUCCESS</option>
                    <option value="ERROR">ERROR</option>
                    <option value="BLOCKED">BLOCKED</option>
                </select>
            </div>
            <div class="col-auto">
                <input class="form-control" type="datetime-local" name="from" data-filter="from" placeholder="起始时间">
            </div>
            <div class="col-auto">
                <input class="form-control" type="datetime-local" name="to" data-filter="to" placeholder="截止时间">
            </div>
            <div class="col-auto">
                <button class="btn btn-primary" type="submit">筛选</button>
            </div>
            <div class="col-auto">
                <button class="btn btn-outline-success" type="button" data-export>导出CSV</button>
            </div>
        </form>
        <div class="text-muted" data-loading>加载中…</div>
        <table class="table table-hover table-sm d-none" data-table>
            <thead class="table-light">
                <tr>
                    <th>ID</th>
                    <th>连接</th>
                    <th>账号</th>
                    <th>SQL</th>
                    <th>状态</th>
                    <th>耗时</th>
                    <th>时间</th>
                </tr>
            </thead>
            <tbody></tbody>
        </table>
        <nav aria-label="分页" class="mt-3" data-pagination></nav>
    `;

    const form = container.querySelector('[data-filter-form]');
    const connSelect = form.querySelector('[data-filter="connectionId"]');
    const accountSelect = form.querySelector('[data-filter="accountId"]');
    const statusSelect = form.querySelector('[data-filter="status"]');
    const fromInput = form.querySelector('[data-filter="from"]');
    const toInput = form.querySelector('[data-filter="to"]');
    const loading = container.querySelector('[data-loading]');
    const table = container.querySelector('[data-table]');
    const tbody = table.querySelector('tbody');
    const pagination = container.querySelector('[data-pagination]');

    statusSelect.value = filters.status;
    fromInput.value = filters.from;
    toInput.value = filters.to;

    let filterOptions;
    try {
        filterOptions = await api.get('/api/logs/filters');
    } catch (err) {
        loading.className = 'alert alert-danger';
        loading.textContent = err.message || '加载筛选选项失败';
        alert.error(err.message || '加载筛选选项失败');
        return;
    }

    fillSelect(connSelect, filterOptions.connections, filters.connectionId, '全部连接', 'id', 'name');
    fillSelect(accountSelect, filterOptions.accountIds, filters.accountId, '全部账号');

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        updateHash({
            connectionId: connSelect.value,
            accountId: accountSelect.value,
            status: statusSelect.value,
            from: fromInput.value,
            to: toInput.value,
            page: 0,
        });
    });

    form.querySelector('[data-export]').addEventListener('click', () => {
        downloadCsv({
            connectionId: connSelect.value,
            accountId: accountSelect.value,
            status: statusSelect.value,
            from: fromInput.value,
            to: toInput.value,
            page: 0,
        });
    });

    let pageData;
    try {
        const qs = buildQuery(filters).toString();
        pageData = await api.get(`/api/logs?${qs}`);
    } catch (err) {
        loading.className = 'alert alert-danger';
        loading.textContent = err.message || '加载日志失败';
        alert.error(err.message || '加载日志失败');
        return;
    }

    loading.remove();
    table.classList.remove('d-none');
    renderTable(tbody, pageData.content);
    renderPagination(pagination, pageData, filters);
}

export default { render };
