import { api } from '../api.js';
import { alert } from '../alert.js';
import { formatDurationMs, formatNumber, formatPercent, abbreviate } from '../utils/format.js';

function svgEl(name, attrs = {}) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', name);
    for (const [k, v] of Object.entries(attrs)) {
        el.setAttribute(k, String(v));
    }
    return el;
}

function hourlyCount(hourlyQps, hour) {
    if (!hourlyQps) {
        return 0;
    }
    const v = hourlyQps[hour] ?? hourlyQps[String(hour)];
    return Number(v) || 0;
}

function renderHourlyChart(hourlyQps) {
    const svg = svgEl('svg', {
        width: '100%',
        height: '160',
        viewBox: '0 0 480 160',
        xmlns: 'http://www.w3.org/2000/svg',
    });

    let maxVal = 1;
    for (let hour = 0; hour < 24; hour += 1) {
        maxVal = Math.max(maxVal, hourlyCount(hourlyQps, hour));
    }

    for (let hour = 0; hour < 24; hour += 1) {
        const count = hourlyCount(hourlyQps, hour);
        const h = Math.round((count * 120) / maxVal);
        svg.appendChild(svgEl('rect', {
            x: hour * 20,
            y: 140 - h,
            width: 18,
            height: Math.max(h, 0),
            fill: '#0d6efd',
            rx: 2,
        }));
        const label = svgEl('text', {
            x: hour * 20 + 9,
            y: 155,
            'text-anchor': 'middle',
            'font-size': 9,
            fill: '#6c757d',
        });
        label.textContent = String(hour);
        svg.appendChild(label);
    }
    return svg;
}

function renderStatusBar(successCount, errorCount, blockedCount) {
    const svg = svgEl('svg', {
        width: '100%',
        height: '160',
        viewBox: '0 0 480 160',
        xmlns: 'http://www.w3.org/2000/svg',
    });

    const total = successCount + errorCount + blockedCount;
    const sW = total > 0 ? (successCount * 460) / total : 0;
    const eW = total > 0 ? (errorCount * 460) / total : 0;
    const bW = total > 0 ? (blockedCount * 460) / total : 0;

    const segments = [
        { x: 10, w: sW, fill: '#198754', textFill: 'white', label: `${successCount} 成功` },
        { x: 10 + sW, w: eW, fill: '#dc3545', textFill: 'white', label: `${errorCount} 错误` },
        { x: 10 + sW + eW, w: bW, fill: '#ffc107', textFill: 'black', label: `${blockedCount} 拦截` },
    ];

    for (const seg of segments) {
        if (seg.w <= 0) {
            continue;
        }
        svg.appendChild(svgEl('rect', {
            x: seg.x,
            y: 30,
            width: seg.w,
            height: 40,
            fill: seg.fill,
            rx: 4,
        }));
        if (seg.w >= 40) {
            const text = svgEl('text', {
                x: seg.x + seg.w / 2,
                y: 55,
                'text-anchor': 'middle',
                fill: seg.textFill,
                'font-size': 12,
            });
            text.textContent = seg.label;
            svg.appendChild(text);
        }
    }

    if (total === 0) {
        const empty = svgEl('text', {
            x: 240,
            y: 55,
            'text-anchor': 'middle',
            fill: '#6c757d',
            'font-size': 12,
        });
        empty.textContent = '暂无数据';
        svg.appendChild(empty);
    }
    return svg;
}

function fillTopSql(tbody, topSql) {
    tbody.replaceChildren();
    if (!topSql || topSql.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 3;
        td.className = 'text-center text-muted';
        td.textContent = '暂无数据';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }
    for (const row of topSql) {
        const tr = document.createElement('tr');
        const sqlTd = document.createElement('td');
        sqlTd.textContent = abbreviate(row.sqlText, 80);
        sqlTd.title = row.sqlText || '';
        const countTd = document.createElement('td');
        countTd.textContent = formatNumber(row.count);
        const durTd = document.createElement('td');
        durTd.textContent = formatDurationMs(row.avgDuration);
        tr.append(sqlTd, countTd, durTd);
        tbody.appendChild(tr);
    }
}

function fillPoolStatus(tbody, poolStatus) {
    tbody.replaceChildren();
    if (!poolStatus || poolStatus.length === 0) {
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 5;
        td.className = 'text-center text-muted';
        td.textContent = '无活跃连接池';
        tr.appendChild(td);
        tbody.appendChild(tr);
        return;
    }
    for (const p of poolStatus) {
        const tr = document.createElement('tr');
        for (const key of ['name', 'active', 'idle', 'total', 'waiting']) {
            const td = document.createElement('td');
            td.textContent = p[key] == null ? '-' : String(p[key]);
            tr.appendChild(td);
        }
        tbody.appendChild(tr);
    }
}

export async function render(container) {
    container.innerHTML = `
        <h2 class="mb-3">仪表盘</h2>
        <div class="text-muted">加载中…</div>
    `;

    let stats;
    try {
        stats = await api.get('/api/dashboard/stats');
    } catch (err) {
        container.innerHTML = '<div class="alert alert-danger" role="alert"></div>';
        container.querySelector('.alert').textContent = err.message || '加载仪表盘失败';
        alert.error(err.message || '加载仪表盘失败');
        return;
    }

    const successCount = Number(stats.successCount) || 0;
    const errorCount = Number(stats.errorCount) || 0;
    const blockedCount = Number(stats.blockedCount) || 0;

    container.innerHTML = `
        <div class="row mb-4">
            <div class="col-md-3">
                <div class="card card-stat">
                    <div class="card-body">
                        <div class="stat-value" data-stat="total"></div>
                        <div class="stat-label">近24h查询量</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card card-stat">
                    <div class="card-body">
                        <div class="stat-value text-danger" data-stat="errorRate"></div>
                        <div class="stat-label">错误率</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card card-stat">
                    <div class="card-body">
                        <div class="stat-value" data-stat="avgDuration"></div>
                        <div class="stat-label">平均耗时</div>
                    </div>
                </div>
            </div>
            <div class="col-md-3">
                <div class="card card-stat">
                    <div class="card-body">
                        <div class="stat-value">
                            <span class="text-success" data-stat="success"></span> /
                            <span class="text-danger" data-stat="error"></span> /
                            <span class="text-warning" data-stat="blocked"></span>
                        </div>
                        <div class="stat-label">成功/错误/拦截</div>
                    </div>
                </div>
            </div>
        </div>

        <div class="row mb-4">
            <div class="col-md-6">
                <h5>近24h 按小时 QPS</h5>
                <div data-chart="hourly"></div>
            </div>
            <div class="col-md-6">
                <h5>状态占比</h5>
                <div data-chart="status"></div>
            </div>
        </div>

        <div class="row mb-4">
            <div class="col-md-6">
                <h5>Top 5 高频 SQL</h5>
                <table class="table table-sm table-hover">
                    <thead class="table-light">
                        <tr><th>SQL</th><th>次数</th><th>平均耗时</th></tr>
                    </thead>
                    <tbody data-table="topSql"></tbody>
                </table>
            </div>
            <div class="col-md-6">
                <h5>连接池状态</h5>
                <table class="table table-sm table-hover">
                    <thead class="table-light">
                        <tr><th>连接</th><th>活跃</th><th>空闲</th><th>总数</th><th>等待</th></tr>
                    </thead>
                    <tbody data-table="pool"></tbody>
                </table>
            </div>
        </div>
    `;

    container.querySelector('[data-stat="total"]').textContent = formatNumber(stats.totalLast24h);
    container.querySelector('[data-stat="errorRate"]').textContent = formatPercent(stats.errorRate);
    container.querySelector('[data-stat="avgDuration"]').textContent = formatDurationMs(stats.avgDuration);
    container.querySelector('[data-stat="success"]').textContent = formatNumber(successCount);
    container.querySelector('[data-stat="error"]').textContent = formatNumber(errorCount);
    container.querySelector('[data-stat="blocked"]').textContent = formatNumber(blockedCount);

    container.querySelector('[data-chart="hourly"]').appendChild(renderHourlyChart(stats.hourlyQps));
    container.querySelector('[data-chart="status"]').appendChild(
        renderStatusBar(successCount, errorCount, blockedCount),
    );
    fillTopSql(container.querySelector('[data-table="topSql"]'), stats.topSql);
    fillPoolStatus(container.querySelector('[data-table="pool"]'), stats.poolStatus);
}

export default { render };
