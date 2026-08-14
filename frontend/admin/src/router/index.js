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
