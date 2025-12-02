<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="时间范围">
          <el-date-picker
              v-model="searchForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 434px"
          />
        </el-form-item>
        <br>
        <el-form-item label="化验产品名称">
          <el-input
              v-model="searchForm.productName"
              placeholder="请输入化验产品名称"
              clearable
              style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="化验员名称">
          <el-input
              v-model="searchForm.testerName"
              placeholder="请输入化验员名称名称"
              clearable
              style="width: 165px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="dialogVisible = true;operationType='新增化验'">新增</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" style="max-width: 1200px">
      <el-table
          :data="resultList"
          style="width: 95%"
          height="500"
          stripe
          border
          v-loading="loading"
          row-key="id"
          :tree-props="{children: 'historyVersions', hasChildren: 'hasHistory'}"
      >
        <el-table-column prop="productName" label="化验产品名称" width="120"/>
        <el-table-column prop="sampleDate" label="采样日期" width="120" sortable/>
        <el-table-column prop="colorValue" label="色值" width="120" sortable/>
        <el-table-column prop="reducingSugar" label="还原糖分" width="120" sortable/>
        <el-table-column prop="dryWeight" label="干燥失重" width="120" sortable/>
        <el-table-column prop="conductivityAsh" label="电导灰分" width="120" sortable/>
        <el-table-column prop="sucrose" label="蔗糖分" width="120" sortable/>
        <el-table-column prop="insolubleImpurity" label="不溶于水杂质" width="150" sortable/>
        <el-table-column prop="phValue" label="pH值" width="120" sortable/>
        <el-table-column prop="testerName" label="化验员名称" width="120"/>
        <el-table-column prop="version" label="版本" width="120"/>
        <el-table-column prop="isQualified" label="是否合格" width="120">
          <template #default="{ row }">
            <el-tag type="success" v-if="row.isQualified === '合格'">合格</el-tag>
            <el-tag type="danger" v-else>不合格</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qualifiedStandards" label="合格标准" width="200"/>
        <el-table-column type="expand" width="100" label="历史版本" fixed="left">
          <template #default="{ row }">
            <div v-if="row.historyVersions && row.historyVersions.length > 0">
              <el-table :data="row.historyVersions" border style="background-color: #03791e">
                <el-table-column width="100"/>
                <el-table-column prop="productName" label="化验产品名称" width="120"/>
                <el-table-column prop="sampleDate" label="采样日期" width="120" sortable/>
                <el-table-column prop="colorValue" label="色值" width="120" sortable/>
                <el-table-column prop="reducingSugar" label="还原糖分" width="120" sortable/>
                <el-table-column prop="dryWeight" label="干燥失重" width="120" sortable/>
                <el-table-column prop="conductivityAsh" label="电导灰分" width="120" sortable/>
                <el-table-column prop="sucrose" label="蔗糖分" width="120" sortable/>
                <el-table-column prop="insolubleImpurity" label="不溶于水杂质" width="150" sortable/>
                <el-table-column prop="phValue" label="pH值" width="120" sortable/>
                <el-table-column prop="testerName" label="化验员名称" width="120"/>
                <el-table-column prop="version" label="版本" width="120"/>
                <el-table-column prop="isQualified" label="是否合格" width="120">
                  <template #default="{ row: historyRow }">
                    <el-tag type="success" v-if="historyRow.isQualified === '合格'">合格</el-tag>
                    <el-tag type="danger" v-else>不合格</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="qualifiedStandards" label="合格标准" width="200"/>
                <el-table-column width="150"/>
              </el-table>
            </div>
            <div v-else>
              无历史版本数据
            </div>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="200">
          <template #default="{ row }">
            <el-button type="primary" size="small"
                       @click="dialogVisible = true;operationType='修改化验';handleEdit(row)">编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            <el-button type="success" size="small" @click="handleCopy(row)">复制</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :page-size="pageSize"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
        :title=operationType
        v-model="dialogVisible"
        width="40%"
        :before-close="handleClose"
    >
      <el-form :model="submitForm" :rules="rule" label-width="auto">
        <!-- 1. 选择类型切换（单选框版本） -->
        <el-form-item label="选择类型" prop="selectType" required v-if="operationType === '新增化验'">
          <el-radio-group
              v-model="submitForm.selectType"
              class="radio-group"
          >
            <el-radio
                label="1"
                border
                class="radio-item"
            > 选择产品
            </el-radio>
            <el-radio
                label="2"
                border
                class="radio-item"
            >
              选择验收标准
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="化验产品名称" prop="productId"
                      v-if="(operationType === '新增化验' && submitForm.selectType === '1') || operationType === '复制化验'">
          <el-cascader
              v-model="submitForm.productId"
              :options="productOptions"
              :props="cascaderProps"
              placeholder="请选择化验产品名称"
              style="width: 100%"
              clearable
          />
        </el-form-item>
        <!-- 2.2 化验标准选择（多选框） -->
        <el-form-item
            v-if="submitForm.selectType === '2'"
            label="验收标准"
            prop="relatedId"
            required
        >
          <el-select
              v-model="submitForm.relatedId"
              placeholder="请选择化验标准"
              style="width: 100%"
              collapse-tags
              filterable
              clearable
          >
            <el-option
                v-for="standard in assayStandardList"
                :key="standard.id"
                :label="standard.standardName"
                :value="standard.id"
            >
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="采样日期" prop="sampleDate">
          <el-date-picker v-model="submitForm.sampleDate" type="date" placeholder="请选择采样日期"
                          style="width: 100%"></el-date-picker>
        </el-form-item>
        <el-form-item label="色值" prop="colorValue">
          <el-input v-model="submitForm.colorValue" placeholder="请输入色值" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="还原糖分" prop="reducingSugar">
          <el-input v-model="submitForm.reducingSugar" placeholder="请输入还原糖分" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="干燥失重" prop="dryWeight">
          <el-input v-model="submitForm.dryWeight" placeholder="请输入干燥失重" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="电导灰分" prop="conductivityAsh">
          <el-input v-model="submitForm.conductivityAsh" placeholder="请输入电导灰分" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="蔗糖分" prop="sucrose">
          <el-input v-model="submitForm.sucrose" placeholder="请输入蔗糖分" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="不溶于水杂质" prop="insolubleImpurity">
          <el-input v-model="submitForm.insolubleImpurity" placeholder="请输入不溶于水杂质"
                    style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="pH值" prop="phValue">
          <el-input v-model="submitForm.phValue" placeholder="请输入pH值" style="width: 100%"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary"
                   @click="operationType === '新增化验' || operationType === '复制化验' ? handleNew() : handleUpdate()">
          确定
        </el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import dayjs from 'dayjs'
import {computed, onMounted, reactive, ref} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {addAssay, deleteAssay, getAssay, getSemiProduct, getStProduct, updateAssay} from "@/api/assay";
import {getProductList} from "@/api/product";
import {getAssayGroup} from "@/api/assayGroup";
// 搜索表单
const searchForm = ref({
  productName: '',
  dateRange: [],
  testerName: '',
  version: ''
})
// 列表
let resultList = ref([])
const originalList = ref([])
// 加载状态
const loading = ref(false)

// 分页参数
const pageSize = ref(10)
const currentPage = ref(1)
const total = ref(0)
// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  handleSearch()
}
const handleCurrentChange = (page) => {
  currentPage.value = page
  handleSearch()
}

// 处理搜索
const handleSearch = async () => {
  let params = {
    page: currentPage.value,
    size: pageSize.value
  }
  if (searchForm.value.productName !== '') {
    params.productName = searchForm.value.productName
  }
  if (searchForm.value.dateRange !== []) {
    params.startDate = searchForm.value.dateRange[0]
    params.endDate = searchForm.value.dateRange[1]
  }
  if (searchForm.value.testerName !== '') {
    params.testerName = searchForm.value.testerName
  }
  if (searchForm.value.version !== '') {
    params.version = parseInt(searchForm.value.version)
  }
  loading.value = true
  let res = await getAssay(params)
  if (res.code === 200) {
    total.value = res.data.total
    originalList.value = res.data.records
    for (let i = 0; i < originalList.value.length; i++) {
      originalList.value[i].qualifiedStandards = JSON.parse(originalList.value[i].qualifiedStandards)
    }
    resultList = processResultList(originalList.value);
    loading.value = false
  } else {
    ElMessage.error(res.msg)
  }
}

// 化验标准相关
const assayStandardList = ref([])
const assayStandardLoading = ref(false)

// 获取化验标准列表
const getAssayStandards = async (params = {}) => {
  try {
    assayStandardLoading.value = true
    const res = await getAssayGroup({
      page: 1,
      size: 1000,
      ...params
    })
    assayStandardList.value = res.data.records || []
  } catch (error) {
    ElMessage.error('获取化验标准列表失败：' + error.message)
  } finally {
    assayStandardLoading.value = false
  }
}
// 处理数据的方法
const processResultList = (data) => {
  // 按产品名称和采样日期分组
  const grouped = data.reduce((acc, item) => {
    const key = `${item.productName}-${item.sampleDate}` // 组合键

    if (!acc[key]) {
      acc[key] = []
    }
    acc[key].push(item)
    return acc
  }, {})

  // 对每组按版本排序（假设版本号是数字或可比较的字符串）
  Object.keys(grouped).forEach(key => {
    grouped[key].sort((a, b) => {
      // 这里根据你的实际版本格式进行调整
      // 如果是数字版本
      return Number(b.version) - Number(a.version)
      // 如果是日期字符串版本
      // return new Date(b.version) - new Date(a.version)
    })
  })

  // 构建树形结构
  return Object.keys(grouped).map(key => {
    const versions = grouped[key]
    const latestVersion = versions[0]

    return {
      ...latestVersion,
      historyVersions: versions.slice(1), // 除最新版本外的所有版本
      hasHistory: versions.length > 1    // 是否有历史版本
    }
  })
}

// 处理重置
const handleReset = () => {
  searchForm.value = {
    productName: '',
    dateRange: [],
    testerName: '',
    version: ''
  }
  handleSearch()
}

const rule = {
  productId: [
    {required: true, message: '请选择化验产品名称', trigger: 'blur'}
  ],
  sampleDate: [
    {required: true, message: '请选择采样日期', trigger: 'blur'}
  ],
  colorValue: [
    {type: 'string', message: '色值必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/}
  ],
  reducingSugar: [
    {type: 'string', message: '去糖度必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/}
  ],
  dryWeight: [
    {type: 'string', message: '干重必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/}
  ],
  conductivityAsh: [
    {type: 'string', message: '电导率必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/}
  ],
  sucrose: [
    {type: 'string', message: '糖度必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/}
  ],
  insolubleImpurity: [
    {type: 'string', message: '杂质必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/}
  ],
  phValue: [
    {type: 'string', message: 'pH值必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/}
  ]
}
const dialogVisible = ref(false)
const handleClose = () => {
  submitForm.value = {}
  dialogVisible.value = false
}
const operationType = ref('')
const submitForm = ref({
  productId: undefined,
  relatedId: undefined,
  selectType: '1',
  sampleDate: ''
})

const handleNew = async () => {
  // 校验表单
  if (submitForm.value.productId === '') {
    ElMessage.error('请输入化验产品名称')
    return
  }
  if (submitForm.value.sampleDate === '') {
    ElMessage.error('请选择采样日期')
    return
  }
  let params = {}
  if (submitForm.value.productId) {
    params.productId = submitForm.value.productId
  }
  if (submitForm.value.sampleDate) {
    submitForm.value.sampleDate = dayjs(submitForm.value.sampleDate).format('YYYY-MM-DD')
    params.sampleDate = submitForm.value.sampleDate
  }
  if (submitForm.value.colorValue) {
    params.colorValue = submitForm.value.colorValue
  }
  if (submitForm.value.reducingSugar) {
    params.reducingSugar = submitForm.value.reducingSugar
  }
  if (submitForm.value.dryWeight) {
    params.dryWeight = submitForm.value.dryWeight
  }
  if (submitForm.value.conductivityAsh) {
    params.conductivityAsh = submitForm.value.conductivityAsh
  }
  if (submitForm.value.sucrose) {
    params.sucrose = submitForm.value.sucrose
  }
  if (submitForm.value.insolubleImpurity) {
    params.insolubleImpurity = submitForm.value.insolubleImpurity
  }
  if (submitForm.value.phValue) {
    params.phValue = submitForm.value.phValue
  }
  params.selectType = submitForm.value.selectType
  params.relatedId = submitForm.value.relatedId
  console.log(params)
  let res = await addAssay([params])
  if (res.code === 200) {
    ElMessage.success('新增成功')
    await handleSearch()
    dialogVisible.value = false
    submitForm.value = {
      productId: undefined,
      sampleDate: ''
    }
  } else {
    ElMessage.error(res.msg)
  }
}

// 编辑
const handleEdit = (row) => {
  operationType.value = '修改化验'
  submitForm.value = {
    id: row.id,
    productId: row.productId,
    sampleDate: row.sampleDate,
    colorValue: row.colorValue,
    reducingSugar: row.reducingSugar,
    dryWeight: row.dryWeight,
    conductivityAsh: row.conductivityAsh,
    sucrose: row.sucrose,
    insolubleImpurity: row.insolubleImpurity,
    phValue: row.phValue
  }
  dialogVisible.value = true
}

const handleUpdate = async () => {
  // 校验表单
  if (submitForm.value.sampleDate === '') {
    ElMessage.error('请输入采样日期')
    return
  }
  let params = {}
  if (submitForm.value.productId) {
    params.productId = submitForm.value.productId
  }
  if (submitForm.value.sampleDate) {
    params.sampleDate = submitForm.value.sampleDate
  }
  if (submitForm.value.colorValue) {
    params.colorValue = submitForm.value.colorValue
  }
  if (submitForm.value.reducingSugar) {
    params.reducingSugar = submitForm.value.reducingSugar
  }
  if (submitForm.value.dryWeight) {
    params.dryWeight = submitForm.value.dryWeight
  }
  if (submitForm.value.conductivityAsh) {
    params.conductivityAsh = submitForm.value.conductivityAsh
  }
  if (submitForm.value.sucrose) {
    params.sucrose = submitForm.value.sucrose
  }
  if (submitForm.value.insolubleImpurity) {
    params.insolubleImpurity = submitForm.value.insolubleImpurity
  }
  if (submitForm.value.phValue) {
    params.phValue = submitForm.value.phValue
  }
  let res = await updateAssay(submitForm.value.id, params)
  if (res.code === 200) {
    ElMessage.success('修改成功')
    await handleSearch()
    dialogVisible.value = false
    submitForm.value = {
      productId: undefined,
      sampleDate: ''
    }
  } else {
    ElMessage.error(res.msg)
  }
}

//删除化验
const handleDelete = async (row) => {
  const confirm = await ElMessageBox.confirm('确认删除该化验记录吗？')
  if (confirm) {
    let res = await deleteAssay(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await handleSearch()
    } else {
      ElMessage.error(res.msg)
    }
  }
}
//复制化验
const handleCopy = async (row) => {
  operationType.value = '复制化验'
  submitForm.value = {
    sampleDate: row.sampleDate,
    colorValue: row.colorValue,
    reducingSugar: row.reducingSugar,
    dryWeight: row.dryWeight,
    conductivityAsh: row.conductivityAsh,
    sucrose: row.sucrose,
    insolubleImpurity: row.insolubleImpurity,
    phValue: row.phValue
  }
  dialogVisible.value = true
}

// 级联组件配置（保持不变）
const cascaderProps = reactive({
  emitPath: false,
  label: 'label',
  value: 'value',
  children: 'children'
})

// 合并处理成品和半成品数据
const productOptions = computed(() => {
  // 合并数据并添加分类标记
  const combinedProducts = [
    ...StProductList.value.map(p => ({...p, category: '成品'})),
    ...semiProductList.value.map(p => ({...p, category: '半成品'}))
  ]

  const categoryMap = {}

  combinedProducts.forEach(product => {
    // 第一级：成品/半成品
    const categoryNode = categoryMap[product.category] || {
      value: product.category,
      label: product.category,
      children: {}
    }

    // 第二级：产品类型
    const typeNode = categoryNode.children[product.productType] || {
      value: product.productType,
      label: product.productType,
      children: []
    }

    // 第三级：具体产品
    const productNode = {
      value: product.productId,
      label: product.productName
    }

    // 更新数据结构
    if (!categoryMap[product.category]) {
      categoryMap[product.category] = categoryNode
    }
    if (!categoryNode.children[product.productType]) {
      categoryNode.children[product.productType] = typeNode
    }
    typeNode.children.push(productNode)
  })

  // 转换数据结构为数组格式
  return Object.values(categoryMap).map(category => ({
    ...category,
    children: Object.values(category.children).map(type => ({
      ...type,
      children: type.children
    }))
  }))
})
const productList = ref([])
const getProduct = async () => {
  let res = await getProductList()
  productList.value = res.data
}
const semiProductList = ref([])
const SemiProduct = async () => {
  let res = await getSemiProduct()
  semiProductList.value = res.data
}
const StProductList = ref([])
const StProduct = async () => {
  let res = await getStProduct()
  StProductList.value = res.data
}
onMounted(() => {
  getAssayStandards()
  handleSearch()
  getProduct()
  SemiProduct()
  StProduct()
})
</script>

<style scoped>
.operation-logs {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
  background: rgb(255, 255, 255);
}

.table-card {
  background: rgba(255, 255, 255, 0.8);
}

.el-form--inline .el-form-item {
  margin-right: 30px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table) {
  --el-table-border-color: #d3d3d3;
  --el-table-header-bg-color: #969696;
  --el-table-row-hover-bg-color: rgb(75, 75, 75);
}

:deep(.el-table__header th) {
  background-color: #fdfdfd !important;
  color: #525252;
}

:deep(.el-table__body tr:hover > td) {
  background-color: rgb(159, 234, 252) !important;
}

</style>
