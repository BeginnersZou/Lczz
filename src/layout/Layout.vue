<template>
  <el-container class="layout-container">
    <el-aside :width="asideWidth" class="sidebar" :class="{ 'mobile-open': mobileMenuOpen }">
      <div class="logo">
        <el-icon :size="22" class="logo-icon">
          <HomeFilled />
        </el-icon>
        <span v-show="!isCollapse" class="logo-text">力创之尊业务系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :unique-opened="true"
        router
        class="sidebar-menu"
        background-color="#101828"
        text-color="#a8b3c7"
        active-text-color="#fff"
        @select="handleMenuSelect"
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-sub-menu v-if="item.children?.length" :index="item.path">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.meta.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              <el-icon><component :is="child.icon" /></el-icon>
              <span>{{ child.meta.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.meta.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <div v-if="isMobile && mobileMenuOpen" class="sidebar-mask" @click="mobileMenuOpen = false"></div>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button
            type="text"
            @click="handleSidebarToggle"
            class="collapse-btn"
            :aria-label="isMobile ? '打开导航菜单' : (isCollapse ? '展开导航菜单' : '收起导航菜单')"
          >
            <el-icon :size="20">
              <Fold v-if="!isMobile && !isCollapse" />
              <Expand v-else />
            </el-icon>
          </el-button>
          <el-breadcrumb class="breadcrumb" separator="/">
            <el-breadcrumb-item v-for="(crumb, index) in breadcrumbs" :key="index">
              <router-link v-if="crumb.path" :to="crumb.path">{{ crumb.title }}</router-link>
              <span v-else>{{ crumb.title }}</span>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon class="user-icon"><User /></el-icon>
              <span>{{ userStore.userName }}</span>
              <el-icon class="arrow-icon"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import menuItems from '@/router/menu'
import {
  HomeFilled,
  Fold,
  Expand,
  User,
  ArrowDown,
  SwitchButton
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(localStorage.getItem('sidebarCollapsed') === 'true')
const isMobile = ref(window.innerWidth <= 900)
const mobileMenuOpen = ref(false)
const asideWidth = computed(() => isMobile.value ? '216px' : (isCollapse.value ? '64px' : '216px'))

watch(isCollapse, value => localStorage.setItem('sidebarCollapsed', String(value)))

function updateViewport() {
  isMobile.value = window.innerWidth <= 900
  if (!isMobile.value) mobileMenuOpen.value = false
}

function handleSidebarToggle() {
  if (isMobile.value) {
    mobileMenuOpen.value = !mobileMenuOpen.value
  } else {
    isCollapse.value = !isCollapse.value
  }
}

function handleMenuSelect() {
  if (isMobile.value) mobileMenuOpen.value = false
}

onMounted(() => window.addEventListener('resize', updateViewport))
onBeforeUnmount(() => window.removeEventListener('resize', updateViewport))

const activeMenu = computed(() => {
  const currentPath = route.path
  // 从扁平菜单项中找到当前路径所属的父级菜单（当前路径以菜单项 path 为前缀）
  // 按路径长度倒序，确保最长前缀匹配优先，避免 '/' 这类短路径误匹配
  const matched = menuItems
    .filter(item => currentPath === item.path || currentPath.startsWith(item.path + '/'))
    .sort((a, b) => b.path.length - a.path.length)
  return matched.length > 0 ? matched[0].path : currentPath
})

const breadcrumbs = computed(() => {
  const title = route.meta?.title || ''
  const parent = menuItems
    .filter(item => route.path === item.path || route.path.startsWith(item.path + '/'))
    .sort((a, b) => b.path.length - a.path.length)[0]
  if (!parent || parent.meta.title === title) return title ? [{ title, path: '' }] : []
  return [
    { title: parent.meta.title, path: parent.path },
    { title, path: '' }
  ]
})

async function handleCommand(command) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
  background: var(--surface-page);
}

.sidebar {
  background: #101828;
  transition: width 0.22s ease;
  overflow: hidden;
  box-shadow: none;
  border-right: 1px solid rgba(255, 255, 255, 0.04);

  .logo {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 56px;
    padding: 0 16px;
    background: #101828;
    border-bottom: 1px solid rgba(255, 255, 255, 0.07);

    .logo-icon {
      flex-shrink: 0;
      color: #60a5fa;
    }

    .logo-text {
      margin-left: 9px;
      font-size: 16px;
      font-weight: 600;
      color: #fff;
      white-space: nowrap;
      letter-spacing: 0;
    }
  }

  .sidebar-menu {
    border-right: none;
    height: calc(100vh - 56px);
    overflow-y: auto;
    padding: 10px 0;

    // 收起态下取消内边距，让图标居中更自然
    &.el-menu--collapse {
      padding: 10px 0;
    }

    :deep(.el-menu-item) {
      gap: 11px;
      margin: 2px 8px;
      height: 40px;
      line-height: 40px;
      border-radius: 6px;
      // 展开态：图标+文字左对齐
      justify-content: flex-start;
      padding-left: 14px !important;
      padding-right: 12px !important;
      transition: background-color 0.2s ease, color 0.2s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.055) !important;
        color: #e9eef7 !important;
      }
    }

    // 选中态：克制的大厂风格——左侧指示条 + 半透明高亮背景，替代花哨渐变
    :deep(.el-menu-item.is-active) {
      background: rgba(37, 99, 235, 0.2) !important;
      color: #fff !important;
      position: relative;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 20px;
        background: #60a5fa;
        border-radius: 0 3px 3px 0;
      }
    }

    :deep(.el-menu-item .el-icon) {
      width: 16px;
      margin-right: 0;
      color: #8fa0b8;
      font-size: 16px;
      transition: color 0.2s ease;
    }

    :deep(.el-menu-item:hover .el-icon) {
      color: #cbd5e1;
    }

    :deep(.el-menu-item.is-active .el-icon) {
      color: #7db5ff;
    }

    :deep(.el-menu-item span) {
      font-size: 14px;
      line-height: 20px;
      font-weight: 400;
      letter-spacing: 0;
    }

    :deep(.el-menu-item.is-active span) {
      font-weight: 600;
    }

    // 二级子菜单样式保持一致
    :deep(.el-sub-menu .el-sub-menu__title) {
      gap: 11px;
      margin: 2px 8px;
      height: 40px;
      line-height: 40px;
      border-radius: 6px;
      justify-content: flex-start;
      padding-left: 14px !important;
      padding-right: 12px !important;
      transition: background-color 0.2s ease, color 0.2s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.055) !important;
        color: #e9eef7 !important;
      }
    }

    :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
      background: rgba(37, 99, 235, 0.2) !important;
      color: #fff !important;
      position: relative;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 20px;
        background: #60a5fa;
        border-radius: 0 3px 3px 0;
      }
    }

    :deep(.el-sub-menu .el-sub-menu__title .el-icon) {
      width: 16px;
      margin-right: 0;
      color: #8fa0b8;
      font-size: 16px;
      transition: color 0.2s ease;
    }

    :deep(.el-sub-menu.is-active > .el-sub-menu__title .el-icon) {
      color: #7db5ff;
    }

    :deep(.el-sub-menu .el-sub-menu__title span) {
      font-size: 14px;
      line-height: 20px;
      font-weight: 400;
    }

    :deep(.el-sub-menu.is-active > .el-sub-menu__title span) {
      font-weight: 600;
    }

    // 收起态：图标居中对齐，覆盖展开态 padding 以保证居中
    :deep(.el-menu--collapse .el-menu-item) {
      justify-content: center;
      gap: 0;
      margin: 2px 8px;
      padding: 0 !important;
    }

    :deep(.el-menu--collapse .el-sub-menu__title) {
      justify-content: center;
      gap: 0;
      margin: 2px 8px;
      padding: 0 !important;
    }

    ::-webkit-scrollbar {
      width: 4px;
    }

    ::-webkit-scrollbar-thumb {
      background-color: #334155;
      border-radius: 2px;
    }
  }
}

.sidebar-mask {
  display: none;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  height: 56px;
  padding: 0 24px;
  border-bottom: 1px solid var(--border-default);
  box-shadow: none;

  .header-left {
    display: flex;
    align-items: center;

    .collapse-btn {
      width: 32px;
      height: 32px;
      margin-right: 12px;
      padding: 0;
      color: var(--text-secondary);

      &:hover {
        color: var(--brand-primary);
        background: var(--brand-primary-soft);
      }
    }

    .breadcrumb {
      font-size: 14px;

      :deep(.el-breadcrumb__item) {
        .el-breadcrumb__inner {
          color: var(--text-secondary);
        }
        .el-breadcrumb__inner.is-link {
          color: var(--brand-primary);

          &:hover {
            color: #2563eb;
          }
        }
      }
    }
  }

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      cursor: pointer;
      height: 36px;
      padding: 0 10px;
      border-radius: 6px;
      transition: all 0.2s;

      &:hover {
        background: var(--surface-subtle);
      }

      .user-icon {
        width: 28px;
        height: 28px;
        margin-right: 8px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: var(--brand-primary);
        background: var(--brand-primary-soft);
        border-radius: 50%;
        font-size: 16px;
      }

      span {
        font-size: 14px;
        color: var(--text-primary);
        font-weight: 500;
      }

      .arrow-icon {
        margin-left: 8px;
        font-size: 16px;
        color: var(--text-tertiary);
      }
    }
  }
}

.main-content {
  padding: clamp(18px, 2vw, 28px);
  overflow-y: auto;
  background: var(--surface-page);

  > * {
    width: 100%;
    max-width: 1480px;
    margin-right: auto;
    margin-left: auto;
  }

  ::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  ::-webkit-scrollbar-thumb {
    background-color: #cbd5e1;
    border-radius: 4px;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.14s ease;
}

.fade-enter-from {
  opacity: 0;
}

.fade-leave-to {
  opacity: 0;
}

// 修改密码弹窗底部按钮
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    z-index: 2001;
    left: 0;
    top: 0;
    bottom: 0;
    transform: translateX(-100%);
    transition: transform 0.25s ease;

    &.mobile-open {
      transform: translateX(0);
    }
  }

  .sidebar-mask {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 2000;
    background: rgba(15, 23, 42, 0.46);
  }

  .header {
    padding: 0 16px;

    .header-left .collapse-btn {
      margin-right: 8px;
    }

    .breadcrumb {
      font-size: 14px;
    }

    .header-right .user-info {
      padding: 8px;

      > span,
      .arrow-icon {
        display: none;
      }
    }
  }

  .main-content {
    padding: 16px;
  }
}
</style>
