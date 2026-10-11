<template>
  <el-card>
    <el-tabs v-model="tab">
      <!-- 资源 -->
      <el-tab-pane label="资源（Resource）" name="resources">
        <el-row :gutter="16">
          <el-col :span="6">
            <div class="muted">业务系统 / 模块（{{ systems.length }} 个系统）</div>
            <el-tree :data="systemTree" node-key="key" highlight-current :expand-on-click-node="false"
                     @node-click="pickSystem" data-test="system-tree" />
          </el-col>
          <el-col :span="18">
            <div class="toolbar">
              <el-select v-model="resQuery.type" placeholder="类型" clearable style="width: 120px" @change="loadResources(1)">
                <el-option v-for="(v, k) in RESOURCE_TYPE_LABEL" :key="k" :label="v" :value="k" />
              </el-select>
              <el-input v-model="resQuery.keyword" placeholder="编码 / 名称" clearable style="width: 200px"
                        @keyup.enter="loadResources(1)" />
              <el-button @click="loadResources(1)">查询</el-button>
              <span class="spacer" />
              <el-button v-perm="'rbac:permission:create'" type="primary" @click="resVisible = true">+ 新建资源</el-button>
            </div>
            <el-table :data="resources.items" data-test="resource-table">
              <el-table-column label="资源编码" width="170">
                <template #default="{ row }"><PermissionCode :code="row.resourceCode" /></template>
              </el-table-column>
              <el-table-column prop="resourceName" label="名称" />
              <el-table-column label="类型" width="70">
                <template #default="{ row }">{{ label(RESOURCE_TYPE_LABEL, row.resourceType) }}</template>
              </el-table-column>
              <el-table-column prop="moduleName" label="模块" width="140" />
              <el-table-column prop="systemName" label="业务系统" width="160" />
            </el-table>
            <div class="pager">
              <el-pagination v-model:current-page="resPage" :page-size="50" :total="resources.total"
                             layout="total, prev, pager, next" @current-change="loadResources()" />
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 操作 -->
      <el-tab-pane label="操作（Action）" name="actions">
        <el-table :data="actions" style="max-width: 520px" data-test="action-table">
          <el-table-column label="编码" width="140">
            <template #default="{ row }"><span class="mono">{{ row.actionCode }}</span></template>
          </el-table-column>
          <el-table-column prop="actionName" label="名称" />
        </el-table>
        <p class="muted">前五个（查看、新增、编辑、审批、导出）来自权限清单，「全部」展开为这五个；其余是权限中心自身的管理操作。</p>
      </el-tab-pane>

      <!-- 权限 -->
      <el-tab-pane label="权限（Permission = Resource × Action）" name="permissions">
        <div class="toolbar">
          <el-select v-model="permQuery.systemId" placeholder="业务系统" clearable style="width: 200px" @change="loadPermissions(1)">
            <el-option v-for="s in systems" :key="s.id" :label="s.systemName" :value="s.id" />
          </el-select>
          <el-input v-model="permQuery.keyword" placeholder="权限码 / 资源名" clearable style="width: 220px"
                    @keyup.enter="loadPermissions(1)" data-test="perm-keyword" />
          <el-button @click="loadPermissions(1)">查询</el-button>
          <span class="spacer" />
          <el-button v-perm="'rbac:permission:create'" type="primary" @click="permVisible = true">+ 为资源新增操作</el-button>
        </div>
        <el-table :data="permissions.items" data-test="permission-table">
          <el-table-column label="权限码" min-width="220">
            <template #default="{ row }"><PermissionCode :code="row.permissionCode" /></template>
          </el-table-column>
          <el-table-column prop="resourceName" label="资源" />
          <el-table-column prop="actionName" label="操作" width="80" />
          <el-table-column prop="systemName" label="业务系统" width="160" />
        </el-table>
        <div class="pager">
          <el-pagination v-model:current-page="permPage" :page-size="50" :total="permissions.total"
                         layout="total, prev, pager, next" @current-change="loadPermissions()" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="resVisible" title="新建资源" width="560px">
      <el-form label-width="90px">
        <el-form-item label="功能模块">
          <el-cascader v-model="newRes.modulePath" :options="moduleOptions" placeholder="业务系统 / 模块" />
        </el-form-item>
        <el-form-item label="资源编码">
          <el-input v-model="newRes.resourceCode" placeholder="以业务系统编码开头，如 oa:doc:receive" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="newRes.resourceName" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="newRes.resourceType">
            <el-radio v-for="(v, k) in RESOURCE_TYPE_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="newRes.description" /></el-form-item>
        <el-form-item label="操作">
          <el-checkbox-group v-model="newRes.actionCodes">
            <el-checkbox v-for="a in actions" :key="a.actionCode" :value="a.actionCode">{{ a.actionName }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resVisible = false">取消</el-button>
        <el-button type="primary" @click="createResource">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permVisible" title="为资源新增操作" width="480px">
      <el-form label-width="90px">
        <el-form-item label="资源编码">
          <el-input v-model="newPerm.resourceCode" placeholder="如 oa:m01:f02" />
        </el-form-item>
        <el-form-item label="操作">
          <el-select v-model="newPerm.actionCode">
            <el-option v-for="a in actions" :key="a.actionCode" :label="a.actionName" :value="a.actionCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" @click="createPermission">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { catalogApi } from '@/api'
import type { ActionView, Page, PermissionView, ResourceType, ResourceView, SystemView } from '@/api/types'
import { RESOURCE_TYPE_LABEL, label } from '@/utils/format'
import PermissionCode from '@/components/PermissionCode.vue'

/** 权限管理（docs/05-ui.md §3.6）：区分 Resource、Action、Permission 三个页签。 */
const tab = ref('resources')
const systems = ref<SystemView[]>([])
const actions = ref<ActionView[]>([])
const resQuery = reactive({ systemId: null as number | null, type: '' as ResourceType | '', keyword: '' })
const resPage = ref(1)
const resources = ref<Page<ResourceView>>({ items: [], total: 0, page: 0, size: 50 })
const permQuery = reactive({ systemId: undefined as number | undefined, keyword: '' })
const permPage = ref(1)
const permissions = ref<Page<PermissionView>>({ items: [], total: 0, page: 0, size: 50 })
const resVisible = ref(false)
const permVisible = ref(false)
const newRes = reactive({ modulePath: [] as number[], resourceCode: '', resourceName: '', resourceType: 'BUSINESS' as ResourceType,
  description: '', actionCodes: [] as string[] })
const newPerm = reactive({ resourceCode: '', actionCode: 'view' })

const systemTree = computed(() => systems.value.map((s) => ({
  key: 's' + s.id, label: `${s.systemName}（${s.systemCode}）`, systemId: s.id,
  children: s.modules.map((m) => ({ key: 'm' + m.id, label: `${m.moduleName}（${m.moduleCode}）`, systemId: s.id })),
})))
const moduleOptions = computed(() => systems.value.map((s) => ({
  value: s.id, label: s.systemName, children: s.modules.map((m) => ({ value: m.id, label: m.moduleName })),
})))

function pickSystem(node: { systemId: number }) {
  resQuery.systemId = node.systemId
  loadResources(1)
}

async function loadResources(p?: number) {
  if (p) resPage.value = p
  resources.value = await catalogApi.resources({ ...resQuery, page: resPage.value - 1, size: 50 })
}

async function loadPermissions(p?: number) {
  if (p) permPage.value = p
  permissions.value = await catalogApi.permissions({ ...permQuery, page: permPage.value - 1, size: 50 })
}

async function createResource() {
  await catalogApi.createResource({ moduleId: newRes.modulePath[newRes.modulePath.length - 1], resourceCode: newRes.resourceCode,
    resourceName: newRes.resourceName, resourceType: newRes.resourceType, description: newRes.description || undefined,
    actionCodes: newRes.actionCodes })
  ElMessage.success('已新建资源')
  resVisible.value = false
  systems.value = await catalogApi.systems()
  loadResources(1)
}

async function createPermission() {
  const found = await catalogApi.resources({ keyword: newPerm.resourceCode, size: 50 })
  const res = found.items.find((r) => r.resourceCode === newPerm.resourceCode)
  if (!res) {
    ElMessage.error('资源不存在')
    return
  }
  await catalogApi.createPermission(res.id, newPerm.actionCode)
  ElMessage.success('已新增权限')
  permVisible.value = false
  loadPermissions(1)
}

onMounted(async () => {
  ;[systems.value, actions.value] = await Promise.all([catalogApi.systems(), catalogApi.actions()])
  await Promise.all([loadResources(1), loadPermissions(1)])
})
</script>
