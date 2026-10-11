import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * v-perm="'rbac:user:delete'"：没有该权限时禁用按钮并提示缺少的权限码；
 * v-perm.hide：没有权限时隐藏。
 * 默认禁用而不是隐藏：按钮直接消失时，用户分不清是功能不存在还是自己没权限（docs/05-ui.md §4.1）。
 * 这只是体验：目标对象是否在管理范围内，以后端的 403 为准。
 */
function apply(el: HTMLElement, binding: DirectiveBinding<string>) {
  const allowed = useAuthStore().has(binding.value)
  if (binding.modifiers.hide) {
    el.style.display = allowed ? '' : 'none'
    return
  }
  const target = (el.tagName === 'BUTTON' ? el : el.querySelector('button')) as HTMLButtonElement | null
  if (allowed) {
    // 只撤销本指令自己加的禁用，不覆盖按钮原本的 :disabled
    if (el.classList.contains('perm-denied')) {
      el.removeAttribute('title')
      el.classList.remove('perm-denied')
      if (target) {
        target.disabled = false
        target.classList.remove('is-disabled')
      }
    }
  } else {
    el.setAttribute('title', `缺少权限 ${binding.value}`)
    el.classList.add('perm-denied')
    if (target) {
      target.disabled = true
      target.classList.add('is-disabled')
    }
  }
}

export const vPerm: Directive<HTMLElement, string> = {
  mounted: apply,
  updated: apply,
}
