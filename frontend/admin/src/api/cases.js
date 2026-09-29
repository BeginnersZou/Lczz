import request from '@/utils/request'
import { getFileId, uploadFileApi } from './files'

function normalizeFile(file) {
  const value = typeof file === 'string' ? { url: file } : (file || {})
  const originalUrl = value.originalUrl || value.url || ''
  return {
    ...value,
    id: getFileId(value),
    url: originalUrl,
    originalUrl,
    displayUrl: value.displayUrl || originalUrl,
    thumbnailUrl: value.thumbnailUrl || value.displayUrl || originalUrl
  }
}

function normalizeCase(item = {}) {
  return {
    ...item,
    siteName: item.siteName || '',
    coverImage: item.coverImage ? normalizeFile(item.coverImage) : null,
    images: (item.images || []).map(normalizeFile).filter(image => image.id || image.url)
  }
}

function toPayload(data = {}) {
  return {
    siteName: String(data.siteName || '').trim(),
    imageFileIds: (data.imageFileIds || data.images || []).map(getFileId).filter(Boolean)
  }
}

export function getProjectCaseListApi(params) {
  return request({ url: '/cases/list', method: 'get', params })
    .then(result => ({ ...result, list: (result?.list || []).map(normalizeCase) }))
}

export function getProjectCaseDetailApi(id) {
  return request({ url: `/cases/${id}`, method: 'get' }).then(normalizeCase)
}

export function createProjectCaseApi(data) {
  return request({ url: '/cases', method: 'post', data: toPayload(data) })
}

export function updateProjectCaseApi(id, data) {
  return request({ url: `/cases/${id}`, method: 'put', data: toPayload(data) })
}

export function deleteProjectCaseApi(id) {
  return request({ url: `/cases/${id}`, method: 'delete' })
}

export function uploadProjectCaseImageApi(formData) {
  return uploadFileApi(formData)
}
