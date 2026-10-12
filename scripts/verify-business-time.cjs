const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { spawnSync } = require('node:child_process')

const root = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')

function loadTime(relative) {
  const context = vm.createContext({ Date, Number, String })
  vm.runInContext(read(relative).replace(/export /g, ''), context)
  return {
    formatDateTime: context.formatDateTime,
    formatDate: context.formatDate,
    formatDateTimeInput: context.formatDateTimeInput,
    parseDateTime: context.parseDateTime,
    timeTransFrom: context.timeTransFrom
  }
}

async function verify() {
  const admin = loadTime('frontend/admin/src/utils/format.js')
  const mini = loadTime('frontend/miniprogram/utils/time.js')
  const instant = Date.parse('2026-10-12T02:36:00Z')
  const cases = [
    ['2026-10-12T02:36:00Z', '2026-10-12 10:36'],
    ['2026-10-12T10:36:00+08:00', '2026-10-12 10:36'],
    ['2026-10-12T10:36:00+0800', '2026-10-12 10:36'],
    ['2026-10-12T10:36:00', '2026-10-12 10:36'],
    ['2026-10-12 10:36', '2026-10-12 10:36'],
    ['2026-10-12T02:36:00.123456789Z', '2026-10-12 10:36'],
    ['2026-10-11T16:15:00Z', '2026-10-12 00:15'],
    ['2026-12-31T16:15:00Z', '2027-01-01 00:15'],
    ['2026-10-12', '2026-10-12 00:00'],
    [instant, '2026-10-12 10:36'],
    [instant / 1000, '2026-10-12 10:36'],
    [String(instant), '2026-10-12 10:36'],
    [String(instant / 1000), '2026-10-12 10:36'],
    [new Date(instant), '2026-10-12 10:36'],
    [946684800000, '2000-01-01 08:00'],
    ['946684800000', '2000-01-01 08:00']
  ]
  for (const formatter of [admin, mini]) {
    for (const [input, expected] of cases) assert.equal(formatter.formatDateTime(input), expected, String(input))
    assert.equal(formatter.formatDateTime('2026-10-12T02:36:25Z', { seconds: true }), '2026-10-12 10:36:25')
    assert.equal(formatter.formatDateTime(formatter.formatDateTime('2026-10-12T02:36:00Z')), '2026-10-12 10:36')
    assert.equal(formatter.formatDateTime('invalid-time'), 'invalid-time')
    assert.equal(formatter.parseDateTime('invalid-time'), null)
  }
  assert.equal(admin.formatDateTime(null), '-')
  assert.equal(mini.formatDateTime(null), '')
  assert.equal(mini.timeTransFrom(instant / 1000), '2026-10-12 10:36')
  assert.equal(mini.timeTransFrom(946684800), '2000-01-01 08:00')
  assert.equal(admin.formatDate('2026-10-11T16:15:00Z'), '2026-10-12')
  assert.equal(admin.formatDateTimeInput('2026-10-12T02:00:00Z'), '2026-10-12T10:00:00')
  assert.equal(admin.formatDateTimeInput('2026-10-12T10:00:00+08:00'), '2026-10-12T10:00:00')
  assert.equal(admin.formatDateTimeInput('2026-10-12T10:00:00'), '2026-10-12T10:00:00')
  assert.equal(admin.formatDateTimeInput(null), '')
  assert.equal(admin.parseDateTime(admin.formatDateTimeInput('2026-10-12T02:00:00Z')).toISOString(), '2026-10-12T02:00:00.000Z')

  // Run the real order export and CSV generator, including their date value callbacks.
  const csvSource = read('frontend/admin/src/utils/export.js').replace(/export /g, '')
  const createCsvBlob = new Function(csvSource + '\nreturn createCsvBlob')()
  const ordersSource = read('frontend/admin/src/api/orders.js').replace(/^import .+$/gm, '').replace(/export /g, '')
  const request = async options => {
    assert.equal(options.url, '/orders/list')
    return { total: 2, list: [
      { id: 1, orderNo: 'WO-TIME', createdAt: '2026-10-11T16:15:00Z', customerConfirmedAt: '2026-10-12T02:36:00Z' },
      { id: 2, orderNo: 'WO-EMPTY' }
    ] }
  }
  const exportOrders = new Function('request', 'createCsvBlob', 'formatDateTime', ordersSource + '\nreturn exportOrdersApi')(
    request, createCsvBlob, admin.formatDateTime)
  const csv = await (await exportOrders({})).text()
  assert.match(csv, /2026-10-12 00:15:00/)
  assert.match(csv, /2026-10-12 10:36:00/)
  assert.doesNotMatch(csv, /2026-10-11T16:15/)
  assert.match(csv, /"WO-EMPTY"[^\r\n]*,"",""$/)
  console.log(`PASS: zoned/legacy times, timestamps, midnight, appointment models and CSV (${process.env.TZ})`)
}

if (process.argv[2] === '--child') {
  verify().catch(error => { console.error(error); process.exitCode = 1 })
} else {
  for (const zone of ['Asia/Shanghai', 'UTC', 'America/Los_Angeles']) {
    const result = spawnSync(process.execPath, [__filename, '--child'], {
      env: { ...process.env, TZ: zone }, encoding: 'utf8'
    })
    process.stdout.write(result.stdout || '')
    process.stderr.write(result.stderr || '')
    assert.equal(result.status, 0, `Business time checks failed in ${zone}`)
  }
}
