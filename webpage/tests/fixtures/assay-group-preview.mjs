// Display-only fixture. No authentication, network business API or database writes.
import { createApp, computed, ref } from 'vue/dist/vue.esm-bundler.js'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import AssayEntryPreviewCard from '../../src/components/agent/AssayEntryPreviewCard.vue'
import { previewAsCard } from '../../src/components/agent/assayEntryPresentation.mjs'

createApp({
  components: { AssayEntryPreviewCard },
  setup() {
    const mode = ref('preview')
    const card = computed(() => {
      const saved = mode.value === 'saved'
      return previewAsCard({ entryKind: 'GROUP_V1', groupName: '展示测试组', sampleDate: '2026-09-24', productCount: 2,
        saved, previewOnly: !saved, saveOutcomeUnknown: mode.value === 'uncertain', invalidated: mode.value === 'invalid',
        products: [
          { productName: '黄冰糖（袋）', proposedVersion: 1, recordVersion: 1, judgementLabel: '合格', appliedStandardName: '展示标准甲', appliedStandardVersion: 1 },
          { productName: '白冰糖（箱）', existingVersion: 3, proposedVersion: 4, recordVersion: 4, judgementLabel: '不合格', appliedStandardName: '展示标准乙', appliedStandardVersion: 2,
            failedMetrics: [{ name: '色值', reason: '检测值 10 超出上限 5' }] }
        ].map(row => ({ ...row, saved, sampleDate: '2026-09-24', metrics: [
          { name: '色值', value: 10, provided: true }, { name: '还原糖分', provided: false },
          { name: '干燥失重', provided: false }, { name: '电导灰分', provided: false },
          { name: '蔗糖分', provided: false }, { name: '不溶于水杂质', provided: false }, { name: 'pH值', value: 0, provided: true }
        ] }))
      })
    })
    return { mode, card }
  },
  template: `<main style="max-width:580px;margin:24px auto;padding:0 16px;font-family:Arial,sans-serif">
    <p style="font-size:12px;color:#64748b">仅展示测试，不连接业务接口</p>
    <nav style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap">
      <el-button @click="mode='preview'">未保存</el-button><el-button @click="mode='saved'">已保存</el-button>
      <el-button @click="mode='uncertain'">待核实</el-button><el-button @click="mode='invalid'">已失效</el-button>
    </nav><AssayEntryPreviewCard :card="card" /></main>`
}).use(ElementPlus).mount('#app')
