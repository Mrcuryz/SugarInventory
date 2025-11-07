<template>
  <div class="app-container">

    <div class="el-main">
      <div>
        <el-form width="30%">
          <el-form-item label="产品名称">
            <el-input v-model="productName" style="width: 200px"></el-input>
            <el-button type="primary" @click="handleClick" style="margin-left: 10px">查询</el-button>
          </el-form-item>
        </el-form>

      </div>
      <el-tabs v-model="activeName" @tab-click="handleClick">
        <el-tab-pane label="产品库存" name="成品">
          <el-table
              :data="tableData"
              style="width: 100%">
            <el-table-column
                prop="productName"
                label="产品名称"
                center
                width="300">
            </el-table-column>
            <el-table-column
                label="数量"
                width="180">
              <template #default="{ row }">
               <el-tag>{{ row.stockInfo }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
                prop="totalWeight"
                label="重量（kg）"
                width="180">
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="半成品库存" name="半成品">
          <el-table
              :data="tableData"
              style="width: 100%">
            <el-table-column
                prop="productName"
                label="产品名称"
                center
                width="300">
            </el-table-column>
            <el-table-column
                label="数量"
                width="180">
              <template #default="{ row }">
                <el-tag>{{ row.stockInfo }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
                prop="totalWeight"
                label="重量（kg）"
                width="180">
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>

</template>

<script setup>
import {onMounted, ref} from "vue";
import {getProductStock} from "@/api/warehouseinfo";

const activeName = ref('成品')
const tableData = ref([])
const productName = ref('')
const handleClick = async () => {
  let ret = await getProductStock({productStatus: activeName.value, productName: productName.value});
  tableData.value = ret.data
}

onMounted(() => {
  handleClick()
})
</script>
<style scoped>

</style>
