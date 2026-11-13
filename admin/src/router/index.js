import { createRouter, createWebHistory } from 'vue-router'

// 路由守卫：未登录一律跳登录页
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue'),
      meta: { title: '管理员登录' }
    },
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('../views/Dashboard.vue'),
          meta: { title: '数据看板', icon: 'DataBoard' }
        },
        {
          path: 'items',
          name: 'Items',
          component: () => import('../views/Items.vue'),
          meta: { title: '拍品审核', icon: 'Checked' }
        },
        {
          path: 'users',
          name: 'Users',
          component: () => import('../views/Users.vue'),
          meta: { title: '用户管理', icon: 'User' }
        },
        {
          path: 'auctions',
          name: 'Auctions',
          component: () => import('../views/Auctions.vue'),
          meta: { title: '拍卖监控', icon: 'Monitor' }
        },
        {
          path: 'orders',
          name: 'Orders',
          component: () => import('../views/Orders.vue'),
          meta: { title: '订单查询', icon: 'Tickets' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach((to) => {
  const token = localStorage.getItem('admin_token')
  if (to.name !== 'Login' && !token) {
    return { name: 'Login' }
  }
  if (to.meta.title) {
    document.title = `${to.meta.title} · 校园拍卖管理后台`
  }
  return true
})

export default router
