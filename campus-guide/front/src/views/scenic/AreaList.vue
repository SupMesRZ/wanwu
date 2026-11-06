<template>
  <div class="current-area-page">
    <section class="area-hero" v-if="currentArea">
      <div class="hero-copy">
        <div class="context-label">当前校区</div>
        <div class="title-row">
          <h1>{{ currentArea.name || '河北大学七一路校区' }}</h1>
          <el-tag :type="currentArea.status === 1 ? 'success' : 'info'" effect="light">
            {{ currentArea.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </div>
        <p>
          当前阶段后台暂不提供多校区切换。地图、路线、知识库和统计数据默认围绕该校区展开。
        </p>
      </div>
      <div class="hero-actions">
        <el-button :icon="MapLocation" @click="goMapWorkbench">地图工作台</el-button>
        <el-button :icon="Promotion" @click="goMapPublish">地图发布</el-button>
        <el-button type="primary" :icon="Edit" @click="openEditDialog">编辑资料</el-button>
      </div>
    </section>

    <section v-if="currentArea" class="area-grid">
      <article class="info-panel info-panel--wide">
        <div class="panel-head">
          <h2>主体资料</h2>
        </div>
        <div class="description-box">
          {{ currentArea.description || '暂无简介' }}
        </div>
        <div class="field-list">
          <label>
            <span>校区名称</span>
            <strong>{{ currentArea.name || '-' }}</strong>
          </label>
          <label>
            <span>详细地址</span>
            <strong>待定</strong>
          </label>
          <label>
            <span>联系电话</span>
            <strong>待定</strong>
          </label>
          <label>
            <span>创建时间</span>
            <strong>{{ formatDateTime(currentArea.createdAt) }}</strong>
          </label>
        </div>
      </article>

      <article class="info-panel">
        <div class="panel-head">
          <h2>开放与票务</h2>
        </div>
        <div class="field-list">
          <label>
            <span>开放时间</span>
            <strong>待定</strong>
          </label>
          <label>
            <span>门票说明</span>
            <strong>待定</strong>
          </label>
          <label>
            <span>启停状态</span>
            <strong>{{ currentArea.status === 1 ? '启用' : '停用' }}</strong>
          </label>
        </div>
      </article>

      <article class="info-panel">
        <div class="panel-head">
          <h2>功能入口</h2>
        </div>
        <div class="link-grid">
          <button @click="goMapWorkbench">
            <el-icon><MapLocation /></el-icon>
            <span>地图工作台</span>
          </button>
          <button @click="goMapPublish">
            <el-icon><Promotion /></el-icon>
            <span>地图发布</span>
          </button>
        </div>
      </article>
    </section>

    <section v-else class="empty-panel">
      <el-icon><Location /></el-icon>
      <strong>暂无当前校区资料</strong>
      <span>请先确认后端已创建默认校区“河北大学七一路校区”</span>
      <el-button type="primary" @click="loadData">重新加载</el-button>
    </section>

    <el-dialog
      v-model="dialogVisible"
      title="编辑当前校区资料"
      width="640px"
      destroy-on-close
      append-to-body
    >
      <el-form label-position="top" class="area-form">
        <el-form-item label="校区名称 *">
          <el-input v-model="form.name" clearable aria-label="校区名称" name="areaName" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            aria-label="校区状态"
            name="areaStatus"
          />
        </el-form-item>
        <el-form-item class="form-wide" label="简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            aria-label="校区简介"
            name="areaDescription"
          />
        </el-form-item>
        <el-form-item class="form-wide" label="详细地址">
          <el-alert
            title="详细地址和联系电话暂不在当前接口中维护"
            type="info"
            :closable="false"
            show-icon
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitArea">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Edit, Location, MapLocation, Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

const router = useRouter()
const areas = ref([])
const dialogVisible = ref(false)

const form = reactive({
  name: '',
  description: '',
  status: 1
})

const currentArea = computed(() => areas.value[0] || null)

const loadData = async () => {
  try {
    const res = await request.get('/admin/scenic-areas', {
      params: { page: 1, pageSize: 1 }
    })
    if (res.code === 'OK') {
      const records = Array.isArray(res.data) ? res.data : (res.data.records || [])
      areas.value = records
      return
    }
    ElMessage.warning(res.message || '加载当前校区失败')
  } catch (err) {
    ElMessage.error('加载当前校区失败')
  }
}

const openEditDialog = () => {
  if (!currentArea.value) return
  Object.assign(form, {
    name: currentArea.value.name || '',
    description: currentArea.value.description || '',
    status: currentArea.value.status ?? 1
  })
  dialogVisible.value = true
}

const submitArea = async () => {
  if (!currentArea.value) return
  if (!form.name?.trim()) {
    ElMessage.warning('请填写校区名称')
    return
  }
  try {
    const res = await request.put(`/admin/scenic-areas/${currentArea.value.id}`, {
      name: form.name.trim(),
      description: form.description,
      centerLongitude: currentArea.value.centerLongitude,
      centerLatitude: currentArea.value.centerLatitude,
      defaultZoom: currentArea.value.defaultZoom ?? currentArea.value.mapZoomLevel ?? 15,
      status: form.status
    })
    if (res.code === 'OK') {
      dialogVisible.value = false
      ElMessage.success('当前校区资料已保存')
      await loadData()
      return
    }
    ElMessage.warning(res.message || '保存失败')
  } catch (err) {
    ElMessage.error('保存失败')
  }
}

const goMapWorkbench = () => {
  router.push('/scenic/map-workbench')
}

const goMapPublish = () => {
  router.push('/scenic/map-publish')
}

const formatDateTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = number => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

onMounted(loadData)
</script>

<style scoped>
.current-area-page {
  min-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.area-hero,
.info-panel,
.empty-panel {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.area-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 22px;
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(37, 99, 235, 0.08)),
    #ffffff;
}

.hero-copy {
  min-width: 0;
  display: grid;
  gap: 8px;
}

.context-label {
  width: fit-content;
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  color: #0f766e;
  background: #e6f4f1;
  font-size: 12px;
  font-weight: 800;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-row h1 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  letter-spacing: 0;
}

.hero-copy p {
  max-width: 720px;
  margin: 0;
  color: #475569;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.area-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 16px;
}

.info-panel {
  min-width: 0;
  padding: 18px;
}

.info-panel--wide {
  grid-row: span 2;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  letter-spacing: 0;
}

.description-box {
  min-height: 140px;
  padding: 14px;
  border: 1px solid #edf2f7;
  border-radius: var(--radius-sm);
  background: #fbfdff;
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.8;
}

.field-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.field-list label {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #edf2f7;
  border-radius: var(--radius-sm);
  background: #ffffff;
}

.field-list span {
  color: #64748b;
  font-size: 12px;
  font-weight: 800;
}

.field-list strong {
  color: #0f172a;
  font-size: 13px;
  text-align: right;
}

.link-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.link-grid button {
  min-height: 96px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px solid #edf2f7;
  border-radius: var(--radius-sm);
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
}

.link-grid button:hover {
  border-color: #99f6e4;
  background: #f4fffc;
}

.link-grid .el-icon {
  color: #0f766e;
  font-size: 24px;
}

.empty-panel {
  min-height: 360px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: #64748b;
}

.empty-panel .el-icon {
  color: #0f766e;
  font-size: 34px;
}

.empty-panel strong {
  color: #0f172a;
}

.area-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.area-form .form-wide {
  grid-column: 1 / -1;
}

@media (max-width: 980px) {
  .area-hero,
  .area-grid {
    grid-template-columns: 1fr;
  }

  .area-hero {
    display: grid;
  }

  .hero-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .link-grid,
  .area-form {
    grid-template-columns: 1fr;
  }
}
</style>
