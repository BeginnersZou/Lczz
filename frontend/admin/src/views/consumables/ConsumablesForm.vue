<template>
  <div class="order-form-page">
    <!-- 顶部标题返回栏（与订单表单完全统一） -->
    <div class="page-header">
      <el-button plain :icon="ArrowLeft" @click="handleCancel" class="back-btn">
        返回
      </el-button><!--  -->
      <h2 class="page-title">{{ isEdit ? '修改耗材' : '发布耗材' }}</h2>
      <div class="header-empty"></div>
    </div>

    <div v-if="loadError" class="error-state form-load-error">
      <span>{{ loadError }}</span><el-button type="primary" link @click="loadEditData">重新加载</el-button>
    </div>
    <el-form v-else v-loading="pageLoading" ref="formRef" :model="form" :rules="rules" label-width="120px" class="form-content">
      <el-card class="form-card" shadow="never">
        <template #header>
          <span class="card-title"><el-icon>
              <Box />
            </el-icon>耗材基础信息</span>
        </template>

        <!-- 耗材名称 -->
        <el-form-item label="耗材名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入耗材名称" class="input-base" maxlength="30" show-word-limit />
        </el-form-item>

        <!-- 耗材分类（二级级联） -->
        <el-form-item label="耗材分类" prop="category">
          <div class="category-control">
            <el-cascader v-model="form.category" :options="categoryOptions"
              :placeholder="categoryLoading ? '正在加载分类…' : '请选择耗材分类'" class="input-base"
              :disabled="categoryLoading || categoryOptions.length === 0" style="width: 100%" clearable filterable />
            <div v-if="categoryError" class="field-feedback is-error">
              <span>{{ categoryError }}</span>
              <el-button type="primary" link :loading="categoryLoading" @click="loadCategories">重新加载</el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="规格类型">
          <el-radio-group v-model="form.hasSpecs" @change="handleSpecModeChange">
            <el-radio-button :label="false">无规格商品</el-radio-button>
            <el-radio-button :label="true">多规格商品</el-radio-button>
          </el-radio-group>
          <span class="spec-help">规格名称和值均可自定义，例如口径、长度、大小、材质、颜色等</span>
        </el-form-item>

        <template v-if="!form.hasSpecs">
          <el-form-item label="规格说明">
            <el-input v-model="form.spec" placeholder="选填，例如通用款" class="input-base" />
          </el-form-item>
          <el-form-item label="单位" prop="unit">
            <el-select v-model="form.unit" filterable allow-create default-first-option placeholder="请选择或输入单位" class="input-base" style="width: 100%">
              <el-option v-for="u in unitOptions" :key="u" :label="u" :value="u" />
            </el-select>
          </el-form-item>
          <el-form-item label="库存数量" prop="stock">
            <el-input-number v-model="form.stock" :min="0" :max="999999" controls-position="right" style="width: 220px" />
            <span class="unit-suffix">{{ form.unit || '单位' }}</span>
          </el-form-item>
        </template>

        <el-form-item v-else label="规格配置" required>
          <div class="spec-editor">
            <div v-for="(dimension, dimensionIndex) in form.specDimensions" :key="dimension.uid" class="dimension-card">
              <div class="dimension-header">
                <el-input v-model="dimension.name" maxlength="64" placeholder="规格名称，如：口径、长度、材质" @input="markSkuStructureDirty" />
                <el-button type="danger" link :icon="Delete" @click="removeDimension(dimensionIndex)">删除维度</el-button>
              </div>
              <div class="dimension-values">
                <div v-for="(value, valueIndex) in dimension.values" :key="value.uid" class="value-editor">
                  <el-input v-model="value.text" maxlength="128" placeholder="规格值" @input="markSkuStructureDirty" />
                  <el-button link type="danger" :icon="Delete" @click="removeSpecValue(dimensionIndex, valueIndex)">删除规格值</el-button>
                </div>
                <el-button plain type="primary" :icon="Plus" @click="addSpecValue(dimensionIndex)">添加规格值</el-button>
              </div>
            </div>
            <el-button v-if="form.specDimensions.length < 8" plain type="primary" :icon="Plus" @click="addDimension">添加规格维度</el-button>
            <el-button type="primary" plain :icon="Refresh" @click="generateSkuCombinations">生成/更新SKU组合</el-button>
            <div class="combination-tip" :class="{ 'is-warning': skuStructureDirty }">
              {{ skuStructureDirty ? '规格配置已变更，请生成/更新SKU组合后再提交。' : '最多500个组合；每个组合独立维护编码、单位、库存和状态。' }}
            </div>
          </div>
        </el-form-item>

        <el-form-item v-if="form.hasSpecs" label="SKU组合" required>
          <el-table :data="form.skus" border class="sku-table" empty-text="请先配置完整的规格维度和值">
            <el-table-column v-for="dimension in validDimensions" :key="dimension.name" :label="dimension.name" min-width="110">
              <template #default="{ row }">{{ row.specValues[dimension.name] }}</template>
            </el-table-column>
            <el-table-column label="SKU编码" min-width="170">
              <template #default="{ row }"><el-input v-model="row.code" placeholder="可自动生成" /></template>
            </el-table-column>
            <el-table-column label="单位" width="130">
              <template #default="{ row }">
                <el-select v-model="row.unit" filterable allow-create default-first-option placeholder="单位">
                  <el-option v-for="u in unitOptions" :key="u" :label="u" :value="u" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="库存" width="150">
              <template #default="{ row }"><el-input-number v-model="row.stock" :min="0" :max="999999" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="启用" width="90" align="center">
              <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
            </el-table-column>
          </el-table>
        </el-form-item>

        <!-- 耗材图片（最多3张，小程序详情页轮播展示） -->
        <el-form-item label="耗材图片" prop="productImages" required>
          <div class="upload-tip">
            <el-icon>
              <InfoFilled />
            </el-icon>
            <span>上传1~3张图片（jpg/png/gif/webp），小程序详情页将按顺序轮播，不超过5MB/张，已选 {{ form.productImages.length }}/3 张</span>
          </div>
          <div class="upload-wrap">
            <div class="img-card" v-for="(fileItem, idx) in form.productImages" :key="fileItem.uid"
              @click="handleImagePreview(fileItem.previewUrl)">
              <img :src="fileItem.previewUrl" alt="耗材图片" class="preview-img" />
              <div v-if="fileItem.uploading" class="uploading-mask"><el-icon class="is-loading"><Loading /></el-icon><span>上传中</span></div>
              <div v-else class="img-mask" @click.stop="removeProductImage(idx)">
                <el-icon class="mask-icon">
                  <Delete />
                </el-icon>
                <span class="mask-text">删除</span>
              </div>
            </div>
            <div class="upload-add file-upload-box" v-if="form.productImages.length < MAX_PRODUCT_IMAGE_COUNT"
              role="button" tabindex="0" @click="triggerUpload" @keyup.enter="triggerUpload">
              <el-icon :size="26" class="upload-icon">
                <Plus />
              </el-icon>
              <span>上传图片</span>
              <input ref="fileInputRef" type="file" multiple class="upload-input"
                accept="image/jpeg,image/jpg,image/png,image/gif,image/webp" @change="handleUpload" />
            </div>
          </div>
        </el-form-item>

        <!-- 备注 -->
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注信息（选填）" class="textarea-base"
            maxlength="200" show-word-limit />
        </el-form-item>

        <!-- 耗材详情（最多9张图片，小程序详情页展示） -->
        <el-form-item label="耗材详情" prop="detailImages" required>
          <div class="upload-tip">
            <el-icon>
              <InfoFilled />
            </el-icon>
            <span>上传耗材详情图片（jpg/png/gif/webp），至少1张，最多9张且不超过5MB/张，已选 {{ form.detailImages.length }}/{{ MAX_DETAIL_FILE_COUNT }} 张</span>
          </div>
          <div class="upload-wrap">
            <!-- 已上传图片卡片：hover 显示删除遮罩 -->
            <div class="img-card" v-for="(fileItem, idx) in form.detailImages" :key="fileItem.uid">
              <img :src="fileItem.previewUrl" alt="耗材详情图" class="preview-img" />
              <div v-if="fileItem.uploading" class="uploading-mask"><el-icon class="is-loading"><Loading /></el-icon><span>上传中</span></div>
              <div v-else class="img-mask" @click="removeDetailImage(idx)">
                <el-icon class="mask-icon">
                  <Delete />
                </el-icon>
                <span class="mask-text">删除</span>
              </div>
            </div>
            <!-- 上传按钮：达到9张自动隐藏 -->
            <div class="upload-add file-upload-box" v-if="form.detailImages.length < MAX_DETAIL_FILE_COUNT" role="button" tabindex="0" @click="triggerDetailUpload" @keyup.enter="triggerDetailUpload">
              <el-icon :size="26" class="upload-icon">
                <Plus />
              </el-icon>
              <span>上传图片</span>
              <input ref="detailFileInputRef" type="file" multiple class="upload-input"
                accept="image/jpeg,image/jpg,image/png,image/gif,image/webp" @change="handleDetailUpload" />
            </div>
          </div>
        </el-form-item>
      </el-card>

      <!-- 底部操作区 -->
      <div class="form-footer">
        <div class="footer-empty"></div>
        <div class="footer-right">
          <el-button plain :icon="ArrowLeft" @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :icon="Check"
            :loading="submitLoading">确认提交</el-button>
        </div>
      </div>
    </el-form>

    <!-- 图片预览弹窗 -->
    <el-dialog v-model="previewVisible" title="图片预览" width="480px">
      <img :src="previewImageUrl" alt="耗材图片" style="width: 100%; border-radius: 8px" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Plus, Box, Check, Delete, InfoFilled, Loading, Refresh
} from '@element-plus/icons-vue'
import { useRouter, useRoute } from 'vue-router'
import {
  getConsumablesDetailApi,
  getConsumableCategoriesApi,
  addConsumablesApi,
  updateConsumablesApi,
  uploadConsumablesImageApi
} from '@/api/consumables'
import { useUnsavedChanges } from '@/composables/useUnsavedChanges'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const fileInputRef = ref(null)
const detailFileInputRef = ref(null)

// 基础状态
const isEdit = ref(false)
const consumablesId = ref('')
const submitLoading = ref(false)
const pageLoading = ref(false)
const loadError = ref('')
const formIsDirty = ref(false)
const previewVisible = ref(false)
const previewImageUrl = ref('')
const categoryLoading = ref(false)
const categoryError = ref('')
const skuStructureDirty = ref(false)
// 详情图片唯一 uid 生成器（编辑回显与新增上传共用，避免冲突）
let imageUid = 1
let specUid = 1

// 单位选项
const unitOptions = ['米', '瓶', '个', '把', '套', '卷', '台', '件']

const categoryOptions = ref([])
const categoryIdByName = new Map()

// 表单数据
const form = reactive({
  name: '',
  category: [],
  categoryId: null,
  spec: '',
  unit: '',
  stock: 0,
  hasSpecs: false,
  specDimensions: [],
  skus: [],
  price: 0,
  enabled: true,
  sortOrder: 0,
  productImages: [], // 轮播图片，最多3张；第1张映射为封面，其余图片前置到详情文件
  remark: '',
  detailImages: [] // 耗材详情图片，最多9张，{uid, file, previewUrl, uploading}
})

// 校验规则
const rules = {
  name: [
    { required: true, message: '请输入耗材名称', trigger: 'blur' },
    { min: 2, max: 30, message: '名称长度2~30字符', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择耗材分类', trigger: 'change' }
  ],
  unit: [{ validator: (_rule, value, callback) => {
    if (!form.hasSpecs && !String(value || '').trim()) callback(new Error('请选择或输入单位'))
    else callback()
  }, trigger: 'change' }],
  stock: [{ required: true, message: '请输入库存数量', trigger: 'blur' }],
  productImages: [{
    validator: (_rule, value, callback) => {
      if (!Array.isArray(value) || value.length === 0) callback(new Error('请至少上传1张耗材图片'))
      else if (value.length > 3) callback(new Error('耗材图片最多上传3张'))
      else callback()
    },
    trigger: 'change'
  }],
  detailImages: [{
    validator: (_rule, value, callback) => {
      if (!Array.isArray(value) || value.length === 0) callback(new Error('请至少上传1张耗材详情图片'))
      else callback()
    },
    trigger: 'change'
  }]
}

const validDimensions = computed(() => form.specDimensions
  .map(dimension => ({ ...dimension, name: String(dimension.name || '').trim(), values: dimension.values.filter(value => String(value.text || '').trim()) }))
  .filter(dimension => dimension.name && dimension.values.length))

function handleSpecModeChange(hasSpecs) {
  if (hasSpecs && form.specDimensions.length === 0) addDimension()
  if (!hasSpecs) form.skus = []
}

function addDimension() {
  if (form.specDimensions.length >= 8) return
  form.specDimensions.push({ uid: specUid++, name: '', values: [{ uid: specUid++, text: '' }] })
  markSkuStructureDirty()
}

function removeDimension(index) {
  form.specDimensions.splice(index, 1)
  if (!form.specDimensions.length) form.hasSpecs = false
  markSkuStructureDirty()
}

function addSpecValue(dimensionIndex) {
  form.specDimensions[dimensionIndex].values.push({ uid: specUid++, text: '' })
  markSkuStructureDirty()
}

function removeSpecValue(dimensionIndex, valueIndex) {
  const values = form.specDimensions[dimensionIndex].values
  values.splice(valueIndex, 1)
  if (!values.length) values.push({ uid: specUid++, text: '' })
  markSkuStructureDirty()
}

function markSkuStructureDirty() { skuStructureDirty.value = true }

function skuSignature(values, dimensions = validDimensions.value) {
  return dimensions.map(dimension => `${dimension.name}=${values[dimension.name] || ''}`).join('|')
}

function buildCartesian(dimensions, index = 0, current = {}, rows = []) {
  if (index >= dimensions.length) {
    rows.push({ ...current })
    return rows
  }
  const dimension = dimensions[index]
  dimension.values.forEach(value => buildCartesian(dimensions, index + 1,
    { ...current, [dimension.name]: String(value.text).trim() }, rows))
  return rows
}

async function generateSkuCombinations() {
  if (!form.hasSpecs) return
  const dimensions = validDimensions.value
  if (dimensions.length !== form.specDimensions.length || dimensions.some(d => !d.values.length)) {
    ElMessage.warning('请填写完整的规格名称和规格值')
    return
  }
  const names = dimensions.map(d => d.name)
  if (new Set(names).size !== names.length) {
    ElMessage.warning('规格维度名称不能重复')
    return
  }
  for (const dimension of dimensions) {
    const values = dimension.values.map(value => String(value.text).trim())
    if (new Set(values).size !== values.length) {
      ElMessage.warning(`“${dimension.name}”的规格值不能重复`)
      return
    }
  }
  const combinations = buildCartesian(dimensions)
  if (combinations.length > 500) {
    ElMessage.warning('SKU组合最多500个，请减少规格值')
    return
  }
  const existing = new Map(form.skus.map(sku => [skuSignature(sku.specValues, dimensions), sku]))
  const next = combinations.map((specValues, index) => {
    const old = existing.get(skuSignature(specValues, dimensions))
    return old ? { ...old, specValues } : {
      code: '', specValues, unit: form.unit || '件', stock: 0, enabled: true, sortOrder: index
    }
  })
  const nextSignatures = new Set(next.map(sku => skuSignature(sku.specValues, dimensions)))
  const preserved = next.filter(sku => existing.has(skuSignature(sku.specValues, dimensions))).length
  const added = next.length - preserved
  const removed = form.skus.filter(sku => !nextSignatures.has(skuSignature(sku.specValues, dimensions))).length
  if (form.skus.length && (added || removed)) {
    try {
      await ElMessageBox.confirm(
        `将保留 ${preserved} 个原组合，新增 ${added} 个，移除 ${removed} 个。被移除组合的编码、库存和启停状态不会继承给其他组合。`,
        '确认更新SKU组合', { confirmButtonText: '确认更新', cancelButtonText: '取消', type: 'warning' })
    } catch { return }
  }
  form.skus = next
  skuStructureDirty.value = false
  ElMessage.success(`已生成 ${next.length} 个SKU组合`)
}

function validateSkuEditor() {
  if (!form.hasSpecs) return true
  const dimensions = validDimensions.value
  if (!dimensions.length || dimensions.length !== form.specDimensions.length) {
    ElMessage.warning('请填写完整的规格名称和规格值')
    return false
  }
  const names = dimensions.map(d => d.name)
  if (new Set(names).size !== names.length) {
    ElMessage.warning('规格维度名称不能重复')
    return false
  }
  for (const dimension of dimensions) {
    const values = dimension.values.map(value => value.text.trim())
    if (new Set(values).size !== values.length) {
      ElMessage.warning(`“${dimension.name}”的规格值不能重复`)
      return false
    }
  }
  const expected = buildCartesian(dimensions).map(values => skuSignature(values, dimensions))
  const actual = form.skus.map(sku => skuSignature(sku.specValues, dimensions))
  if (skuStructureDirty.value || expected.length !== actual.length || expected.some(value => !actual.includes(value))) {
    ElMessage.warning('规格配置已变更，请先点击“生成/更新SKU组合”')
    return false
  }
  if (!form.skus.length) {
    ElMessage.warning('未生成有效SKU组合，组合总数不能超过500个')
    return false
  }
  if (form.skus.some(sku => !String(sku.unit || '').trim() || Number(sku.stock) < 0)) {
    ElMessage.warning('请完善每个SKU的单位和库存')
    return false
  }
  if (!form.skus.some(sku => sku.enabled)) {
    ElMessage.warning('至少保留一个启用的SKU')
    return false
  }
  const codes = form.skus.map(sku => String(sku.code || '').trim().toUpperCase()).filter(Boolean)
  if (new Set(codes).size !== codes.length) {
    ElMessage.warning('SKU编码不能重复')
    return false
  }
  return true
}

watch(form, () => {
  formIsDirty.value = true
}, { deep: true })

useUnsavedChanges(formIsDirty, '耗材内容尚未保存，确定要放弃并离开吗？')

onMounted(() => {
  loadCategories().then(() => {
    if (route.params.id) loadEditData()
  })
  if (route.params.id) {
    isEdit.value = true
    consumablesId.value = route.params.id
  }
})

async function loadCategories() {
  categoryLoading.value = true
  categoryError.value = ''
  try {
    const categories = await getConsumableCategoriesApi()
    const parents = (categories || []).filter(item => item.level === 1)
    const children = (categories || []).filter(item => item.level === 2)
    const options = parents.map(parent => ({
      value: parent.name,
      label: parent.name,
      children: children.filter(child => child.parentId === parent.id).map(child => {
        categoryIdByName.set(child.name, child.id)
        return { value: child.name, label: child.name }
      })
    })).filter(parent => parent.children.length)
    if (!options.length) throw new Error('EMPTY_CATEGORY_TREE')
    categoryOptions.value = options
    if (form.category.length) {
      const selectedId = categoryIdByName.get(form.category[form.category.length - 1])
      if (selectedId) form.categoryId = selectedId
    }
  } catch {
    categoryOptions.value = []
    categoryError.value = '耗材分类加载失败，请检查分类接口后重试。'
  } finally {
    categoryLoading.value = false
  }
}

/**
 * 编辑回显：从后端拉取耗材详情
 */
async function loadEditData() {
  pageLoading.value = true
  loadError.value = ''
  try {
    const data = await getConsumablesDetailApi(consumablesId.value)
    const allDetailImages = (data.detailImages || []).map(item => ({
      uid: imageUid++,
      id: item.id || null,
      file: null,
      previewUrl: typeof item === 'string' ? item : (item.url || ''),
      uploading: false
    })).filter(item => item.previewUrl)
    const loadedCarousel = (data.images?.length ? data.images : (data.image ? [{ id: data.coverFileId, url: data.image }] : [])).map(item => ({
      uid: imageUid++, id: item.id || null, file: null, previewUrl: typeof item === 'string' ? item : (item.url || ''), uploading: false
    })).filter(item => item.previewUrl)
    const loadedDimensions = (data.specDimensions || []).map(dimension => ({
      uid: specUid++,
      name: dimension.name || '',
      values: (dimension.values || []).map(value => ({ uid: specUid++, text: value.value || '' }))
    }))
    const loadedSkus = (data.skus || []).map((sku, index) => ({
      id: sku.id,
      code: sku.code || '',
      specValues: { ...(sku.specValues || {}) },
      unit: sku.unit || data.unit || '件',
      stock: Number(sku.stock || 0),
      enabled: sku.enabled !== false,
      sortOrder: sku.sortOrder ?? index
    }))
    Object.assign(form, {
      name: data.name || '',
      category: Array.isArray(data.category) ? [...data.category] : [],
      categoryId: data.categoryId || null,
      spec: data.spec || '',
      unit: data.unit || '',
      stock: data.stock != null ? data.stock : 0,
      hasSpecs: loadedDimensions.length > 0,
      specDimensions: loadedDimensions,
      skus: loadedDimensions.length ? loadedSkus : [],
      price: data.price != null ? data.price : 0,
      enabled: data.enabled !== false,
      sortOrder: data.sortOrder || 0,
      productImages: loadedCarousel,
      remark: data.remark || '',
      detailImages: allDetailImages
    })
    const selectedId = categoryIdByName.get(form.category[form.category.length - 1])
    if (selectedId) form.categoryId = selectedId
    skuStructureDirty.value = false
    formIsDirty.value = false
  } catch (err) {
    loadError.value = '耗材详情加载失败，请重试后再编辑。'
  } finally {
    pageLoading.value = false
  }
}

// ====================== 耗材轮播图片上传（最多3张） ======================
const ALLOW_IMG_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp']
const MAX_IMG_SIZE = 5 * 1024 * 1024 // 5MB
const MAX_PRODUCT_IMAGE_COUNT = 3
const MAX_DETAIL_FILE_COUNT = 9

function triggerUpload() {
  fileInputRef.value.click()
}

async function handleUpload(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  const remaining = MAX_PRODUCT_IMAGE_COUNT - form.productImages.length
  if (remaining <= 0) return ElMessage.warning(`耗材图片最多上传${MAX_PRODUCT_IMAGE_COUNT}张`)
  const validFiles = []
  for (const file of files) {
    if (!ALLOW_IMG_TYPES.includes(file.type)) {
      ElMessage.warning(`文件${file.name}不是支持的图片格式`)
      continue
    }
    if (file.size > MAX_IMG_SIZE) {
      ElMessage.warning(`图片${file.name}超过5MB，禁止上传`)
      continue
    }
    if (form.productImages.length + validFiles.length >= MAX_PRODUCT_IMAGE_COUNT) {
      ElMessage.warning(`耗材图片最多上传${MAX_PRODUCT_IMAGE_COUNT}张`)
      break
    }
    validFiles.push(file)
  }
  for (const file of validFiles) {
    const uid = imageUid++
    const blobUrl = URL.createObjectURL(file)
    form.productImages.push({ uid, file, previewUrl: blobUrl, uploading: true })
    formIsDirty.value = true
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await uploadConsumablesImageApi(formData)
      const index = form.productImages.findIndex(item => item.uid === uid)
      if (index !== -1) {
        Object.assign(form.productImages[index], { id: res.id, file: null, previewUrl: res.url, uploading: false })
      }
    } catch {
      const index = form.productImages.findIndex(item => item.uid === uid)
      if (index !== -1) form.productImages.splice(index, 1)
    } finally {
      URL.revokeObjectURL(blobUrl)
      formRef.value?.validateField('productImages').catch(() => {})
    }
  }
}

function removeProductImage(index) {
  const target = form.productImages[index]
  if (target?.previewUrl?.startsWith('blob:')) URL.revokeObjectURL(target.previewUrl)
  form.productImages.splice(index, 1)
  formIsDirty.value = true
  formRef.value?.validateField('productImages').catch(() => {})
}

// ====================== 耗材详情图片上传（最多9张，去重、预览、删除、释放内存） ======================
function triggerDetailUpload() {
  detailFileInputRef.value.click()
}

async function handleDetailUpload(event) {
  const files = Array.from(event.target.files)
  event.target.value = ''
  const remaining = MAX_DETAIL_FILE_COUNT - form.detailImages.length
  if (remaining <= 0) {
    ElMessage.warning(`当前最多只能上传${MAX_DETAIL_FILE_COUNT}张详情图片`)
    return
  }
  // 收集本轮通过校验的文件（保留原有 9 张上限、去重、类型/大小校验逻辑）
  const toUpload = []
  for (const file of files) {
    // 1. 拦截非图片
    if (!ALLOW_IMG_TYPES.includes(file.type)) {
      ElMessage.warning(`文件${file.name}不是图片，禁止上传`)
      continue
    }
    // 2. 大小校验
    if (file.size > MAX_IMG_SIZE) {
      ElMessage.warning(`图片${file.name}超过5MB，禁止上传`)
      continue
    }
    // 3. 文件名+大小双条件去重
    const repeat = form.detailImages.some(item => item.file && item.file.name === file.name && item.file.size === file.size)
    if (repeat) {
      ElMessage.info(`图片${file.name}已存在，无需重复上传`)
      continue
    }
    // 4. 超过9张上限拦截
    if (form.detailImages.length + toUpload.length >= MAX_DETAIL_FILE_COUNT) {
      ElMessage.warning(`当前最多只能上传${MAX_DETAIL_FILE_COUNT}张详情图片，超出部分已忽略`)
      break
    }
    toUpload.push(file)
  }
  if (toUpload.length === 0) return
  formIsDirty.value = true
  // 逐个上传：先加入列表用 blob 即时预览，上传成功后用远程 url 替换并释放临时 blob
  for (const file of toUpload) {
    const uid = imageUid++
    const blobUrl = URL.createObjectURL(file)
    form.detailImages.push({ uid, file, previewUrl: blobUrl, uploading: true })
    try {
      const formData = new FormData()
      formData.append('file', file)
      const res = await uploadConsumablesImageApi(formData)
      // 上传成功：用远程 url 替换 blob
      const idx = form.detailImages.findIndex(i => i.uid === uid)
      if (idx !== -1) {
        form.detailImages[idx].previewUrl = res.url
        form.detailImages[idx].id = res.id
        form.detailImages[idx].file = null
        form.detailImages[idx].uploading = false
      }
      URL.revokeObjectURL(blobUrl)
    } catch (err) {
      // 上传失败：移除该项并释放（错误已由拦截器提示）
      const idx = form.detailImages.findIndex(i => i.uid === uid)
      if (idx !== -1) {
        form.detailImages.splice(idx, 1)
      }
      URL.revokeObjectURL(blobUrl)
    }
  }
  formRef.value?.validateField('detailImages').catch(() => {})
}

// 删除详情图片，仅本地 blob 预览需释放内存，远程回显 url 不处理
function removeDetailImage(idx) {
  const target = form.detailImages[idx]
  if (target && target.previewUrl && target.previewUrl.startsWith('blob:')) {
    URL.revokeObjectURL(target.previewUrl)
  }
  form.detailImages.splice(idx, 1)
  formIsDirty.value = true
  formRef.value?.validateField('detailImages').catch(() => {})
}

function handleImagePreview(url) {
  previewImageUrl.value = url
  previewVisible.value = true
}

// ====================== 提交、取消 ======================
async function handleSubmit() {
  if (submitLoading.value) return
  await formRef.value.validate(async valid => {
    if (!valid) return
    if (!validateSkuEditor()) return
    // 图片上传完成校验：避免提交尚未上传完成的 blob url
    if (form.productImages.some(i => i.uploading || i.previewUrl?.startsWith('blob:'))) {
      ElMessage.warning('耗材图片正在上传，请稍候')
      return
    }
    if (form.detailImages.some(i => i.previewUrl && i.previewUrl.startsWith('blob:'))) {
      ElMessage.warning('详情图片正在上传，请稍候')
      return
    }
    submitLoading.value = true
    try {
      const categoryId = categoryIdByName.get(form.category[form.category.length - 1]) || form.categoryId
      if (!categoryId) {
        ElMessage.warning('请选择后端已启用的耗材分类')
        return
      }
      const [coverImage] = form.productImages
      const storedDetailImages = [...form.detailImages]
      const submitData = {
        name: form.name,
        category: form.category,
        spec: form.spec,
        unit: form.unit,
        stock: form.stock,
        price: form.price,
        image: coverImage.previewUrl,
        coverFileId: coverImage.id,
        imageFileIds: form.productImages.map(item => item.id).filter(Boolean),
        remark: form.remark,
        // detailImages 转为 url 数组
        detailImages: storedDetailImages.map(item => ({ id: item.id, url: item.previewUrl })),
        detailFileIds: storedDetailImages.map(item => item.id).filter(Boolean),
        categoryId,
        enabled: form.enabled,
        sortOrder: form.sortOrder
      }
      if (form.hasSpecs) {
        submitData.specDimensions = validDimensions.value.map((dimension, index) => ({
          name: dimension.name,
          sortOrder: index,
          values: dimension.values.map((value, valueIndex) => ({ value: value.text.trim(), sortOrder: valueIndex }))
        }))
        submitData.skus = form.skus.map((sku, index) => ({
          code: String(sku.code || '').trim() || undefined,
          specValues: { ...sku.specValues },
          unit: sku.unit,
          stock: Number(sku.stock || 0),
          enabled: sku.enabled !== false,
          sortOrder: index
        }))
        const firstSku = submitData.skus[0]
        submitData.spec = Object.entries(firstSku.specValues).map(([key, value]) => `${key}=${value}`).join(' / ')
        submitData.unit = firstSku.unit
        submitData.stock = submitData.skus.filter(sku => sku.enabled).reduce((total, sku) => total + sku.stock, 0)
      } else {
        submitData.specDimensions = []
        submitData.skus = [{
          code: undefined,
          specValues: {},
          unit: form.unit,
          stock: Number(form.stock || 0),
          enabled: true,
          sortOrder: 0
        }]
      }
      if (isEdit.value) {
        await updateConsumablesApi(consumablesId.value, submitData)
        ElMessage.success('耗材修改成功')
      } else {
        await addConsumablesApi(submitData)
        ElMessage.success('耗材发布成功')
      }
      formIsDirty.value = false
      returnToConsumablesList()
    } catch (err) {
      // 错误已由响应拦截器统一提示
    } finally {
      submitLoading.value = false
    }
  })
}

function handleCancel() {
  returnToConsumablesList()
}

function returnToConsumablesList() {
  const backPath = window.history.state?.back
  if (typeof backPath === 'string' && /^\/consumables(?:\?|$)/.test(backPath)) {
    router.back()
  } else {
    router.push({ name: 'Consumables' })
  }
}
</script>

<style lang="scss" scoped>
.order-form-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: calc(100vh - 60px);

  .page-header {
    display: grid;
    grid-template-columns: 160px 1fr 160px;
    align-items: center;
    margin-bottom: 20px;
    padding: 14px 18px;
    background: #ffffff;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);

    .back-btn {
      display: flex;
      align-items: center;
      gap: 5px;
      color: #64748b;
    }

    .page-title {
      text-align: center;
      font-size: 20px;
      font-weight: 600;
      color: #1f2937;
      margin: 0;
    }

    .header-empty {
      text-align: right;
    }
  }

  .form-content {
    width: 100%;
  }

  .form-card {
    margin-bottom: 16px;
    border-radius: 8px;
    border: none;

    :deep(.el-card__header) {
      padding: 12px 18px;
      background: #f8fafc;
      border-bottom: 1px solid #e5e7eb;
    }

    .card-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 15px;
      font-weight: 500;
      color: #1f2937;
    }

    :deep(.el-card__body) {
      padding: 20px;
    }
  }

  .input-base {
    width: 100%;

    :deep(.el-input__wrapper) {
      border: 1px solid #d1d5db;
      border-radius: 6px;
      box-shadow: none;
      transition: border 0.2s;

      &:hover {
        border-color: #94a3b8;
      }

      &.is-focus {
        border-color: #409eff;
      }
    }
  }

  .category-control {
    width: 100%;
  }

  .field-feedback {
    display: flex;
    align-items: center;
    gap: 4px;
    min-height: 24px;
    margin-top: 4px;
    font-size: 12px;

    &.is-error {
      color: var(--brand-danger);
    }
  }

  .textarea-base :deep(.el-textarea__inner) {
    border: 1px solid #d1d5db;
    border-radius: 6px;
    box-shadow: none;

    &:hover {
      border-color: #94a3b8;
    }

    &:focus {
      border-color: #409eff;
    }
  }

  .unit-suffix {
    margin-left: 10px;
    font-size: 13px;
    color: #94a3b8;
  }

  // 图片上传提示条
  .upload-tip {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;
    font-size: 12px;
    color: #64748b;
    margin-bottom: 12px;
    padding: 7px 12px;
    background: #f1f5f9;
    border-radius: 6px;
    border-left: 3px solid #3b82f6;
  }

  .upload-wrap {
    display: flex;
    flex-wrap: wrap;
    gap: 14px;
  }

  // 图片卡片
  .img-card {
    width: 120px;
    height: 120px;
    border-radius: 10px;
    overflow: hidden;
    position: relative;
    box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
    transition: transform 0.25s ease, box-shadow 0.25s ease;
    cursor: pointer;

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 20px rgba(15, 23, 42, 0.18);

      .img-mask {
        opacity: 1;
      }
    }

    .preview-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      display: block;
    }

    .img-mask {
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, rgba(15, 23, 42, 0) 0%, rgba(15, 23, 42, 0.6) 100%);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 4px;
      opacity: 0;
      transition: opacity 0.25s ease;
      cursor: pointer;

      .mask-icon {
        font-size: 20px;
        color: #fff;
        transition: color 0.2s;
      }

      .mask-text {
        font-size: 12px;
        color: #fff;
      }

      &:hover .mask-icon {
        color: #f87171;
      }
    }
  }

  // 上传框
  .file-upload-box {
    width: 120px;
    height: 120px;
    border: 1px dashed #cbd5e1;
    border-radius: 10px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    background: #f8fafc;
    transition: all 0.25s ease;

    &:hover {
      border-color: #3b82f6;
      background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);

      .upload-icon {
        transform: scale(1.15);
        color: #3b82f6;
      }

      span {
        color: #3b82f6;
      }
    }

    .upload-icon {
      color: #94a3b8;
      transition: all 0.25s ease;
    }

    span {
      font-size: 12px;
      color: #94a3b8;
      margin-top: 8px;
      transition: color 0.25s ease;
    }
  }

  .upload-input {
    display: none;
  }

  .form-footer {
    margin-top: 24px;
    padding-top: 16px;
    border-top: 1px solid #e5e7eb;
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 30px;

    .footer-empty {
      flex: 1;
    }

    .footer-right {
      display: flex;
      gap: 14px;
    }

    :deep(.el-button) {
      padding: 8px 26px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      gap: 6px;
    }
  }

  :deep(.el-form-item__label) {
    width: 120px;
    color: #374151;
  }

  :deep(.el-form-item__error) {
    font-size: 12px;
  }
}

@media (max-width: 768px) {
  .order-form-page {
    padding: 12px;
  }

  .order-form-page .page-header {
    grid-template-columns: 1fr;
    gap: 12px;

    .page-title {
      text-align: left;
    }
  }

  .form-footer {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .footer-right {
    width: 100%;
    justify-content: flex-end;
  }
}

.spec-help { margin-left: 16px; color: #64748b; font-size: 13px; }
.spec-editor { width: 100%; display: flex; flex-direction: column; gap: 12px; }
.dimension-card { padding: 14px; border: 1px solid #dce5ef; border-radius: 8px; background: #f8fafc; }
.dimension-header { display: grid; grid-template-columns: minmax(240px, 1fr) auto; align-items: center; gap: 12px; }
.dimension-values { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
.value-editor { display: flex; width: 300px; align-items: center; gap: 6px; }
.value-editor :deep(.el-button) { flex-shrink: 0; }
.combination-tip { color: #64748b; font-size: 13px; }
.sku-table { width: 100%; }
.sku-table :deep(.el-input-number) { width: 120px; }
</style>
