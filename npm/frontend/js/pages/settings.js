import { api } from '../api.js';
import { alert } from '../alert.js';

const LOCAL_ENTRIES = new Set(['127.0.0.1', '::1']);

function isLocalOnly(entries) {
    return entries.length > 0 && entries.every((entry) => LOCAL_ENTRIES.has(entry));
}

function remoteEntries(entries) {
    return entries.filter((entry) => !LOCAL_ENTRIES.has(entry));
}

export async function render(container) {
    container.innerHTML = `
        <div style="max-width:720px">
            <h2 class="mb-3">访问设置</h2>
            <div class="text-muted" data-loading>加载中…</div>
            <form class="d-none" data-form>
                <div class="mb-3">
                    <label class="form-label d-block">允许访问的客户端</label>
                    <div class="form-check">
                        <input class="form-check-input" type="radio" name="mode" id="local-only" value="local">
                        <label class="form-check-label" for="local-only">仅本机</label>
                    </div>
                    <div class="form-check">
                        <input class="form-check-input" type="radio" name="mode" id="custom-access" value="custom">
                        <label class="form-check-label" for="custom-access">本机 + 指定 IP / 网段</label>
                    </div>
                </div>
                <div class="mb-3 d-none" data-custom-fields>
                    <label class="form-label" for="allowed">允许的 IP 或 CIDR 网段</label>
                    <textarea class="form-control" id="allowed" rows="6"
                              placeholder="每行一条，例如：&#10;192.168.1.20&#10;192.168.1.0/24"></textarea>
                    <div class="form-text">本机地址 127.0.0.1 和 ::1 会始终保留。保存后立即生效。</div>
                </div>
                <div class="alert alert-warning">
                    只有白名单内的客户端可以访问 Web 控制台和 MCP 服务。请确认填写的是运行 db-connector-mcp 这台电脑上看到的客户端 IP 或网段。
                </div>
                <button class="btn btn-primary" type="submit">保存白名单</button>
            </form>
        </div>
    `;

    const loading = container.querySelector('[data-loading]');
    const form = container.querySelector('[data-form]');
    const localRadio = container.querySelector('#local-only');
    const customRadio = container.querySelector('#custom-access');
    const customFields = container.querySelector('[data-custom-fields]');
    const allowed = container.querySelector('#allowed');

    function updateMode() {
        customFields.classList.toggle('d-none', !customRadio.checked);
    }

    localRadio.addEventListener('change', updateMode);
    customRadio.addEventListener('change', updateMode);

    try {
        const policy = await api.get('/api/access-policy');
        const entries = Array.isArray(policy.allowed) ? policy.allowed : [];
        if (isLocalOnly(entries)) {
            localRadio.checked = true;
        } else {
            customRadio.checked = true;
            allowed.value = remoteEntries(entries).join('\n');
        }
        updateMode();
        loading.remove();
        form.classList.remove('d-none');
    } catch (err) {
        loading.textContent = err.message || '加载访问设置失败';
        loading.classList.add('text-danger');
        return;
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const entries = customRadio.checked
            ? allowed.value.split(/\r?\n/).map((entry) => entry.trim()).filter(Boolean)
            : [];
        try {
            const result = await api.put('/api/access-policy', { allowed: entries });
            const saved = Array.isArray(result.allowed) ? result.allowed : [];
            allowed.value = remoteEntries(saved).join('\n');
            alert.success('白名单已保存并立即生效');
        } catch (err) {
            alert.error(err.message || '保存白名单失败');
        }
    });
}

export default { render };
