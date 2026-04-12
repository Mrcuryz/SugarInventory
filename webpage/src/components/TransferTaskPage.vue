<template>
  <PalletTaskCenter
      title="调拨任务"
      description="仅展示托盘调拨任务"
      :default-query="{ taskType: 'TRANSFER' }"
      :search-fields="['code', 'status', 'targetWarehouseName', 'productName', 'productionDate']"
      :columns="['taskType', 'targetWarehouse', 'productStatus']"
      :top-actions="['transferCreate']"
      :row-actions="[]"
      :batch-actions="['transferConfirm', 'cancel']"
  />
</template>

<script setup>
import PalletTaskCenter from '@/components/PalletTaskCenter.vue'
</script>
