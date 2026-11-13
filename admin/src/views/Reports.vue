<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-radio-group v-model="status" @change="onFilter">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="PENDING">待处理</el-radio-button>
        <el-radio-button value="RESOLVED">已核实</el-radio-button>
        <el-radio-button value="DISMISSED">已驳回</el-radio-button>
      </el-radio-group>
      <span class="pending-tip" v-if="status === 'PENDING' || status === ''">
        核实后将扣除被举报方信用分 20 分并通知双方
      </span>
    </div>

    <el-table :data="rows" v-loading="loading">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="对象" width="90">
        <template #default="{ row }">
          <el-tag :type="row.targetType === 'ITEM' ? 'primary' : 'warning'" size="small">
            {{ row.targetType === 'ITEM' ? '拍品' : '用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="itemTitle" label="拍品标题" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ row.itemTitle || '-' }}</template>
      </el-table-column>
      <el-table-column prop="targetUser" label="被举报方" width="120" show-overflow-tooltip />
      <el-table-column prop="reason" label="举报理由" min-width="200" show-overflow-tooltip />
      <el-table-column prop="reporter" label="举报人" width="110" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handleResult" label="处理说明" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.handleResult || '-' }}</template>
      </el-table-column>
      <el-table-column label="提交时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button type="danger" size="small" @click="openHandle(row, 'RESOLVED')">核实扣分</el-button>
            <el-button size="small" @click="openHandle(row, 'DISMISSED')">驳回</el-button>
          </template>
          <span v-else class="done">已处理</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next, sizes"
                   :total="total" :current-page="page" :page-size="size"
                   :page-sizes="[10, 20, 50]"
                   @current-change="onPage" @size-change="onSize" />

    <!-- 处理弹窗 -->
    <el-dialog v-model="handleVisible" :title="handleAction === 'RESOLVED' ? '核实举报' : '驳回举报'" width="480px">
      <el-alert v-if="handleAction === 'RESOLVED'" type="warning" :closable="false"
                title="确认后将扣除被举报方信用分 20 分，并向双方发送处理结果通知" style="margin-bottom: 16px" />
      <el-input v-model="handleResult" type="textarea" :rows="3"
                placeholder="处理说明（可选，双方可见）" />
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button :type="handleAction === 'RESOLVED' ? 'danger' : 'primary'"
                   :loading="handling" @click="onHandle">
          {{ handleAction === 'RESOLVED' ? '确认核实' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../api/admin'

const STATUS = {
  PENDING: { text: '待处理', type: 'warning' },
  RESOLVED: { text: '已核实', type: 'danger' },
  DISMISSED: { text: '已驳回', type: 'info' }
}

const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const status = ref('PENDING')
const loading = ref(false)

const handleVisible = ref(false)
const handleAction = ref('RESOLVED')
const handleResult = ref('')
const handling = ref(false)
const currentReport = ref(null)

function statusText(s) { return STATUS[s]?.text || s }
function statusType(s) { return STATUS[s]?.type || 'info' }
function fmtTime(t) { return (t || '').replace('T', ' ').slice(0, 16) }

async function load() {
  loading.value = true
  try {
    const data = await adminApi.reports({
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

function openHandle(row, action) {
  currentReport.value = row
  handleAction.value = action
  handleResult.value = ''
  handleVisible.value = true
}

async function onHandle() {
  handling.value = true
  try {
    await adminApi.handleReport(currentReport.value.id, {
      action: handleAction.value,
      handleResult: handleResult.value.trim() || null
    })
    ElMessage.success('处理完成')
    handleVisible.value = false
    load()
  } finally {
    handling.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.pending-tip {
  margin-left: 16px;
  font-size: 12px;
  color: #999;
}

.done {
  color: #bbb;
  font-size: 12px;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
