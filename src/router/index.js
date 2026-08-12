import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/Forbidden.vue'),
    meta: { title: '无权限' }
  },
  {
    path: '/',
    component: () => import('@/layout/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'dynamic',
        name: 'Dynamic',
        component: () => import('@/views/Dynamic.vue'),
        meta: { title: '内容管理' }
      },
      //发布动态信息
      {
        path: 'dynamic/publicInfo',
        name: 'DynamicPublicInfo',
        component: () => import('@/views/DynamicPublicInfo.vue'),
        meta: { title: '发布动态资讯' }
      },
      //查看发布的信息和编辑发布的信息
      {
        path: 'dynamic/publicInfo/:id',
        name: 'DynamicPublicInfoDetail',
        component: () => import('@/views/DynamicPublicInfoDetail.vue'),
        meta: { title: '动态资讯详情' }
      },
      {
        path: 'dynamic/publish',
        name: 'DynamicPublish',
        component: () => import('@/views/PublishAirConditioner.vue'),
        meta: { title: '发布空调产品' }
      },
      {
        path: 'dynamic/:id',
        name: 'DynamicDetail',
        component: () => import('@/views/DynamicDetail.vue'),
        meta: { title: '空调信息详情' }
      },
      {
        path: 'dynamic/:id/edit',
        name: 'DynamicEdit',
        component: () => import('@/views/PublishAirConditioner.vue'),
        meta: { title: '编辑空调信息' }
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/orders/OrderList.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'orders/form',
        name: 'OrderForm',
        component: () => import('@/views/orders/OrderForm.vue'),
        meta: { title: '新增订单' }
      },
      //编辑订单（与新增共用 OrderForm.vue，通过 route.params.id 判断编辑态）
      {
        path: 'orders/form/:id',
        name: 'OrderEdit',
        component: () => import('@/views/orders/OrderForm.vue'),
        meta: { title: '编辑订单' }
      },

      //耗材管理
      {
        path: 'consumables',
        name: 'Consumables',
        component: () => import('@/views/consumables/consumableslist.vue'),
        meta: { title: '耗材管理' }
      },
      //发布耗材（新增）
      {
        path: 'consumables/form',
        name: 'ConsumablesForm',
        component: () => import('@/views/consumables/ConsumablesForm.vue'),
        meta: { title: '发布耗材' }
      },
      //修改耗材（与新增共用 ConsumablesForm.vue，通过 route.params.id 判断编辑态）
      {
        path: 'consumables/form/:id',
        name: 'ConsumablesEdit',
        component: () => import('@/views/consumables/ConsumablesForm.vue'),
        meta: { title: '修改耗材' }
      },
      //订单备货
      {
        path: 'preparation',
        name: 'Preparation',
        component: () => import('@/views/preparation/PreparationList.vue'),
        meta: { title: '订单备货' }
      },
      //用户管理
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/users/UserList.vue'),
        meta: { title: '用户管理' }
      },
      //用户审核
      {
        path: 'users/audit',
        name: 'UserAudit',
        component: () => import('@/views/users/AuditList.vue'),
        meta: { title: '用户审核' }
      },
      //用户审核详情
      {
        path: 'users/audit/:id',
        name: 'UserAuditDetail',
        component: () => import('@/views/users/AuditDetail.vue'),
        meta: { title: '用户审核详情' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
