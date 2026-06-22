<template>
  <PalletTaskCenter
      title="半成品入库任务"
      description="仅展示半成品二维码入库任务；生产订单领用请进入生产管理，半成品快速入库请使用智能报数处理。"
      :default-query="{ taskType: 'SEMI_IN', productStatus: '半成品' }"
      :search-fields="['code', 'status', 'productName', 'productType', 'productionDate']"
      :columns="[]"
      :top-actions="[]"
      :row-actions="[]"
      :batch-actions="['confirmIn', 'cancel']"
  />
</template>

<script setup>
import PalletTaskCenter from '@/components/PalletTaskCenter.vue'
</script>
