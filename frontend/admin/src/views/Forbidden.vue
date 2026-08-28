<template>
  <div class="forbidden-page">
    <el-result icon="warning" title="无权访问" sub-title="当前账号没有后台管理权限，请使用管理员账号登录。">
      <template #extra>
        <el-button type="primary" :loading="loggingOut" @click="backToLogin">返回登录</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loggingOut = ref(false)

async function backToLogin() {
  if (loggingOut.value) return
  loggingOut.value = true
  await userStore.logout()
  await router.replace('/login')
  loggingOut.value = false
}
</script>

<style scoped>
.forbidden-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-page, #f5f7fa);
}
</style>
