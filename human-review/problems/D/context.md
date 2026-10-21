1. 共同定义调用者、目标用户、用户所属公司/部门、角色所属公司、指派适用部门、账号/角色状态、selfOnly、SA旁路与指派回收
2. 页面 → API封装 → 契约权限 → Controller → Service/helper → Mapper/SQL → 提交/回滚 → 返回与刷新完整追踪
3. 优先推演本人/同级/上级/SA、兄弟部门/其他公司、停用/删除/过期、调岗、先缓存后撤权、重复提交、中途失败与请求乱序
## D 角色权限与权限目录

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java)

角色与角色权限D；batchAssign/batchRevoke C；重点：完整阅读参数、字段、调用方及状态变化

- [RoleController](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L36)
- [list](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L42)
- [create](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L49)
- [detail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L54)
- [update](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L59)
- [delete](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L65)
- [users](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L71)
- [grant](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L78)
- [ungrant](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L84)
- [batchAssign](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L91)
- [batchRevoke](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L96)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java)

目录D；组织E；菜单F；重点：完整阅读参数、字段、调用方及状态变化

- [CatalogDtos](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L8)
- [ModuleView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L11)
- [SystemView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L14)
- [ResourceView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L17)
- [ActionView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L21)
- [PermissionView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L24)
- [CreateResource](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L29)
- [CreatePermission](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L33)
- [MenuNode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L36)
- [SaveMenu](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L41)
- [CompanyView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L45)
- [DeptNode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java#L48)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java)

完整阅读参数、字段、调用方及状态变化

- [RoleDtos](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java#L9)
- [RoleView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java#L13)
- [CreateRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java#L18)
- [UpdateRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java#L21)
- [RolePermissionView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java#L24)
- [RoleDetail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java#L29)
- [GrantPermissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java#L32)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Action.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Action.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/FunctionModule.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/FunctionModule.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Permission.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Permission.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Resource.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Resource.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Role.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Role.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/RolePermission.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/RolePermission.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/SoftwareSystem.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/SoftwareSystem.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findByActionCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java#L11)
- [findAllByOrderBySortOrderAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java#L13)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findBySystemIdAndModuleCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java#L11)
- [findBySystemIdOrderBySortOrderAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java#L13)
- [findAllByOrderBySystemIdAscSortOrderAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java#L15)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findByPermissionCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java#L16)
- [existsByResourceIdAndActionId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java#L18)
- [findByResourceId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java#L20)
- [findByResourceIdIn](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java#L22)
- [search](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java#L43)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findByResourceCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java#L15)
- [existsByResourceCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java#L17)
- [findByModuleIdOrderByIdAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java#L19)
- [search](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java#L40)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findByRoleCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java#L12)
- [existsByRoleCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java#L14)
- [findAllByOrderByIdAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java#L16)
- [countUsersByRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java#L20)
- [countPermissionsByRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java#L24)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java)

手工维护D；导入同步E；指派授权上界C；重点：完整阅读参数、字段、调用方及状态变化

- [findByRoleIdAndPermissionId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java#L13)
- [existsByRoleIdAndPermissionId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java#L15)
- [countByRoleId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java#L17)
- [findPermissionCodesOfRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java#L21)
- [findGrantScopesOfRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java#L25)
- [findBusinessGrantsOfRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java#L37)
- [findPermissionDetailsOfRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java#L46)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findBySystemCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java#L11)
- [findAllByOrderBySortOrderAscIdAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java#L13)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java)

目录查询、createResource、createPermission、addPermission、分页参数

- [CatalogService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L48)
- [systems](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L60)
- [resources](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L71)
- [actions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L81)
- [permissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L88)
- [createResource](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L99)
- [createPermission](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L136)
- [addPermission](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L148)
- [pageable](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L160)
- [blankToNull](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L164)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java)

list/detail/users、create/update/delete、grant/ungrant、requireCompanyLevel、firstNotHeld

- [RoleManagementService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L63)
- [list](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L81)
- [detail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L97)
- [users](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L111)
- [create](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L129)
- [update](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L150)
- [delete](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L182)
- [grant](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L202)
- [ungrant](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L246)
- [requireCompanyLevel](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L263)
- [firstNotHeld](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L281)
- [requireVisible](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L297)
- [companiesOf](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L307)
- [toCountMap](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L315)
- [toView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L321)

# 后端测试断言审查分工
| 主审 | 测试方法 | 阅读 | 执行 | 分支/断言/结果证据 |
| --- | --- | --- | --- | --- |
| D | [AcceptanceTest.java.t13_changingRolePermissionsAffectsAllHoldersOnTheNextRequest](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L311) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.roleLifecycleAndPermissions](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L264) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.roleHoldersAreLimitedToManagementScopeWithConsistentTotals](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L313) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.companyAdminManagesOnlyCompanyRoles](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L329) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.catalogResourcesActionsPermissions](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L431) | 未读 | 未执行 | 待填写 |