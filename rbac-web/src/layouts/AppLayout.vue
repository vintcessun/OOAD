<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">统一授权中心</div>
      <el-menu :default-active="activePath" :default-openeds="openeds" router background-color="#1f2d3d" text-color="#bfcbd9"
               active-text-color="#409EFF" data-test="side-menu">
        <template v-for="m in auth.menus" :key="m.id">
          <el-sub-menu v-if="m.children.length" :index="'dir-' + m.id">
            <template #title>
              <el-icon><component :is="iconOf(m.icon)" /></el-icon>
              <span>{{ m.menuName }}</span>
            </template>
            <el-menu-item v-for="c in m.children" :key="c.id" :index="c.routePath ?? ''">
              <el-icon><component :is="iconOf(c.icon)" /></el-icon>
              <span>{{ c.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="m.routePath ?? ''">
            <el-icon><component :is="iconOf(m.icon)" /></el-icon>
            <span>{{ m.menuName }}</span>
          </el-menu-item>
        </template>
      </el-menu>
      <div v-if="auth.menus.length === 0" class="empty-menu">没有可用的管理菜单</div>
    </el-aside>
    <el-container>
      <el-header class="header">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="section">{{ section }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta.title }}</el-breadcrumb-item>
        </el-breadcrumb>
        <el-dropdown @command="onCommand">
          <span class="user" data-test="user-menu">
            <el-icon><User /></el-icon>
            {{ auth.me?.user.username }}<span v-if="auth.superAdmin" class="muted">（超级管理员）</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="me">个人中心</el-dropdown-item>
              <el-dropdown-item command="password">修改口令</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view :key="route.fullPath" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

// 菜单图标沿用 sys_menu.icon 的取值，映射到 Element Plus 图标
const ICONS: Record<string, string> = {
  odometer: 'Odometer', setting: 'Setting', user: 'User', avatar: 'Avatar', key: 'Key', menu: 'Menu',
  document: 'Document', office: 'OfficeBuilding', search: 'Search', grid: 'Grid',
}
const iconOf = (icon: string | null) => ICONS[icon ?? ''] ?? 'Document'

const activePath = computed(() => {
  // 详情页高亮所属的列表菜单
  if (route.path.startsWith('/users/')) return '/users'
  if (route.path.startsWith('/roles/')) return '/roles'
  return route.path
})

// 目录默认全部展开：菜单不多，展开后一眼能看到自己有哪些功能
const openeds = computed(() => auth.menus.filter((m) => m.children.length).map((m) => 'dir-' + m.id))

const section = computed(() => {
  for (const m of auth.menus) {
    if (m.children.some((c) => c.routePath === activePath.value)) return m.menuName
  }
  return ''
})

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await auth.logout()
    router.push('/login')
  } else {
    router.push('/' + cmd)
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #1f2d3d;
  overflow-y: auto;
}
.aside .el-menu {
  border-right: none;
}
.logo {
  color: #fff;
  font-weight: 600;
  font-size: 17px;
  padding: 18px 20px;
}
.empty-menu {
  color: #8391a5;
  padding: 0 20px;
  font-size: 13px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
}
.user {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
</style>
