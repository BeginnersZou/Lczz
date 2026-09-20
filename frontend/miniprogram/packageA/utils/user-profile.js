export function isInstallerProfile(userInfo = {}) {
  const role = String(userInfo.role || '').toLowerCase()
  const roles = Array.isArray(userInfo.roles)
    ? userInfo.roles.map(value => String(value).toLowerCase())
    : []
  return role === 'installer' || roles.includes('installer')
}

export function profileForm(userInfo = {}) {
  return {
    nickname: String(userInfo.nickname || userInfo.name || ''),
    realName: String(userInfo.realName || '')
  }
}

export function validateUserProfile(form = {}, userInfo = {}) {
  const nickname = String(form.nickname || '').trim()
  const realName = String(form.realName || '').trim()
  if (!nickname) return '请填写昵称'
  if (nickname.length > 64) return '昵称不能超过64字'
  if (realName.length > 64) return '真实姓名不能超过64字'
  if (isInstallerProfile(userInfo) && !realName) return '安装师傅必须填写真实姓名'
  return ''
}

export function userProfilePayload(form = {}) {
  return {
    nickname: String(form.nickname || '').trim(),
    realName: String(form.realName || '').trim()
  }
}
