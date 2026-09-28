import { api } from '../api.js';
import { alert } from '../alert.js';

function readForm(form) {
    const data = new FormData(form);
    const password = String(data.get('password') || '');
    return {
        name: String(data.get('name') || '').trim(),
        dbType: String(data.get('dbType') || '').trim(),
        host: String(data.get('host') || '').trim(),
        port: Number(data.get('port')),
        username: String(data.get('username') || '').trim(),
        password,
        databaseName: String(data.get('databaseName') || '').trim() || null,
        extraParams: String(data.get('extraParams') || '').trim() || null,
        poolMin: Number(data.get('poolMin')),
        poolMax: Number(data.get('poolMax')),
        active: form.querySelector('#active').checked,
    };
}

function validate(body, isNew) {
    if (!body.name) {
        return '名称不能为空';
    }
    if (!body.dbType) {
        return '请选择数据库类型';
    }
    if (!body.host) {
        return '主机不能为空';
    }
    if (!Number.isFinite(body.port) || body.port < 1 || body.port > 65535) {
        return '端口无效';
    }
    if (!body.username) {
        return '用户名不能为空';
    }
    if (isNew && !body.password) {
        return '密码不能为空';
    }
    if (!Number.isFinite(body.poolMin) || body.poolMin < 0) {
        return '最小池大小无效';
    }
    if (!Number.isFinite(body.poolMax) || body.poolMax < 1) {
        return '最大池大小无效';
    }
    if (body.poolMin > body.poolMax) {
        return '最小池大小不能大于最大池大小';
    }
    return null;
}

function fillForm(form, conn) {
    if (!conn) {
        return;
    }
    form.name.value = conn.name || '';
    form.dbType.value = conn.dbType || '';
    form.host.value = conn.host || '';
    form.port.value = conn.port ?? '';
    form.username.value = conn.username || '';
    form.databaseName.value = conn.databaseName || '';
    form.extraParams.value = conn.extraParams || '';
    form.poolMin.value = conn.poolMin ?? 2;
    form.poolMax.value = conn.poolMax ?? 10;
    form.active.checked = conn.active !== false;
}

export async function render(container, params = {}) {
    const mode = params.mode || 'new';
    const isNew = mode !== 'edit';
    const id = params.id || '';

    container.innerHTML = `
        <div style="max-width:720px">
            <h2 class="mb-3"></h2>
            <div class="text-muted" data-loading>加载中…</div>
            <form class="d-none" novalidate>
                <div class="mb-3">
                    <label class="form-label" for="name">名称</label>
                    <input class="form-control" id="name" name="name" required>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="dbType">数据库类型</label>
                    <select class="form-select" id="dbType" name="dbType" required>
                        <option value="">请选择</option>
                        <option value="DM">DM</option>
                        <option value="H2">H2</option>
                    </select>
                </div>
                <div class="row mb-3">
                    <div class="col-8">
                        <label class="form-label" for="host">主机</label>
                        <input class="form-control" id="host" name="host" required>
                    </div>
                    <div class="col-4">
                        <label class="form-label" for="port">端口</label>
                        <input class="form-control" id="port" name="port" type="number" min="1" max="65535" required>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="username">用户名</label>
                    <input class="form-control" id="username" name="username" required>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="password">密码</label>
                    <input class="form-control" id="password" name="password" type="password"
                           autocomplete="new-password">
                </div>
                <div class="mb-3">
                    <label class="form-label" for="databaseName">数据库名</label>
                    <input class="form-control" id="databaseName" name="databaseName">
                </div>
                <div class="mb-3">
                    <label class="form-label" for="extraParams">额外参数（JSON）</label>
                    <input class="form-control" id="extraParams" name="extraParams" placeholder='{"ssl":true}'>
                </div>
                <div class="row mb-3">
                    <div class="col-6">
                        <label class="form-label" for="poolMin">最小池大小</label>
                        <input class="form-control" id="poolMin" name="poolMin" type="number" min="0" value="2">
                    </div>
                    <div class="col-6">
                        <label class="form-label" for="poolMax">最大池大小</label>
                        <input class="form-control" id="poolMax" name="poolMax" type="number" min="1" value="10">
                    </div>
                </div>
                <div class="mb-3 form-check">
                    <input class="form-check-input" id="active" name="active" type="checkbox" checked>
                    <label class="form-check-label" for="active">启用</label>
                </div>
                <div class="d-flex gap-2">
                    <button class="btn btn-primary" type="submit">保存</button>
                    <a class="btn btn-secondary" href="#/connections">取消</a>
                </div>
            </form>
        </div>
    `;

    const title = container.querySelector('h2');
    title.textContent = isNew ? '新建连接' : '编辑连接';
    const form = container.querySelector('form');
    const loading = container.querySelector('[data-loading]');
    const passwordInput = form.querySelector('#password');
    passwordInput.placeholder = isNew ? '必填' : '留空则不修改';
    if (isNew) {
        passwordInput.required = true;
    }

    if (!isNew) {
        try {
            const conn = await api.get(`/api/connections/${encodeURIComponent(id)}`);
            fillForm(form, conn);
        } catch (err) {
            loading.textContent = '';
            loading.className = 'alert alert-danger';
            loading.textContent = err.message || '加载连接失败';
            alert.error(err.message || '加载连接失败');
            return;
        }
    }

    loading.remove();
    form.classList.remove('d-none');

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const body = readForm(form);
        const error = validate(body, isNew);
        if (error) {
            alert.error(error);
            return;
        }
        if (!isNew && !body.password) {
            delete body.password;
        }

        const submitBtn = form.querySelector('button[type="submit"]');
        submitBtn.disabled = true;
        try {
            if (isNew) {
                await api.post('/api/connections', body);
                alert.success('创建成功');
            } else {
                await api.put(`/api/connections/${encodeURIComponent(id)}`, body);
                alert.success('保存成功');
            }
            window.location.hash = '#/connections';
        } catch (err) {
            alert.error(err.message || '保存失败');
            submitBtn.disabled = false;
        }
    });
}

export default { render };
