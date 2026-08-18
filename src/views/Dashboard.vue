<template>
  <div class="dashboard">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">工作台</h2>
        <span class="page-desc">聚合订单与库存待办，快速掌握今日业务状态</span>
      </div>
    </div>
    <el-card class="data-card">
      <template #header>
        <span class="card-title">业务概览</span>
        <div class="tabs">
          <el-button v-for="tab in tabs" :key="tab.value" dashed plain
            :type="activeTab === tab.value ? 'primary' : 'default'" @click="activeTab = tab.value" size="small">
            {{ tab.label }}
          </el-button>
        </div>
      </template>
      <el-alert v-if="dashboardError" :title="dashboardError" type="error" show-icon :closable="false">
        <template #default><el-button type="primary" link @click="reloadDashboard">重新加载</el-button></template>
      </el-alert>
      <div class="stats-grid" v-loading="overviewLoading">
        <div v-for="item in statCards" :key="item.key" class="stat-item actionable" role="button" tabindex="0"
          @click="goToStat(item)" @keyup.enter="goToStat(item)">
          <div class="stat-top"><span class="stat-name">{{ item.label }}</span><el-icon><ArrowRight /></el-icon></div>
          <p class="stat-num">{{ formatNumber(stats[item.key]) }}</p>
          <p class="stat-hint">{{ item.hint }}</p>
        </div>
      </div>
    </el-card>

    <div class="dashboard-bottom">
      <el-card class="chart-card">
        <template #header>
          <span class="card-title">订单趋势</span>
        </template>
        <div ref="chartRef" class="chart-container"></div>
      </el-card>

      <el-card class="feedback-card">
        <template #header>
          <span class="card-title">反馈统计</span>
        </template>
        <div class="feedback-content">
          <div class="rating-section">
            <div class="rating-value">{{ ratingValue || '-' }}</div>
            <div class="rating-stars">
              <el-icon v-for="i in 5" :key="i" :size="20" class="star-icon"
                :class="{ active: i <= Math.floor(ratingValue), half: i === Math.ceil(ratingValue) && !Number.isInteger(ratingValue) }">
                <StarFilled />
              </el-icon>
            </div>
            <p class="rating-count">({{ ratingCount }}条评价反馈)</p>
          </div>
          <div class="rating-bars">
            <div v-for="item in ratingData" :key="item.star" class="rating-bar-item">
              <span class="star-label">{{ item.star }}星</span>
              <div class="bar-container">
                <div class="bar-fill" :style="{ width: item.percent + '%' }"></div>
              </div>
              <span class="percent-label">{{ item.percent }}%</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <el-card class="order-stat-card">
      <template #header>
        <span class="card-title">订单统计</span>
      </template>
      <div class="order-content">
        <div ref="orderChartRef" class="order-chart"></div>
        <div class="order-legend">
          <div v-for="item in orderLegend" :key="item.name" class="legend-item">
            <span class="legend-color" :style="{ backgroundColor: item.color }"></span>
            <span class="legend-name">{{ item.name }}</span>
            <span class="legend-value">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { StarFilled, ArrowRight } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { getOverviewApi, getOrderTrendApi, getOrderStatusApi } from '@/api/dashboard'
import { formatNumber } from '@/utils/format'

echarts.use([LineChart, PieChart, TooltipComponent, GridComponent, CanvasRenderer])
const router = useRouter()

const activeTab = ref('day')
const chartRef = ref(null)
const orderChartRef = ref(null)
const overviewLoading = ref(false)
const dashboardError = ref('')
let chartInstance = null
let orderChartInstance = null

const ratingData = ref([
  { star: 5, percent: 0 },
  { star: 4, percent: 0 },
  { star: 3, percent: 0 },
  { star: 2, percent: 0 },
  { star: 1, percent: 0 }
])

const orderLegend = ref([
  { name: '', value: 0, color: '#3b82f6' }
])

const tabs = [
  { label: '今日', value: 'day' },
  { label: '近7天', value: '7d' },
  { label: '近30天', value: '30d' },
  { label: '汇总', value: 'summary' }
]

const stats = reactive({
  todayOrders: 0,
  pendingAssign: 0,
  processingOrders: 0,
  completedOrders: 0,
  lowStock: 0
})

const statCards = computed(() => [
  { key: 'todayOrders', label: '今日订单', hint: '查看今日新增', route: { name: 'Orders' } },
  { key: 'pendingAssign', label: '待派单', hint: '需要尽快处理', route: { name: 'Orders', query: { status: 'PENDING_VISIT' } } },
  { key: 'processingOrders', label: '进行中', hint: '跟进施工进度', route: { name: 'Orders', query: { status: 'IN_PROGRESS' } } },
  { key: 'completedOrders', label: '已完成', hint: '查看完成情况', route: { name: 'Orders', query: { status: 'PENDING_REVIEW' } } },
  { key: 'lowStock', label: '库存预警', hint: '处理缺货耗材', route: { name: 'Consumables', query: { stockStatus: 'low' } } }
])

// 活跃度图表数据（xAxis + series values）
const chartData = reactive({
  labels: [],
  values: []
})

// 订单总数（饼图中心展示）
const orderTotalCount = ref(0)
// 反馈评分
const ratingValue = ref(0)
const ratingCount = ref(0)

/**
 * 加载概览统计
 * 后端返回字段名约定：customerTotal/viewTotal/orderTotal/shareTotal/saveTotal/revenueTotal
 */
async function loadOverview() {
  overviewLoading.value = true
  try {
    const data = await getOverviewApi({ range: activeTab.value }, { silent: true })
    Object.assign(stats, {
      todayOrders: data.todayOrders ?? data.orderToday ?? data.orderTotal ?? 0,
      pendingAssign: data.pendingAssign ?? data.pendingOrderTotal ?? 0,
      processingOrders: data.processingOrders ?? data.processingTotal ?? 0,
      completedOrders: data.completedOrders ?? data.completedTotal ?? 0,
      lowStock: data.lowStock ?? data.lowStockTotal ?? 0
    })
    // 反馈评分（若后端返回）
    if (data.rating != null) ratingValue.value = data.rating
    if (data.ratingCount != null) ratingCount.value = data.ratingCount
    if (Array.isArray(data.ratingBars)) {
      ratingData.value = data.ratingBars
    }
  } catch {
    dashboardError.value = '工作台数据加载失败，请检查后端服务。'
  } finally {
    overviewLoading.value = false
  }
}

/**
 * 加载活跃度趋势
 */
async function loadTrend() {
  try {
    const data = await getOrderTrendApi({ range: activeTab.value }, { silent: true })
    chartData.labels = data.xAxis || []
    chartData.values = data.series?.[0]?.data || data.values || []
    updateChart()
  } catch {
    chartData.labels = []
    chartData.values = []
    updateChart()
  }
}

/**
 * 加载订单状态分布（饼图）
 */
async function loadOrderStatus() {
  try {
    const data = await getOrderStatusApi({ silent: true })
    // data: [{ name, value, color }]
    orderLegend.value = Array.isArray(data) && data.length
      ? data.map((item, idx) => ({
        name: item.name,
        value: item.value,
        color: item.color || defaultColors[idx % defaultColors.length]
      }))
      : [{ name: '暂无数据', value: 0, color: '#cbd5e1' }]
    orderTotalCount.value = orderLegend.value.reduce((sum, item) => sum + (item.value || 0), 0)
    updateOrderChart()
  } catch {
    orderLegend.value = [{ name: '暂无数据', value: 0, color: '#cbd5e1' }]
    updateOrderChart()
  }
}

const defaultColors = ['#3b82f6', '#84cc16', '#64748b', '#f97316', '#06b6d4']

onMounted(() => {
  initChart()
  initOrderChart()
  window.addEventListener('resize', handleResize)
  // 并行加载首屏数据
  loadOverview()
  loadTrend()
  loadOrderStatus()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  if (orderChartInstance) {
    orderChartInstance.dispose()
    orderChartInstance = null
  }
})

// 切换 tab 重新拉取对应维度数据
watch(activeTab, () => {
  dashboardError.value = ''
  loadOverview()
  loadTrend()
})

function goToStat(item) {
  router.push(item.route)
}

function reloadDashboard() {
  dashboardError.value = ''
  loadOverview()
  loadTrend()
  loadOrderStatus()
}

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

function updateChart() {
  if (!chartInstance) return
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      textStyle: {
        color: '#334155'
      },
      formatter: (params) => {
        const item = params[0]
        return `<div style="padding: 8px;">
          <div style="font-weight: 600; margin-bottom: 4px;">${item.name}</div>
          <div style="color: #3b82f6;">订单量：<strong>${item.value}</strong></div>
        </div>`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      outerBoundsMode: 'same',
      outerBoundsContain: 'axisLabel'
    },
    xAxis: {
      type: 'category',
      data: chartData.labels,
      axisLine: {
        lineStyle: {
          color: '#e2e8f0'
        }
      },
      axisLabel: {
        color: '#64748b',
        fontSize: 12
      },
      axisTick: {
        show: false
      }
    },
    yAxis: {
      type: 'value',
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      },
      axisLabel: {
        color: '#64748b',
        fontSize: 12
      },
      splitLine: {
        lineStyle: {
          color: '#f1f5f9',
          type: 'dashed'
        }
      }
    },
    series: [
      {
        name: '活跃度',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: {
          width: 3,
          color: '#3b82f6'
        },
        itemStyle: {
          color: '#3b82f6',
          borderWidth: 2,
          borderColor: '#fff'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59, 130, 246, 0.3)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0.05)' }
          ])
        },
        data: chartData.values
      }
    ]
  }
  chartInstance.setOption(option, true)
}

function initOrderChart() {
  if (!orderChartRef.value) return
  orderChartInstance = echarts.init(orderChartRef.value)
  updateOrderChart()
}

function updateOrderChart() {
  if (!orderChartInstance) return
  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e2e8f0',
      borderWidth: 1,
      padding: [12, 16],
      textStyle: {
        color: '#334155',
        fontSize: 14
      },
      formatter: (params) => {
        return `<div>
          <div style="font-weight: 600; margin-bottom: 4px;">${params.name}</div>
          <div style="color: ${params.color};">订单数: <strong>${params.value}</strong></div>
          <div style="color: #94a3b8; font-size: 12px;">占比: ${params.percent}%</div>
        </div>`
      }
    },
    series: [
      {
        name: '订单统计',
        type: 'pie',
        radius: ['45%', '75%'],
        center: ['50%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 3
        },
        label: {
          show: true,
          position: 'center',
          formatter: () => {
            return `{name|订单总数}\n{value|${orderTotalCount.value}}`
          },
          rich: {
            name: {
              fontSize: 14,
              color: '#94a3b8',
              fontWeight: 500,
              padding: [0, 0, 8, 0]
            },
            value: {
              fontSize: 36,
              color: '#1e293b',
              fontWeight: 'bold'
            }
          }
        },
        labelLine: {
          show: false
        },
        emphasis: {
          scale: true,
          scaleSize: 10,
          itemStyle: {
            shadowBlur: 20,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.2)'
          }
        },
        data: orderLegend.value.map(item => ({
          value: item.value,
          name: item.name,
          itemStyle: {
            color: item.color,
            shadowBlur: 8,
            shadowOffsetX: 2,
            shadowOffsetY: 2,
            shadowColor: 'rgba(0, 0, 0, 0.1)'
          }
        }))
      }
    ]
  }
  orderChartInstance.setOption(option, true)
}

function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
  if (orderChartInstance) {
    orderChartInstance.resize()
  }
}
</script>

<style lang="scss" scoped>
.dashboard {
  .data-card {
    margin-bottom: 16px;
    border-radius: 8px;

    :deep(.el-card__header) {
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid #e2e8f0;
      padding: 16px 20px;
    }

    .card-title {
      font-size: 16px;
      font-weight: 600;
      color: #1e293b;
    }

    .tabs {
      display: flex;
      gap: 8px;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 12px;
      padding: 20px;
    }

    .stat-item {
      background-color: #f8fafc;
      border-radius: 6px;
      padding: 16px;
      text-align: left;
      border: 1px solid #e5e7eb;

      &.actionable {
        cursor: pointer;
        transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;

        &:hover,
        &:focus-visible {
          transform: translateY(-2px);
          border-color: #93c5fd;
          box-shadow: 0 8px 20px rgba(37, 99, 235, 0.1);
          outline: none;
        }
      }

      .stat-top {
        display: flex;
        align-items: center;
        justify-content: space-between;
        color: #64748b;
      }

      .stat-num {
        font-size: 24px;
        font-weight: bold;
        color: #1e293b;
        margin: 14px 0 6px;
      }

      .stat-name {
        font-size: 14px;
        color: #64748b;
        margin: 0;
      }

      .stat-hint {
        margin: 0;
        color: #94a3b8;
        font-size: 12px;
      }
    }
  }

  .dashboard-bottom {
    display: grid;
    grid-template-columns: 1fr 400px;
    gap: 16px;
  }

  .chart-card {
    border-radius: 8px;

    :deep(.el-card__header) {
      border-bottom: 1px solid #e2e8f0;
      padding: 16px 20px;
    }

    .chart-container {
      width: 100%;
      height: 280px;
      padding: 20px;
    }
  }

  .feedback-card {
    border-radius: 8px;

    :deep(.el-card__header) {
      border-bottom: 1px solid #e2e8f0;
      padding: 16px 20px;
    }

    .feedback-content {
      padding: 20px;
    }

    .rating-section {
      text-align: center;
      margin-bottom: 20px;
      padding-bottom: 20px;
      border-bottom: 1px solid #f1f5f9;

      .rating-value {
        font-size: 48px;
        font-weight: bold;
        color: #1e293b;
        margin-bottom: 8px;
      }

      .rating-stars {
        margin-bottom: 8px;

        .star-icon {
          color: #e2e8f0;

          &.active {
            color: #fbbf24;
          }

          &.half {
            color: #fbbf24;
            opacity: 0.5;
          }
        }
      }

      .rating-count {
        font-size: 14px;
        color: #94a3b8;
        margin: 0;
      }
    }

    .rating-bars {
      display: flex;
      flex-direction: column;
      gap: 12px;

      .rating-bar-item {
        display: flex;
        align-items: center;
        gap: 12px;

        .star-label {
          width: 30px;
          font-size: 13px;
          color: #64748b;
        }

        .bar-container {
          flex: 1;
          height: 12px;
          background-color: #f1f5f9;
          border-radius: 6px;
          overflow: hidden;

          .bar-fill {
            height: 100%;
            background-color: #fbbf24;
            border-radius: 6px;
            transition: width 0.3s;
          }
        }

        .percent-label {
          width: 40px;
          text-align: right;
          font-size: 13px;
          color: #64748b;
        }
      }
    }
  }
}

.order-stat-card {
  margin-top: 16px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;

  :deep(.el-card__header) {
    border-bottom: 1px solid #e2e8f0;
    padding: 16px 20px;
    background: #fff;
  }

  .order-content {
    display: flex;
    gap: 60px;
    padding: 30px;
    justify-content: center;
    align-items: center;
  }

  .order-chart {
    width: 280px;
    height: 280px;
  }

  .order-legend {
    display: flex;
    flex-direction: column;
    gap: 16px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 16px;
      background: #f8fafc;
      border-radius: 8px;
      transition: all 0.3s;

      &:hover {
        background: #eff6ff;
        transform: translateX(4px);
      }

      .legend-color {
        width: 20px;
        height: 20px;
        border-radius: 6px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      }

      .legend-name {
        flex: 1;
        font-size: 14px;
        color: #475569;
        font-weight: 500;
      }

      .legend-value {
        font-size: 16px;
        font-weight: 700;
        color: #1e293b;
      }
    }
  }
}

@media (max-width: 1200px) {
  .dashboard {
    .data-card {
      .stats-grid {
        grid-template-columns: repeat(3, 1fr);
      }
    }

    .dashboard-bottom {
      grid-template-columns: 1fr;
    }
  }

  .order-stat-card {
    .order-content {
      flex-direction: column;
    }
  }
}

@media (max-width: 768px) {
  .dashboard {
    .data-card {
      .stats-grid {
        grid-template-columns: repeat(2, 1fr);
      }
    }
  }

  .order-stat-card {
    .order-content {
      flex-direction: column;
    }

    .order-chart {
      width: 200px;
      height: 200px;
    }
  }
}
</style>
