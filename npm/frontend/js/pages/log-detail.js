import { api } from '../api.js';
import { alert } from '../alert.js';
import { formatDateTime, formatDurationMs, statusBadgeClass } from '../utils/format.js';

function addRow(tbody, label, valueNode) {
    const tr = document.createElement('tr');
    const th = document.createElement('th');
    th.style.width = '140px';
    th.textContent = label;
    const td = document.createElement('td');
    if (typeof valueNode === 'string' || valueNode == null) {
        td.textContent = valueNode == null || valueNode === '' ? '-' : valueNode;
    } else {
        td.appendChild(valueNode);
    }
    tr.append(th, td);
    tbody.appendChild(tr);
}

function preText(text, className) {
    const pre = document.createElement('pre');
    if (className) {
        pre.className = className;
    }
    pre.style.whiteSpace = 'pre-wrap';
    pre.style.wordBreak = 'break-word';
    pre.style.marginBottom = '0';
    pre.textContent = text == null || text === '' ? '-' : String(text);
    return pre;
}

export async function render(container, params = {}) {
    const id = params.id || '';
    container.innerHTML = `
        <div style="max-width:960px">
            <h2 class="mb-3">日志详情 <small class="text-muted"></small></h2>
            <div class="text-muted">加载中…</div>
        </div>
    `;
    container.querySelector('small').textContent = id ? `#${id}` : '';

    let log;
    try {
        log = await api.get(`/api/logs/${encodeURIComponent(id)}`);
    } catch (err) {
        container.querySelector('.text-muted').className = 'alert alert-danger';
        container.querySelector('.alert').textContent = err.message || '加载日志详情失败';
        alert.error(err.message || '加载日志详情失败');
        const back = document.createElement('a');
        back.className = 'btn btn-secondary mt-2';
        back.href = '#/logs';
        back.textContent = '返回列表';
        container.querySelector('div').appendChild(back);
        return;
    }

    const wrap = container.querySelector('div');
    wrap.querySelector('.text-muted')?.remove();

    const table = document.createElement('table');
    table.className = 'table table-bordered';
    const tbody = document.createElement('tbody');
    table.appendChild(tbody);

    addRow(tbody, '连接ID', log.connectionId);
    addRow(tbody, '账号', log.accountId);
    addRow(tbody, 'SQL', preText(log.sqlText));
    addRow(tbody, '参数', preText(log.params));

    const statusSpan = document.createElement('span');
    statusSpan.className = statusBadgeClass(log.status);
    statusSpan.textContent = log.status || '-';
    addRow(tbody, '状态', statusSpan);

    addRow(tbody, '行数', log.rowCount == null ? '-' : String(log.rowCount));
    addRow(tbody, '耗时', formatDurationMs(log.durationMs));
    if (log.errorMsg) {
        addRow(tbody, '错误', preText(log.errorMsg, 'text-danger'));
    }
    addRow(tbody, '客户端IP', log.clientIp);
    addRow(tbody, '执行时间', formatDateTime(log.executedAt));

    const back = document.createElement('a');
    back.className = 'btn btn-secondary';
    back.href = '#/logs';
    back.textContent = '返回列表';

    wrap.append(table, back);
}

export default { render };
