<template>
  <div class="acceptance-standard">
    <!-- 搜索区域 -->
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="标准名称">
          <el-input
              v-model="searchForm.standardName"
              placeholder="请输入批量化验组名称"
              clearable
              style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" style="max-width: 1200px">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <el-button type="primary" @click="openDialog('新增批量化验组')">新增</el-button>
        </div>
      </div>

      <!-- 批量化验组表格 -->
      <el-table
          :data="standardList"
          style="width: 100%"
          stripe
          border
          v-loading="loading"
      >
        <el-table-column prop="id" label="ID" width="80" align="center"/>
        <el-table-column prop="standardName" label="批量化验组名称" min-width="200">
        </el-table-column>
        <el-table-column label="关联产品" min-width="300" class-name="related-products-column">
          <template #default="{ row }">
            <div class="related-products-wrap">
              <el-tag
                  v-for="(product, idx) in row.relatedProductList"
                  :key="idx"
                  size="small"
                  type="primary"
                  class="related-product-tag"
              >
                {{ product.productName }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" align="center"/>
        <el-table-column prop="updatedAt" label="更新时间" width="180" align="center"/>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button
                type="primary"
                size="small"
                @click="openDialog('编辑批量化验组', row)"
            >
              编辑
            </el-button>
            <el-button
                type="danger"
                size="small"
                @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部分页 -->
      <div class="table-footer">
        <el-pagination
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            :current-page="pageInfo.currentPage"
            :page-sizes="[10, 20, 50, 100]"
            :page-size="pageInfo.pageSize"
            layout="total, ->, prev, pager, next, ->, sizes"
            :total="pageInfo.total"
            style="margin-top: 16px; text-align: right"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
        :title="dialogTitle"
        v-model="dialogVisible"
        width="50%"
        :before-close="handleDialogClose"
        destroy-on-close
    >
      <el-form
          :model="standardForm"
          :rules="formRules"
          ref="standardFormRef"
          label-width="140px"
          class="dialog-form"
      >
        <!-- 标准名称 -->
        <el-form-item label="批量化验组名称" prop="standardName" required>
          <el-input
              v-model="standardForm.standardName"
              placeholder="请输入批量化验组名称"
              clearable
              max-length="50"
              show-word-limit
          />
        </el-form-item>

        <el-form-item label="化验产品名称" prop="relatedProducts">
          <el-cascader
              v-model="standardForm.relatedProducts"
              :options="productOptions"
              :props="cascaderProps"
              placeholder="请选择化验产品名称"
              style="width: 100%"
              clearable
          />
        </el-form-item>
        <!--        &lt;!&ndash; 关联产品（多选） &ndash;&gt;-->
        <!--        <el-form-item label="关联产品" prop="relatedProducts" required>-->
        <!--          <el-select-->
        <!--              v-model="standardForm.relatedProducts"-->
        <!--              placeholder="请选择关联产品（可多选）"-->
        <!--              multiple-->
        <!--              collapse-tags-->
        <!--              style="width: 100%"-->
        <!--          >-->
        <!--            <el-option-->
        <!--                v-for="item in productList"-->
        <!--                :key="item.value"-->
        <!--                :label="item.label"-->
        <!--                :value="item"-->
        <!--                :disabled="item.disabled"-->
        <!--            />-->
        <!--          </el-select>-->
        <!--        </el-form-item>-->

        <!-- 备注信息 -->
        <el-form-item label="备注说明">
          <el-input
              v-model="standardForm.remark"
              placeholder="请输入备注信息（可选）"
              type="textarea"
              rows="3"
              max-length="200"
              show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button
              type="primary"
              @click="handleFormSubmit"
              :loading="submitLoading"
          >
            {{ dialogType === '新增' ? '确认新增' : '确认修改' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, reactive, onMounted, getCurrentInstance, computed} from 'vue'
import {ElMessage, ElMessageBox, ElLoading} from 'element-plus'
import {addAssayGroup, deleteAssayGroup, getAssayGroup, updateAssayGroup} from "@/api/assayGroup";
import * as XLSX from 'xlsx'
import {getAssay, getSemiProduct, getStProduct} from "@/api/assay";

// -------------------------- 接口请求（需根据实际项目替换）--------------------------
// 模拟接口：获取批量化验组列表
const getAcceptanceStandardList = async (params) => {
  loading.value = true
  return getAssayGroup(params)
}

// 模拟接口：新增批量化验组
const addAcceptanceStandard = async (data) => {
  return addAssayGroup(data)
}

// 模拟接口：编辑批量化验组
const editAcceptanceStandard = async (data) => {
  return updateAssayGroup(data.id, data)
}

// 模拟接口：删除批量化验组
const deleteAcceptanceStandard = async (id) => {
  return deleteAssayGroup(id)
}

// -------------------------- 页面状态管理 --------------------------
// 搜索表单
const searchForm = reactive({
  standardName: '',
  relatedProduct: '',
  status: ''
})

// 级联组件配置（保持不变）
const cascaderProps = reactive({
  emitPath: false,
  multiple: true,
  checkStrictly: false,
  label: 'label',
  value: 'value',
  children: 'children'
})

// 批量化验组列表
const standardList = ref([])

// 加载状态
const loading = ref(false)

// 分页信息
const pageInfo = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 表单引用
const standardFormRef = ref(null)

// 弹窗状态
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogType = ref('') // 新增/编辑

// 表单数据
const standardForm = reactive({
  id: '',
  standardName: '',
  relatedProducts: [], // 存储选中的产品对象（多选中）
  status: '启用',
  remark: ''
})

// 表单校验规则
const formRules = reactive({
  standardName: [
    {required: true, message: '请输入批量化验组名称', trigger: 'blur'},
    {max: 50, message: '标准名称长度不能超过50个字符', trigger: 'blur'}
  ],
  relatedProducts: [
    {required: true, message: '请至少选择1个关联产品', trigger: 'blur'},
    {
      validator: (rule, value, callback) => {
        if (value.length > 100) {
          callback(new Error('最多只能选择100个关联产品'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  status: [
    {required: true, message: '请选择标准状态', trigger: 'blur'}
  ]
})

// 提交加载状态
const submitLoading = ref(false)


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
// -------------------------- 方法定义 --------------------------
// 获取批量化验组列表
const getStandardList = async () => {
  try {
    loading.value = true
    // 构造请求参数
    const params = {
      ...searchForm,
      page: pageInfo.currentPage,
      size: pageInfo.pageSize
    }
    console.log('请求参数：', params)
    const res = await getAcceptanceStandardList(params)
    standardList.value = res.data.records
    pageInfo.total = res.data.total
    console.log('返回数据：', standardList.value)
    loading.value = false
  } catch (error) {
    ElMessage.error('获取数据异常：' + error.message)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pageInfo.currentPage = 1 // 重置为第一页
  getStandardList()
}

// 重置搜索
const handleReset = () => {
  searchForm.standardName = ''
  searchForm.relatedProduct = ''
  searchForm.status = ''
  pageInfo.currentPage = 1
  getStandardList()
}

// 分页大小改变
const handleSizeChange = (val) => {
  pageInfo.pageSize = val
  getStandardList()
}

// 当前页改变
const handleCurrentChange = (val) => {
  pageInfo.currentPage = val
  getStandardList()
}

// 打开弹窗
const openDialog = (title, row = null) => {
  dialogTitle.value = title
  dialogType.value = title.includes('新增') ? '新增' : '编辑'
  dialogVisible.value = true

  // 重置表单
  if (standardFormRef.value) {
    standardFormRef.value.resetFields()
  }

  // 编辑时回显数据
  if (row) {
    standardForm.id = row.id
    standardForm.standardName = row.standardName
    standardForm.relatedProducts = getProductIds(row.relatedProducts)// 深拷贝避免双向绑定问题
    standardForm.status = row.status
    standardForm.remark = row.remark || ''
  } else {
    // 新增时重置表单
    standardForm.id = ''
    standardForm.standardName = ''
    standardForm.relatedProducts = []
    standardForm.status = '启用'
    standardForm.remark = ''
  }
}

const getProductIds = (value) => {
  if (!value) return []
  if (Array.isArray(value)) return value.map(Number) // 如果已经是数组，转为数字
  if (typeof value === 'string') {
    return value.split(',').map(id => Number(id)).filter(id => !isNaN(id))
  }
  return []
}

// 关闭弹窗
const handleDialogClose = () => {
  dialogVisible.value = false
  if (standardFormRef.value) {
    standardFormRef.value.resetFields()
  }
}

// 表单提交
const handleFormSubmit = async () => {
  try {
    // 表单校验
    await standardFormRef.value.validate()
    submitLoading.value = true
    console.log('提交数据：', standardForm)
    // 构造提交数据（处理关联产品格式，根据后端需求调整）
    const submitData = {
      id: standardForm.id,
      standardName: standardForm.standardName
    }
    submitData.relatedProducts = standardForm.relatedProducts.join(",")
    submitData.status = standardForm.status
    submitData.remark = standardForm.remark
    let res;
    if (submitData.id) {
      res = await editAcceptanceStandard(submitData)
    } else {
      res = await addAcceptanceStandard(submitData)
    }
    if (res.code === 200) {
      ElMessage.success(res.msg || '操作成功')
      handleDialogClose()
      getStandardList()
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
    submitLoading.value = false
    handleDialogClose()
  } catch (error) {
    ElMessage.error('操作异常：' + error.message)
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  deleteAcceptanceStandard(row.id).then(res => {
    if (res.code === 200) {
      ElMessage.success(res.msg || '删除成功')
      getStandardList()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  }).catch(error => {
    ElMessage.error('删除异常：' + error.message)
  })
}


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
onMounted(() => {
  SemiProduct()
  StProduct()
  handleSearch()
})
</script>

<style scoped>
:deep(.related-products-column .cell) {
  white-space: normal;
  overflow: visible;
  text-overflow: initial;
}

.related-products-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  line-height: 1.6;
}

.related-product-tag {
  max-width: 100%;
  height: auto;
  min-height: 24px;
  white-space: normal;
  word-break: break-all;
  line-height: 1.4;
  padding: 3px 8px;
}
</style>

