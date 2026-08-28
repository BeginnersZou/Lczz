import { onMounted, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'

/**
 * 统一拦截浏览器关闭、返回、侧边栏切换等离开行为，避免表单内容意外丢失。
 */
export function useUnsavedChanges(isDirty, message = '当前内容尚未保存，确定要离开吗？') {
  const handleBeforeUnload = (event) => {
    if (!isDirty.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onBeforeRouteLeave(async () => {
    if (!isDirty.value) return true
    try {
      await ElMessageBox.confirm(message, '未保存的更改', {
        confirmButtonText: '放弃并离开',
        cancelButtonText: '继续编辑',
        type: 'warning',
        distinguishCancelAndClose: true
      })
      return true
    } catch {
      return false
    }
  })

  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))
}
