<template>
  <PalletTaskCenter
      title="半成品出库任务"
      description="按普通出库和转入备料池分组处理半成品出库任务"
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
        },
        PREPARE_CONSUMED: {
          topActions: ['semiPrepareCreate'],
          batchActions: ['semiPrepareConfirm', 'cancel'],
          labels: {
            semiPrepareCreate: '创建任务',
            semiPrepareConfirm: '确认',
            cancel: '取消'
          }
        }
      }"
      :biz-scene-tabs="[
        { label: '普通出库', value: 'DIRECT_OUT' },
        { label: '转入备料池', value: 'PREPARE_CONSUMED' }
      ]"
  />
</template>

<script setup>
import PalletTaskCenter from '@/components/PalletTaskCenter.vue'
</script>
