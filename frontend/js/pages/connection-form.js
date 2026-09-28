/** Connection create/edit form stub — full UI in Step 3. */

export function render(container, params = {}) {
    const mode = params.mode || 'new';
    const id = params.id || '';
    container.innerHTML = `
        <h2 class="mb-3"></h2>
        <div class="alert alert-secondary">连接表单页面骨架（Step 3 实现完整 UI）</div>
        <a href="#/connections" class="btn btn-outline-secondary btn-sm">返回列表</a>
    `;
    container.querySelector('h2').textContent =
        mode === 'edit' ? `编辑连接 #${id}` : '新建连接';
}

export default { render };
