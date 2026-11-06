<template>
  <div class="qa-resource-shell">
    <section v-if="config.previewable" class="answer-preview-panel">
      <div>
        <strong>问答验证</strong>
        <span>按游客端 FAQ 优先、RAG 其次、安全兜底规则预览，不计入命中统计</span>
      </div>
      <div class="preview-form">
        <el-input v-model="previewForm.question" clearable placeholder="输入游客可能会问的问题" @keyup.enter="previewAnswer" />
        <el-input v-model="previewForm.currentSpotName" clearable placeholder="当前景点，可选" />
        <el-button type="primary" :loading="previewing" @click="previewAnswer">验证</el-button>
      </div>
      <div v-if="previewResult" class="preview-result">
        <el-tag :type="previewResult.safeFallback ? 'warning' : 'success'" effect="light">
          {{ previewResult.sourceType }}
        </el-tag>
        <span>{{ previewResult.evidence || 'NO_EVIDENCE' }}</span>
        <p>{{ previewResult.answer }}</p>
      </div>
    </section>

    <PageTemplate
      ref="pageRef"
      :columns="config.columns"
      :data-list="rows"
      :total="total"
      :form-fields="config.formFields"
      :modal-title="modalTitle"
      :table-title="config.tableTitle"
      :table-sub-title="config.tableSubTitle"
      :add-text="config.addText"
      :show-add="!config.readonly"
      :show-edit="!config.readonly"
      :show-delete="!config.readonly"
      :action-width="config.actionWidth || 210"
      @search="handleSearch"
      @reset="handleReset"
      @add="handleAdd"
      @edit="handleEdit"
      @delete="handleDelete"
      @submit="handleSubmit"
      @page-change="handlePageChange"
      @page-size-change="handlePageChange"
    >
    <template #filters>
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          clearable
          :placeholder="config.keywordPlaceholder || '请输入关键词'"
          style="width: 220px"
          @keyup.enter="loadData"
        />
      </el-form-item>
      <el-form-item v-if="config.statusFilter" label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px" @change="loadData">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="config.vectorFilter" label="向量状态">
        <el-select v-model="query.vectorStatus" clearable placeholder="全部" style="width: 150px" @change="loadData">
          <el-option label="待处理" value="PENDING" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已完成" value="DONE" />
          <el-option label="失败" value="FAILED" />
        </el-select>
      </el-form-item>
    </template>

    <template #actions="{ row }">
      <div class="action-group">
        <el-button v-if="config.type === 'docs'" size="small" :icon="Refresh" @click="handleReindex(row)">
          重建
        </el-button>
        <el-button v-if="config.type === 'docs'" size="small" :icon="Tickets" @click="goChunks(row)">
          切片
        </el-button>
        <el-button v-if="!config.readonly" size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
        <el-button v-if="!config.readonly" size="small" type="danger" :icon="Delete" @click="handleDelete(row)">
          删除
        </el-button>
      </div>
    </template>
    </PageTemplate>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Delete, Edit, Refresh, Tickets } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTemplate from '../../components/PageTemplate.vue'
import request from '../../utils/request'

const props = defineProps({
  type: { type: String, required: true }
})

const targetTypeOptions = [
  { label: '景区', value: 'AREA' },
  { label: '景点', value: 'SPOT' },
  { label: '服务点', value: 'FACILITY' },
  { label: '路线', value: 'ROUTE' }
]

const route = useRoute()
const router = useRouter()
const pageRef = ref()
const rows = ref([])
const total = ref(0)
const editingId = ref(null)
const modalTitle = ref('')
const previewing = ref(false)
const previewResult = ref(null)
const previewForm = reactive({
  scenicAreaId: 1,
  question: '',
  currentSpotName: ''
})
const query = reactive({
  keyword: '',
  status: '',
  vectorStatus: '',
  docId: '',
  page: 1,
  pageSize: 20
})

const configs = {
  docs: {
    type: 'docs',
    tableTitle: '知识文档',
    tableSubTitle: '维护 RAG 文档、解析状态、向量化状态和业务对象关联',
    addText: '新增文档',
    endpoint: '/admin/qa/knowledge-docs',
    previewable: true,
    keywordPlaceholder: '文档标题或摘要',
    vectorFilter: true,
    actionWidth: 250,
    columns: [
      { key: 'id', title: 'ID', width: 72 },
      { key: 'title', title: '文档标题', minWidth: 220 },
      { key: 'docType', title: '类型', width: 90, type: 'tag' },
      { key: 'chunkCount', title: '切片数', width: 90 },
      { key: 'parseStatus', title: '解析状态', width: 110, type: 'tag' },
      { key: 'vectorStatus', title: '向量状态', width: 110, type: 'tag' },
      { key: 'status', title: '状态', width: 90, type: 'status' },
      { key: 'updatedAt', title: '更新时间', width: 170, type: 'datetime' }
    ],
    formFields: [
      { key: 'scenicAreaId', label: '景区ID', type: 'number', required: true, default: 1, min: 1 },
      { key: 'title', label: '文档标题', type: 'text', required: true },
      { key: 'docType', label: '文档类型', type: 'select', default: 'TEXT', options: [
        { label: '文本', value: 'TEXT' },
        { label: 'PDF', value: 'PDF' },
        { label: 'Word', value: 'DOCX' },
        { label: 'Markdown', value: 'MD' },
        { label: '网页', value: 'URL' }
      ] },
      { key: 'fileName', label: '文件名', type: 'text' },
      { key: 'fileUrl', label: '文件地址', type: 'text', wide: true },
      { key: 'summary', label: '内容摘要', type: 'textarea', wide: true },
      { key: 'bindingTargetType', label: '关联类型', type: 'select', default: 'AREA', options: targetTypeOptions },
      { key: 'bindingTargetId', label: '关联对象ID', type: 'number', default: 1, min: 1 },
      { key: 'status', label: '状态', type: 'switch', default: true }
    ]
  },
  chunks: {
    type: 'chunks',
    readonly: true,
    tableTitle: '文档切片',
    tableSubTitle: '查看知识文档切片、向量状态和召回次数',
    endpoint: '/admin/qa/knowledge-chunks',
    keywordPlaceholder: '切片内容',
    columns: [
      { key: 'id', title: 'ID', width: 72 },
      { key: 'docId', title: '文档ID', width: 90 },
      { key: 'chunkIndex', title: '序号', width: 80 },
      { key: 'content', title: '切片内容', minWidth: 360 },
      { key: 'tokenCount', title: 'Token', width: 90 },
      { key: 'hitCount', title: '召回', width: 90 },
      { key: 'vectorStatus', title: '向量状态', width: 110, type: 'tag' }
    ],
    formFields: []
  },
  faqs: {
    type: 'faqs',
    tableTitle: 'FAQ 管理',
    tableSubTitle: '维护高频问答、分类、标签和启停状态',
    addText: '新增 FAQ',
    endpoint: '/admin/qa/faqs',
    previewable: true,
    keywordPlaceholder: '问题或答案',
    statusFilter: true,
    columns: [
      { key: 'id', title: 'ID', width: 72 },
      { key: 'question', title: '问题', minWidth: 260 },
      { key: 'category', title: '分类', width: 120 },
      { key: 'tags', title: '标签', minWidth: 160 },
      { key: 'hitCount', title: '命中', width: 90 },
      { key: 'status', title: '状态', width: 90, type: 'status' },
      { key: 'updatedAt', title: '更新时间', width: 170, type: 'datetime' }
    ],
    formFields: [
      { key: 'scenicAreaId', label: '景区ID', type: 'number', required: true, default: 1, min: 1 },
      { key: 'category', label: '分类', type: 'text' },
      { key: 'tags', label: '标签', type: 'text' },
      { key: 'question', label: '问题', type: 'textarea', required: true, wide: true },
      { key: 'answer', label: '答案', type: 'textarea', required: true, wide: true },
      { key: 'status', label: '状态', type: 'switch', default: true }
    ]
  },
  scripts: {
    type: 'scripts',
    tableTitle: '讲解词管理',
    tableSubTitle: '维护景区、景点、服务点或路线的预置讲解内容',
    addText: '新增讲解词',
    endpoint: '/admin/qa/guide-scripts',
    previewable: true,
    keywordPlaceholder: '标题或内容',
    statusFilter: true,
    columns: [
      { key: 'id', title: 'ID', width: 72 },
      { key: 'title', title: '标题', minWidth: 180 },
      { key: 'targetType', title: '关联类型', width: 110, type: 'tag' },
      { key: 'targetId', title: '对象ID', width: 90 },
      { key: 'language', title: '语言', width: 100 },
      { key: 'priority', title: '优先级', width: 90 },
      { key: 'status', title: '状态', width: 90, type: 'status' },
      { key: 'updatedAt', title: '更新时间', width: 170, type: 'datetime' }
    ],
    formFields: [
      { key: 'scenicAreaId', label: '景区ID', type: 'number', required: true, default: 1, min: 1 },
      { key: 'targetType', label: '关联类型', type: 'select', required: true, default: 'SPOT', options: targetTypeOptions },
      { key: 'targetId', label: '关联对象ID', type: 'number', required: true, default: 1, min: 1 },
      { key: 'title', label: '标题', type: 'text', required: true },
      { key: 'language', label: '语言', type: 'select', default: 'zh-CN', options: [
        { label: '中文', value: 'zh-CN' },
        { label: '英文', value: 'en-US' }
      ] },
      { key: 'priority', label: '优先级', type: 'number', default: 0 },
      { key: 'content', label: '讲解内容', type: 'textarea', required: true, wide: true, rows: 6 },
      { key: 'status', label: '状态', type: 'switch', default: true }
    ]
  }
}

const config = computed(() => configs[props.type])

const loadData = async () => {
  const params = {
    page: query.page,
    pageSize: query.pageSize
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.status !== '') params.status = query.status
  if (query.vectorStatus) params.vectorStatus = query.vectorStatus
  if (config.value.type === 'chunks' && query.docId) params.docId = query.docId
  const res = await request.get(config.value.endpoint, { params })
  if (res.code === 'OK') {
    rows.value = res.data?.records || []
    total.value = Number(res.data?.total || rows.value.length)
  }
}

const previewAnswer = async () => {
  if (!previewForm.question.trim()) {
    ElMessage.warning('请先输入要验证的问题')
    return
  }
  previewing.value = true
  try {
    const res = await request.post('/admin/qa/answer-preview', {
      scenicAreaId: previewForm.scenicAreaId || 1,
      question: previewForm.question.trim(),
      currentSpotName: previewForm.currentSpotName.trim()
    })
    if (res.code === 'OK') {
      previewResult.value = res.data
    } else {
      ElMessage.warning(res.message || '问答验证失败')
    }
  } finally {
    previewing.value = false
  }
}

const handleSearch = () => {
  query.page = 1
  loadData()
}

const handleReset = () => {
  query.keyword = ''
  query.status = ''
  query.vectorStatus = ''
  query.page = 1
  loadData()
}

const handlePageChange = ({ page, pageSize }) => {
  query.page = page
  query.pageSize = pageSize
  loadData()
}

const handleAdd = () => {
  editingId.value = null
  modalTitle.value = config.value.addText
  pageRef.value.openModal(null)
}

const handleEdit = (row) => {
  editingId.value = row.id
  modalTitle.value = `编辑${config.value.tableTitle}`
  const extra = {}
  if (config.value.type === 'docs') {
    const binding = row.bindings?.[0]
    extra.bindingTargetType = binding?.targetType || 'AREA'
    extra.bindingTargetId = binding?.targetId || row.scenicAreaId || 1
  }
  pageRef.value.openModal(row, extra)
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除「${row.title || row.question || row.id}」？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  const res = await request.delete(`${config.value.endpoint}/${row.id}`)
  if (res.code === 'OK') {
    ElMessage.success('删除成功')
    loadData()
  }
}

const handleSubmit = async ({ form, isEdit }) => {
  const payload = normalizePayload(form)
  const res = isEdit
    ? await request.put(`${config.value.endpoint}/${editingId.value}`, payload)
    : await request.post(config.value.endpoint, payload)
  if (res.code === 'OK') {
    ElMessage.success('保存成功')
    pageRef.value.closeModal()
    loadData()
  }
}

const normalizePayload = (form) => {
  const status = form.status === true ? 1 : form.status === false ? 0 : form.status
  if (config.value.type === 'docs') {
    return {
      scenicAreaId: form.scenicAreaId || 1,
      title: form.title,
      fileName: form.fileName,
      fileUrl: form.fileUrl,
      docType: form.docType || 'TEXT',
      summary: form.summary,
      status: status ?? 1,
      bindings: [{
        targetType: form.bindingTargetType || 'AREA',
        targetId: form.bindingTargetId || form.scenicAreaId || 1
      }]
    }
  }
  return {
    ...form,
    scenicAreaId: form.scenicAreaId || 1,
    status: status ?? 1
  }
}

const handleReindex = async (row) => {
  const res = await request.post(`${config.value.endpoint}/${row.id}/reindex`)
  if (res.code === 'OK') {
    ElMessage.success('索引重建完成')
    loadData()
  }
}

const goChunks = (row) => {
  router.push({ path: '/qa/knowledge-chunks', query: { docId: row.id } })
}

watch(
  () => props.type,
  () => {
    query.keyword = ''
    query.status = ''
    query.vectorStatus = ''
    query.docId = props.type === 'chunks' ? route.query.docId || '' : ''
    previewResult.value = null
    query.page = 1
    loadData()
  }
)

onMounted(() => {
  if (props.type === 'chunks') {
    query.docId = route.query.docId || ''
  }
  loadData()
})
</script>

<style scoped>
.qa-resource-shell {
  display: grid;
  gap: 14px;
}

.answer-preview-panel {
  display: grid;
  gap: 12px;
  border: 1px solid #dbe8e5;
  border-radius: 8px;
  padding: 14px;
  background: #f8fffd;
}

.answer-preview-panel > div:first-child {
  display: grid;
  gap: 4px;
}

.answer-preview-panel strong {
  color: #0f172a;
  font-size: 15px;
}

.answer-preview-panel span {
  color: #64748b;
  font-size: 13px;
}

.preview-form {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 220px auto;
  gap: 10px;
}

.preview-result {
  display: grid;
  grid-template-columns: auto 1fr;
  align-items: center;
  gap: 8px;
  border-top: 1px solid #dbe8e5;
  padding-top: 10px;
}

.preview-result p {
  grid-column: 1 / -1;
  margin: 0;
  color: #0f172a;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .preview-form {
    grid-template-columns: 1fr;
  }
}
</style>
