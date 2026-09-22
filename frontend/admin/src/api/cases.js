import request from '@/utils/request'
import { getFileId, uploadFileApi } from './files'

function normalizeFile(file) {
  return {
    ...(typeof file === 'object' ? file : {}),
    id: getFileId(file),
    url: typeof file === 'string' ? file : (file?.url || '')
  }
}

function normalizeCase(item = {}) {
  return {
    ...item,
    siteName: item.siteName || '',
    coverImage: item.coverImage ? normalizeFile(item.coverImage) : null,
    imageCount: item.imageCount ?? (Array.isArray(item.images) ? item.images.length : null),
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
