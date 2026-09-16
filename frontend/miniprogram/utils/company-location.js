export const COMPANY_LOCATION = Object.freeze({
  name: '力创之尊',
  address: '湖北省武汉市江岸区不锈钢路S17-49-51号',
  longitude: 114.306997,
  latitude: 30.665673,
  scale: 18
})

/**
 * 调起微信的位置页面。可继续由微信和设备系统提供已安装地图应用的选择。
 */
export function openCompanyLocation(uniApi = uni) {
  if (!uniApi || typeof uniApi.openLocation !== 'function') {
    uniApi?.showModal?.({
      title: '无法打开地图',
      content: '当前设备暂不支持地图服务，请稍后重试。',
      showCancel: false
    })
    return
  }
  uniApi.openLocation({
    ...COMPANY_LOCATION,
    fail: () => {
      uniApi.showModal?.({
        title: '无法打开地图',
        content: '请检查定位服务或地图应用后重试。',
        showCancel: false,
        confirmText: '知道了'
      })
    }
  })
}
