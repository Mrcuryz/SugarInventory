<template>
  <PalletTaskCenter
      title="成品出库任务"
      description="仅展示成品托盘出库任务"
      :default-query="{ taskType: 'OUT', productStatus: '成品', bizScene: 'FINISH_OUT' }"
      :search-fields="['code', 'status', 'productName', 'productionDate']"
      :columns="['bizScene']"
      :top-actions="['finishOutCreate']"
      :row-actions="[]"
      :batch-actions="['finishOutConfirm', 'cancel']"
  />
</template>

<script setup>
import PalletTaskCenter from '@/components/PalletTaskCenter.vue'
</script>
