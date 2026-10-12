import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../utils/company-location.js', import.meta.url), 'utf8')
const { COMPANY_LOCATION, openCompanyLocation } = await import(
  `data:text/javascript;base64,${Buffer.from(source).toString('base64')}`
)

test('opens the confirmed company location with the correct coordinates', () => {
  let options
  openCompanyLocation({ openLocation: value => { options = value }, showModal() {} })
  assert.equal(options.name, '力创之尊')
  assert.equal(options.address, '湖北省武汉市江岸区不锈钢路S17-49-51号')
  assert.equal(options.longitude, 114.306997)
  assert.equal(options.latitude, 30.665673)
  assert.equal(options.scale, 18)
})

test('shows a clear message when the map cannot be opened', () => {
  let modal
  let options
  openCompanyLocation({
    openLocation: value => { options = value },
    showModal: value => { modal = value }
  })
  options.fail()
  assert.equal(modal.title, '无法打开地图')
  assert.match(modal.content, /定位服务或地图应用/)
})
