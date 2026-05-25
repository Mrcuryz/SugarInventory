<template>
  <PalletTaskCenter
      title="成品入库任务"
      description="仅展示成品二维码入库任务；成品与半成品追溯通过生产订单关联。"
      :default-query="{ taskType: 'FINISH_IN', productStatus: '成品' }"
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
