<template>
  <PageTemplate
    ref="pageRef"
    :columns="columns"
    :data-list="dataList"
    :total="total"
    :form-fields="formFields"
    :modal-title="modalTitle"
    :show-add="isRoot"
    table-title="角色权限"
    table-sub-title="角色决定后台账号能看到哪些菜单、能做哪些操作；权限码由后端定义，这里只做勾选"
    add-text="新增角色"
    :action-width="200"
    @search="handleSearch"
    @reset="handleReset"
    @page-change="handlePageChange"
    @add="handleAdd"
    @edit="handleEdit"
    @delete="handleDelete"
    @submit="handleSubmit"
  >
    <template #filters>
      <el-form-item label="关键词">
        <el-input
          v-model="searchForm.keyword"
          placeholder="角色标识/名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
    </template>

    <template #cell-builtin="{ value }">
      <el-tag :type="value ? 'warning' : 'info'">{{ value ? '内置' : '自定义' }}</el-tag>
    </template>

    <template #cell-status="{ value }">
      <el-tag :type="value === 1 ? 'success' : 'info'">{{ value === 1 ? '启用' : '停用' }}</el-tag>
    </template>

    <template #cell-permissions="{ value }">
      <span>{{ (value || []).length }} 项</span>
    </template>

    <template #actions="{ row }">
      <el-button size="small" :icon="Edit" @click="handleEdit(row)">
        {{ isRoot ? '编辑' : '查看' }}
      </el-button>
      <el-button
        v-if="isRoot && !row.builtin"
        size="small"
        type="danger"
        :icon="Delete"
        @click="handleDelete(row)"
      >
        删除
      </el-button>
    </template>

    <template #modal-form="{ form, isEdit }">
      <el-form label-position="top" class="role-form">
        <el-form-item label="角色标识 *">
          <!-- 标识是权限判定之外的稳定引用，建后不允许改 -->
          <el-input
            v-model="form.code"
            :disabled="isEdit || !isRoot"
            placeholder="英文小写，如 content-editor"
          />
        </el-form-item>
        <el-form-item label="角色名称 *">
          <el-input v-model="form.name" :disabled="!isRoot" placeholder="如 内容运营" />
        </el-form-item>
        <el-form-item label="角色说明">
          <el-input v-model="form.description" :disabled="!isRoot" placeholder="这个角色负责什么" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :disabled="!isRoot" active-text="启用" inactive-text="停用" />
        </el-form-item>

        <el-form-item label="权限勾选">
          <div class="permission-catalog">
            <div v-for="group in catalog" :key="group.module" class="permission-module">
              <div class="permission-module__title">
                <span>{{ group.module }}</span>
                <el-button
                  v-if="isRoot"
                  link
                  type="primary"
                  size="small"
                  @click="toggleModule(form, group)"
                >
                  {{ isModuleAllChecked(form, group) ? '取消全选' : '全选' }}
                </el-button>
              </div>
              <el-checkbox-group v-model="form.permissions">
                <el-checkbox
                  v-for="item in group.permissions"
                  :key="item.code"
                  :value="item.code"
                  :disabled="!isRoot"
                >
                  {{ item.description }}
                  <span class="permission-code">{{ item.code }}</span>
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </template>
  </PageTemplate>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Delete, Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageTemplate from '../../components/PageTemplate.vue'
import request from '../../utils/request'
import { isRoot as currentIsRoot } from '../../utils/permission'

const isRoot = computed(() => currentIsRoot.value)
const pageRef = ref()

const columns = [
  { key: 'id', title: 'ID', width: '60px' },
  { key: 'code', title: '角色标识' },
  { key: 'name', title: '角色名称' },
  { key: 'description', title: '说明', minWidth: 200 },
  { key: 'permissions', title: '权限数', width: '100px' },
  { key: 'builtin', title: '类型', width: '100px' },
  { key: 'status', title: '状态', width: '90px' }
]

// 只用来给新增表单兜底初值，弹窗本体走 modal-form 插槽
const formFields = [
  { key: 'status', default: true },
  { key: 'permissions', default: [] }
]

const searchForm = reactive({ keyword: '' })
const dataList = ref([])
const total = ref(0)
const pagination = reactive({ page: 1, pageSize: 20 })
const catalog = ref([])

const loadData = async () => {
  try {
    const res = await request.get('/admin/roles', {
      params: { keyword: searchForm.keyword, page: pagination.page, pageSize: pagination.pageSize }
    })
    if (res.code === 'OK') {
      dataList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载角色列表失败')
    }
  } catch (err) {
    console.error('加载角色列表失败', err)
  }
}

const loadCatalog = async () => {
  try {
    const res = await request.get('/admin/roles/permissions')
    if (res.code === 'OK') catalog.value = res.data || []
  } catch (err) {
    console.error('加载权限清单失败', err)
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadData()
}
const handleReset = () => {
  searchForm.keyword = ''
  pagination.page = 1
  loadData()
}
const handlePageChange = ({ page, pageSize }) => {
  pagination.page = page
  pagination.pageSize = pageSize
  loadData()
}

const modalTitle = ref('')
const editingId = ref(null)

const handleAdd = () => {
  editingId.value = null
  modalTitle.value = '新增角色'
  pageRef.value.openModal(null)
}

const handleEdit = (row) => {
  editingId.value = row.id
  modalTitle.value = isRoot.value ? `编辑角色 - ${row.name}` : `查看角色 - ${row.name}`
  pageRef.value.openModal({
    ...row,
    status: row.status === 1,
    permissions: [...(row.permissions || [])]
  })
}

const isModuleAllChecked = (form, group) => {
  const checked = form.permissions || []
  return group.permissions.every(item => checked.includes(item.code))
}

const toggleModule = (form, group) => {
  const codes = group.permissions.map(item => item.code)
  const checked = form.permissions || []
  form.permissions = isModuleAllChecked(form, group)
    ? checked.filter(code => !codes.includes(code))
    : Array.from(new Set([...checked, ...codes]))
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(
    `确认删除角色「${row.name}」？仍有管理员在用的角色不可删除`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  const res = await request.delete(`/admin/roles/${row.id}`)
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '删除失败')
    return
  }
  ElMessage.success('删除成功')
  loadData()
}

const handleSubmit = async ({ form, isEdit }) => {
  // 非 root 只能看，后端也会拦，这里先给出明确提示
  if (!isRoot.value) {
    pageRef.value.closeModal()
    return
  }
  if (!isEdit && !form.code) {
    ElMessage.warning('请填写角色标识')
    return
  }
  if (!form.name) {
    ElMessage.warning('请填写角色名称')
    return
  }
  const payload = {
    code: form.code,
    name: form.name,
    description: form.description,
    status: form.status ? 1 : 0,
    permissions: form.permissions || []
  }
  const url = isEdit ? `/admin/roles/${editingId.value}` : '/admin/roles'
  const res = await request[isEdit ? 'put' : 'post'](url, payload)
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '保存失败')
    return
  }
  pageRef.value.closeModal()
  ElMessage.success('保存成功')
  loadData()
}

onMounted(() => {
  loadData()
  loadCatalog()
})
</script>

<style scoped>
.permission-catalog {
  width: 100%;
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px;
}

.permission-module + .permission-module {
  margin-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 12px;
}

.permission-module__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 4px;
}

.permission-code {
  margin-left: 6px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
