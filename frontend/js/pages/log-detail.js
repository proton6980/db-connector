/** Audit log detail page stub — full UI in Step 3. */

export function render(container, params = {}) {
    const id = params.id || '';
    container.innerHTML = `
        <h2 class="mb-3"></h2>
        <div class="alert alert-secondary">日志详情页面骨架（Step 3 实现完整 UI）</div>
        <a href="#/logs" class="btn btn-outline-secondary btn-sm">返回列表</a>
    `;
    container.querySelector('h2').textContent = `日志详情 #${id}`;
}

export default { render };
