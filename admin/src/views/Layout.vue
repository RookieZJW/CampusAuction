<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">
        <span class="brand-name">校园拍场 · 后台</span>
      </div>
      <el-menu :default-active="activeMenu" router background-color="#0d3a6e"
               text-color="#c7d5e8" active-text-color="#ffffff">
        <el-menu-item v-for="r in menuRoutes" :key="r.path" :index="'/' + r.path">
          <el-icon><component :is="r.meta.icon" /></el-icon>
          <span>{{ r.meta.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">{{ route.meta.title }}</span>
        <el-button link @click="onLogout">
          <el-icon style="margin-right: 4px"><SwitchButton /></el-icon>退出登录
        </el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const menuRoutes = router.options.routes.find(r => r.path === '/').children

const activeMenu = computed(() => route.path)

function onLogout() {
  localStorage.removeItem('admin_token')
  router.push({ name: 'Login' })
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #0d3a6e;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-weight: bold;
  font-size: 17px;
}

.el-menu {
  border-right: none;
}

.header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  border-bottom: 1px solid #e6e8eb;
}

.header-title {
  font-size: 17px;
  font-weight: bold;
  color: #1a1a1a;
}

.main {
  background: #f5f6f8;
}
</style>
