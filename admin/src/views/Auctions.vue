<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="onFilter">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">待开始</el-radio-button>
        <el-radio-button value="ONGOING">进行中</el-radio-button>
        <el-radio-button value="SETTLING">结算中</el-radio-button>
        <el-radio-button value="FINISHED">已成交</el-radio-button>
        <el-radio-button value="FAILED">流拍</el-radio-button>
        <el-radio-button value="CANCELLED">已取消</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="itemId" label="拍品ID" width="90" />
      <el-table-column prop="title" label="拍品标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前价" width="130">
        <template #default="{ row }">
          <span class="price">¥{{ ((row.currentPrice || 0) / 100).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="bidCount" label="出价数" width="90" />
      <el-table-column prop="extendCount" label="延时次数" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.extendCount > 0" type="warning" size="small">{{ row.extendCount }}</el-tag>
          <span v-else>0</span>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.startTime) || '-' }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.endTime) || '-' }}</template>
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
import { adminApi } from '../api/admin'

const STATUS = {
  PENDING: { text: '待开始', type: 'info' },
  ONGOING: { text: '进行中', type: 'primary' },
  SETTLING: { text: '结算中', type: 'warning' },
  FINISHED: { text: '已成交', type: 'success' },
  FAILED: { text: '流拍', type: 'danger' },
  CANCELLED: { text: '已取消', type: 'info' }
}

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('')
const loading = ref(false)

function statusText(s) { return STATUS[s]?.text || s }
function statusType(s) { return STATUS[s]?.type || 'info' }
function fmtTime(t) { return (t || '').toString().replace('T', ' ').slice(0, 16) }

async function load() {
  loading.value = true
  try {
    const data = await adminApi.auctions({
      page: page.value, size: size.value,
      status: status.value || undefined
    })
    rows.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onFilter() {
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

onMounted(load)
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.price {
  color: #a32d2d;
  font-weight: bold;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
