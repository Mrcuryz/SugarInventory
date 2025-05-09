<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" clearable placeholder="请输入产品名称" />
        </el-form-item>
        <el-form-item label="仓库名称">
          <el-input v-model="searchForm.warehouseName" clearable  placeholder="请输入仓库名称" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="searchForm.operatorName" clearable  placeholder="请输入操作人" />
        </el-form-item>
        <br>
        <el-form-item label="查询类型">
          <el-select v-model="searchFormType"
                     placeholder="请选择"
                     clearable
                     style="width: 200px">
            <el-option label="入库" value="入库"></el-option>
            <el-option label="出库" value="出库"></el-option>
            <el-option label="半成品入库" value="半成品入库"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
              v-model="searchForm.dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 400px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button type="primary" @click="handleReset">重置</el-button>
          <el-button type="success" @click="exportExcel">导出Excel</el-button>
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
        <el-table-column prop="productName" label="产品名称" width="150"></el-table-column>
        <el-table-column prop="warehouseName" label="仓库名称" width="150"></el-table-column>
        <el-table-column prop="quantity" label="数量（件）" width="150" sortable></el-table-column>
        <el-table-column prop="totalWeight" label="重量（kg）" width="150" sortable></el-table-column>
        <el-table-column prop="entryDate" label="入库日期" width="150" v-if="searchFormType === '入库'" sortable></el-table-column>
        <el-table-column prop="inDate" label="入库日期" width="150" v-if="searchFormType === '出库'" sortable></el-table-column>
        <el-table-column prop="outDate" label="出库日期" width="150" v-if="searchFormType === '出库'" sortable></el-table-column>
        <el-table-column prop="operationDate" label="入库日期" width="150" v-if="searchFormType === '半成品入库'" sortable></el-table-column>
        <el-table-column prop="operator" label="操作人" width="150"></el-table-column>
        <el-table-column prop="meshName" label="筛网名称" width="150" v-if="searchFormType !== '出库'"></el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="dialogVisible = true;operationType='查看半成品';handleEdit(row)" v-if="searchFormType === '入库'">查看半成品</el-button>
            <el-button type="danger" size="small" @click="dialogVisible = true;operationType='查看化验记录';handleEdit(row)">查看化验记录</el-button>
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
        :title="operationType"
        v-model="dialogVisible"
        width="40%"
        :before-close="handleClose"
    >
      <!-- 卡片容器 -->
      <div v-if="semiProductRecords?.length && operationType === '查看半成品'" class="card-container">
        <el-card
            v-for="(item, index) in semiProductRecords "
            :key="index"
            class="record-card"
            shadow="hover"
        >
          <div class="card-content">
            <!-- 半成品名称 -->
            <div class="info-item">
              <span class="label">半成品名称：</span>
              <span class="value">{{ item.productName || '-' }}</span>
            </div>

            <!-- 数量 -->
            <div class="info-item">
              <span class="label">数量：</span>
              <span class="value">{{ item.quantity ? `${item.quantity} 件` : '-' }}</span>
            </div>

            <!-- 日期 -->
            <div class="info-item">
              <span class="label">日期：</span>
              <span class="value">{{ item.productionDate || '未填写日期' }}</span>
            </div>
          </div>
        </el-card>
      </div>
      <div v-if="operationType === '查看化验记录'" class="card-container">
        <el-card
            v-for="(item, index) in resultList"
            :key="index"
            class="record-card"
            shadow="hover"
        >
          <div class="card-content">
            <el-row :gutter="20" v-if="item.id === currentRow.id">
              <el-col :span="20">
                <div class="info-item">
                  <label>采样日期：</label>
                  <span>{{ item.sampleDate }}</span>
                </div>
                <div class="info-item">
                  <label>色值：</label>
                  <span>{{ item.colorValue }}</span>
                </div>
                <div class="info-item">
                  <label>还原糖：</label>
                  <span>{{ item.reducingSugar }}</span>
                </div>
                <div class="info-item">
                  <label>干重：</label>
                  <span>{{ item.dryWeight }}</span>
                </div>
                <div class="info-item">
                  <label>电导灰分：</label>
                  <span>{{ item.conductivityAsh }}</span>
                </div>
                <div class="info-item">
                  <label>蔗糖：</label>
                  <span>{{ item.sucrose }}</span>
                </div>
                <div class="info-item">
                  <label>不溶物：</label>
                  <span>{{ item.insolubleImpurity }}</span>
                </div>
                <div class="info-item">
                  <label>pH值：</label>
                  <span>{{ item.phValue }}</span>
                </div>
              </el-col>
            </el-row>

            <!-- 底部状态栏 -->
            <div class="status-bar" >
              <el-tag
                  :span="12"
                  :type="item.isQualified === '合格' ? 'success' : 'danger'"
                  size="medium"
              >
                {{ item.isQualified }}
              </el-tag>
              <div class="meta-info">
                <span>检测人：{{ item.testerName }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>
      <div v-if="!semiProductRecords?.length && operationType === '查看半成品'" class="empty-container">
        <el-empty description="暂无半成品记录" :image-size="100" />
      </div>
      <div v-if="!resultList.length" class="empty-container">
        <el-empty description="暂无数据" :image-size="100" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import {getMesh, updateMesh} from '@/api/mesh'
import { ElMessage, ElMessageBox} from 'element-plus'
import {addInStock, getInStock, getOutStock, getSemiProductList} from "@/api/stock";
import {getProductList} from "@/api/product";
import {getSemiProduct, getStProduct} from "@/api/assay";
import {Delete} from "@element-plus/icons-vue";
import dayjs from "dayjs";
import * as XLSX from 'xlsx';

const exportExcel = async () => {
  if (!searchFormType.value) {
    ElMessage.warning('请先选择查询类型')
    return
  }

  // 构建完整参数（包含分页）
  const params = {
    productName: searchForm.value.productName,
    warehouseName: searchForm.value.warehouseName,
    operatorName: searchForm.value.operatorName,
    startDate: searchForm.value.dateRange[0]
        ? dayjs(searchForm.value.dateRange[0]).format('YYYY-MM-DD')
        : '',
    endDate: searchForm.value.dateRange[1]
        ? dayjs(searchForm.value.dateRange[1]).format('YYYY-MM-DD')
        : '',
    page: 1,
    size: total.value // 获取全部数据
  }

  try {
    let res
    switch(searchFormType.value) {
      case '入库':
        res = await getInStock(params)
        break
      case '出库':
        res = await getOutStock(params)
        break
      case '半成品入库':
        res = await getSemiProductList(params)
        break
    }

    if (res.code !== 200) {
      ElMessage.error('导出失败：' + res.msg)
      return
    }

    // 数据处理
    const processData = (items) => {
      return items.map(item => {
        const baseData = {
          '产品名称': item.productName,
          '仓库名称': item.warehouseName,
          '数量（件）': item.quantity,
          '重量（kg）': item.totalWeight,
          '操作人': item.operator,
          '检测人': item.testerName,
          '是否合格': item.isQualified,
          '采样日期': item.sampleDate,
          '色值': item.colorValue,
          '还原糖': item.reducingSugar,
          '干重': item.dryWeight,
          '电导灰分': item.conductivityAsh,
          '蔗糖': item.sucrose,
          '不溶物': item.insolubleImpurity,
          'pH值': item.phValue
        }

        // 添加类型特定字段
        switch(searchFormType.value) {
          case '入库':
            baseData['入库日期'] = item.entryDate
            baseData['筛网名称'] = item.meshName
            baseData['半成品记录'] = JSON.stringify(item.semiProductRecords)
            break
          case '出库':
            baseData['入库日期'] = item.inDate
            baseData['出库日期'] = item.outDate
            break
          case '半成品入库':
            baseData['操作日期'] = item.operationDate
            break
        }

        return baseData
      })
    }

    // 创建Excel
    const ws = XLSX.utils.json_to_sheet(processData(res.data.records))
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '数据')

    // 生成文件名
    const filename = `${searchFormType.value}记录_${dayjs().format('YYYYMMDDHHmmss')}.xlsx`
    XLSX.writeFile(wb, filename)

    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败：' + error.message)
  }
}
// 搜索表单

const searchFormType = ref('')
const searchForm = ref({
  productName: '',
  warehouseName: '',
  operatorName: '',
  dateRange: []
})
// 列表
const resultList = ref([])
// 分页参数
const pageSize = ref(10)
const currentPage = ref(1)
const total = ref(0)
// 加载状态
const loading = ref(false)

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
    size: pageSize.value,
  }
  if (searchForm.value.productName !== '') {
    params.productName = searchForm.value.productName
  }
  if (searchForm.value.warehouseName !== '') {
    params.warehouseName = searchForm.value.warehouseName
  }
  if (searchForm.value.operatorName !== '') {
    params.operatorName = searchForm.value.operatorName
  }
  if (searchForm.value.dateRange.length !== 0) {
    params.startDate = dayjs(searchForm.value.dateRange[0]).format('YYYY-MM-DD')
    params.endDate = dayjs(searchForm.value.dateRange[1]).format('YYYY-MM-DD')
  }
  loading.value = true
  if (searchFormType.value === '入库') {
    let res = await getInStock(params)
    if (res.code === 200) {
      total.value = res.data.total
      resultList.value = res.data.records
      resultList.value.forEach(item => {
        item.semiProductRecords = JSON.parse(item.semiProductRecords)
      })
      console.log(resultList.value)
    } else {
      ElMessage.error(res.msg)
    }
  } else if (searchFormType.value === '出库') {
    let res = await getOutStock(params)
    if (res.code === 200) {
      total.value = res.data.total
      resultList.value = res.data.records
    } else {
      ElMessage.error(res.msg)
    }
  }else if (searchFormType.value === '半成品入库') {
    let res = await getSemiProductList(params)
    console.log(res)
    if (res.code === 200) {
      total.value = res.data.total
      resultList.value = res.data.records
    } else {
      ElMessage.error(res.msg)
    }
  }
  loading.value = false
}
// 处理重置
const handleReset = () => {
  searchForm.value = {
  }
  handleSearch()
}
const semiProductRecords = ref([])

const dialogVisible = ref(false)
const handleClose = () => {
  dialogVisible.value = false
  visible.value = false
}
const operationType = ref('')
const submitForm = ref({
  productId: '',
  warehouseName: '',
  quantity: '',
  side: '',
  screenMeshId: '',
  semiRecords: []
})
const currentRow = ref('')
const visible = ref(false)
const handleEdit = (row) => {
  semiProductRecords.value = row.semiProductRecords
  dialogVisible.value = true
  currentRow.value = row
}
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
const meshList = ref([])
const getMeshList = async () => {
  let res = await getMesh()
  if (res.code === 200) {
    meshList.value = res.data
  } else {
    ElMessage.error(res.msg)
  }
}
onMounted(() => {
  handleSearch()
  getProduct()
  SemiProduct()
  StProduct()
  getMeshList()
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