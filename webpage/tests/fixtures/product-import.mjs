// Browser presentation fixture. Use intercepted API responses; never point this fixture at business data.
import { createApp, provide, ref } from 'vue/dist/vue.esm-bundler.js'
import { reviewBookKey } from '../../src/components/agent/productImportSelection.mjs'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import ProductImportDialog from '../../src/components/agent/ProductImportDialog.vue'
import ProductImportReviewCard from '../../src/components/agent/ProductImportReviewCard.vue'

createApp({
  components: { ProductImportDialog, ProductImportReviewCard },
  setup() {
    const visible = ref(false), available = ref(true), count = ref(0)
    const card = { fields: [{ statusLabel: '需要核对', allowCreate: true,
      sourceValues: { productName: '袋装黄冰糖', weightPerPiece: 25 },
      options: [
        { ref: 'fixture-a', values: { productName: '黄冰糖（袋）', weightPerPiece: 25 }, label: '可修改',
          changes: [{ field: 'canStack', before: true, after: false }] },
        { ref: 'fixture-b', values: { productName: '黄冰糖（小袋）', weightPerPiece: 15 }, blocked: true,
          label: '已有库存，不能更改规格', changes: [{ field: 'weightPerPiece', before: 15, after: 25 }] }
      ] }] }
    const summaryDemo = new URLSearchParams(location.search).has('summary')
    if (summaryDemo) {
      const entries = ref([5, 9].map(rowNumber => ({ rowNumber, choice: 0, included: true, sessionId: 'product-import-ui-fixture',
        card: structuredClone(card), reference: 'aa000000-0000-4000-8000-000000000001', expiresAt: Date.now() + 1800000 })))
      provide(reviewBookKey, { entries, forCard: c => entries.value.find(e => e.card === c),
        async reviewBatchRow(item) {
          await new Promise(resolve => setTimeout(resolve, 700))
          if (entries.value.some(e => e.rowNumber === item.rowNumber)) throw new Error('不应重复核对')
          entries.value.push({ ...item, choice: null, included: false, ack: false, card: structuredClone(card) })
          return true
        }
      })
    }
    return { visible, available, count, card, summaryDemo }
  },
  template: `<main style="max-width:860px;margin:40px auto;font-family:Arial,sans-serif;padding:16px">
    <h1>产品表格界面验收</h1><p>仅测试界面与模拟响应，不连接数据库或执行产品保存。</p>
    <el-button type="primary" @click="visible=true">打开产品表格</el-button>
    <el-button @click="available=false">模拟会话失效</el-button><span> 表格 {{count}} 份</span>
    <h2>第5行 · 袋装黄冰糖</h2><ProductImportReviewCard :card="card" />
    <ProductImportDialog v-model="visible" agent-session-id="product-import-ui-fixture" :available="available" :matching-enabled="summaryDemo"
      @attachments-changed="items => count=items.length" />
  </main>`
}).use(createPinia()).use(ElementPlus).mount('#app')
