<template>
  <div>
    <!-- 核心指标 -->
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in statCards" :key="card.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <!-- 拍卖状态分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>拍卖状态分布</template>
          <div class="status-bars">
            <div v-for="s in statusList" :key="s.label" class="status-row">
              <span class="status-name">{{ s.label }}</span>
              <div class="status-track">
                <div class="status-fill" :class="s.cls" :style="{ width: barWidth(s.count) }"></div>
              </div>
              <span class="status-count">{{ s.count }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <!-- 类目成交分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>类目成交分布（已完成订单）</template>
          <el-table :data="categoryRows" size="small" v-if="categoryRows.length">
            <el-table-column prop="name" label="分类" />
            <el-table-column prop="count" label="成交量" width="120" />
            <el-table-column label="占比" width="160">
              <template #default="{ row }">
                <el-progress :percentage="row.percent" :stroke-width="10" />
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无成交数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '../api/admin'

const stats = ref(null)

const statCards = computed(() => stats.value ? [
  { label: '注册用户', value: stats.value.totalUsers },
  { label: '拍品总数', value: stats.value.totalItems },
  { label: '待审核拍品', value: stats.value.pendingItems },
  { label: '进行中拍卖', value: stats.value.ongoing },
  { label: '已成交订单', value: stats.value.completedOrders },
  { label: '成交总额（元）', value: stats.value.dealAmountYuan },
  { label: '流拍场次', value: stats.value.failed },
  { label: '已结束场次', value: stats.value.finished }
] : [])

const statusList = computed(() => stats.value ? [
  { label: '进行中', count: stats.value.ongoing, cls: 'fill-ongoing' },
  { label: '已结束（成交）', count: stats.value.finished, cls: 'fill-success' },
  { label: '流拍', count: stats.value.failed, cls: 'fill-danger' },
  { label: '待审核拍品', count: stats.value.pendingItems, cls: 'fill-warning' }
] : [])

const maxStatus = computed(() =>
  Math.max(1, ...statusList.value.map(s => Number(s.count)))
)

function barWidth(count) {
  return Math.max(2, Math.round((count / maxStatus.value) * 100)) + '%'
}

const categoryRows = computed(() => {
  if (!stats.value) return []
  const entries = Object.entries(stats.value.categoryStats || {})
  const total = entries.reduce((sum, e) => sum + e[1], 0)
  return entries.map(([name, count]) => ({
    name,
    count,
    percent: total ? Math.round((count / total) * 100) : 0
  }))
})

onMounted(async () => {
  stats.value = await adminApi.stats()
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 8px 0;
}

.stat-value {
  font-size: 30px;
  font-weight: bold;
  color: #185fa5;
}

.stat-label {
  margin-top: 6px;
  color: #888;
  font-size: 13px;
}

.status-row {
  display: flex;
  align-items: center;
  margin: 14px 0;
}

.status-name {
  width: 110px;
  font-size: 13px;
  color: #555;
}

.status-track {
  flex: 1;
  height: 12px;
  background: #eef1f5;
  border-radius: 6px;
  overflow: hidden;
}

.status-fill {
  height: 100%;
  border-radius: 6px;
}

.fill-ongoing { background: #409eff; }
.fill-success { background: #67c23a; }
.fill-danger { background: #f56c6c; }
.fill-warning { background: #e6a23c; }

.status-count {
  width: 50px;
  text-align: right;
  font-size: 13px;
  color: #555;
}
</style>
