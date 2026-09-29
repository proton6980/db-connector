import { api } from '../api.js';
import { alert } from '../alert.js';

function statusBadge(active) {
    const span = document.createElement('span');
    if (active) {
        span.className = 'badge bg-success';
        span.textContent = '启用';
    } else {
        span.className = 'badge bg-secondary';
        span.textContent = '停用';
    }
    return span;
}

function actionButton(label, className) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = `btn btn-sm ${className}`;
    btn.textContent = label;
    return btn;
}

async function testConnection(id) {
    try {
        const result = await api.post(`/api/connections/${encodeURIComponent(id)}/test`);
        if (result.ok) {
            alert.success(result.message || `测试成功（${result.durationMs}ms）`);
        } else {
            alert.error(result.message || '测试失败');
        }
    } catch (err) {
        alert.error(err.message || '测试失败');
    }
}

async function reloadConnection(id) {
    try {
        await api.post(`/api/connections/${encodeURIComponent(id)}/reload`);
        alert.success('连接池已重载');
    } catch (err) {
        alert.error(err.message || '重载失败');
    }
}

async function deleteConnection(id, name, onDone) {
    if (!window.confirm(`确认删除连接「${name || id}」？`)) {
        return;
    }
    try {
        await api.delete(`/api/connections/${encodeURIComponent(id)}`);
        alert.success('已删除');
        await onDone();
    } catch (err) {
        alert.error(err.message || '删除失败');
    }
}

function renderRows(tbody, connections, reload) {
    tbody.replaceChildren();
    if (!connections || connections.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 5;
        td.className = 'text-center text-muted';
        td.textContent = '暂无连接';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }

    for (const c of connections) {
        const tr = document.createElement('tr');

        const nameTd = document.createElement('td');
        nameTd.textContent = c.name || '';

        const typeTd = document.createElement('td');
        typeTd.textContent = c.dbType || '';

        const hostTd = document.createElement('td');
        hostTd.textContent = `${c.host || ''}:${c.port ?? ''}`;

        const statusTd = document.createElement('td');
        statusTd.appendChild(statusBadge(!!c.active));

        const actionsTd = document.createElement('td');
        const testBtn = actionButton('测试', 'btn-outline-info');
        const reloadBtn = actionButton('重载', 'btn-outline-warning');
        const editLink = document.createElement('a');
        editLink.className = 'btn btn-sm btn-outline-secondary';
        editLink.href = `#/connections/${encodeURIComponent(c.id)}/edit`;
        editLink.textContent = '编辑';
        const deleteBtn = actionButton('删除', 'btn-outline-danger');

        testBtn.addEventListener('click', () => testConnection(c.id));
        reloadBtn.addEventListener('click', () => reloadConnection(c.id));
        deleteBtn.addEventListener('click', () => deleteConnection(c.id, c.name, reload));

        actionsTd.append(testBtn, ' ', reloadBtn, ' ', editLink, ' ', deleteBtn);
        tr.append(nameTd, typeTd, hostTd, statusTd, actionsTd);
        tbody.appendChild(tr);
    }
}

export async function render(container) {
    container.innerHTML = `
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="mb-0">连接管理</h2>
            <a class="btn btn-primary btn-sm" href="#/connections/new">新建连接</a>
        </div>
        <div class="text-muted">加载中…</div>
    `;

    async function load() {
        let connections;
        try {
            connections = await api.get('/api/connections');
        } catch (err) {
            container.querySelector('.text-muted')?.remove();
            const existing = container.querySelector('table');
            if (existing) {
                existing.remove();
            }
            const errEl = document.createElement('div');
            errEl.className = 'alert alert-danger';
            errEl.textContent = err.message || '加载连接列表失败';
            container.appendChild(errEl);
            alert.error(err.message || '加载连接列表失败');
            return;
        }

        let table = container.querySelector('table');
        if (!table) {
            container.querySelector('.text-muted')?.remove();
            container.querySelector('.alert-danger')?.remove();
            table = document.createElement('table');
            table.className = 'table table-hover';
            table.innerHTML = `
                <thead class="table-light">
                    <tr>
                        <th>名称</th>
                        <th>类型</th>
                        <th>主机:端口</th>
                        <th>状态</th>
                        <th>操作</th>
                    </tr>
                </thead>
                <tbody></tbody>
            `;
            container.appendChild(table);
        }
        renderRows(table.querySelector('tbody'), connections, load);
    }

    await load();
}

export default { render };
