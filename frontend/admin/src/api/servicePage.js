import request from '@/utils/request'
import { getFileId, uploadFileApi } from './files'

function normalizeFile(file = {}) {
  return { id: getFileId(file), url: file.url || '' }
}

export function normalizeServicePage(data = {}) {
  return {
    ...data,
    companyName: data.companyName || '',
    companySubtitle: data.companySubtitle || '',
    slogan: data.slogan || '',
    profileText: data.profileText || '',
    phonePrimary: data.phonePrimary || '',
    phoneSecondary: data.phoneSecondary || '',
    address: data.address || '',
    businessHours: data.businessHours || '',
    heroStats: (data.heroStats || []).map(item => ({ title: item.title || '', description: item.description || '' })),
    services: (data.services || []).map(item => ({ title: item.title || '', description: item.description || '' })),
    profileTags: [...(data.profileTags || [])],
    galleryImages: (data.galleryImages || []).map(normalizeFile).filter(file => file.id),
    advantages: (data.advantages || []).map(item => ({ title: item.title || '', description: item.description || '' }))
  }
}

export function getServicePageApi() {
  return request({ url: '/admin/service-page', method: 'get' }).then(normalizeServicePage)
}

export function updateServicePageApi(data) {
  return request({
    url: '/admin/service-page',
    method: 'put',
    data: {
      ...data,
      galleryImageFileIds: (data.galleryImages || []).map(getFileId).filter(Boolean)
    }
  }).then(normalizeServicePage)
}

export function uploadServiceImageApi(formData) {
  return uploadFileApi(formData)
}
