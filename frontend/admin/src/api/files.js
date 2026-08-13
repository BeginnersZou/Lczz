import request from '@/utils/request'

export function uploadFileApi(formData) {
  return request({
    url: '/files/upload',
    method: 'post',
    data: formData
  })
}

export function bindFileApi(fileId, data) {
  return request({
    url: `/files/${fileId}/relations`,
    method: 'post',
    data
  })
}

export function getFileId(file) {
  if (typeof file === 'number' && Number.isFinite(file)) return file
  if (typeof file === 'string' && /^\d+$/.test(file)) return Number(file)
  if (file?.id != null) return Number(file.id)
  const match = String(file?.url || file?.previewUrl || file || '').match(/\/files\/(?:access\/)?(\d+)/)
  return match ? Number(match[1]) : null
}
