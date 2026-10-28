<template>
  <div class="order-origin">
    <el-tag :type="['DEALER_APPOINTMENT', 'SALES_APPOINTMENT'].includes(order.orderSource) ? 'primary' : 'info'" size="small">
      {{ order.orderSource === 'DEALER_APPOINTMENT' ? '经销商预约' : order.orderSource === 'SALES_APPOINTMENT' ? '销售员预约' : '后台创建' }}
    </el-tag>
    <div v-if="order.orderSource === 'DEALER_APPOINTMENT'" class="dealer-name">提交经销商：{{ order.dealerName || '暂无经销商名称' }}</div>
    <div v-if="order.salesUserId" class="dealer-name">指定销售：{{ order.salesName || `#${order.salesUserId}` }}</div>
  </div>
</template>

<script setup>
defineProps({ order: { type: Object, required: true } })
</script>

<style scoped>
.order-origin { display:flex; flex-direction:column; align-items:flex-start; gap:8px; }
.dealer-name { color:#64748b; font-size:13px; overflow-wrap:anywhere; line-height:1.5; }
</style>
