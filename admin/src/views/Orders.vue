<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="onFilter">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING_TRADING">待交割</el-radio-button>
        <el-radio-button value="COMPLETED">已完成</el-radio-button>
        <el-radio-button value="CANCELLED">已违约取消</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="itemId" label="拍品ID" width="90" />
      <el-table-column prop="buyerId" label="买方ID" width="90" />
      <el-table-column prop="sellerId" label="卖方ID" width="90" />
      <el-table-column label="成交价" width="130">
        <template #default="{ row }">
          <span class="price">¥{{ ((row.dealPrice || 0) / 100).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="交割方式" width="120">
        <template #default="{ row }">{{ row.tradeType === 'OFFLINE' ? '线下面交' : (row.tradeType || '-') }}</template>
      </el-table-column>
      <el-table-column prop="tradePlace" label="交割地点" min-width="150" show-overflow-tooltip />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="完成时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.completedAt) || '-' }}</template>
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
  PENDING_TRADING: { text: '待交割', type: 'warning' },
  COMPLETED: { text: '已完成', type: 'success' },
  CANCELLED: { text: '已违约取消', type: 'danger' }
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
    const data = await adminApi.orders({
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
