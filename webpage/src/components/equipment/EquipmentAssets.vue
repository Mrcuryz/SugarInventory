<template>
  <div class="equipment-page">
    <el-card class="page-card">
      <div class="page-heading">
        <div>
          <h2>设备台账</h2>
          <p>沿用旧设备档案字段，编号由系统按已启用的编号规则自动生成。</p>
        </div>
        <div class="heading-actions">
          <el-button v-if="canExport" @click="handleExport">导出台账</el-button>
          <el-button v-if="canCreate" type="primary" @click="openCreate">新增设备</el-button>
        </div>
      </div>

      <el-form :model="query" inline class="filter-form">
        <el-form-item label="设备">
          <el-input v-model="query.keyword" placeholder="编号、子编号或名称" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="设备单位">
          <el-select v-model="query.unitId" clearable filterable placeholder="全部" style="width: 170px">
            <el-option v-for="item in options.units" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类别">
          <el-select v-model="query.categoryId" clearable filterable placeholder="全部" style="width: 170px">
            <el-option v-for="item in options.categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="生产厂家">
          <el-select v-model="query.manufacturerId" clearable filterable placeholder="全部" style="width: 190px">
            <el-option v-for="item in options.manufacturers" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备关系">
          <el-select v-model="query.auxiliaryOnly" clearable placeholder="全部" style="width: 130px">
            <el-option label="主设备" :value="false" />
            <el-option label="附属设备" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe height="calc(100vh - 330px)" @row-dblclick="openDetail">
        <el-table-column prop="equipmentCode" label="设备编号" min-width="175" fixed="left" show-overflow-tooltip />
        <el-table-column prop="unitName" label="设备单位" width="120" />
        <el-table-column prop="categoryName" label="设备类别" width="120" />
        <el-table-column prop="equipmentName" label="设备名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="model" label="设备型号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="ratedPowerKw" label="功率(kW)" width="110" />
        <el-table-column prop="ratedVoltageV" label="电压(V)" width="100" />
        <el-table-column prop="ratedCurrentA" label="电流(A)" width="100" />
        <el-table-column prop="ratedSpeedRpm" label="转速(r/min)" width="115" />
        <el-table-column prop="manufacturerName" label="生产厂家" min-width="160" show-overflow-tooltip />
        <el-table-column prop="installationLocation" label="安装位置" min-width="150" show-overflow-tooltip />
        <el-table-column prop="commissioningDate" label="启用日期" width="115" />
        <el-table-column label="关系" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.parentEquipmentId" type="info">附属设备</el-tag>
            <el-tag v-else>主设备</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="canUpdate" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <span>共 {{ total }} 台设备</span>
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="sizes, prev, pager, next, jumper"
          @size-change="search"
          @current-change="loadRows"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editId ? '编辑设备' : '新增设备'" width="900px" destroy-on-close>
      <el-alert v-if="!editId" type="info" :closable="false" title="完整设备编号将根据设备单位、设备类别及其唯一启用的编号规则自动生成" class="form-alert" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-divider content-position="left">编号信息</el-divider>
        <el-row :gutter="18">
          <el-col :span="8">
            <el-form-item label="设备单位" prop="unitId">
              <el-select v-model="form.unitId" :disabled="Boolean(editId)" filterable style="width: 100%">
                <el-option v-for="item in options.units" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设备类别" prop="categoryId">
              <el-select v-model="form.categoryId" :disabled="Boolean(editId)" filterable style="width: 100%">
                <el-option v-for="item in options.categories" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="editId" :span="16">
            <el-form-item label="设备编号">
              <el-input v-model="form.equipmentCode" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="技改类别">
              <el-select v-model="form.renovationTypeId" clearable filterable style="width: 100%">
                <el-option v-for="item in options.renovationTypes" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="18">
          <el-col :span="8"><el-form-item label="设备名称" prop="equipmentName"><el-input v-model="form.equipmentName" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="设备型号"><el-input v-model="form.model" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="出厂编号"><el-input v-model="form.factorySerialNo" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="生产厂家">
              <el-select v-model="form.manufacturerId" clearable filterable style="width: 100%">
                <el-option v-for="item in options.manufacturers" :key="item.id" :label="`${item.name}（${item.code}）`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="厂家编号"><el-input :model-value="selectedManufacturerCode" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="安装位置"><el-input v-model="form.installationLocation" maxlength="255" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="主设备">
              <el-select v-model="form.parentEquipmentId" clearable filterable style="width: 100%" placeholder="留空表示主设备">
                <el-option v-for="item in parentOptions" :key="item.id" :label="`${item.equipmentCode} - ${item.equipmentName}`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">额定参数与价值</el-divider>
        <el-row :gutter="18">
          <el-col v-for="field in numericFields" :key="field.key" :span="8">
            <el-form-item :label="field.label">
              <el-input-number v-model="form[field.key]" :min="0" :precision="field.precision" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">日期与说明</el-divider>
        <el-row :gutter="18">
          <el-col :span="8"><el-form-item label="生产日期"><el-date-picker v-model="form.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="进厂日期"><el-date-picker v-model="form.arrivalDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="启用日期"><el-date-picker v-model="form.commissioningDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="说明"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="设备详情" size="760px">
      <template v-if="detail.asset">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="设备编号" :span="2">{{ detail.asset.equipmentCode }}</el-descriptions-item>
          <el-descriptions-item label="子编号">{{ detail.asset.equipmentSubNo }}</el-descriptions-item>
          <el-descriptions-item label="设备单位">{{ detail.asset.unitName }}</el-descriptions-item>
          <el-descriptions-item label="设备类别">{{ detail.asset.categoryName }}</el-descriptions-item>
          <el-descriptions-item label="技改类别">{{ detail.asset.renovationTypeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ detail.asset.equipmentName }}</el-descriptions-item>
          <el-descriptions-item label="设备型号">{{ detail.asset.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生产厂家">{{ detail.asset.manufacturerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="出厂编号">{{ detail.asset.factorySerialNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="安装位置" :span="2">{{ detail.asset.installationLocation || '-' }}</el-descriptions-item>
          <el-descriptions-item label="功率(kW)">{{ display(detail.asset.ratedPowerKw) }}</el-descriptions-item>
          <el-descriptions-item label="电压(V)">{{ display(detail.asset.ratedVoltageV) }}</el-descriptions-item>
          <el-descriptions-item label="电流(A)">{{ display(detail.asset.ratedCurrentA) }}</el-descriptions-item>
          <el-descriptions-item label="转速(r/min)">{{ display(detail.asset.ratedSpeedRpm) }}</el-descriptions-item>
          <el-descriptions-item label="价格(元)">{{ display(detail.asset.price) }}</el-descriptions-item>
          <el-descriptions-item label="安装费(元)">{{ display(detail.asset.installationCost) }}</el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ detail.asset.productionDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="进厂日期">{{ detail.asset.arrivalDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="启用日期">{{ detail.asset.commissioningDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="3">{{ detail.asset.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <h3 class="detail-title">附属设备</h3>
        <el-table :data="detail.auxiliaryEquipment || []" border size="small" empty-text="暂无附属设备">
          <el-table-column prop="equipmentCode" label="设备编号" min-width="170" />
          <el-table-column prop="equipmentName" label="设备名称" />
          <el-table-column prop="model" label="设备型号" />
        </el-table>

        <h3 class="detail-title">修理记录</h3>
        <el-table :data="detail.repairRecords || []" border size="small" empty-text="暂无修理记录">
          <el-table-column prop="repairDate" label="修理日期" width="110" />
          <el-table-column prop="repairTypeName" label="修理类型" width="120" />
          <el-table-column prop="repairPerson" label="修理人" width="110" />
          <el-table-column prop="repairContent" label="修理内容" min-width="220" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createEquipmentAsset,
  deleteEquipmentAsset,
  exportEquipmentAssets,
  getEquipmentAsset,
  getEquipmentAssetOptions,
  getEquipmentBasicOptions,
  queryEquipmentAssets,
  updateEquipmentAsset
} from '@/api/equipment'

const authStore = useAuthStore()
const canCreate = computed(() => authStore.hasPermission('equipment:asset:create'))
const canUpdate = computed(() => authStore.hasPermission('equipment:asset:update'))
const canDelete = computed(() => authStore.hasPermission('equipment:asset:delete'))
const canExport = computed(() => authStore.hasPermission('equipment:asset:export'))

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ keyword: '', unitId: null, categoryId: null, manufacturerId: null, renovationTypeId: null, auxiliaryOnly: null, page: 1, size: 20 })
const options = reactive({ units: [], categories: [], manufacturers: [], renovationTypes: [], assets: [] })

const emptyForm = () => ({
  unitId: null, categoryId: null, equipmentCode: '', renovationTypeId: null,
  parentEquipmentId: null, manufacturerId: null, equipmentName: '', model: '', ratedPowerKw: null,
  ratedVoltageV: null, ratedCurrentA: null, ratedSpeedRpm: null, price: null, installationCost: null,
  installationLocation: '', factorySerialNo: '', productionDate: null, arrivalDate: null,
  commissioningDate: null, remark: '', version: 0
})
const form = reactive(emptyForm())
const formRef = ref()
const dialogVisible = ref(false)
const editId = ref(null)
const detailVisible = ref(false)
const detail = reactive({ asset: null, auxiliaryEquipment: [], repairRecords: [] })
const rules = {
  unitId: [{ required: true, message: '请选择设备单位', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择设备类别', trigger: 'change' }],
  equipmentName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }]
}
const numericFields = [
  { key: 'ratedPowerKw', label: '额定功率(kW)', precision: 3 },
  { key: 'ratedVoltageV', label: '额定电压(V)', precision: 3 },
  { key: 'ratedCurrentA', label: '额定电流(A)', precision: 3 },
  { key: 'ratedSpeedRpm', label: '额定转速', precision: 3 },
  { key: 'price', label: '价格(元)', precision: 2 },
  { key: 'installationCost', label: '安装费(元)', precision: 2 }
]

const selectedManufacturerCode = computed(() => options.manufacturers.find(item => item.id === form.manufacturerId)?.code || '')
const parentOptions = computed(() => options.assets.filter(item => item.id !== editId.value))
const display = value => value === null || value === undefined || value === '' ? '-' : value

const loadOptions = async () => {
  const [units, categories, manufacturers, renovations, assets] = await Promise.all([
    getEquipmentBasicOptions('units'), getEquipmentBasicOptions('categories'),
    getEquipmentBasicOptions('manufacturers'), getEquipmentBasicOptions('renovation-types'),
    getEquipmentAssetOptions()
  ])
  options.units = units.data || []
  options.categories = categories.data || []
  options.manufacturers = manufacturers.data || []
  options.renovationTypes = renovations.data || []
  options.assets = assets.data || []
}

const loadRows = async () => {
  loading.value = true
  try {
    const res = await queryEquipmentAssets({ ...query })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
const search = () => { query.page = 1; loadRows() }
const resetQuery = () => {
  Object.assign(query, { keyword: '', unitId: null, categoryId: null, manufacturerId: null, renovationTypeId: null, auxiliaryOnly: null, page: 1, size: 20 })
  loadRows()
}
const resetForm = () => Object.assign(form, emptyForm())
const openCreate = () => { editId.value = null; resetForm(); dialogVisible.value = true }
const openEdit = row => {
  editId.value = row.id
  const next = emptyForm()
  Object.keys(next).forEach(key => { next[key] = row[key] ?? next[key] })
  Object.assign(form, next)
  dialogVisible.value = true
}
const openDetail = async row => {
  const res = await getEquipmentAsset(row.id)
  Object.assign(detail, res.data || { asset: null, auxiliaryEquipment: [], repairRecords: [] })
  detailVisible.value = true
}

const save = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    let res
    if (editId.value) {
      const payload = { ...form }
      delete payload.unitId; delete payload.categoryId; delete payload.equipmentCode
      res = await updateEquipmentAsset(editId.value, payload)
    } else {
      const payload = { ...form }
      delete payload.equipmentCode; delete payload.version
      res = await createEquipmentAsset(payload)
    }
    ElMessage.success(editId.value ? '设备已更新' : `设备已创建，编号：${res.data?.equipmentCode || ''}`)
    dialogVisible.value = false
    await Promise.all([loadRows(), loadOptions()])
  } finally {
    saving.value = false
  }
}

const remove = async row => {
  await ElMessageBox.confirm(`确认删除设备“${row.equipmentCode} - ${row.equipmentName}”吗？`, '删除设备', { type: 'warning' })
  await deleteEquipmentAsset(row.id)
  ElMessage.success('设备已删除')
  await Promise.all([loadRows(), loadOptions()])
}

const handleExport = async () => {
  const response = await exportEquipmentAssets({ ...query, page: 1, size: 100 })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `设备台账-${new Date().toISOString().slice(0, 10)}.xlsx`
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  await loadOptions()
  await loadRows()
})
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
.form-alert { margin-bottom: 14px; }
.detail-title { margin: 24px 0 10px; font-size: 16px; }
:deep(.el-table__header th) { background: #f7f8fb; color: var(--app-text-secondary); }
</style>
