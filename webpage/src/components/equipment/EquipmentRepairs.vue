<template>
  <div class="equipment-page">
    <el-card class="page-card">
      <div class="page-heading">
        <div>
          <h2>修理记录</h2>
          <p>记录旧设备管理程序中的修理日期、类型、人员、验收人与修理内容。</p>
        </div>
        <div class="heading-actions">
          <el-button v-if="canExport" @click="handleExport">导出记录</el-button>
          <el-button v-if="canCreate" type="primary" @click="openCreate">新增修理记录</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="设备">
          <el-input v-model="query.keyword" placeholder="设备编号或名称" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="设备单位">
          <el-select v-model="query.unitId" clearable filterable placeholder="全部" style="width: 160px">
            <el-option v-for="item in options.units" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类别">
          <el-select v-model="query.categoryId" clearable filterable placeholder="全部" style="width: 160px">
            <el-option v-for="item in options.categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="修理类型">
          <el-select v-model="query.repairTypeId" clearable filterable placeholder="全部" style="width: 150px">
            <el-option v-for="item in options.repairTypes" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="修理人"><el-input v-model="query.repairPerson" clearable style="width: 130px" /></el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe height="calc(100vh - 330px)">
        <el-table-column prop="equipmentCode" label="设备编号" min-width="175" fixed="left" />
        <el-table-column prop="equipmentName" label="设备名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unitName" label="设备单位" width="120" />
        <el-table-column prop="categoryName" label="设备类别" width="120" />
        <el-table-column prop="repairDate" label="修理日期" width="115" />
        <el-table-column prop="repairTypeName" label="修理类型" width="130" />
        <el-table-column prop="repairPerson" label="修理人" width="120" />
        <el-table-column prop="acceptancePerson" label="验收人" width="120" />
        <el-table-column prop="repairContent" label="修理内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <span>共 {{ total }} 条修理记录</span>
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
                       :page-sizes="[10, 20, 50, 100]" layout="sizes, prev, pager, next, jumper"
                       @size-change="search" @current-change="loadRows" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑修理记录' : '新增修理记录'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" filterable style="width: 100%">
            <el-option v-for="item in options.assets" :key="item.id" :label="`${item.equipmentCode} - ${item.equipmentName}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="修理日期" prop="repairDate"><el-date-picker v-model="form.repairDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="修理类型" prop="repairTypeId"><el-select v-model="form.repairTypeId" filterable style="width: 100%"><el-option v-for="item in options.repairTypes" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="修理人"><el-input v-model="form.repairPerson" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="验收人"><el-input v-model="form.acceptancePerson" maxlength="100" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="修理内容" prop="repairContent"><el-input v-model="form.repairContent" type="textarea" :rows="5" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createEquipmentRepair,
  deleteEquipmentRepair,
  exportEquipmentRepairs,
  getEquipmentAssetOptions,
  getEquipmentBasicOptions,
  queryEquipmentRepairs,
  updateEquipmentRepair
} from '@/api/equipment'

const authStore = useAuthStore()
const canCreate = computed(() => authStore.hasPermission('equipment:repair:create'))
const canUpdate = computed(() => authStore.hasPermission('equipment:repair:update'))
const canDelete = computed(() => authStore.hasPermission('equipment:repair:delete'))
const canExport = computed(() => authStore.hasPermission('equipment:repair:export'))
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const dateRange = ref([])
const query = reactive({ keyword: '', unitId: null, categoryId: null, repairTypeId: null, repairPerson: '', startDate: null, endDate: null, page: 1, size: 20 })
const options = reactive({ units: [], categories: [], repairTypes: [], assets: [] })
const emptyForm = () => ({ equipmentId: null, repairDate: null, repairTypeId: null, repairPerson: '', acceptancePerson: '', repairContent: '', version: 0 })
const form = reactive(emptyForm())
const formRef = ref()
const dialogVisible = ref(false)
const editId = ref(null)
const rules = {
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  repairDate: [{ required: true, message: '请选择修理日期', trigger: 'change' }],
  repairTypeId: [{ required: true, message: '请选择修理类型', trigger: 'change' }],
  repairContent: [{ required: true, message: '请输入修理内容', trigger: 'blur' }]
}

watch(dateRange, value => {
  query.startDate = value?.[0] || null
  query.endDate = value?.[1] || null
})

const loadOptions = async () => {
  const [units, categories, types, assets] = await Promise.all([
    getEquipmentBasicOptions('units'), getEquipmentBasicOptions('categories'),
    getEquipmentBasicOptions('repair-types'), getEquipmentAssetOptions()
  ])
  options.units = units.data || []
  options.categories = categories.data || []
  options.repairTypes = types.data || []
  options.assets = assets.data || []
}
const loadRows = async () => {
  loading.value = true
  try {
    const res = await queryEquipmentRepairs({ ...query })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
const search = () => { query.page = 1; loadRows() }
const resetQuery = () => {
  dateRange.value = []
  Object.assign(query, { keyword: '', unitId: null, categoryId: null, repairTypeId: null, repairPerson: '', startDate: null, endDate: null, page: 1, size: 20 })
  loadRows()
}
const openCreate = () => { editId.value = null; Object.assign(form, emptyForm()); dialogVisible.value = true }
const openEdit = row => { editId.value = row.id; Object.assign(form, emptyForm(), row); dialogVisible.value = true }
const save = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      equipmentId: form.equipmentId, repairDate: form.repairDate, repairTypeId: form.repairTypeId,
      repairPerson: form.repairPerson, acceptancePerson: form.acceptancePerson,
      repairContent: form.repairContent, ...(editId.value ? { version: form.version } : {})
    }
    if (editId.value) await updateEquipmentRepair(editId.value, payload)
    else await createEquipmentRepair(payload)
    ElMessage.success(editId.value ? '修理记录已更新' : '修理记录已新增')
    dialogVisible.value = false
    await loadRows()
  } finally { saving.value = false }
}
const remove = async row => {
  await ElMessageBox.confirm(`确认删除 ${row.equipmentCode} 在 ${row.repairDate} 的修理记录吗？`, '删除修理记录', { type: 'warning' })
  await deleteEquipmentRepair(row.id)
  ElMessage.success('修理记录已删除')
  await loadRows()
}
const handleExport = async () => {
  const response = await exportEquipmentRepairs({ ...query, page: 1, size: 100 })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `设备修理记录-${new Date().toISOString().slice(0, 10)}.xlsx`
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => { await loadOptions(); await loadRows() })
</script>

<style scoped>
.equipment-page { min-width: 980px; }
.page-card { border: 1px solid var(--app-border-soft); }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
.page-heading h2 { margin: 0 0 6px; color: var(--app-text); }
.page-heading p { margin: 0; color: var(--app-text-tertiary); }
.heading-actions { display: flex; gap: 10px; }
.filter-form { padding: 14px 14px 0; margin-bottom: 16px; background: #f7f8fb; border-radius: 8px; }
.pagination-row { display: flex; justify-content: space-between; align-items: center; margin-top: 16px; color: var(--app-text-tertiary); }
:deep(.el-table__header th) { background: #f7f8fb; color: var(--app-text-secondary); }
</style>
