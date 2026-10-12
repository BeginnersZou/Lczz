<template>
  <div class="order-origin">
    <el-tag :type="order.orderSource === 'DEALER_APPOINTMENT' ? 'primary' : 'info'" size="small">
      {{ order.orderSource === 'DEALER_APPOINTMENT' ? '经销商预约' : (order.orderSource && order.orderSource !== 'ADMIN' ? '其他来源' : '后台创建') }}
    </el-tag>
    <div v-if="order.orderSource === 'DEALER_APPOINTMENT'" class="dealer-name">提交经销商：{{ order.dealerName || '暂无经销商名称' }}</div>
  </div>
</template>

<script setup>
defineProps({ order: { type: Object, required: true } })
</script>

<style scoped>
.order-origin { display:flex; flex-direction:column; align-items:flex-start; gap:8px; }
.dealer-name { color:#64748b; font-size:13px; overflow-wrap:anywhere; line-height:1.5; }
</style>
