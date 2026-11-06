<template>
  <PageTemplate
    ref="pageRef"
    :columns="columns"
    :data-list="dataList"
    :total="total"
    :form-fields="formFields"
    :modal-title="modalTitle"
    :show-add="isRoot"
    table-title="管理员账号"
    table-sub-title="维护后台管理员身份、账号状态和最近登录信息"
    add-text="新增管理员"
    :action-width="180"
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
          placeholder="用户名/昵称/手机号"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
    </template>

    <template #cell-isRoot="{ value }">
      <el-tag :type="value ? 'warning' : 'info'">
        {{ value ? 'Root' : '普通' }}
      </el-tag>
    </template>

    <template #cell-status="{ value }">
      <el-tag :type="value === 1 ? 'success' : 'info'">
        {{ value === 1 ? '启用' : '禁用' }}
      </el-tag>
    </template>

    <template #cell-roleNames="{ row }">
      <el-tag v-if="row.isRoot" type="warning">全部权限</el-tag>
      <template v-else-if="row.roleNames && row.roleNames.length">
        <el-tag v-for="name in row.roleNames" :key="name" class="role-tag" type="info">
          {{ name }}
        </el-tag>
      </template>
      <span v-else class="text-muted">未分配</span>
    </template>

    <template #actions="{ row }">
      <el-button size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
      <el-button
        v-if="isRoot && !row.isRoot"
        size="small"
        type="danger"
        :icon="Delete"
        @click="handleDelete(row)"
      >
        删除
      </el-button>
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
  { key: 'username', title: '用户名' },
  { key: 'nickname', title: '昵称' },
  { key: 'phone', title: '手机号' },
  { key: 'roleNames', title: '角色', minWidth: 160 },
  { key: 'isRoot', title: '身份' },
  { key: 'status', title: '状态' },
  { key: 'lastLoginAt', title: '最后登录', type: 'datetime' }
]

const searchForm = reactive({ keyword: '' })
const dataList = ref([])
const total = ref(0)
const pagination = reactive({ page: 1, pageSize: 20 })

const loadData = async () => {
  try {
    const res = await request.get('/admin/users', {
      params: { keyword: searchForm.keyword, page: pagination.page, pageSize: pagination.pageSize }
    })
    if (res.code === 'OK') {
      dataList.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      ElMessage.error(res.message || '加载管理员列表失败')
    }
  } catch (err) {
    console.error('加载管理员列表失败', err)
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

const roleOptions = ref([])

const loadRoleOptions = async () => {
  try {
    const res = await request.get('/admin/roles/options')
    if (res.code === 'OK') {
      roleOptions.value = (res.data || []).map(role => ({ label: role.name, value: role.id }))
    }
  } catch (err) {
    console.error('加载角色选项失败', err)
  }
}

const modalTitle = ref('')
const formFields = computed(() => [
  { key: 'username', label: '用户名', type: 'text', required: true },
  { key: 'nickname', label: '昵称', type: 'text' },
  { key: 'phone', label: '手机号', type: 'text' },
  { key: 'password', label: '密码', type: 'text', placeholder: '留空则不修改' },
  {
    key: 'roleIds',
    label: '分配角色',
    type: 'multiselect',
    options: roleOptions.value,
    placeholder: '不选表示没有任何后台权限',
    wide: true,
    default: []
  },
  { key: 'status', label: '状态', type: 'switch', default: true }
])

const editingId = ref(null)
const handleAdd = () => {
  editingId.value = null
  modalTitle.value = '新增管理员'
  pageRef.value.openModal(null)
}
const handleEdit = (row) => {
  editingId.value = row.id
  modalTitle.value = '编辑管理员'
  pageRef.value.openModal({
    ...row,
    status: row.status === 1,
    password: '',
    roleIds: [...(row.roleIds || [])]
  })
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除管理员「${row.username}」？删除后不可恢复`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  const res = await request.delete(`/admin/users/${row.id}`)
  if (res.code !== 'OK') {
    ElMessage.error(res.message || '删除失败')
    return
  }
  ElMessage.success('删除成功')
  loadData()
}

const handleSubmit = async ({ form, isEdit }) => {
  // 状态在表单里是开关（布尔），后端约定 1/0
  const payload = { ...form, status: form.status ? 1 : 0, roleIds: form.roleIds || [] }
  if (!payload.password) delete payload.password
  // roleNames 只是列表展示用的回显字段，不回传
  delete payload.roleNames
  const url = isEdit ? `/admin/users/${editingId.value}` : '/admin/users'
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
  loadRoleOptions()
})
</script>

<style scoped>
.role-tag + .role-tag {
  margin-left: 4px;
}
</style>
