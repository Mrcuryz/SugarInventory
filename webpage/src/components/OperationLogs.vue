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
              style="width: 400px"
          />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select
              v-model="searchForm.operationType"
              placeholder="请选择"
              clearable
              style="width: 200px"
          >
            <el-option
                v-for="item in operationTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>
        <br>
        <el-form-item label="操作业务">
          <el-input
              v-model="searchForm.tableName"
              placeholder="请输入操作内容"
              clearable
              style="width: 420px"
          />
        </el-form-item>

        <el-form-item label="操作人员">
          <el-input
              v-model="searchForm.operator"
              placeholder="请输入操作人姓名"
              clearable
              style="width: 300px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" style="max-width: 1200px">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <el-button @click="exportExcel">导出Excel</el-button>
        </div>
      </div>
      <el-table
          :data="filteredLogs"
          style="width: 95%"
          heigth="300"
          stripe
          border
          v-loading="loading"
      >
        <el-table-column prop="operationTime" label="时间" width="180" sortable/>
        <el-table-column prop="tableName" label="操作业务" width="120"/>
        <el-table-column prop="operationType" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTagType(row.operationType)" style="width: 70px">
              <div v-if="row.operationType === 'INSERT'">新增</div>
              <div v-if="row.operationType === 'UPDATE'">修改</div>
              <div v-if="row.operationType === 'DELETE'">删除</div>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="120"/>
        <el-table-column prop="changedFields" label="操作内容" min-width="auto">
          <template #default="{ row }">
            <div v-if="row.changedFields && Object.keys(row.changedFields).length">
              <div v-for="(value, key) in row.changedFields" :key="key">
                <div v-if="value === 'ADMIN'">{{ $t(`fields.${key}`) }}: {{ '管理员' }}</div>
                <div v-else-if="value === 'QC'">{{ $t(`fields.${key}`) }}: {{ '化验员' }}</div>
                <div v-else-if="value === 'STAFF'">{{ $t(`fields.${key}`) }}: {{ '员工' }}</div>
                <div v-else-if="value === true">{{ $t(`fields.${key}`) }}: {{ '是' }}</div>
                <div v-else-if="value === false">{{ $t(`fields.${key}`) }}: {{ '否' }}</div>
                <div v-else>
                  {{ $t(`fields.${key}`) }}: {{ value }}
                </div>
              </div>
            </div>
            <span v-else>无</span>
          </template>
        </el-table-column>
        <el-table-column prop="oldData" label="原数据" width="auto">
          <template #default="{ row }">
            <div v-if="row.oldData && Object.keys(row.oldData).length">
              <div v-for="(value, key) in row.oldData" :key="key">
                <div v-if="value === 'ADMIN'">{{ $t(`fields.${key}`) }}: {{ '管理员' }}</div>
                <div v-else-if="value === 'QC'">{{ $t(`fields.${key}`) }}: {{ '化验员' }}</div>
                <div v-else-if="value === 'STAFF'">{{ $t(`fields.${key}`) }}: {{ '员工' }}</div>
                <div v-else-if="value === true">{{ $t(`fields.${key}`) }}: {{ '是' }}</div>
                <div v-else-if="value === false">{{ $t(`fields.${key}`) }}: {{ '否' }}</div>
                <div v-else>
                  {{ $t(`fields.${key}`) }}: {{ value }}
                </div>
              </div>
            </div>
            <span v-else>无</span>
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
  </div>
</template>

<script setup>
import {ref, onMounted} from 'vue'
import {getOperationLogs} from '@/api/operationLogs'
import {ElMessage} from "element-plus"
import * as XLSX from 'xlsx'
import {useI18n} from "vue-i18n";

const {t} = useI18n(); // 确保已经导入useI18n

const exportExcel = async () => {
  try {
    loading.value = true;
    // 获取所有符合条件的数据
    const params = {
      page: 1,
      size: total.value
    }
    if (searchForm.value.tableName) {
      params.tableName = searchForm.value.tableName
    }
    if (searchForm.value.operationType) {
      params.operationType = searchForm.value.operationType
    }
    if (searchForm.value.operator) {
      params.operator = searchForm.value.operator
    }
    if (searchForm.value.dateRange !== []) {
      params.startTime = searchForm.value.dateRange[0]
      params.endTime = searchForm.value.dateRange[1]
    }
    const res = await getOperationLogs(params);
    if (res.code === 200) {
      const data = res.data.records.map(item => {
        // 处理数据格式
        const processed = {
          ...item,
          changedFields: JSON.parse(JSON.parse(item.changedFields)),
          oldData: JSON.parse(JSON.parse(item.oldData)),
          operationTime: item.operationTime.replace('T', ' ').replace('Z', ' ')
        };

        delete processed.changedFields.id;
        delete processed.oldData.id;

        // if (processed.changedFields.sampleDate) {
        //   processed.changedFields.sampleDate =
        //       `${processed.changedFields.sampleDate[0]}年${
        //           processed.changedFields.sampleDate[1]}月${
        //           processed.changedFields.sampleDate[2]}日`;
        // }

        return processed;
      });

      // 生成Excel数据
      const excelData = data.map(log => ({
        时间: log.operationTime,
        操作业务: log.tableName,
        操作类型: {INSERT: '新增', UPDATE: '修改', DELETE: '删除'}[log.operationType],
        操作人: log.operator,
        操作内容: formatFields(log.changedFields),
        原数据: formatFields(log.oldData)
      }));

      // 创建工作表并导出
      const worksheet = XLSX.utils.json_to_sheet(excelData);
      const workbook = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(workbook, worksheet, '操作日志');
      XLSX.writeFile(workbook, `操作日志_${new Date().toISOString().slice(0, 10)}.xlsx`);
      ElMessage.success('导出成功');
    }
  } catch (error) {
    ElMessage.error('导出失败: ' + error.message);
  } finally {
    loading.value = false;
  }
};

// 格式化字段显示
const formatFields = (fields) => {
  if (!fields || Object.keys(fields).length === 0) return '无';

  return Object.entries(fields)
      .map(([key, value]) => {
        let displayValue = value;
        switch (value) {
          case 'ADMIN':
            displayValue = '管理员';
            break;
          case 'QC':
            displayValue = '化验员';
            break;
          case 'STAFF':
            displayValue = '员工';
            break;
          case true:
            displayValue = '是';
            break;
          case false:
            displayValue = '否';
            break;
        }
        return `${t(`fields.${key}`)}: ${displayValue}`;
      })
      .join('\n');
};

// 搜索表单
const searchForm = ref({
  tableName: '',
  operationType: '',
  operator: '',
  dateRange: []
})
// 过滤后的日志列表
const filteredLogs = ref([])

// 分页参数
const pageSize = ref(10)
const currentPage = ref(1)
const total = ref(0)

// 操作类型选项
const operationTypes = [
  {value: 'INSERT', label: '新增'},
  {value: 'UPDATE', label: '修改'},
  {value: 'DELETE', label: '删除'},
]

// 加载状态
const loading = ref(false)

// 标签类型映射
const getTagType = (type) => {
  const typeMap = {
    'INSERT': 'success',
    'UPDATE': 'warning',
    'DELETE': 'danger'
  }
  return typeMap[type] || ''
}

// 处理搜索
const handleSearch = async () => {
  let params = {
    page: currentPage.value,
    size: pageSize.value
  }
  if (searchForm.value.tableName) {
    params.tableName = searchForm.value.tableName
  }
  if (searchForm.value.operationType) {
    params.operationType = searchForm.value.operationType
  }
  if (searchForm.value.operator) {
    params.operator = searchForm.value.operator
  }
  if (searchForm.value.dateRange !== []) {
    params.startTime = searchForm.value.dateRange[0]
    params.endTime = searchForm.value.dateRange[1]
  }
  loading.value = true
  let res = await getOperationLogs(params)
  if (res.code === 200) {
    total.value = res.data.total
    filteredLogs.value = res.data.records
    filteredLogs.value.forEach(item => {
      item.changedFields = JSON.parse(JSON.parse(item.changedFields))
      item.oldData = JSON.parse(JSON.parse(item.oldData))
    })
    // 过滤掉id字段
    filteredLogs.value.forEach(item => {
      delete item.changedFields.id
      delete item.oldData.id
      //处理时间格式，原格式[2025,3,8]改为'2025-03-08'
      // if(item.changedFields.sampleDate){
      //   item.changedFields.sampleDate = `${item.changedFields.sampleDate[0]}年${item.changedFields.sampleDate[1]}月${item.changedFields.sampleDate[2]}日`
      // }
    })
    //处理操作日期格式，员格式2025-03-08T00:00:00 改为 2025年03月08日00:00:00
    filteredLogs.value.forEach(item => {
      if (item.operationTime) {
        item.operationTime = item.operationTime.replace('T', ' ').replace('Z', ' ')
      }
    })
    filteredLogs.value.forEach(item => {
      if (item.changedFields.qualifiedStandards) {
        item.changedFields.qualifiedStandards = JSON.parse(item.changedFields.qualifiedStandards)
      }
    })
    loading.value = false
  } else {
    ElMessage.error(res.msg)
  }
}

// 处理重置
const handleReset = () => {
  searchForm.value = {
    dateRange: [],
    operationType: '',
    keyword: ''
  }
  handleSearch()
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  handleSearch()
}
const handleCurrentChange = (page) => {
  currentPage.value = page
  handleSearch()
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
  background: var(--app-panel);
}

.table-card {
  background: var(--app-panel);
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
  --el-table-border-color: var(--app-border-soft);
  --el-table-header-bg-color: #f7f8fb;
  --el-table-row-hover-bg-color: var(--app-hover);
}

:deep(.el-table__header th) {
  background-color: #f7f8fb;
  color: var(--app-text-secondary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--app-hover) !important;
}
</style>
