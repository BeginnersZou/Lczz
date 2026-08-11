<template>
  <div class="detail-page">
    <!-- 顶部操作栏 全部按钮统一 dashed plain type icon -->
    <div class="page-header">
      <el-button dashed plain type="default" :icon="ArrowLeft" @click="goBack">
        返回列表
      </el-button>
    </div>

    <el-card v-if="detail" class="main-card" shadow="light">
      <!-- 头部标题区 纯白无渐变 -->
      <div class="title-bar">
        <div class="title-left">
          <div class="title-wrap">
            <h1 class="prod-title">{{ detail.name }}</h1>
            <el-tag :type="getStatusType(detail.status)" size="small">{{ detail.status }}</el-tag>
          </div>
          <div class="meta-row">
            <span class="brand-tag">{{ detail.brand }}</span>
            <span class="price-text">¥{{ detail.price }}</span>
          </div>
        </div>
        <div class="title-right">
          <span class="time-text">发布时间：{{ detail.publishTime }}</span>
        </div>
      </div>

      <div class="content-wrap">
        <!-- 左侧图片区域 -->
        <div class="col-left">
          <el-card class="img-card" shadow="light">
            <template #header>
              <span class="card-title"><el-icon>
                  <Picture />
                </el-icon>产品图片</span>
            </template>
            <div class="img-box">
              <img :src="detail.image" alt="产品图" class="cover-img"
                @error="$event.target.src = 'https://picsum.photos/id/0/800/800'" />
            </div>
          </el-card>

          <el-card v-if="detail.video" class="video-card" shadow="light">
            <template #header>
              <span class="card-title"><el-icon>
                  <VideoPlay />
                </el-icon>产品视频</span>
            </template>
            <div class="video-box">
              <video :src="detail.video" controls class="video-player">
                浏览器不支持视频
              </video>
            </div>
          </el-card>
        </div>

        <!-- 右侧参数介绍 -->
        <div class="col-right">
          <el-card class="param-card" shadow="light">
            <template #header>
              <span class="card-title"><el-icon>
                  <Setting />
                </el-icon>规格参数</span>
            </template>
            <div class="param-grid">
              <div class="param-item">
                <span class="label">匹数</span>
                <span class="val">{{ detail.horsepower }}匹</span>
              </div>
              <div class="param-item">
                <span class="label">适用面积</span>
                <span class="val">{{ detail.roomSize }}㎡</span>
              </div>
              <div class="param-item">
                <span class="label">能效等级</span>
                <span class="val">{{ detail.energyEfficiency }}</span>
              </div>
              <div class="param-item">
                <span class="label">制冷量</span>
                <span class="val">{{ detail.coolingCapacity }}W</span>
              </div>
              <div class="param-item">
                <span class="label">制热量</span>
                <span class="val">{{ detail.heatingCapacity }}W</span>
              </div>
              <div class="param-item">
                <span class="label">噪音范围</span>
                <span class="val">{{ detail.noise }}dB</span>
              </div>
              <div class="param-item">
                <span class="label">循环风量</span>
                <span class="val">{{ detail.airFlow }}m³/h</span>
              </div>
              <div class="param-item">
                <span class="label">电源电压</span>
                <span class="val">{{ detail.voltage }}V</span>
              </div>
              <div class="param-item">
                <span class="label">额定功率</span>
                <span class="val">{{ detail.power }}W</span>
              </div>
            </div>
          </el-card>

          <el-card class="brief-card" shadow="light">
            <template #header>
              <span class="card-title"><el-icon>
                  <InfoFilled />
                </el-icon>产品简介</span>
            </template>
            <p class="brief-text">{{ detail.description }}</p>
          </el-card>

          <el-card class="detail-text-card" shadow="light">
            <template #header>
              <span class="card-title"><el-icon>
                  <Document />
                </el-icon>详细介绍</span>
            </template>
            <div class="rich-text" v-html="detail.detail"></div>
          </el-card>
        </div>
      </div>
    </el-card>

    <div v-else class="loading-box">
      <el-icon size="48" color="#409EFF">
        <Loading />
      </el-icon>
      <p>加载中...</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
// 全部正确图标，无报错
import {
  ArrowLeft,
  Edit,
  Delete,
  Picture,
  VideoPlay,
  Setting,
  InfoFilled,
  Document,
  Loading
} from '@element-plus/icons-vue'
import { getAirConditionerDetailApi, deleteAirConditionerApi } from '@/api/airConditioner'

const router = useRouter()
const route = useRoute()
const detail = ref(null)
const pageLoading = ref(false)

onMounted(loadDetail)

/**
 * 加载空调产品详情
 */
async function loadDetail() {
  pageLoading.value = true
  try {
    const data = await getAirConditionerDetailApi(route.params.id)
    detail.value = data
  } catch {
    detail.value = null
  } finally {
    pageLoading.value = false
  }
}

const getStatusType = (status) => {
  const map = { '已发布': 'success', 'published': 'success', '草稿': 'warning', 'draft': 'warning', '审核中': 'info' }
  return map[status] || 'default'
}

// 返回列表
const goBack = () => router.push('/dynamic')

// 编辑路由防护
const edit = () => {
  if (!detail.value?.id) {
    ElMessage.warning('数据加载异常，无法进入编辑')
    return
  }
  router.push({ path: `/dynamic/${detail.value.id}/edit` })
}

// 删除弹窗
const deleteItem = () => {
  if (!detail.value?.id) {
    ElMessage.warning('数据加载异常，无法删除')
    return
  }
  ElMessageBox.confirm(
    '确定永久删除该产品？删除后无法恢复',
    '操作提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await deleteAirConditionerApi(detail.value.id)
      ElMessage.success('删除成功')
      router.push('/dynamic')
    } catch {
      // 拦截器已提示
    }
  }).catch(() => ElMessage.info('已取消'))
}
</script>

<style lang="scss" scoped>
.detail-page {
  padding: 20px;
  background-color: #f8fafc;
  min-height: calc(100vh - 60px);

  // 顶部按钮栏 极简纯白卡片
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding: 14px 18px;
    background: #ffffff;
    border-radius: 8px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);

    .header-actions {
      display: flex;
      gap: 10px;
    }

    :deep(.el-button) {
      border-radius: 6px;
      padding: 7px 16px;
      display: flex;
      align-items: center;
      gap: 5px;
    }
  }

  // 主详情卡片
  .main-card {
    border-radius: 8px;
    border: none;

    :deep(.el-card__body) {
      padding: 0;
    }
  }

  // 头部标题区域 无渐变纯白
  .title-bar {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 22px 24px;
    border-bottom: 1px solid #e5e7eb;

    .title-left {
      flex: 1;
    }

    .title-wrap {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 10px;

      .prod-title {
        font-size: 22px;
        font-weight: 600;
        color: #1f2937;
        margin: 0;
      }
    }

    .meta-row {
      display: flex;
      align-items: center;
      gap: 14px;

      .brand-tag {
        padding: 4px 12px;
        background: #e6f4ff;
        color: #1677ff;
        border-radius: 6px;
        font-size: 14px;
      }

      .price-text {
        font-size: 24px;
        font-weight: 600;
        color: #1677ff;
      }
    }

    .title-right .time-text {
      font-size: 13px;
      color: #6b7280;
    }
  }

  // 主体左右布局
  .content-wrap {
    display: grid;
    grid-template-columns: 380px 1fr;
    gap: 22px;
    padding: 24px;
  }

  // 左侧图片列
  .col-left {
    display: flex;
    flex-direction: column;
    gap: 14px;

    .img-card,
    .video-card {
      border-radius: 8px;
      border: none;

      :deep(.el-card__header) {
        padding: 12px 16px;
        background: #f8fafc;
        border-bottom: 1px solid #e5e7eb;
      }

      .card-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 14px;
        color: #374151;
      }

      .img-box,
      .video-box {
        padding: 14px;
      }

      .cover-img {
        width: 100%;
        height: 280px;
        object-fit: cover;
        border-radius: 6px;
      }

      .video-player {
        width: 100%;
        height: 200px;
        border-radius: 6px;
      }
    }
  }

  // 右侧参数介绍列
  .col-right {
    display: flex;
    flex-direction: column;
    gap: 14px;

    .param-card,
    .brief-card,
    .detail-text-card {
      border-radius: 8px;
      border: none;

      :deep(.el-card__header) {
        padding: 12px 16px;
        background: #f8fafc;
        border-bottom: 1px solid #e5e7eb;
      }

      :deep(.el-card__body) {
        padding: 18px;
      }

      .card-title {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 15px;
        font-weight: 500;
        color: #1f2937;
      }
    }

    // 参数网格 静态无hover上浮
    .param-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;

      .param-item {
        padding: 12px 14px;
        background: #f8fafc;
        border-radius: 6px;
        display: flex;
        flex-direction: column;

        .label {
          font-size: 12px;
          color: #6b7280;
          margin-bottom: 4px;
        }

        .val {
          font-size: 14px;
          color: #1f2937;
          font-weight: 500;
        }
      }
    }

    .brief-text {
      margin: 0;
      font-size: 14px;
      line-height: 1.7;
      color: #4b5563;
    }

    .rich-text {
      font-size: 14px;
      line-height: 1.75;
      color: #374151;

      :deep(p) {
        margin-bottom: 14px;
      }

      :deep(ul) {
        padding-left: 20px;
        margin-bottom: 14px;
      }

      :deep(li) {
        margin-bottom: 6px;
      }

      :deep(strong) {
        color: #111827;
      }

      :deep(img) {
        max-width: 100%;
        border-radius: 6px;
      }
    }
  }

  // 加载空状态
  .loading-box {
    padding: 100px 0;
    background: #fff;
    border-radius: 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;

    p {
      font-size: 15px;
      color: #6b7280;
    }
  }
}

// 平板适配
@media (max-width: 1024px) {
  .detail-page .content-wrap {
    grid-template-columns: 1fr;
  }

  .detail-page .param-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

// 手机适配
@media (max-width: 768px) {
  .detail-page {
    padding: 12px;
  }

  .detail-page .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .detail-page .title-bar {
    flex-direction: column;
    gap: 14px;
  }

  .detail-page .param-grid {
    grid-template-columns: 1fr;
  }

  .detail-page .content-wrap {
    padding: 16px;
  }
}
</style>