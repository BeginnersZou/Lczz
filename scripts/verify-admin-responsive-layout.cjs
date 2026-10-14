// Real browser layout checks against a local Vite instance; all API responses are fixtures.
// Install playwright-core separately and set LCZZ_PLAYWRIGHT_MODULE / LCZZ_BROWSER_EXECUTABLE if needed.
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require(process.env.LCZZ_PLAYWRIGHT_MODULE || 'playwright-core')
const baseUrl = process.env.LCZZ_QA_URL || 'http://127.0.0.1:5187'
assert.ok(['127.0.0.1', 'localhost'].includes(new URL(baseUrl).hostname), 'Use a local test instance')
const output = process.env.LCZZ_QA_OUTPUT || path.resolve('.runtime/admin-layout-180')
fs.mkdirSync(output, { recursive: true })
const admin = { id: 1, username: 'layout-test', name: '系统管理员', role: 'admin', roles: ['admin'] }
const orders = Array.from({ length: 6 }, (_, index) => ({ id: index + 1, orderNo: `WO20261014001${index}`,
  taskType: '空调安装', description: '安装室内外机、管线及现场调试', customerName: `测试客户${index + 1}`, customerPhone: '13800000001',
  projectAddress: '武汉市测试项目地址', statusCode: 'IN_PROGRESS', orderSource: 'ADMIN', selectedMasterList: [{ masterName: '测试师傅' }], createdAt: '2026-10-14T10:00:00+08:00' }))
const consumables = Array.from({ length: 6 }, (_, index) => ({ id: index + 1, name: `PVC弯头${index + 1}`, productCode: `P${index}`,
  category: ['管材', 'PVC管件'], spec: '25mm', unit: '个', stock: 10, skuCount: 1, enabled: true, createdAt: '2026-10-14T10:00:00+08:00', skus: [] }))
function reply(url) {
  const pathname = new URL(url).pathname.replace(/^\/api\/v1/, '')
  if (pathname === '/auth/info') return admin
  if (pathname === '/orders/list') return { list: orders, total: 36 }
  if (pathname === '/consumables/list') return { list: consumables, total: 36 }
  if (pathname === '/consumables/categories') return [{ id: 1, name: '管材', parentId: null, children: [{ id: 2, name: 'PVC管件', parentId: 1 }] }]
  if (pathname === '/users/list') return { list: [{ ...admin, nickname: '测试管理员', realName: '测试管理员', accountStatus: 'ENABLED', blacklist: false }], total: 1 }
  if (pathname === '/preparation/list') return { list: orders.map(order => ({ ...order, source: 'W', productName: '工程耗材', submitterName: '测试师傅', statusCode: 'PENDING', statusLabel: '待备货', materials: [{ name: 'PVC弯头', count: 2, unit: '个', spec: '25mm' }] })), total: 36 }
  if (pathname === '/admin/cases' || pathname === '/cases/list') return { list: [], total: 0 }
  if (pathname === '/admin/service-page') return { companyName: '力创之尊', companySubtitle: '专业机电服务', slogan: '服务安心', profileText: '测试公司介绍', services: [{ title: '安装服务', description: '上门服务' }], heroStats: [{ title: '专业', description: '规范施工' }], advantages: [{ title: '标准施工', description: '可靠交付' }] }
  if (pathname === '/dashboard/overview') return { todayOrders: 6, pendingAssign: 2, processingOrders: 4, completedOrders: 3, stockAlerts: 1 }
  if (pathname === '/dashboard/order-trend') return { xAxis: ['10-12', '10-13', '10-14'], values: [2, 5, 6] }
  if (pathname === '/dashboard/order-status') return [{ name: '处理中', value: 4 }, { name: '已完成', value: 3 }]
  if (pathname === '/regions/provinces' || pathname === '/regions/tree') return []
  throw Error(`Unexpected fixture request: ${pathname}`)
}
async function setup(browser, width) {
  const context = await browser.newContext({ viewport: { width, height: 1000 }, reducedMotion: 'reduce' })
  await context.addInitScript(info => { localStorage.setItem('token', 'layout-only-token'); localStorage.setItem('user', JSON.stringify(info)) }, admin)
  await context.route('**/api/v1/**', async route => {
    assert.equal(route.request().method(), 'GET', 'Layout verification must not write business data')
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'success', data: reply(route.request().url()) }) })
  })
  const page = await context.newPage()
  page.on('pageerror', error => { throw error })
  return { context, page }
}
async function layout(page, route) {
  await page.goto(baseUrl + route)
  await page.locator('.main-content > *').waitFor()
  await page.locator('.el-loading-mask').waitFor({ state: 'hidden' }).catch(() => {})
  await page.waitForTimeout(160)
  return page.evaluate(() => {
    const bounds = selector => { const { x, y, width, height, right } = document.querySelector(selector).getBoundingClientRect(); return { x, y, width, height, right } }
    const controls = [...document.querySelectorAll('.page-header .el-button, .search-card .el-input, .search-card .el-select, .filter-card .el-input, .filter-card .el-select')].filter(el => el.getBoundingClientRect().width > 0)
    return { width: innerWidth, sidebar: bounds('.sidebar'), main: bounds('.main-content'), content: bounds('.main-content > *'),
      gutter: parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--workspace-gutter')),
      bodyWidth: document.documentElement.scrollWidth,
      table: document.querySelector('.business-table') ? bounds('.business-table') : null,
      controls: controls.map(el => { const { x, right } = el.getBoundingClientRect(); return { x, right } }) }
  })
}
async function main() {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.LCZZ_BROWSER_EXECUTABLE || undefined })
  const results = []
  try {
    for (const width of [375, 900, 1024, 1280, 1366, 1920, 2560, 3816, 3840]) {
      const { context, page } = await setup(browser, width)
      try {
        for (const route of ['/orders', '/consumables', '/preparation', '/users', '/cases', '/orders/form', '/consumables/form', '/cases/form', '/service', '/dashboard']) {
          const info = await layout(page, route)
          assert.ok(info.bodyWidth <= width + 1, `${width} ${route}: document horizontal overflow`)
          const expectedLeft = width <= 900 ? info.gutter : info.sidebar.right + info.gutter
          assert.ok(Math.abs(info.content.x - expectedLeft) <= 1, `${width} ${route}: content separated from sidebar (${info.content.x} vs ${expectedLeft})`)
          assert.ok(info.content.right <= width - info.gutter + 1, `${width} ${route}: page exceeds viewport`)
          for (const control of info.controls) assert.ok(control.x >= info.main.x && control.right <= info.main.right + 1, `${width} ${route}: inaccessible filter/action`)
          if (width >= 1280 && ['/orders', '/consumables'].includes(route)) {
            const input = await page.locator('.search-card .el-input').first().boundingBox()
            const search = await page.locator('.search-card').getByRole('button', { name: '搜索', exact: true }).boundingBox()
            assert.ok(Math.abs(input.y - search.y) <= 2, 'Filters and search action should share a row when there is room')
          }
          if (info.table) assert.ok(info.table.width <= info.content.width + 1, `${width} ${route}: entire table exceeds container`)
          if (['/orders', '/consumables', '/preparation', '/users'].includes(route)) {
            assert.ok(Math.abs(info.content.width - (info.main.width - info.gutter * 2)) <= 1, `${width} ${route}: list fails to use available width`)
            const scroll = page.locator('.business-table .el-scrollbar__wrap').first()
            const initial = await scroll.evaluate(el => el.scrollLeft)
            assert.equal(initial, 0, 'First columns must start visible')
            const first = await page.locator('.business-table th').first().boundingBox()
            assert.ok(first.x >= info.table.x - 1, 'First column must not be clipped')
            if (width <= 900) {
              const actions = await page.getByRole('columnheader', { name: '操作', exact: true }).boundingBox()
              assert.ok(actions.x >= first.x + first.width, 'Pinned actions must not cover the first columns on narrow screens')
            }
            await scroll.evaluate(el => { el.scrollLeft = el.scrollWidth })
            assert.ok(await page.getByRole('columnheader', { name: '操作', exact: true }).isVisible())
            const actions = await page.getByRole('columnheader', { name: '操作', exact: true }).boundingBox()
            assert.ok(actions.x >= info.table.x - 1 && actions.x + actions.width <= info.table.right + 1, 'Actions must be reachable inside the table scroll area')
            await scroll.evaluate(el => { el.scrollLeft = 0 })
          }
          if (width === 3816 && route === '/orders' || width === 1366 && route === '/orders' || width === 1024 && route === '/consumables' || width === 375 && route === '/users') {
            await page.screenshot({ path: path.join(output, `${route.slice(1)}-${width}.png`) })
          }
          results.push({ width, route, contentLeft: info.content.x, contentWidth: info.content.width, gutter: info.gutter })
          if (route === '/users') {
            await page.getByRole('button', { name: '新增用户', exact: true }).click()
            const dialog = page.getByRole('dialog', { name: '新增用户', exact: true })
            await dialog.waitFor()
            await page.waitForTimeout(160)
            const box = await dialog.locator('.el-dialog').boundingBox()
            if (width === 375 || width === 1024) await page.screenshot({ path: path.join(output, `user-dialog-${width}.png`) })
            assert.ok(box.x >= info.gutter - 1 && box.x + box.width <= width - info.gutter + 1, `User dialog must fit viewport: ${width} ${JSON.stringify(box)}`)
            for (const input of await dialog.locator('.el-input, .el-select').all()) {
              const field = await input.boundingBox()
              if (field) assert.ok(field.x >= box.x && field.x + field.width <= box.x + box.width + 1, 'Dialog field exceeds bounds')
            }
            await dialog.getByRole('button', { name: '取消', exact: true }).click()
          }
        }
        console.log(`PASS: ${width}px lists, filters, tables, forms, service page and dashboard`)
        if (width === 1920) {
          await page.goto(baseUrl + '/dashboard'); await page.locator('.chart-container canvas').waitFor()
          const before = await page.locator('.chart-container').boundingBox()
          await page.getByRole('button', { name: '收起导航菜单', exact: true }).click()
          await page.waitForTimeout(400)
          const after = await page.locator('.chart-container').boundingBox()
          assert.ok(after.width > before.width, 'Charts must follow sidebar collapse')
          const canvas = await page.locator('.chart-container canvas').first().boundingBox()
          assert.ok(Math.abs(canvas.width - (after.width - 40)) <= 2, 'Chart canvas must resize with its actual container')
          await page.setViewportSize({ width: 375, height: 1000 }); await page.waitForTimeout(180)
          await page.getByRole('button', { name: '打开导航菜单', exact: true }).click()
          await page.locator('.sidebar.mobile-open').waitFor()
          assert.ok(await page.locator('.logo-text').isVisible(), 'Collapsed desktop must become a full mobile drawer')
          await page.locator('.sidebar .el-menu-item').filter({ hasText: '订单管理' }).click()
          await page.waitForTimeout(180)
          assert.equal(await page.locator('.sidebar.mobile-open').count(), 0, 'Selecting a route must close the mobile drawer')
          await page.setViewportSize({ width: 1920, height: 1000 }); await page.waitForTimeout(240)
          assert.ok(Math.abs((await page.locator('.sidebar').boundingBox()).width - 64) <= 1, 'Desktop preference must survive breakpoint changes')
          console.log('PASS: menu collapse, full mobile drawer, route selection, breakpoint recovery and chart resize')
        }
      } finally { await context.close() }
    }
    fs.writeFileSync(path.join(output, 'layout-results.json'), JSON.stringify(results, null, 2))
    console.log(`PASS: ${results.length} screen/page combinations; screenshots in ${output}`)
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
