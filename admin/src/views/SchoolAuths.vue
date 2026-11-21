<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div>
        <div class="title">学校认证审核</div>
        <div class="subtitle">学生证信息由用户提交，通过后该用户进入本校拍场，可发布拍品并参与竞价。</div>
      </div>
      <el-button type="primary" plain :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" row-key="id">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column label="申请人" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.nickname || '微信用户' }}</template>
      </el-table-column>
      <el-table-column prop="realName" label="真实姓名" width="110" />
      <el-table-column prop="studentNo" label="学号" width="140" />
      <el-table-column label="申请学校" width="150">
        <template #default="{ row }">{{ row.shortName || row.schoolName || '-' }}</template>
      </el-table-column>
      <el-table-column label="学生证" width="110">
        <template #default="{ row }">
          <el-image v-if="row.studentCard" class="card-thumb" :src="row.studentCard"
                    :preview-src-list="[row.studentCard]" fit="cover" preview-teleported />
          <span v-else class="done">未上传</span>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="165">
        <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button type="success" size="small" :loading="actingId === row.id" @click="onApprove(row)">通过</el-button>
          <el-button type="danger" size="small" plain @click="openReject(row)">驳回</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="empty">暂无待审核的学生证认证</div>
      </template>
    </el-table>

    <el-dialog v-model="rejectVisible" title="驳回学生证认证" width="460px">
      <div class="dialog-hint">驳回后用户可修改信息重新提交，请填写清楚原因。</div>
      <el-input v-model="reason" type="textarea" :rows="3" maxlength="255"
                placeholder="例如：学生证照片模糊，无法看清姓名与学号" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" :disabled="!reason.trim()" @click="onReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/admin'

const rows = ref([])
const loading = ref(false)
const actingId = ref(null)
const rejectVisible = ref(false)
const rejecting = ref(false)
const reason = ref('')
const current = ref(null)

function fmtTime(t) { return (t || '').replace('T', ' ').slice(0, 16) }

async function load() {
  loading.value = true
  try {
    rows.value = (await adminApi.schoolAuths()) || []
  } finally {
    loading.value = false
  }
}

async function onApprove(row) {
  try {
    await ElMessageBox.confirm(
      `确认通过 ${row.realName}（${row.shortName || row.schoolName}）的学生证认证？`,
      '审核通过', { type: 'warning' }
    )
  } catch {
    return
  }
  actingId.value = row.id
  try {
    await adminApi.approveSchoolAuth(row.id)
    ElMessage.success('已通过认证')
    await load()
  } finally {
    actingId.value = null
  }
}

function openReject(row) {
  current.value = row
  reason.value = ''
  rejectVisible.value = true
}

async function onReject() {
  if (!reason.value.trim() || !current.value) {
    return
  }
  rejecting.value = true
  try {
    await adminApi.rejectSchoolAuth(current.value.id, { reason: reason.value.trim() })
    ElMessage.success('已驳回，用户可修改后重新提交')
    rejectVisible.value = false
    await load()
  } finally {
    rejecting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 16px;
}
.title {
  font-size: 17px;
  font-weight: 600;
  color: #1b3654;
}
.subtitle {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.card-thumb {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  border: 1px solid #e4ddce;
  cursor: pointer;
}
.dialog-hint {
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}
.empty {
  padding: 36px 0;
  color: #909399;
  font-size: 13px;
}
</style>
