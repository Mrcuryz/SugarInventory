<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品名称">
          <el-input
              v-model="searchForm.name"
              placeholder="请输入产品名称"
              clearable
              style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="产品类型">
          <el-select
              v-model="searchForm.type"
              placeholder="请选择"
              clearable
              style="width: 200px"
          >
            <el-option
                v-for="item in productTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品状态">
          <el-select
              v-model="searchForm.status"
              placeholder="请选择"
              clearable
              style="width: 200px"
          >
           <el-option
               v-for="item in productStatus"
               :key="item.value"
               :label="item.label"
               :value="item.value"
           />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="dialogVisible = true;operationType='新增产品'">新增</el-button>
          <el-button type="warning" @click="exportExcel">导出Excel</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" style="max-width: 1200px">
      <el-table
          :data="productList"
          style="width: 95%"
          heigth="300"
          stripe
          border
          v-loading="loading"
      >
        <el-table-column prop="productName" label="产品名称" width="150" />
        <el-table-column prop="productType" label="产品类型" width="150" />
        <el-table-column prop="status" label="产品状态" width="150"/>
        <el-table-column prop="packagingMethod" label="打包方式" width="100" />
        <el-table-column prop="weightPerPiece" label="每件重量（kg）" width="150"/>
        <el-table-column prop="piecesPerPallet" label="每板件数" width="100"/>
        <el-table-column prop="canStack" label="是否可堆叠" width="100">
          <template #default="{ row }">
            <span v-if="row.canStack">是</span>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="auto">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="dialogVisible = true;operationType='修改产品';editProduct(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="deleteProduct(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
        :title=operationType
        v-model="dialogVisible"
        width="40%"
        :before-close="handleClose"
    >
      <el-form :model="productForm" :rules="rule" label-width="auto">
        <el-form-item label="产品名称" prop="productName" required>
          <el-input v-model="productForm.productName" clearable />
        </el-form-item>
        <el-form-item label="产品类型" prop="productType" required>
          <el-select v-model="productForm.productType" placeholder="请选择">
            <el-option
                v-for="item in productTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品状态" prop="status" required>
          <el-select v-model="productForm.status" placeholder="请选择">
            <el-option
                v-for="item in productStatus"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="打包方式" prop="packagingMethod">
          <el-input v-model="productForm.packagingMethod" clearable />
        </el-form-item>
        <el-form-item label="每件重量（kg）" prop="weightPerPiece" required>
          <el-input v-model="productForm.weightPerPiece" clearable />
        </el-form-item>
        <el-form-item label="每板件数" prop="piecesPerPallet" required>
          <el-input v-model="productForm.piecesPerPallet" clearable />
        </el-form-item>
        <el-form-item label="是否可堆叠" prop="canStack">
          <el-switch v-model="productForm.canStack" active-color="#13ce66" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
<!--        符合规则的情况下才可以点击确定按钮 -->
        <el-button type="primary" @click="operationType === '新增产品' ? newProduct() : updateProduct()">确定</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </div>
    </el-dialog>
    </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getProductList, addProduct, removeProduct, changeProduct} from '@/api/product'
import { ElMessage, ElMessageBox} from 'element-plus'
// 在已有导入基础上添加XLSX
import * as XLSX from 'xlsx';
import {useI18n} from "vue-i18n";

// 添加导出方法
const exportExcel = async () => {
  try {
    loading.value = true;
    // 使用当前搜索条件获取所有数据
    let params = {}
    if (searchForm.value.name) {
      params.name = searchForm.value.name
    }
    if (searchForm.value.type) {
      params.type = searchForm.value.type
    }
    if (searchForm.value.status) {
      params.status = searchForm.value.status
    }
    let res = await getProductList(params)
    console.log(res)
    if (res.code === 200) {
      // 处理数据格式
      const excelData = res.data.map(product => ({
        '产品名称': product.productName,
        '产品类型': product.productType,
        '产品状态': productStatus.find(item => item.value === product.status)?.label || product.status,
        '打包方式': product.packagingMethod,
        '每件重量（kg）': product.weightPerPiece,
        '每板件数': product.piecesPerPallet,
        '是否可堆叠': product.canStack ? '是' : '否'
      }));
      // 创建工作表
      const worksheet = XLSX.utils.json_to_sheet(excelData);
      const workbook = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(workbook, worksheet, '产品列表');

      // 生成文件名
      const filename = `产品列表_${new Date().toISOString().split('T')[0]}.xlsx`;

      // 保存文件
      XLSX.writeFile(workbook, filename);
      ElMessage.success('导出成功');
    }
  } catch (error) {
    ElMessage.error(`导出失败: ${error.message}`);
  } finally {
    loading.value = false;
  }
};
// 搜索表单
const searchForm = ref({
  name: '',
  type: '',
  status: ''
})
// 产品列表
const productList = ref([])
// 产品类型选项
const productTypes = [
   { value: '白冰糖', label: '白冰糖' },
   { value: '黄冰糖', label: '黄冰糖' }
]
// 产品状态选项
const productStatus = [
    { value: '半成品', label: '半成品' },
    { value: '成品', label: '成品' }
]
// 加载状态
const loading = ref(false)
// 处理搜索
const handleSearch = async () => {
  let params = {}
  if (searchForm.value.name) {
    params.name = searchForm.value.name
  }
  if (searchForm.value.type) {
    params.type = searchForm.value.type
  }
  if (searchForm.value.status) {
    params.status = searchForm.value.status
  }
  loading.value = true
  let res = await getProductList(params)
  if (res.code === 200) {
    productList.value = res.data
    loading.value = false
  } else {
    ElMessage.error(res.msg)
  }
}
// 处理重置
const handleReset = () => {
  searchForm.value = {
    name: '',
    type: '',
    status: ''
  }
  handleSearch()
}

const rule = {
  productName: [
    { required: true, message: '请输入产品名称', trigger: 'blur' }
  ],
  productType: [
    { required: true, message: '请选择产品类型', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择产品状态', trigger: 'blur' }
  ],
  weightPerPiece: [
    { required: true, message: '请输入每件重量', trigger: 'blur' },
    { type: 'string', message: '请输入数字', trigger: 'blur' ,pattern: /^-?\d+(\.\d+)?$/}
  ],
  piecesPerPallet: [
    { required: true, message: '请输入每板件数', trigger: 'blur' },
    { type: 'string', message: '请输入整数', trigger: 'blur' ,pattern: /^-?\d+(\.\d+)?$/}
  ]
}
const dialogVisible = ref(false)
const handleClose = () => {
  productForm.value = {
    productName: '',
    productType: '',
    status: '',
    packagingMethod: '',
    weightPerPiece: '',
    piecesPerPallet: '',
    canStack: false
  }
  dialogVisible.value = false
}
const operationType = ref('')
const productForm = ref({
  productName: '',
  productType: '',
  status: '',
  packagingMethod: '',
  weightPerPiece: '',
  piecesPerPallet: '',
  canStack: false
})
// 新增产品
const newProduct = async () => {
  // 校验表单
  if (productForm.value.productName === '') {
    ElMessage.error('请输入产品名称')
    return
  }
  if (productForm.value.productType === '') {
    ElMessage.error('请选择产品类型')
    return
  }
  if (productForm.value.status === '') {
    ElMessage.error('请选择产品状态')
    return
  }
  if (productForm.value.weightPerPiece === '') {
    ElMessage.error('请输入每件重量')
    return
  }
  if (productForm.value.piecesPerPallet === '') {
    ElMessage.error('请输入每板件数')
    return
  }
  let res = await addProduct(productForm.value)
  if (res.code === 200) {
    ElMessage.success('新增成功')
    await handleSearch()
    dialogVisible.value = false
    productForm.value = {
      productName: '',
      productType: '',
      status: '',
      packagingMethod: '',
      weightPerPiece: '',
      piecesPerPallet: '',
      canStack: false
    }
  } else {
    ElMessage.error(res.msg)
  }
}

// 删除产品
const deleteProduct = async (row) => {
  await ElMessageBox.confirm(
      '你确认要删除这条信息吗?',
      '温馨提示',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning',
      }
  )
      .then(async () => {
        //调用接口
        let res = await removeProduct(row.id)
        if (res.code === 200) {
          ElMessage({
            type: 'success',
            message: '删除成功',
          })
          await handleSearch()
        } else {
          ElMessage.error(res.msg)
        }
      })
      .catch(() => {
        ElMessage({
          type: 'info',
          message: '用户取消了删除',
        })
      })
}

// 编辑产品
const editProduct = (row) => {
  operationType.value = '修改产品'
  productForm.value = {
    productId: row.id,
    productName: row.productName,
    productType: row.productType,
    status: row.status,
    packagingMethod: row.packagingMethod,
    weightPerPiece: row.weightPerPiece,
    piecesPerPallet: row.piecesPerPallet,
    canStack: row.canStack
  }
  dialogVisible.value = true
}
const updateProduct = async () => {
  // 校验表单
  if (productForm.value.productName === '') {
    ElMessage.error('请输入产品名称')
    return
  }
  if (productForm.value.productType === '') {
    ElMessage.error('请选择产品类型')
    return
  }
  if (productForm.value.status === '') {
    ElMessage.error('请选择产品状态')
    return
  }
  if (productForm.value.weightPerPiece === '') {
    ElMessage.error('请输入每件重量')
    return
  }
  if (productForm.value.piecesPerPallet === '') {
    ElMessage.error('请输入每板件数')
    return
  }
  let res = await changeProduct(productForm.value)
  if (res.code === 200) {
    ElMessage.success('修改成功')
    await handleSearch()
    dialogVisible.value = false
    productForm.value = {
      productName: '',
      productType: '',
      status: '',
      packagingMethod: '',
      weightPerPiece: '',
      piecesPerPallet: '',
      canStack: false
    }
  } else {
    ElMessage.error(res.msg)
  }
}

onMounted(() => {
  handleSearch()
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