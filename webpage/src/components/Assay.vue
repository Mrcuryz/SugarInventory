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
          heigth="300"
          stripe
          border
          v-loading="loading"
          row-key="id"
      >
        <el-table-column prop="productName" label="化验产品名称" width="120" />
        <el-table-column prop="status" label="产品状态" width="100">
          <template #default="{ row }">
            <span
              :style="{
                color:
                  row.status === '半成品'
                    ? '#e60000'
                    : row.status === '成品'
                    ? 'green'
                    : '#000'
              }"
            >
              {{ row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="sampleDate" label="采样日期" width="120" sortable/>
        <el-table-column prop="colorValue" label="色值" width="120" sortable/>
        <el-table-column prop="reducingSugar" label="去糖度" width="120" sortable/>
        <el-table-column prop="dryWeight" label="干重" width="120" sortable/>
        <el-table-column prop="conductivityAsh" label="电导率" width="120" sortable/>
        <el-table-column prop="sucrose" label="糖度" width="120" sortable/>
        <el-table-column prop="insolubleImpurity" label="杂质" width="120" sortable/>
        <el-table-column prop="phValue" label="pH值" width="120" sortable/>
        <el-table-column prop="testerName" label="化验员名称" width="120" />
        <el-table-column prop="version" label="化验版本数" width="120" />
        <el-table-column prop="isQualified" label="合格/不合格" width="120" >
          <template #default="{ row }">
            <el-tag type="success" v-if="row.isQualified === '合格'">合格</el-tag>
            <el-tag type="danger" v-else>不合格</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qualifiedStandards" label="合格标准" width="200" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="dialogVisible = true;operationType='修改化验';handleEdit(row)">编辑</el-button>
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
        <el-form-item label="化验产品名称" prop="productId" v-if="operationType === '新增化验'">
          <el-cascader
              v-model="submitForm.productId"
              :options="productOptions"
              :props="cascaderProps"
              placeholder="请选择化验产品名称"
              style="width: 100%"
              clearable
          />
        </el-form-item>
        <el-form-item label="采样日期" prop="sampleDate">
          <el-date-picker v-model="submitForm.sampleDate" type="date" placeholder="请选择采样日期" style="width: 100%" ></el-date-picker>
        </el-form-item>
        <el-form-item label="色值" prop="colorValue">
          <el-input v-model="submitForm.colorValue" placeholder="请输入色值" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="去糖度" prop="reducingSugar">
          <el-input v-model="submitForm.reducingSugar" placeholder="请输入去糖度" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="干重" prop="dryWeight">
          <el-input v-model="submitForm.dryWeight" placeholder="请输入干重" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="电导率" prop="conductivityAsh">
          <el-input v-model="submitForm.conductivityAsh" placeholder="请输入电导率" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="糖度" prop="sucrose">
          <el-input v-model="submitForm.sucrose" placeholder="请输入糖度" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="杂质" prop="insolubleImpurity">
          <el-input v-model="submitForm.insolubleImpurity" placeholder="请输入杂质" style="width: 100%"></el-input>
        </el-form-item>
        <el-form-item label="pH值" prop="phValue">
          <el-input v-model="submitForm.phValue" placeholder="请输入pH值" style="width: 100%"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="operationType === '新增化验' ? handleNew() : handleUpdate()">确定</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import dayjs from 'dayjs'
import {ref, onMounted, reactive, computed} from 'vue'
import { ElMessage, ElMessageBox} from 'element-plus'
import {addAssay, getAssay, getSemiProduct, getStProduct, updateAssay} from "@/api/assay";
import {getProductList} from "@/api/product";
// 搜索表单
const searchForm = ref({
  productName: '',
  dateRange: [],
  testerName: '',
  version: ''
})
// 列表
const resultList = ref([])

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
    resultList.value = res.data.records
    loading.value = false
  } else {
    ElMessage.error(res.msg)
  }
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
      { required: true, message: '请选择化验产品名称', trigger: 'blur' }
    ],
    sampleDate: [
      { required: true, message: '请选择采样日期', trigger: 'blur' }
    ],
    colorValue: [
      { type: 'float', message: '色值必须为数字', trigger: 'blur' }
    ],
    reducingSugar: [
      { type: 'float', message: '去糖度必须为数字', trigger: 'blur' }
    ],
    dryWeight: [
      { type: 'float', message: '干重必须为数字', trigger: 'blur' }
    ],
    conductivityAsh: [
      { type: 'float', message: '电导率必须为数字', trigger: 'blur' }
    ],
    sucrose: [
      { type: 'float', message: '糖度必须为数字', trigger: 'blur' }
    ],
    insolubleImpurity: [
      { type: 'float', message: '杂质必须为数字', trigger: 'blur' }
    ],
    phValue: [
      { type: 'float', message: 'pH值必须为数字', trigger: 'blur' }
    ]
}
const dialogVisible = ref(false)
const handleClose = () => {
  submitForm.value = {
  }
  dialogVisible.value = false
}
const operationType = ref('')
const submitForm = ref({
  productId: undefined,
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
    ...StProductList.value.map(p => ({ ...p, category: '成品' })),
    ...semiProductList.value.map(p => ({ ...p, category: '半成品' }))
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
  background-color: #fdfdfd;
  color: #525252;
}

:deep(.el-table__body tr:hover > td) {
  background-color: rgb(159, 234, 252) !important;
}
</style>