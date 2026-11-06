<template>
  <div class="admin-page">
    <section class="page-toolbar">
      <el-form class="toolbar-form" @submit.prevent>
        <div class="toolbar-fields">
          <slot name="filters">
            <el-form-item label="关键词">
              <el-input
                v-model="searchForm.keyword"
                clearable
                placeholder="请输入关键词"
                style="width: 220px"
                @keyup.enter="handleSearch"
              />
            </el-form-item>
          </slot>
        </div>
        <div class="toolbar-actions">
          <el-button :icon="RefreshLeft" @click="handleReset">重置</el-button>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <slot name="extra-actions"></slot>
          <el-button v-if="showAdd" type="primary" :icon="Plus" @click="$emit('add')">
            {{ addText }}
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="table-panel">
      <div class="table-panel__header">
        <div>
          <h2>{{ tableTitle }}</h2>
          <span>{{ tableSubTitle }}</span>
        </div>
        <slot name="table-extra"></slot>
      </div>

      <el-table
        :data="dataList"
        stripe
        border
        style="width: 100%"
        :empty-text="emptyText"
      >
        <el-table-column
          v-for="col in columns"
          :key="col.key"
          :prop="col.key"
          :label="col.title"
          :width="col.width"
          :min-width="col.minWidth || 120"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <slot :name="`cell-${col.key}`" :row="row" :value="getCellValue(row, col)">
              <el-tag
                v-if="col.type === 'tag'"
                :type="getTagType(row, col)"
                effect="light"
              >
                {{ getCellValue(row, col) }}
              </el-tag>
              <el-image
                v-else-if="col.type === 'image' && getCellValue(row, col)"
                :src="getCellValue(row, col)"
                fit="cover"
                class="table-image"
              />
              <span v-else-if="col.type === 'image'" class="text-muted">-</span>
              <span v-else-if="col.type === 'datetime'" class="text-muted">
                {{ formatDateTime(getCellValue(row, col)) }}
              </span>
              <el-tag v-else-if="col.type === 'status'" :type="getCellValue(row, col) ? 'success' : 'info'">
                {{ getCellValue(row, col) ? '启用' : '停用' }}
              </el-tag>
              <span v-else>{{ getCellValue(row, col) || '-' }}</span>
            </slot>
          </template>
        </el-table-column>

        <el-table-column v-if="showAction" label="操作" fixed="right" :width="actionWidth">
          <template #default="{ row }">
            <div class="action-group">
              <slot name="actions" :row="row">
                <el-button v-if="showEdit" size="small" :icon="Edit" @click="$emit('edit', row)">
                  编辑
                </el-button>
                <el-button
                  v-if="showDelete"
                  size="small"
                  type="danger"
                  :icon="Delete"
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </slot>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="showPagination && total > 0" class="pagination-row">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="showModal"
      :title="modalTitle"
      width="720px"
      destroy-on-close
      append-to-body
    >
      <slot name="modal-form" :form="formData" :is-edit="isEdit">
        <el-form label-position="top" class="dialog-form">
          <el-form-item
            v-for="field in formFields"
            :key="field.key"
            :label="`${field.label}${field.required ? ' *' : ''}`"
            :class="{ 'form-wide': field.type === 'textarea' || field.wide }"
          >
            <el-input
              v-if="field.type === 'text'"
              v-model="formData[field.key]"
              clearable
              :placeholder="field.placeholder"
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="formData[field.key]"
              controls-position="right"
              style="width: 100%"
              :min="field.min"
              :max="field.max"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="formData[field.key]"
              clearable
              filterable
              placeholder="请选择"
              style="width: 100%"
            >
              <el-option
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-select
              v-else-if="field.type === 'multiselect'"
              v-model="formData[field.key]"
              multiple
              collapse-tags
              collapse-tags-tooltip
              clearable
              filterable
              :placeholder="field.placeholder || '请选择'"
              style="width: 100%"
            >
              <el-option
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="formData[field.key]"
              type="textarea"
              :rows="field.rows || 4"
              :maxlength="field.maxlength"
              show-word-limit
              :placeholder="field.placeholder"
            />
            <el-switch
              v-else-if="field.type === 'switch'"
              v-model="formData[field.key]"
              active-text="启用"
              inactive-text="停用"
            />
            <el-input v-else v-model="formData[field.key]" clearable :placeholder="field.placeholder" />
          </el-form-item>
        </el-form>
      </slot>
      <template #footer>
        <el-button @click="closeModal">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Delete, Edit, Plus, RefreshLeft, Search } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const props = defineProps({
  columns: { type: Array, default: () => [] },
  dataList: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  showAction: { type: Boolean, default: true },
  showEdit: { type: Boolean, default: true },
  showDelete: { type: Boolean, default: true },
  showAdd: { type: Boolean, default: true },
  addText: { type: String, default: '新增' },
  emptyText: { type: String, default: '暂无数据' },
  showPagination: { type: Boolean, default: true },
  formFields: { type: Array, default: () => [] },
  modalTitle: { type: String, default: '' },
  tableTitle: { type: String, default: '数据列表' },
  tableSubTitle: { type: String, default: '支持筛选、分页与行级操作' },
  actionWidth: { type: [String, Number], default: 180 }
})

const emit = defineEmits([
  'search',
  'reset',
  'add',
  'edit',
  'delete',
  'submit',
  'page-change',
  'page-size-change'
])

const searchForm = ref({ keyword: '' })
const page = ref(1)
const pageSize = ref(20)
const showModal = ref(false)
const isEdit = ref(false)
const formData = ref({})

const handleSearch = () => {
  page.value = 1
  emit('search', { ...searchForm.value })
}

const handleReset = () => {
  page.value = 1
  searchForm.value = { keyword: '' }
  emit('reset')
}

const handlePageChange = (currentPage) => {
  emit('page-change', { page: currentPage, pageSize: pageSize.value })
}

const handlePageSizeChange = (size) => {
  page.value = 1
  emit('page-size-change', { page: 1, pageSize: size })
  emit('page-change', { page: 1, pageSize: size })
}

const getCellValue = (row, col) => {
  if (col.formatter) return col.formatter(row)
  return row[col.key]
}

const getTagType = (row, col) => {
  if (col.tagType) return col.tagType(row)
  const value = getCellValue(row, col)
  if (value === 1 || value === true || value === '启用' || value === '已完成') return 'success'
  if (value === 0 || value === false || value === '停用') return 'info'
  if (value === '失败' || value === '异常') return 'danger'
  if (value === '处理中' || value === '排队中') return 'warning'
  return ''
}

const formatDateTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm('确认删除该记录？删除后不可恢复', '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  emit('delete', row)
}

const openModal = (row = null, extraData = {}) => {
  isEdit.value = Boolean(row)
  if (row) {
    formData.value = { ...row, ...extraData }
  } else {
    formData.value = {}
    for (const field of props.formFields) {
      if (field.default !== undefined) {
        formData.value[field.key] = field.default
      }
    }
  }
  showModal.value = true
}

const closeModal = () => {
  showModal.value = false
  formData.value = {}
}

const handleSubmit = () => {
  emit('submit', { form: { ...formData.value }, isEdit: isEdit.value })
}

defineExpose({ openModal, closeModal })
</script>

<style scoped>
.table-image {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  border: 1px solid var(--border);
}
</style>
