# 共享边界与交叉审查

## 按方法划分共享文件

| 文件 | 责任 |
| --- | --- |
| AuthController | A认证，B鉴权解释 |
| RoleController | D角色，C批量指派 |
| CatalogController | D目录，E组织导入，F菜单 |
| UserMapper | C用户编号，A登录，E导入 |
| UserRoleMapper | C写入和SA计数，B授权展开，D持有者 |
| RolePermissionMapper | D手工维护，E导入同步，C上界 |
| CatalogDtos | D目录，E组织，F菜单 |
| StatsDtos | B请求，F统计 |
| UserDetailPanel | C详情/撤权，A个人中心/刷新 |
| api/index和types | 各人业务段，F公共部分 |

## 八个版本更新调用入口

| 主审 | 入口 |
| --- | --- |
| C | AssignmentService.assign、revoke、batchAssign、batchRevoke |
| D | RoleManagementService.update、grant、ungrant |
| E | ImportService.importPermissions |
| B | 统一复核版本、缓存、事务边界 |
| F | 复核审计与最终状态证据 |

逐个记录：影响的授权数据、版本更新位置、是否同一事务、异常后状态、缓存可见性。另反向查找改变有效权限却没有更新版本的写入路径，不能只检查已有调用。

## 固定交叉会议

| 组合 | 必须核对 |
| --- | --- |
| A+B+C | 自我撤权入口权限、范围例外、身份与页面刷新 |
| B+C+D+E | 授权上界、全部写入、版本和事务一致性 |
| C+E+F | 内部编号、升级、导入和测试自增重置 |
| B+F+各业务人 | 范围拒绝、失败审计、独立事务和统计 |
| 全员 | 组合操作、规则疑点与最终问题分类 |

每次会议填写[复核记录](cross-review.md)，复核者必须尝试反驳：是否已有前置检查、是否只在某项业务假设下成立、是否存在反例。不能代替他人签字。
