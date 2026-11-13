<template>
  <div>
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>待审核拍品（{{ list.length }}）</span>
          <el-button size="small" :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
        </div>
      </template>

      <el-table :data="list" v-loading="loading">
        <el-table-column label="拍品图" width="90">
          <template #default="{ row }">
            <el-image v-if="row.images && row.images.length"
                      :src="row.images[0]" :preview-src-list="row.images"
                      fit="cover" style="width: 64px; height: 64px; border-radius: 6px" />
            <span v-else>无图</span>
          </template>
        </el-table-column>
        <el-table-column prop="itemId" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column prop="condition" label="成色" width="90" />
        <el-table-column label="起拍价" width="110">
          <template #default="{ row }">¥{{ fen2yuan(row.startPrice) }}</template>
        </el-table-column>
        <el-table-column label="加价幅度" width="100">
          <template #default="{ row }">¥{{ fen2yuan(row.increment) }}</template>
        </el-table-column>
        <el-table-column label="保证金" width="100">
          <template #default="{ row }">{{ fen2yuan(row.deposit) }} 分</template>
        </el-table-column>
        <el-table-column prop="durationHours" label="时长(h)" width="90" />
        <el-table-column prop="sellerNickname" label="卖家" width="120" show-overflow-tooltip />
        <el-table-column prop="submitTime" label="提交时间" width="160" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" @click="onApprove(row)">通过</el-button>
            <el-button type="danger" size="small" @click="openReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="暂无待审核拍品" />
    </el-card>

    <!-- 描述详情 -->
    <el-dialog v-model="detailVisible" title="拍品描述" width="560px">
      <pre class="desc-pre">{{ detailText }}</pre>
    </el-dialog>

    <!-- 驳回理由 -->
    <el-dialog v-model="rejectVisible" title="驳回拍品" width="440px">
      <el-input v-model="rejectReason" type="textarea" :rows="4"
                placeholder="请填写驳回理由（卖家可见，必填）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="onReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { adminApi } from '../api/admin'

const list = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const detailText = ref('')
const rejectVisible = ref(false)
const rejectReason = ref('')
const rejecting = ref(false)
const currentItem = ref(null)

function fen2yuan(fen) {
  return ((fen || 0) / 100).toFixed(2)
}

async function load() {
  loading.value = true
  try {
    list.value = await adminApi.auditItems() || []
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  detailText.value = row.description || '（无描述）'
  detailVisible.value = true
}

function openReject(row) {
  currentItem.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function onApprove(row) {
  await ElMessageBox.confirm(
    `确认通过「${row.title}」？审核通过后拍卖立即开始。`, '审核确认', { type: 'warning' }
  )
  await adminApi.approveItem(row.itemId)
  ElMessage.success('已通过并开拍')
  load()
}

async function onReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('驳回理由必填')
    return
  }
  rejecting.value = true
  try {
    await adminApi.rejectItem(currentItem.value.itemId, { reason: rejectReason.value.trim() })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    load()
  } finally {
    rejecting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.desc-pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
  color: #333;
}
</style>
