<template>
  <PalletTaskCenter
      title="半成品入库任务"
    description="仅展示半成品二维码入库任务"
      :default-query="{ taskType: 'SEMI_IN', productStatus: '半成品' }"
      :search-fields="['code', 'status', 'productName', 'productType', 'productionDate']"
      :columns="[]"
      :top-actions="['bindPallet']"
      :row-actions="[]"
      :batch-actions="['confirmIn', 'cancel']"
  />
</template>

<script setup>
import PalletTaskCenter from '@/components/PalletTaskCenter.vue'
</script>
