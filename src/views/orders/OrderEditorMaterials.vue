<template>
  <div>
    <div class="material-toolbar">
      <span v-if="request">{{ request.requestNo }} · {{ request.statusLabel }}</span>
      <span v-else class="muted">当前没有有效耗材申请，可按需新增清单。</span>
      <el-button v-if="!disabled" type="primary" plain @click="openPicker">选择耗材</el-button>
    </div>
    <el-table :data="modelValue.items" border empty-text="未选择耗材，可直接保存其它修改">
      <el-table-column label="耗材名称" min-width="150"><template #default="{ row }">{{ row.name }}</template></el-table-column>
      <el-table-column prop="spec" label="具体规格" min-width="160" />
      <el-table-column prop="unit" label="单位" width="75" />
      <el-table-column label="可用库存" width="110"><template #default="{ row }">{{ available(row) ?? '—' }}</template></el-table-column>
      <el-table-column label="申请数量" width="170">
        <template #default="{ row, $index }">
          <el-input-number :model-value="row.quantity" :min="1" :max="999999999" :precision="0"
            :disabled="disabled" controls-position="right" @update:model-value="setQuantity($index, $event)" />
        </template>
      </el-table-column>
      <el-table-column v-if="!disabled" label="操作" width="75">
        <template #default="{ $index }"><el-button link type="danger" @click="remove($index)">移除</el-button></template>
      </el-table-column>
    </el-table>
    <p class="muted">可用库存包含本申请已预占的数量；保存时后端会再次校验。</p>
    <el-input :model-value="modelValue.remark" type="textarea" :rows="2" maxlength="500" show-word-limit
      :disabled="disabled" placeholder="申请备注" @update:model-value="setRemark" />

    <el-dialog v-model="visible" title="选择耗材与具体规格" width="min(960px, 95vw)" destroy-on-close>
      <div class="picker-filters">
        <el-input v-model="keyword" clearable placeholder="搜索耗材名称或编码" @keyup.enter="search" @clear="search" />
        <el-cascader v-model="categoryPath" :options="categories" :props="categoryProps" clearable
          placeholder="选择分类" @change="search" />
        <el-button type="primary" @click="search">搜索</el-button>
      </div>
      <el-alert v-if="categoryError" :title="categoryError" type="warning" :closable="false">
        <el-button link @click="loadCategories">重试分类</el-button>
      </el-alert>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
        <el-button link @click="loadProducts">重新加载</el-button>
      </el-alert>
      <el-table v-loading="loading" :data="skuRows" border empty-text="暂无符合条件的可用耗材">
        <el-table-column label="耗材" min-width="180">
          <template #default="{ row }">
            <div class="product-cell"><el-image v-if="row.image" :src="row.image" fit="cover" loading="lazy" /><span>{{ row.name }}</span></div>
          </template>
        </el-table-column>
        <el-table-column prop="spec" label="规格" min-width="170" />
        <el-table-column prop="unit" label="单位" width="75" />
        <el-table-column label="可用库存" width="105"><template #default="{ row }">{{ available(row) ?? '—' }}</template></el-table-column>
        <el-table-column label="选择" width="90">
          <template #default="{ row }">
            <el-checkbox :model-value="isSelected(row.skuId)" :disabled="!isSelected(row.skuId) && available(row) < 1"
              @change="toggle(row, $event)" />
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :total="total" :page-size="10" layout="total, prev, pager, next"
        @current-change="loadProducts" />
      <template #footer>
        <span class="muted">已选 {{ selected.size }} 个规格</span>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="loading || !!loadError" @click="confirm">确认选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { getConsumablesListApi, getConsumableCategoriesApi } from '@/api/consumables'

const props = defineProps({ modelValue: { type: Object, required: true }, request: Object, disabled: Boolean })
const emit = defineEmits(['update:modelValue'])
const visible = ref(false)
const loading = ref(false)
const loadError = ref('')
const categoryError = ref('')
const keyword = ref('')
const categoryPath = ref([])
const categories = ref([])
const page = ref(1)
const total = ref(0)
const products = ref([])
const selected = ref(new Map())
const categoryProps = { value: 'id', label: 'name', children: 'children', checkStrictly: true }
let requestSequence = 0
const skuRows = computed(() => products.value.flatMap(product => (product.skus || []).filter(sku => sku.enabled !== false).map(sku => ({
  productId: product.id, skuId: sku.id, name: product.name, image: product.thumbnail || product.image,
  spec: sku.specLabel || '通用规格', unit: sku.unit, stock: sku.stock, quantity: 1
}))))
function available(row) {
  if (row.stock == null) return null
  const reserved = props.request?.materials?.find(item => Number(item.skuId) === Number(row.skuId))?.count || 0
  return Number(row.stock) + Number(reserved)
}
function setQuantity(index, quantity) {
  emit('update:modelValue', { ...props.modelValue, items: props.modelValue.items.map((item, i) => i === index ? { ...item, quantity } : item) })
}
function setRemark(remark) { emit('update:modelValue', { ...props.modelValue, remark }) }
function remove(index) { emit('update:modelValue', { ...props.modelValue, items: props.modelValue.items.filter((_, i) => i !== index) }) }
async function openPicker() {
  selected.value = new Map(props.modelValue.items.map(item => [item.skuId ?? `legacy-${item.productId}`, { ...item }]))
  visible.value = true
  page.value = 1
  await Promise.allSettled([loadProducts(), loadCategories()])
}
async function loadCategories() {
  try {
    const rows = await getConsumableCategoriesApi()
    const nodes = new Map(rows.filter(row => row.enabled !== false).map(row => [row.id, { ...row, children: [] }]))
    categories.value = []
    for (const node of nodes.values()) {
      const parent = nodes.get(node.parentId)
      if (parent) parent.children.push(node)
      else categories.value.push(node)
    }
    for (const node of nodes.values()) if (!node.children.length) delete node.children
    categoryError.value = ''
  } catch { categoryError.value = '分类加载失败，仍可按关键词搜索。' }
}
function search() { page.value = 1; return loadProducts() }
async function loadProducts() {
  const sequence = ++requestSequence
  loading.value = true
  loadError.value = ''
  try {
    const result = await getConsumablesListApi({ page: page.value, pageSize: 10, keyword: keyword.value.trim(),
      category: categoryPath.value.at(-1) || undefined, enabled: true })
    if (sequence !== requestSequence) return
    products.value = result.list || []
    total.value = result.total || 0
  } catch {
    if (sequence !== requestSequence) return
    products.value = []
    loadError.value = '耗材列表加载失败，请重新加载。'
  } finally { if (sequence === requestSequence) loading.value = false }
}
const isSelected = id => selected.value.has(id)
function toggle(row, checked) {
  if (checked) selected.value.set(row.skuId, { ...row })
  else selected.value.delete(row.skuId)
}
function confirm() {
  emit('update:modelValue', { ...props.modelValue, items: [...selected.value.values()] })
  visible.value = false
}
</script>

<style scoped>
.material-toolbar, .picker-filters { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.material-toolbar { justify-content: space-between; }
.picker-filters .el-input { flex: 1; min-width: 200px; }
.product-cell { display: flex; align-items: center; gap: 8px; }
.product-cell .el-image { width: 46px; height: 46px; flex-shrink: 0; }
.muted { color: #64748b; font-size: 13px; line-height: 1.7; }
.el-pagination { margin-top: 16px; justify-content: flex-end; }
.el-input-number { width: 140px; }
.el-alert { margin-bottom: 12px; }
</style>
