<template>
  <el-result icon="warning" title="403 无权限" data-test="forbidden">
    <template #sub-title>
      <p>你没有访问这个页面的权限，或页面不存在。</p>
      <p class="muted">菜单只显示你有权限的页面；即使直接访问接口，后端同样会拒绝。</p>
    </template>
    <template #extra>
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
    </template>
  </el-result>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>
