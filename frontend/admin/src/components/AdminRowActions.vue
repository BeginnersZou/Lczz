<template>
  <div class="admin-row-actions">
    <el-button class="admin-row-actions__primary" type="primary" plain size="small"
      :loading="primaryLoading" :disabled="primaryDisabled" @click="$emit('primary')">
      {{ primaryLabel }}
    </el-button>
    <el-dropdown v-if="menuItems.length" trigger="click" placement="bottom-end"
      :disabled="menuDisabled" @command="command => $emit('command', command)">
      <el-button class="admin-row-actions__more" plain size="small" :icon="MoreFilled"
        aria-label="更多操作" title="更多操作" :disabled="menuDisabled" />
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-for="item in menuItems" :key="item.command"
            :command="item.command" :disabled="item.disabled" :class="{ 'is-danger-action': item.danger }">
            {{ item.label }}
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
import { MoreFilled } from '@element-plus/icons-vue'

defineProps({
  primaryLabel: { type: String, required: true },
  primaryLoading: { type: Boolean, default: false },
  primaryDisabled: { type: Boolean, default: false },
  menuDisabled: { type: Boolean, default: false },
  menuItems: { type: Array, default: () => [] }
})
defineEmits(['primary', 'command'])
</script>

<style scoped>
.admin-row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  white-space: nowrap;
}

.admin-row-actions .el-button + .el-button { margin-left: 0; }
.admin-row-actions__primary { min-width: 72px; }
.admin-row-actions__more { width: 30px; padding: 0; }
.is-danger-action { color: var(--brand-danger); }
</style>
