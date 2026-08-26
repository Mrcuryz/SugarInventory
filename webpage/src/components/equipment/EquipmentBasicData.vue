<template>
  <div class="basic-page">
    <el-card class="page-card">
      <div class="page-heading">
        <div>
          <h2>设备基础资料</h2>
          <p>维护旧设备字段所需的单位、类别、厂家、技改类别、修理类型和编号规则。</p>
        </div>
      </div>

      <el-tabs v-model="active" @tab-change="changeTab">
        <el-tab-pane v-for="tab in tabs" :key="tab.key" :name="tab.key" :label="tab.label" />
      </el-tabs>

      <div class="toolbar">
        <el-input v-model="query.keyword" clearable :placeholder="`搜索${currentTab.label}`" style="width: 280px" @keyup.enter="search" />
        <el-select v-if="active !== 'manufacturers'" v-model="query.enabled" clearable placeholder="全部状态" style="width: 130px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <div class="toolbar-spacer" />
        <el-button v-if="canManage" type="primary" @click="openCreate">新增{{ currentTab.label }}</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe height="calc(100vh - 350px)">
        <template v-if="active === 'units'">
          <el-table-column prop="unitCode" label="单位代码" width="140" />
          <el-table-column prop="unitName" label="设备单位" min-width="220" />
          <el-table-column prop="sortOrder" label="排序" width="90" />
        </template>
        <template v-else-if="active === 'categories'">
          <el-table-column prop="categoryName" label="设备类别" min-width="200" />
          <el-table-column prop="majorCode" label="编号大类" width="120" />
          <el-table-column prop="defaultSequenceStart" label="默认号段起点" width="140" />
          <el-table-column prop="defaultSequenceEnd" label="默认号段终点" width="140" />
          <el-table-column prop="sortOrder" label="排序" width="90" />
        </template>
        <template v-else-if="active === 'manufacturers'">
          <el-table-column prop="manufacturerCode" label="厂家编号" width="140" />
          <el-table-column prop="manufacturerName" label="厂家名称" min-width="200" />
          <el-table-column prop="address" label="地址" min-width="190" show-overflow-tooltip />
          <el-table-column prop="contactPerson" label="联系人" width="110" />
          <el-table-column prop="phone" label="电话" width="150" />
          <el-table-column prop="fax" label="传真" width="150" />
        </template>
        <template v-else-if="active === 'codeRules'">
          <el-table-column prop="companyCode" label="企业代码" width="110" />
          <el-table-column prop="unitName" label="设备单位" min-width="140" />
          <el-table-column prop="categoryName" label="设备类别" min-width="140" />
          <el-table-column prop="majorCode" label="大类代码" width="100" />
          <el-table-column prop="sectionCode" label="工段代码" width="100" />
          <el-table-column prop="sequenceStart" label="号段起点" width="100" />
          <el-table-column prop="sequenceEnd" label="号段终点" width="100" />
          <el-table-column prop="nextSequence" label="下一个序号" width="110" />
        </template>
        <template v-else>
          <el-table-column prop="typeName" :label="currentTab.label" min-width="240" />
          <el-table-column prop="sortOrder" label="排序" width="90" />
        </template>

        <el-table-column v-if="active !== 'manufacturers'" label="状态" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :disabled="!canManage" @change="value => toggleEnabled(row, value)" />
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canManage" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canManage" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <span>共 {{ total }} 条</span>
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total"
                       :page-sizes="[10, 20, 50, 100]" layout="sizes, prev, pager, next, jumper"
                       @size-change="search" @current-change="loadRows" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="`${editId ? '编辑' : '新增'}${currentTab.label}`" width="620px" destroy-on-close>
      <el-form :model="form" label-width="120px">
        <template v-if="active === 'units'">
          <el-form-item label="单位代码" required><el-input v-model="form.unitCode" maxlength="20" placeholder="例如 YZ" /></el-form-item>
          <el-form-item label="设备单位" required><el-input v-model="form.unitName" maxlength="100" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="active === 'categories'">
          <el-form-item label="设备类别" required><el-input v-model="form.categoryName" maxlength="100" placeholder="例如 电机" /></el-form-item>
          <el-form-item label="编号大类代码" required><el-input v-model="form.majorCode" maxlength="10" placeholder="例如 D" /></el-form-item>
          <el-form-item label="默认号段">
            <div class="range-row">
              <el-input-number v-model="form.defaultSequenceStart" :min="0" :max="9999" />
              <span>至</span>
              <el-input-number v-model="form.defaultSequenceEnd" :min="0" :max="9999" />
            </div>
          </el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else-if="active === 'manufacturers'">
          <el-form-item label="厂家编号" required><el-input v-model="form.manufacturerCode" maxlength="30" /></el-form-item>
          <el-form-item label="厂家名称" required><el-input v-model="form.manufacturerName" maxlength="150" /></el-form-item>
          <el-form-item label="地址"><el-input v-model="form.address" maxlength="255" /></el-form-item>
          <el-row :gutter="14">
            <el-col :span="12"><el-form-item label="联系人"><el-input v-model="form.contactPerson" maxlength="50" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" maxlength="50" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="传真"><el-input v-model="form.fax" maxlength="50" /></el-form-item></el-col>
          </el-row>
          <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
        </template>

        <template v-else-if="active === 'codeRules'">
          <el-alert type="warning" :closable="false" title="编号游标只能向前调整，已使用的规则不能删除。" class="rule-alert" />
          <el-form-item label="企业代码" required><el-input v-model="form.companyCode" maxlength="20" /></el-form-item>
          <el-form-item label="设备单位" required>
            <el-select v-model="form.unitId" filterable style="width: 100%"><el-option v-for="item in options.units" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" /></el-select>
          </el-form-item>
          <el-form-item label="设备类别" required>
            <el-select v-model="form.categoryId" filterable style="width: 100%"><el-option v-for="item in options.categories" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" /></el-select>
          </el-form-item>
          <el-form-item label="工段代码" required><el-input v-model="form.sectionCode" maxlength="10" /></el-form-item>
          <el-form-item label="号段范围" required>
            <div class="range-row">
              <el-input-number v-model="form.sequenceStart" :min="0" :max="9999" />
              <span>至</span>
              <el-input-number v-model="form.sequenceEnd" :min="0" :max="9999" />
            </div>
          </el-form-item>
          <el-form-item label="下一个序号" required><el-input-number v-model="form.nextSequence" :min="0" :max="10000" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>

        <template v-else>
          <el-form-item :label="currentTab.label" required><el-input v-model="form.typeName" maxlength="100" /></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createEquipmentBasicData,
  deleteEquipmentBasicData,
  getEquipmentBasicOptions,
  queryEquipmentBasicData,
  setEquipmentBasicEnabled,
  updateEquipmentBasicData
} from '@/api/equipment'

const tabs = [
  { key: 'units', resource: 'units', label: '设备单位' },
  { key: 'categories', resource: 'categories', label: '设备类别' },
  { key: 'manufacturers', resource: 'manufacturers', label: '生产厂家' },
  { key: 'renovationTypes', resource: 'renovation-types', label: '技改类别' },
  { key: 'repairTypes', resource: 'repair-types', label: '修理类型' },
  { key: 'codeRules', resource: 'code-rules', label: '编号规则' }
]
const authStore = useAuthStore()
const active = ref('units')
const currentTab = computed(() => tabs.find(item => item.key === active.value) || tabs[0])
const canManage = computed(() => active.value === 'codeRules'
  ? authStore.hasPermission('equipment:code-rule:manage')
  : authStore.hasPermission('equipment:config:manage'))
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ keyword: '', enabled: null, page: 1, size: 20 })
const options = reactive({ units: [], categories: [] })
const dialogVisible = ref(false)
const editId = ref(null)
const form = reactive({})

const emptyForm = () => {
  if (active.value === 'units') return { unitCode: '', unitName: '', sortOrder: 0, enabled: true }
  if (active.value === 'categories') return { categoryName: '', majorCode: '', defaultSequenceStart: null, defaultSequenceEnd: null, sortOrder: 0, enabled: true }
  if (active.value === 'manufacturers') return { manufacturerCode: '', manufacturerName: '', address: '', contactPerson: '', phone: '', fax: '', remark: '' }
  if (active.value === 'codeRules') return { companyCode: 'LBYX', unitId: null, categoryId: null, sectionCode: '', sequenceStart: 0, sequenceEnd: 9999, nextSequence: 0, enabled: true }
  return { typeName: '', sortOrder: 0, enabled: true }
}

const formatTime = value => value ? value.replace('T', ' ').slice(0, 19) : '-'
const loadOptions = async () => {
  const [units, categories] = await Promise.all([getEquipmentBasicOptions('units'), getEquipmentBasicOptions('categories')])
  options.units = units.data || []
  options.categories = categories.data || []
}
const loadRows = async () => {
  loading.value = true
  try {
    const res = await queryEquipmentBasicData(currentTab.value.resource, { ...query })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}
const search = () => { query.page = 1; loadRows() }
const resetQuery = () => { Object.assign(query, { keyword: '', enabled: null, page: 1, size: 20 }); loadRows() }
const changeTab = () => { Object.assign(query, { keyword: '', enabled: null, page: 1, size: 20 }); loadRows() }
const resetForm = value => {
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, value)
}
const openCreate = () => { editId.value = null; resetForm(emptyForm()); dialogVisible.value = true }
const openEdit = row => {
  editId.value = row.id
  const next = emptyForm()
  Object.keys(next).forEach(key => { next[key] = row[key] ?? next[key] })
  resetForm(next)
  dialogVisible.value = true
}

const validate = () => {
  if (active.value === 'units' && (!form.unitCode?.trim() || !form.unitName?.trim())) return '请填写单位代码和设备单位'
  if (active.value === 'categories' && (!form.categoryName?.trim() || !form.majorCode?.trim())) return '请填写设备类别和编号大类代码'
  if (active.value === 'manufacturers' && (!form.manufacturerCode?.trim() || !form.manufacturerName?.trim())) return '请填写厂家编号和厂家名称'
  if (['renovationTypes', 'repairTypes'].includes(active.value) && !form.typeName?.trim()) return `请填写${currentTab.value.label}`
  if (active.value === 'codeRules') {
    if (!form.companyCode?.trim() || !form.unitId || !form.categoryId || !form.sectionCode?.trim()) return '请完整填写编号规则'
    if (form.sequenceStart > form.sequenceEnd || form.nextSequence < form.sequenceStart || form.nextSequence > form.sequenceEnd + 1) return '编号号段或下一个序号无效'
  }
  return ''
}

const save = async () => {
  const message = validate()
  if (message) { ElMessage.warning(message); return }
  saving.value = true
  try {
    if (editId.value) await updateEquipmentBasicData(currentTab.value.resource, editId.value, { ...form })
    else await createEquipmentBasicData(currentTab.value.resource, { ...form })
    ElMessage.success(`${currentTab.value.label}已保存`)
    dialogVisible.value = false
    await Promise.all([loadRows(), loadOptions()])
  } finally { saving.value = false }
}
const toggleEnabled = async (row, enabled) => {
  try {
    await setEquipmentBasicEnabled(currentTab.value.resource, row.id, enabled)
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch (error) {
    row.enabled = !enabled
    throw error
  }
}
const remove = async row => {
  await ElMessageBox.confirm(`确认删除这条${currentTab.value.label}吗？已被引用的数据不能删除。`, '删除确认', { type: 'warning' })
  await deleteEquipmentBasicData(currentTab.value.resource, row.id)
  ElMessage.success('删除成功')
  await Promise.all([loadRows(), loadOptions()])
}

onMounted(async () => { await loadOptions(); await loadRows() })
</script>

<style scoped>
.basic-page { min-width: 900px; }
.page-card { border: 1px solid var(--app-border-soft); }
.page-heading { margin-bottom: 12px; }
.page-heading h2 { margin: 0 0 6px; color: var(--app-text); }
.page-heading p { margin: 0; color: var(--app-text-tertiary); }
.toolbar { display: flex; gap: 10px; align-items: center; padding: 12px; margin: 8px 0 16px; background: #f7f8fb; border-radius: 8px; }
.toolbar-spacer { flex: 1; }
.pagination-row { display: flex; justify-content: space-between; align-items: center; margin-top: 16px; color: var(--app-text-tertiary); }
.range-row { display: flex; align-items: center; gap: 10px; }
.rule-alert { margin-bottom: 16px; }
:deep(.el-table__header th) { background: #f7f8fb; color: var(--app-text-secondary); }
</style>
