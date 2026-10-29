import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../utils/auth-session.js', import.meta.url), 'utf8')
const { isValidAuthUser, getRoleLabel } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)

test('sales and project manager have a valid single-role mini-program session', () => {
  for (const [role, label] of [['sales', '销售员'], ['project_manager', '项目经理']]) {
    assert.equal(isValidAuthUser({ role, roles: [role] }), true)
    assert.equal(getRoleLabel(role), label)
  }
})

test('inconsistent or multiple server roles are rejected, never merged into permissions', () => {
  assert.equal(isValidAuthUser({ role: 'customer', roles: ['customer', 'admin'] }), false)
  assert.equal(isValidAuthUser({ role: 'customer', roles: ['admin'] }), false)
  assert.equal(isValidAuthUser({ role: 'unknown' }), false)
})
