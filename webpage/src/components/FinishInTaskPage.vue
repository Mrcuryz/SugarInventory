<template>
  <PalletTaskCenter
      title="成品入库任务"
      description="仅展示成品托盘入库任务，可维护半成品来源明细"
      :default-query="{ taskType: 'FINISH_IN', productStatus: '成品' }"
      :search-fields="['code', 'status', 'productName', 'productType', 'productionDate']"
      :columns="['semiItemCount', 'hasSemiItems']"
      :top-actions="['bindPallet']"
      :row-actions="['bindSemi']"
      :batch-actions="['confirmIn', 'cancel']"
  />
</template>

<script setup>
import PalletTaskCenter from '@/components/PalletTaskCenter.vue'
</script>
