import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const pageSource = readFileSync(new URL('../src/views/consumables/consumableslist.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../src/api/consumables.js', import.meta.url), 'utf8')

test('consumables table exposes consistent text actions for all sort operations', () => {
  for (const [label, action] of [['置顶', 'TOP'], ['上移', 'UP'], ['下移', 'DOWN']]) {
    assert.match(pageSource, new RegExp(`handleSort\\(scope\\.row, '${action}'\\).*${label}`, 's'))
  }
  assert.match(pageSource, /<el-button text type="primary"/)
})

test('successful sorting refreshes the current table from backend', () => {
  assert.match(apiSource, /export function sortConsumableApi\(id, action\)/)
  assert.match(apiSource, /url: `\/consumables\/\$\{id\}\/sort`/)
  assert.match(pageSource, /const result = await sortConsumableApi\(row\.id, action\)[\s\S]*await loadList\(\)/)
})
