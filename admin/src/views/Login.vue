<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">校园拍卖系统</h2>
      <p class="login-sub">管理后台</p>
      <el-form :model="form" @keyup.enter="onSubmit">
        <el-form-item>
          <el-input v-model="form.username" placeholder="管理员账号" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                    :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%" :loading="loading"
                   @click="onSubmit">登 录</el-button>
      </el-form>
      <p class="login-tip">默认账号 admin / admin123（仅演示环境）</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { adminApi } from '../api/admin'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const data = await adminApi.login({ ...form })
    localStorage.setItem('admin_token', data.token)
    ElMessage.success('登录成功')
    router.push({ name: 'Dashboard' })
  } catch (e) {
    // 拦截器已提示错误
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #185fa5 0%, #0d3a6e 100%);
}

.login-card {
  width: 360px;
  background: #ffffff;
  border-radius: 12px;
  padding: 40px 36px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
}

.login-title {
  margin: 0;
  text-align: center;
  color: #185fa5;
}

.login-sub {
  text-align: center;
  color: #999;
  margin: 8px 0 32px;
}

.login-tip {
  text-align: center;
  color: #bbb;
  font-size: 12px;
  margin-top: 16px;
}
</style>
