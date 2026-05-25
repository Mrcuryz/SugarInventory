<template>
  <PalletTaskCenter
      title="半成品出库任务"
      description="仅处理半成品普通出库；生产订单领用请进入生产管理的半成品领用页。"
      :default-query="{ taskType: 'OUT', productStatus: '半成品' }"
      :search-fields="['code', 'status', 'productName', 'productionDate']"
      :columns="['bizScene']"
      :top-actions="[]"
      :row-actions="[]"
      :batch-actions="[]"
      :tab-action-map="{
        DIRECT_OUT: {
          topActions: ['semiOutCreate'],
          batchActions: ['semiOutConfirm', 'cancel'],
          labels: {
            semiOutCreate: '创建出库任务',
            semiOutConfirm: '确认',
            cancel: '取消'
          }
        }
      }"
      :biz-scene-tabs="[
        { label: '普通出库', value: 'DIRECT_OUT' }
      ]"
  />
</template>

<script setup>
import PalletTaskCenter from '@/components/PalletTaskCenter.vue'
</script>
