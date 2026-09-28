/** Connections list page stub — full UI in Step 3. */

export function render(container) {
    container.innerHTML = `
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="mb-0">连接管理</h2>
            <a class="btn btn-primary btn-sm" href="#/connections/new">新建连接</a>
        </div>
        <div class="alert alert-secondary">连接列表页面骨架（Step 3 实现完整 UI）</div>
    `;
}

export default { render };
