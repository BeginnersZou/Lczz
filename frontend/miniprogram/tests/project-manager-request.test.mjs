import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../utils/request.js', import.meta.url), 'utf8')
  .replace(/^import .+$/gm, '').replace('export default http', 'return http')

function client(reply) {
  const calls = { requests: [], modals: [], toasts: [], logout: 0, login: 0 }
  const uni = {
    showLoading() {}, hideLoading() {},
    showModal: value => calls.modals.push(value),
    showToast: value => calls.toasts.push(value),
    request(options) {
      calls.requests.push(options)
      const response = reply(options)
      options.success({ statusCode: response.code, data: response })
      options.complete()
    }
  }
  const http = new Function('baseUrl', 'clearAuthSession', 'getAuthToken', 'getAuthUserInfo',
    'showLoginChoice', 'uni', source)(
    'https://example.test/api/v1', () => calls.logout++, () => 'manager-token',
    () => ({ role: 'project_manager' }), () => calls.login++, uni)
  return { http, calls }
}

test('project manager write denial shows no-permission dialog even for a silent request', async () => {
  const { http, calls } = client(() => ({ code: 403, error: 'FORBIDDEN', message: '无权访问该资源' }))
  const result = await http.post('/orders/1/progress', {}, { silent: true })
  assert.equal(result.code, 403)
  assert.equal(calls.modals.length, 1)
  assert.equal(calls.modals[0].title, '无操作权限')
  assert.equal(calls.login, 0)
  assert.equal(calls.logout, 0)
})

test('project manager logout and account cancellation requests remain available', async () => {
  const { http, calls } = client(() => ({ code: 200, data: true }))
  assert.equal((await http.post('/auth/logout')).code, 200)
  assert.equal((await http.post('/auth/account/cancel', { confirmed: true })).code, 200)
  assert.deepEqual(calls.requests.map(call => new URL(call.url).pathname),
    ['/api/v1/auth/logout', '/api/v1/auth/account/cancel'])
  assert.equal(calls.modals.length, 0)
})
