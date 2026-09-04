<template>
  <div class="media-list">
    <figure v-for="(file, index) in files" :key="file.id || file.url || index">
      <video v-if="file.mimeType?.startsWith('video/')" :src="file.url" controls preload="metadata" :aria-label="file.originalName || '现场视频'" />
      <el-image v-else :src="file.url" :alt="file.originalName || '订单图片'" fit="cover"
        :preview-src-list="imageUrls" :initial-index="imageUrls.indexOf(file.url)" preview-teleported>
        <template #error><span class="media-error">图片加载失败，请刷新详情</span></template>
      </el-image>
      <figcaption v-if="file.originalName">{{ file.originalName }}</figcaption>
    </figure>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ files: { type: Array, default: () => [] } })
const imageUrls = computed(() => props.files.filter(file => !file.mimeType?.startsWith('video/')).map(file => file.url))
</script>

<style scoped>
.media-list { display: flex; flex-wrap: wrap; gap: 12px; }
figure { margin: 0; width: 160px; }
.el-image, video { width: 160px; height: 120px; border-radius: 8px; background: #f1f5f9; }
figcaption { margin-top: 6px; color: #64748b; font-size: 12px; overflow-wrap: anywhere; }
.media-error { padding: 12px; color: #64748b; font-size: 12px; }
</style>
