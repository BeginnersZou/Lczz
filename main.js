import App from './App'

// 引入封装好的请求工具
import http from '@/utils/request.js'

// #ifndef VUE3
import Vue from 'vue'
import './uni.promisify.adaptor'
Vue.config.productionTip = false

// 挂载到 Vue 原型（Vue2 写法）
Vue.prototype.$http = http

App.mpType = 'app'
const app = new Vue({
  ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
import uviewPlus from '@/node_modules/uview-plus'

export function createApp() {
  const app = createSSRApp(App)
  app.use(uviewPlus)

  // 挂载全局（Vue3 写法）
  app.config.globalProperties.$http = http
  uni.$http = http // 同时挂载到 uni 全局，更方便使用
  return {
    app
  }
}
// #endif
