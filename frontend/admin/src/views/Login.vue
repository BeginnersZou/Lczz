<template>
  <div class="login-container">
    <!-- 左侧品牌展示区 -->
    <div class="brand-side">
      <!-- 装饰背景圆 -->
      <div class="decor-circle decor-circle-1"></div>
      <div class="decor-circle decor-circle-2"></div>
      <div class="decor-circle decor-circle-3"></div>

      <div class="brand-content">
        <div class="brand-logo">
          <el-icon :size="44">
            <HomeFilled />
          </el-icon>
        </div>
        <h1 class="brand-title">力创之尊</h1>
        <p class="brand-subtitle">业务管理系统</p>
        <div class="brand-divider"></div>
        <p class="brand-desc">
          专业的空调安装业务管理平台<br />
          高效管理订单 · 耗材 · 备货 · 用户
        </p>
      </div>
    </div>

    <!-- 右侧登录表单区 -->
    <div class="form-side">
      <div class="form-wrapper">
        <!-- 顶部标题 -->
        <div class="form-header">
          <h2 class="form-title">欢迎登录</h2>
          <p class="form-tip">请输入您的账号密码进行登录</p>
        </div>

        <!-- 登录表单 -->
        <el-form ref="formRef" :model="form" :rules="rules" class="login-form" size="large">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password
              :prefix-icon="Lock" @keyup.enter="handleLogin" />
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住账号</el-checkbox>
          </div>

          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>

        <!-- 底部版权 -->
        <div class="form-footer">
          <span>© {{ currentYear }} 力创之尊 · 版权所有</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { HomeFilled, User, Lock, Document, Box, ShoppingCart, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const currentYear = computed(() => new Date().getFullYear())



// 进入页面时恢复记住的账号
const savedUser = localStorage.getItem('rememberUser')
if (savedUser) {
  form.username = savedUser
  rememberMe.value = true
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const result = await userStore.login(form)
    if (result.success) {
      // 记住账号
      if (rememberMe.value) {
        localStorage.setItem('rememberUser', form.username)
      } else {
        localStorage.removeItem('rememberUser')
      }
      ElMessage.success('登录成功')
      const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//')
        ? route.query.redirect
        : '/dashboard'
      router.replace(redirect)
    } else {
      ElMessage.error(result.message)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

// ====================== 左侧品牌区 ======================
.brand-side {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #101828;
  overflow: hidden;

  // 装饰圆
  .decor-circle {
    position: absolute;
    border-radius: 50%;
    opacity: 0.08;
    background: #fff;

    &-1 {
      width: 500px;
      height: 500px;
      top: -150px;
      right: -120px;
      animation: floatCircle 8s ease-in-out infinite;
    }

    &-2 {
      width: 300px;
      height: 300px;
      bottom: -80px;
      left: -60px;
      animation: floatCircle 10s ease-in-out infinite reverse;
    }

    &-3 {
      width: 150px;
      height: 150px;
      top: 60%;
      right: 15%;
      opacity: 0.05;
      animation: floatCircle 6s ease-in-out infinite;
    }
  }

  .brand-content {
    position: relative;
    z-index: 1;
    text-align: center;
    color: #fff;
    padding: 0 60px;
    animation: fadeInUp 0.8s ease;

    .brand-logo {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 80px;
      height: 80px;
      border-radius: 16px;
      background: rgba(255, 255, 255, 0.1);
      backdrop-filter: blur(10px);
      border: 1px solid rgba(255, 255, 255, 0.15);
      margin-bottom: 28px;

      .el-icon {
        color: #60a5fa;
      }
    }

    .brand-title {
      font-size: 34px;
      font-weight: 700;
      letter-spacing: 4px;
      margin: 0 0 6px 0;
      text-shadow: none;
    }

    .brand-subtitle {
      font-size: 18px;
      font-weight: 400;
      letter-spacing: 8px;
      color: rgba(255, 255, 255, 0.7);
      margin: 0;
    }

    .brand-divider {
      width: 48px;
      height: 3px;
      border-radius: 2px;
      background: linear-gradient(90deg, #60a5fa, #3b82f6);
      margin: 24px auto;
    }

    .brand-desc {
      font-size: 15px;
      line-height: 1.8;
      color: rgba(255, 255, 255, 0.6);
      margin: 0 0 36px 0;
    }

    .brand-features {
      display: flex;
      flex-direction: column;
      gap: 14px;
      align-items: flex-start;
      max-width: 240px;
      margin: 0 auto;

      .feature-item {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 14px;
        color: rgba(255, 255, 255, 0.75);
        transition: color 0.3s;

        .el-icon {
          color: #60a5fa;
        }

        &:hover {
          color: #fff;
        }
      }
    }
  }
}

// ====================== 右侧表单区 ======================
.form-side {
  width: 440px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  flex-shrink: 0;

  .form-wrapper {
    width: 360px;
    animation: fadeInUp 0.6s ease 0.2s both;

    .form-header {
      margin-bottom: 36px;

      .form-title {
        font-size: 24px;
        font-weight: 600;
        color: #1e293b;
        margin: 0 0 8px 0;
      }

      .form-tip {
        font-size: 14px;
        color: #94a3b8;
        margin: 0;
      }
    }

    .login-form {
      :deep(.el-input__wrapper) {
        height: 48px;
        border-radius: 6px;
        box-shadow: 0 0 0 1px #e2e8f0;
        transition: all 0.25s;

        &:hover {
          box-shadow: 0 0 0 1px #cbd5e1;
        }

        &.is-focus {
          box-shadow: 0 0 0 2px #3b82f6;
        }
      }

      :deep(.el-input__prefix) {
        color: #94a3b8;
        font-size: 18px;
      }

      .form-options {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 24px;

        :deep(.el-checkbox__label) {
          font-size: 13px;
          color: #64748b;
        }
      }

      .login-btn {
        width: 100%;
        height: 48px;
        border-radius: 6px;
        font-size: 16px;
        font-weight: 500;
        letter-spacing: 4px;
        border: none;
        background: #2563eb;
        box-shadow: none;
        transition: background-color 0.16s ease;

        &:hover {
          background: #1d4ed8;
        }

        &:active {
          background: #1e40af;
        }
      }
    }

    .form-footer {
      margin-top: 40px;
      text-align: center;
      font-size: 12px;
      color: #cbd5e1;
    }
  }
}

// ====================== 动画 ======================
@keyframes floatCircle {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(20px, -20px); }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

// ====================== 响应式适配 ======================
@media (max-width: 960px) {
  .login-container {
    flex-direction: column;
  }

  .brand-side {
    min-height: 220px;
    flex: none;

    .brand-content {
      padding: 0 20px;

      .brand-title {
        font-size: 28px;
      }

      .brand-subtitle {
        font-size: 14px;
        letter-spacing: 4px;
      }

      .brand-divider {
        margin: 16px auto;
      }

      .brand-desc {
        display: none;
      }

      .brand-features {
        display: none;
      }
    }
  }

  .form-side {
    width: 100%;
    flex: 1;
    padding: 20px;

    .form-wrapper {
      width: 100%;
      max-width: 360px;
    }
  }
}

@media (max-width: 480px) {
  .brand-side {
    min-height: 160px;

    .brand-content {
      .brand-logo {
        width: 64px;
        height: 64px;
        margin-bottom: 16px;

        .el-icon {
          font-size: 32px;
        }
      }

      .brand-title {
        font-size: 22px;
      }
    }
  }

  .form-side .form-wrapper .form-header {
    margin-bottom: 24px;

    .form-title {
      font-size: 22px;
    }
  }
}
</style>
