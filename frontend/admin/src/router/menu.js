import {
  Monitor,
  Bell,
  ShoppingCart,
  Document,
  Box,
  User
} from '@element-plus/icons-vue'

const menuItems = [
  {
    path: '/dashboard',
    name: 'Dashboard',
    icon: Monitor,
    meta: { title: '工作台' }
  },
  {
    path: '/orders',
    name: 'Orders',
    icon:  Document,
    meta: { title: '订单管理' }
  },
  // 耗材管理
  {
    path: '/consumables',
    name: 'Consumables',
    icon:  Box,
    meta: { title: '耗材管理' }
  },
  //订单备货
  {
    path: '/preparation',
    name: 'Preparation',
    icon: ShoppingCart,
    meta: { title: '订单备货' }
  },
  {
    path: '/users',
    name: 'Users',
    icon: User,
    meta: { title: '用户管理' }
  },
  {
    path: '/dynamic',
    name: 'Dynamic',
    icon: Bell,
    meta: { title: '内容管理' }
  }
]

export default menuItems
