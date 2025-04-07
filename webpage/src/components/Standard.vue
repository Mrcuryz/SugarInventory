<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品类型">
          <el-select
              v-model="searchForm.productType"
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
        <el-form-item label="标准名称">
          <el-input
              v-model="searchForm.standardName"
              placeholder="请输入标准名称"
              clearable
              style="width: 150px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="dialogVisible = true;operationType='新增标准'">新增</el-button>
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
      >
        <el-table-column prop="standardName" label="标准名称" width="150" />
        <el-table-column prop="productType" label="产品类型" width="100" />
        <el-table-column label="颜色范围" width="200">
<!--          改为最小值<=x<=最大值的形式-->
          <template #default="{ row }">
            {{ row.colorMin }}
            <span v-if="row.colorMin !== null"> <= </span>
            <span v-if="row.colorMax !== null || row.colorMin !== null">x</span>
            <span v-if="row.colorMax === null && row.colorMin === null"> 无 </span>
            <span v-if="row.colorMax"> <= </span>
            {{ row.colorMax }}
          </template>
        </el-table-column>
        <el-table-column label="糖分范围" width="200">
          <template #default="{ row }">
            {{ row.reducingSugarMin }}
            <span v-if="row.reducingSugarMin !== null"> <= </span>
            <span v-if="row.reducingSugarMax !== null || row.reducingSugarMin !== null">x</span>
            <span v-if="row.reducingSugarMax === null && row.reducingSugarMin === null"> 无 </span>
            <span v-if="row.reducingSugarMax"> <= </span>
            {{ row.reducingSugarMax }}
          </template>
        </el-table-column>
        <el-table-column label="干重范围" width="200">
          <template #default="{ row }">
            {{ row.dryWeightMin }}
            <span v-if="row.dryWeightMin !== null"> <= </span>
            <span v-if="row.dryWeightMax !== null || row.dryWeightMin !== null">x</span>
            <span v-if="row.dryWeightMax === null && row.dryWeightMin === null"> 无 </span>
            <span v-if="row.dryWeightMax"> <= </span>
            {{ row.dryWeightMax }}
          </template>
        </el-table-column>
        <el-table-column label="亚硝酸盐含量范围" width="200">
          <template #default="{ row }">
            {{ row.conductivityAshMin }}
            <span v-if="row.conductivityAshMin !== null"> <= </span>
            <span v-if="row.conductivityAshMax !== null || row.conductivityAshMin !== null">x</span>
            <span v-if="row.conductivityAshMax === null && row.conductivityAshMin === null"> 无 </span>
            <span v-if="row.conductivityAshMax"> <= </span>
            {{ row.conductivityAshMax }}
          </template>
        </el-table-column>
        <el-table-column label="糖度范围" width="200">
          <template #default="{ row }">
            {{ row.sucroseMin }}
            <span v-if="row.sucroseMin !== null"> <= </span>
            <span v-if="row.sucroseMax !== null || row.sucroseMin !== null">x</span>
            <span v-if="row.sucroseMax === null && row.sucroseMin === null"> 无 </span>
            <span v-if="row.sucroseMax"> <= </span>
            {{ row.sucroseMax }}
          </template>
        </el-table-column>
        <el-table-column label="无水杂质含量范围" width="200">
          <template #default="{ row }">
            {{ row.insolubleImpurityMin }}
            <span v-if="row.insolubleImpurityMin !== null"> <= </span>
            <span v-if="row.insolubleImpurityMax !== null || row.insolubleImpurityMin !== null">x</span>
            <span v-if="row.insolubleImpurityMax === null && row.insolubleImpurityMin === null"> 无 </span>
            <span v-if="row.insolubleImpurityMax"> <= </span>
            {{ row.insolubleImpurityMax }}
          </template>
        </el-table-column>
        <el-table-column label="pH范围" width="200">
          <template #default="{ row }">
            {{ row.phMin }}
            <span v-if="row.phMin !== null"> <= </span>
            <span v-if="row.phMax !== null || row.phMin !== null">x</span>
            <span v-if="row.phMax === null && row.phMin === null"> 无 </span>
            <span v-if="row.phMax"> <= </span>
            {{ row.phMax }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="dialogVisible = true;operationType='修改标准';handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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
      <el-form :model="submitForm" :rules="rule" label-width="auto">
        <el-form-item label="标准名称" prop="standardName">
          <el-input v-model="submitForm.standardName" clearable />
        </el-form-item>
        <el-form-item label="产品类型" prop="productType">
          <el-select v-model="submitForm.productType" placeholder="请选择">
            <el-option
                v-for="item in productTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="颜色范围" prop="colorMin">
          <el-input-number v-model="submitForm.colorMin" controls-position="right" />
          <span> <= x <= </span>
          <el-input-number v-model="submitForm.colorMax" controls-position="right" />
        </el-form-item>
        <el-form-item label="糖分范围" prop="reducingSugarMin">
          <el-input-number v-model="submitForm.reducingSugarMin" controls-position="right" />
          <span> <= x <= </span>
          <el-input-number v-model="submitForm.reducingSugarMax" controls-position="right" />
        </el-form-item>
        <el-form-item label="干重范围" prop="dryWeightMin">
          <el-input-number v-model="submitForm.dryWeightMin" controls-position="right" />
          <span> <= x <= </span>
          <el-input-number v-model="submitForm.dryWeightMax" controls-position="right" />
        </el-form-item>
        <el-form-item label="亚硝酸盐含量范围" prop="conductivityAshMin">
          <el-input-number v-model="submitForm.conductivityAshMin" controls-position="right" />
          <span> <= x <= </span>
          <el-input-number v-model="submitForm.conductivityAshMax" controls-position="right" />
        </el-form-item>
        <el-form-item label="糖度范围" prop="sucroseMin">
          <el-input-number v-model="submitForm.sucroseMin" controls-position="right" />
          <span> <= x <= </span>
          <el-input-number v-model="submitForm.sucroseMax" controls-position="right" />
        </el-form-item>
        <el-form-item label="无水杂质含量范围" prop="insolubleImpurityMin">
          <el-input-number v-model="submitForm.insolubleImpurityMin" controls-position="right" />
          <span> <= x <= </span>
          <el-input-number v-model="submitForm.insolubleImpurityMax" controls-position="right" />
        </el-form-item>
        <el-form-item label="pH范围" prop="phMin">
          <el-input-number v-model="submitForm.phMin" controls-position="right" />
          <span> <= x <= </span>
          <el-input-number v-model="submitForm.phMax" controls-position="right" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="operationType === '新增标准' ? handleNew() : handleUpdate()">确定</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox} from 'element-plus'
import {addStandard, deleteStandard, getStandard, updateStandard} from "@/api/standard";
// 搜索表单
const searchForm = ref({
  productType: '',
  standardName: ''
})
// 产品类型选项
const productTypes = [
  { value: '白冰糖', label: '白冰糖' },
  { value: '黄冰糖', label: '黄冰糖' }
]
// 结果列表
const resultList = ref([])

// 加载状态
const loading = ref(false)

// 处理搜索
const handleSearch = async () => {
  let params = {}
  if(searchForm.value.productType){
    params.productType = searchForm.value.productType
  }
  if(searchForm.value.standardName){
    params.standardName = searchForm.value.standardName
  }
  loading.value = true
  let res = await getStandard(params)
  if (res.code === 200) {
    resultList.value = res.data
    loading.value = false
  } else {
    ElMessage.error(res.msg)
  }
}
// 处理重置
const handleReset = () => {
  searchForm.value = {
    meshName: ''
  }
  handleSearch()
}

const rule = {
  standardName: [
    { required: true, message: '请输入标准名称', trigger: 'blur' }
  ],
  productType: [
    { required: true, message: '请选择产品类型', trigger: 'blur' }
  ]
}
const dialogVisible = ref(false)
const handleClose = () => {
  submitForm.value = {
    productType: '',
    standardName: '',
  }
  dialogVisible.value = false
}
const operationType = ref('')
const submitForm = ref({
  productType: '',
  standardName: '',
})

// 新增
const handleNew = async () => {
  // 校验表单
  if (submitForm.value.standardName === '') {
    ElMessage.error('请输入标准名称')
    return
  }
  if (submitForm.value.productType === '') {
    ElMessage.error('请选择产品类型')
    return
  }
  //有值才加属性
  if (submitForm.value.colorMin) {
    submitForm.value.colorMin = parseFloat(submitForm.value.colorMin)
  }
  if (submitForm.value.colorMax) {
    submitForm.value.colorMax = parseFloat(submitForm.value.colorMax)
  }
  if (submitForm.value.reducingSugarMin) {
    submitForm.value.reducingSugarMin = parseFloat(submitForm.value.reducingSugarMin)
  }
  if (submitForm.value.reducingSugarMax) {
    submitForm.value.reducingSugarMax = parseFloat(submitForm.value.reducingSugarMax)
  }
  if (submitForm.value.dryWeightMin) {
    submitForm.value.dryWeightMin = parseFloat(submitForm.value.dryWeightMin)
  }
  if (submitForm.value.dryWeightMax) {
    submitForm.value.dryWeightMax = parseFloat(submitForm.value.dryWeightMax)
  }
  if (submitForm.value.conductivityAshMin) {
    submitForm.value.conductivityAshMin = parseFloat(submitForm.value.conductivityAshMin)
  }
  if (submitForm.value.conductivityAshMax) {
    submitForm.value.conductivityAshMax = parseFloat(submitForm.value.conductivityAshMax)
  }
  if (submitForm.value.sucroseMin) {
    submitForm.value.sucroseMin = parseFloat(submitForm.value.sucroseMin)
  }
  if (submitForm.value.sucroseMax) {
    submitForm.value.sucroseMax = parseFloat(submitForm.value.sucroseMax)
  }
  if (submitForm.value.insolubleImpurityMin) {
    submitForm.value.insolubleImpurityMin = parseFloat(submitForm.value.insolubleImpurityMin)
  }
  if (submitForm.value.insolubleImpurityMax) {
    submitForm.value.insolubleImpurityMax = parseFloat(submitForm.value.insolubleImpurityMax)
  }
  if (submitForm.value.phMin) {
    submitForm.value.phMin = parseFloat(submitForm.value.phMin)
  }
  if (submitForm.value.phMax) {
    submitForm.value.phMax = parseFloat(submitForm.value.phMax)
  }
  let res = await addStandard(submitForm.value)
  if (res.code === 200) {
    ElMessage.success('新增成功')
    await handleSearch()
    dialogVisible.value = false
    submitForm.value = {
      productType: '',
      standardName: '',
    }
  } else {
    ElMessage.error(res.msg)
  }
}

// 删除
const handleDelete = async (row) => {
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
        let res = await deleteStandard(row.id)
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

// 编辑库位
const handleEdit = (row) => {
  operationType.value = '修改标准'
  submitForm.value = {
    id: row.id,
    standardName: row.standardName,
    productType: row.productType,
    colorMin: row.colorMin,
    colorMax: row.colorMax,
    reducingSugarMin: row.reducingSugarMin,
    reducingSugarMax: row.reducingSugarMax,
    dryWeightMin: row.dryWeightMin,
    dryWeightMax: row.dryWeightMax,
    conductivityAshMin: row.conductivityAshMin,
    conductivityAshMax: row.conductivityAshMax,
    sucroseMin: row.sucroseMin,
    sucroseMax: row.sucroseMax,
    insolubleImpurityMin: row.insolubleImpurityMin,
    insolubleImpurityMax: row.insolubleImpurityMax,
    phMin: row.phMin,
    phMax: row.phMax
  }
  dialogVisible.value = true
}

const handleUpdate = async () => {
  // 校验表单
  if (submitForm.value.standardName === '') {
    ElMessage.error('请输入标准名称')
    return
  }
  if (submitForm.value.productType === '') {
    ElMessage.error('请选择产品类型')
    return
  }
  const params = {
    standardName: submitForm.value.standardName,
    productType: submitForm.value.productType
  }
  //有值才加属性
  if (submitForm.value.colorMin) {
    params.colorMin = parseFloat(submitForm.value.colorMin)
  }
  if (submitForm.value.colorMax) {
    params.colorMax = parseFloat(submitForm.value.colorMax)
  }
  if (submitForm.value.reducingSugarMin) {
    params.reducingSugarMin = parseFloat(submitForm.value.reducingSugarMin)
  }
  if (submitForm.value.reducingSugarMax) {
    params.reducingSugarMax = parseFloat(submitForm.value.reducingSugarMax)
  }
  if (submitForm.value.dryWeightMin) {
    params.dryWeightMin = parseFloat(submitForm.value.dryWeightMin)
  }
  if (submitForm.value.dryWeightMax) {
    params.dryWeightMax = parseFloat(submitForm.value.dryWeightMax)
  }
  if (submitForm.value.conductivityAshMin) {
    params.conductivityAshMin = parseFloat(submitForm.value.conductivityAshMin)
  }
  if (submitForm.value.conductivityAshMax) {
    params.conductivityAshMax = parseFloat(submitForm.value.conductivityAshMax)
  }
  if (submitForm.value.sucroseMin) {
    params.sucroseMin = parseFloat(submitForm.value.sucroseMin)
  }
  if (submitForm.value.sucroseMax) {
    params.sucroseMax = parseFloat(submitForm.value.sucroseMax)
  }
  if (submitForm.value.insolubleImpurityMin) {
    params.insolubleImpurityMin = parseFloat(submitForm.value.insolubleImpurityMin)
  }
  if (submitForm.value.insolubleImpurityMax) {
    params.insolubleImpurityMax = parseFloat(submitForm.value.insolubleImpurityMax)
  }
  if (submitForm.value.phMin) {
    params.phMin = parseFloat(submitForm.value.phMin)
  }
  if (submitForm.value.phMax) {
    params.phMax = parseFloat(submitForm.value.phMax)
  }
  let res = await updateStandard(submitForm.value.id, params)
  if (res.code === 200) {
    ElMessage.success('修改成功')
    await handleSearch()
    dialogVisible.value = false
    submitForm.value = {
      meshName: '',
      description: ''
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