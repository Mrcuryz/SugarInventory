<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowDown, CollectionTag, Search } from '@element-plus/icons-vue'
import { buildProductCascaderOptions, productCascaderProps } from '@/utils/productCascader'
import { filterProductsByText } from '@/utils/productSelection'

const props = defineProps({
  modelValue: {
    type: [Number, String],
    default: null
  },
  products: {
    type: Array,
    default: () => []
  },
  allProducts: {
    type: Array,
    default: () => []
  },
  disabled: Boolean,
  invalid: Boolean,
  canCreate: Boolean,
  placeholder: {
    type: String,
    default: '请选择产品'
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'create'])

const visible = ref(false)
const mode = ref('search')
const query = ref('')
const searchInput = ref(null)

const normalizedModelValue = computed(() => String(props.modelValue ?? ''))
const selectedProduct = computed(() => {
  return props.allProducts.find(item => String(item.id ?? item.productId) === normalizedModelValue.value)
    || props.products.find(item => String(item.id ?? item.productId) === normalizedModelValue.value)
})
const displayText = computed(() => selectedProduct.value?.productName || props.placeholder)
const trimmedQuery = computed(() => query.value.trim())
const matchedProducts = computed(() => filterProductsByText(props.products, query.value))
const allMatchedProducts = computed(() => filterProductsByText(props.allProducts, query.value))
const hasIneligibleMatch = computed(() => {
  if (!trimmedQuery.value || matchedProducts.value.length) return false
  return allMatchedProducts.value.length > 0
})
const cascaderOptions = computed(() => buildProductCascaderOptions(props.products))

watch(visible, async opened => {
  if (!opened || mode.value !== 'search') return
  await nextTick()
  searchInput.value?.focus?.()
})

watch(mode, async value => {
  if (value !== 'search' || !visible.value) return
  await nextTick()
  searchInput.value?.focus?.()
})

function productStatus(product) {
  return product?.productStatus || product?.status || ''
}

function chooseProduct(product) {
  const value = product?.id ?? product?.productId ?? null
  emit('update:modelValue', value)
  emit('change', value)
  visible.value = false
  query.value = ''
}

function handleCascaderChange(value) {
  emit('update:modelValue', value ?? null)
  emit('change', value ?? null)
  if (value) visible.value = false
}

function requestCreate() {
  if (!props.canCreate || !trimmedQuery.value) return
  emit('create', trimmedQuery.value)
  visible.value = false
}
</script>

<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-start"
    :width="372"
    trigger="click"
    :disabled="disabled"
    popper-class="production-daily-product-picker-popper"
  >
    <template #reference>
      <button
        type="button"
        class="product-picker-trigger"
        :class="{
          'is-empty': !selectedProduct,
          'is-disabled': disabled,
          'is-invalid': invalid,
          'is-open': visible
        }"
        :disabled="disabled"
        :title="displayText"
      >
        <span>{{ displayText }}</span>
        <el-icon><ArrowDown /></el-icon>
      </button>
    </template>

    <div class="product-picker-panel">
      <div class="product-picker-heading">
        <div>
          <strong>选择产品</strong>
          <span>搜索更快捷，分类选择更直观</span>
        </div>
        <div class="product-picker-modes" role="tablist" aria-label="产品选择方式">
          <button
            type="button"
            :class="{ active: mode === 'search' }"
            role="tab"
            :aria-selected="mode === 'search'"
            @click="mode = 'search'"
          >
            <el-icon><Search /></el-icon>
            名称搜索
          </button>
          <button
            type="button"
            :class="{ active: mode === 'category' }"
            role="tab"
            :aria-selected="mode === 'category'"
            @click="mode = 'category'"
          >
            <el-icon><CollectionTag /></el-icon>
            分类选择
          </button>
        </div>
      </div>

      <div v-if="mode === 'search'" class="product-search-panel">
        <el-input
          ref="searchInput"
          v-model="query"
          clearable
          maxlength="100"
          placeholder="输入产品名称进行搜索"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <div v-if="trimmedQuery && matchedProducts.length" class="product-search-results">
          <button
            v-for="product in matchedProducts"
            :key="product.id ?? product.productId"
            type="button"
            class="product-search-result"
            @click="chooseProduct(product)"
          >
            <span class="product-search-result__name">{{ product.productName }}</span>
            <span class="product-search-result__meta">
              {{ productStatus(product) }}<template v-if="productStatus(product) && product.productType"> · </template>{{ product.productType }}
            </span>
          </button>
        </div>

        <div v-else-if="trimmedQuery" class="product-search-empty">
          <template v-if="hasIneligibleMatch">
            <strong>当前分组不可选择该产品</strong>
            <span>已找到相似产品，但其产品状态不适用于当前日报分组。</span>
          </template>
          <template v-else>
            <span>未找到“{{ trimmedQuery }}”产品，</span>
            <button v-if="canCreate" type="button" class="product-create-link" @click="requestCreate">去新增？</button>
            <span v-else>请联系有权限的管理员新增。</span>
          </template>
        </div>

        <div v-else class="product-search-hint">
          <el-icon><Search /></el-icon>
          <span>输入产品名称快速筛选。</span>
        </div>
      </div>

      <div v-else class="product-category-panel">
        <el-cascader
          :model-value="modelValue"
          :options="cascaderOptions"
          :props="productCascaderProps"
          filterable
          clearable
          placeholder="按状态 / 类型 / 产品逐级选择"
          @change="handleCascaderChange"
        />
      </div>
    </div>
  </el-popover>
</template>

<style scoped>
.product-picker-trigger {
  width: 100%;
  min-width: 0;
  min-height: 28px;
  padding: 1px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: var(--app-text);
  background: var(--el-fill-color-blank);
  border: 0;
  border-radius: 5px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
  cursor: pointer;
  transition: box-shadow .15s ease, background-color .15s ease;
}

.product-picker-trigger:hover,
.product-picker-trigger.is-open {
  box-shadow: 0 0 0 1px var(--app-primary) inset;
}

.product-picker-trigger.is-invalid {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.product-picker-trigger.is-disabled {
  color: var(--el-disabled-text-color);
  background: var(--el-disabled-bg-color);
  cursor: not-allowed;
}

.product-picker-trigger > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  text-align: left;
}

.product-picker-trigger.is-empty > span {
  color: var(--el-text-color-placeholder);
}

.product-picker-trigger .el-icon {
  flex: 0 0 auto;
  color: var(--el-text-color-placeholder);
  transition: transform .15s ease;
}

.product-picker-trigger.is-open .el-icon {
  transform: rotate(180deg);
}

.product-picker-panel {
  color: var(--app-text);
}

.product-picker-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.product-picker-heading > div:first-child {
  min-width: 0;
}

.product-picker-heading strong,
.product-picker-heading span {
  display: block;
}

.product-picker-heading strong {
  font-size: 14px;
}

.product-picker-heading span {
  margin-top: 2px;
  color: var(--app-text-tertiary);
  font-size: 11px;
}

.product-picker-modes {
  flex: 0 0 auto;
  padding: 2px;
  display: flex;
  gap: 2px;
  border-radius: 7px;
  background: #f1f4f9;
}

.product-picker-modes button {
  height: 28px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--app-text-secondary);
  background: transparent;
  border: 0;
  border-radius: 5px;
  font-size: 11px;
  cursor: pointer;
}

.product-picker-modes button.active {
  color: var(--app-primary);
  background: #fff;
  box-shadow: 0 1px 4px rgba(31, 54, 88, .12);
}

.product-search-results {
  max-height: 236px;
  margin-top: 8px;
  padding: 3px;
  overflow-y: auto;
  border: 1px solid var(--app-border-soft);
  border-radius: 7px;
}

.product-search-result {
  width: 100%;
  min-height: 46px;
  padding: 7px 9px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--app-text);
  background: transparent;
  border: 0;
  border-radius: 5px;
  text-align: left;
  cursor: pointer;
}

.product-search-result:hover,
.product-search-result:focus-visible {
  color: var(--app-primary);
  background: var(--app-primary-light);
  outline: none;
}

.product-search-result__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.product-search-result__meta {
  flex: 0 0 auto;
  color: var(--app-text-tertiary);
  font-size: 11px;
}

.product-search-empty,
.product-search-hint {
  min-height: 82px;
  margin-top: 8px;
  padding: 18px 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-text-tertiary);
  background: #f8faff;
  border: 1px dashed #dbe3f0;
  border-radius: 7px;
  font-size: 12px;
  text-align: center;
}

.product-search-empty {
  flex-wrap: wrap;
}

.product-search-empty strong {
  width: 100%;
  margin-bottom: 4px;
  color: var(--app-text-secondary);
}

.product-search-empty strong + span {
  width: 100%;
}

.product-search-hint {
  gap: 5px;
}

.product-create-link {
  padding: 0;
  color: var(--app-primary);
  background: transparent;
  border: 0;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.product-create-link:hover,
.product-create-link:focus-visible {
  text-decoration: underline;
  outline: none;
}

.product-category-panel :deep(.el-cascader) {
  width: 100%;
}

.product-category-panel p {
  margin: 7px 1px 0;
  color: var(--app-text-tertiary);
  font-size: 11px;
}
</style>
