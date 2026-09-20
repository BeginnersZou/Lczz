import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const helperUrl = new URL('../packageA/utils/user-profile.js', import.meta.url)
const helperSource = readFileSync(helperUrl, 'utf8')
const helpers = await import(`data:text/javascript;base64,${Buffer.from(helperSource).toString('base64')}`)
const { isInstallerProfile, profileForm, userProfilePayload, validateUserProfile } = helpers

test('profile form keeps nickname and real name as separate fields', () => {
  assert.deepEqual(profileForm({ nickname: '昵称', realName: '姓名', name: '展示名' }), {
    nickname: '昵称', realName: '姓名'
  })
  assert.deepEqual(profileForm({ name: '旧版展示名' }), { nickname: '旧版展示名', realName: '' })
  assert.deepEqual(userProfilePayload({ nickname: '  昵称  ', realName: '  姓名  ' }), {
    nickname: '昵称', realName: '姓名'
  })
})

test('profile helper stays inside packageA instead of the main package', () => {
  assert.equal(existsSync(new URL('../utils/user-profile.js', import.meta.url)), false)
  const pageSource = readFileSync(new URL('../packageA/profile/profile.vue', import.meta.url), 'utf8')
  assert.match(pageSource, /from '\.\.\/utils\/user-profile\.js'/)
})

test('installer detection supports primary and multi-role accounts', () => {
  assert.equal(isInstallerProfile({ role: 'INSTALLER' }), true)
  assert.equal(isInstallerProfile({ role: 'customer', roles: ['INSTALLER'] }), true)
  assert.equal(isInstallerProfile({ role: 'customer', roles: ['DEALER'] }), false)
})

test('validation requires nickname and installer real name with backend length limits', () => {
  assert.equal(validateUserProfile({ nickname: '用户', realName: '' }, { role: 'customer' }), '')
  assert.match(validateUserProfile({ nickname: ' ', realName: '' }, { role: 'customer' }), /昵称/)
  assert.match(validateUserProfile({ nickname: '用户', realName: ' ' }, { role: 'installer' }), /真实姓名/)
  assert.match(validateUserProfile({ nickname: '字'.repeat(65), realName: '' }, { role: 'customer' }), /64/)
  assert.match(validateUserProfile({ nickname: '用户', realName: '字'.repeat(65) }, { role: 'customer' }), /64/)
})

const require = createRequire(new URL('../../admin/package.json', import.meta.url))
const { ref, reactive, computed } = require('vue')
const { parse, compileScript, compileTemplate } = require('@vue/compiler-sfc')
const pageSource = readFileSync(new URL('../packageA/profile/profile.vue', import.meta.url), 'utf8')
const pageScript = pageSource.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')

function profilePage({ current, updated } = {}) {
  let showHook
  let saved
  let updatePayload
  let navigatedBack = false
  const uni = {
    showToast() {}, switchTab() {}, navigateBack() { navigatedBack = true }
  }
  const authApi = {
    getUserInfo: async () => ({ code: 200, data: current }),
    updateProfile: async payload => { updatePayload = payload; return { code: 200, data: updated } }
  }
  const names = ['ref', 'reactive', 'computed', 'onShow', 'authApi', 'getAuthToken', 'getAuthUserInfo',
    'saveAuthUserInfo', 'uni', 'setTimeout', ...Object.keys(helpers)]
  const values = [ref, reactive, computed, hook => { showHook = hook }, authApi, () => 'token', () => current,
    value => { saved = value; return true }, uni, callback => callback(), ...Object.values(helpers)]
  const instance = new Function(...names, `${pageScript}\nreturn { loading, submitting, userInfo, form, installer, loadProfile, saveProfile };`)(...values)
  return { instance, runShow: () => showHook(), state: () => ({ saved, updatePayload, navigatedBack }) }
}

test('profile page refreshes from server, saves returned user and navigates back', async () => {
  const current = { role: 'customer', nickname: '旧昵称', realName: '' }
  const updated = { role: 'customer', nickname: '新昵称', realName: '张三' }
  const page = profilePage({ current, updated })
  await page.runShow()
  assert.equal(page.instance.form.nickname, '旧昵称')
  page.instance.form.nickname = '  新昵称 '
  page.instance.form.realName = ' 张三 '
  await page.instance.saveProfile()
  assert.deepEqual(page.state().updatePayload, { nickname: '新昵称', realName: '张三' })
  assert.strictEqual(page.state().saved, updated)
  assert.equal(page.state().navigatedBack, true)
})

test('profile and settings pages compile and the profile route is registered', () => {
  for (const relative of ['../packageA/profile/profile.vue', '../packageA/settings/settings.vue']) {
    const filename = new URL(relative, import.meta.url).pathname
    const source = readFileSync(new URL(relative, import.meta.url), 'utf8')
    const parsed = parse(source, { filename })
    assert.equal(parsed.errors.length, 0)
    const script = compileScript(parsed.descriptor, { id: `issue134-${relative}`, inlineTemplate: false })
    const template = compileTemplate({
      source: parsed.descriptor.template.content,
      filename,
      id: `issue134-${relative}`,
      compilerOptions: { bindingMetadata: script.bindings }
    })
    assert.deepEqual(template.errors, [])
  }
  const pages = JSON.parse(readFileSync(new URL('../pages.json', import.meta.url), 'utf8'))
  const packagePages = pages.subPackages.find(value => value.root === 'packageA').pages
  assert.ok(packagePages.some(value => value.path === 'profile/profile'))
})
