<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="按昵称搜索用户" clearable style="width: 240px"
                @keyup.enter="onSearch" @clear="onSearch">
        <template #append>
          <el-button :icon="Search" @click="onSearch" />
        </template>
      </el-input>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="openid" label="OpenID" min-width="220" show-overflow-tooltip />
      <el-table-column label="余额" width="130">
        <template #default="{ row }">
          ¥{{ ((row.balance || 0) / 100).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="信用分" width="110">
        <template #default="{ row }">
          <el-tag :type="row.creditScore >= 90 ? 'success' : row.creditScore >= 60 ? 'warning' : 'danger'">
            {{ row.creditScore }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'danger'">
            {{ row.status === 0 ? '正常' : '已封禁' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="190">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" type="danger" size="small" @click="onBan(row, true)">封禁</el-button>
          <el-button v-else type="success" size="small" @click="onBan(row, false)">解封</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next, sizes"
                   :total="total" :current-page="page" :page-size="size"
                   :page-sizes="[10, 20, 50]"
                   @current-change="onPage" @size-change="onSize" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { adminApi } from '../api/admin'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const loading = ref(false)

function fmtTime(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    const data = await adminApi.users({
      page: page.value, size: size.value,
      keyword: keyword.value || undefined
    })
    rows.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onPage(p) {
  page.value = p
  load()
}

function onSize(s) {
  size.value = s
  page.value = 1
  load()
}

async function onBan(row, ban) {
  const action = ban ? '封禁' : '解封'
  await ElMessageBox.confirm(
    `确认${action}用户「${row.nickname}」？${ban ? '封禁后将无法登录与出价。' : ''}`,
    `${action}确认`, { type: 'warning' }
  )
  if (ban) {
    await adminApi.banUser(row.id)
  } else {
    await adminApi.unbanUser(row.id)
  }
  ElMessage.success(`已${action}`)
  load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
