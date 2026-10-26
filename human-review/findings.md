# 人工审查问题记录

问题按证据登记。任务书中的反例是待验证问题，不是已确认缺陷。使用[完整模板](templates.md)，关联API卡、代码、独立预期及复核结果。

| 编号 | 类型 | 优先级 | API/文件 | 主审 | 复核 | 证据 | 状态/后续任务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| HR-001 | 已确认缺陷 | P1 | `POST /users/{id}/roles`、`POST /assignments/batch`；`AssignmentService.checkOne` | C | 待填 | [problems/C](problems/C/problems.md)；修复前超级管理员跨公司授权 400 `CROSS_COMPANY`；修复后 `ManagementApiTest.assignmentsMayCrossCompaniesWithinManagementScope`、`AcceptanceTest.t19_…` 通过 | 已修复，待复核：取消「适用部门须与用户同公司」，管理范围与授权上界不变（docs/12 R-20，docs/15 DBG-5） |
| HR-002 | 测试缺口、待复现问题 | P2 待定 | E03；`ImportService.readSheet/importDepartments/importEmployees/linkManagers` | E | 待填 | [E 代码证据](problems/E/E-review-20261026.md#hr-002组织导入的异常行号可能不指向原始-excel-行)；标题行或空白行后仍以 `i+2` 报错位 | 未运行复现；需保留原始工作表行号并补断言 |
| HR-003 | 测试缺口、待复现问题 | P2 待定 | E03；`ImportService.importEmployees/linkManagers` | E | 待填 | [E 代码证据](problems/E/E-review-20261026.md#hr-003重复导入不能用已有员工更新部门负责人关联)；只从本次新建员工找负责人 | 未运行复现；先明确重复导入更新口径，核对旧 `manager_user_id` |
| HR-004 | 规则待确认、测试缺口 | P1 待定 | E04；`ImportService.resource` | E | 待填 | [E 代码证据](problems/E/E-review-20261026.md#hr-004同名功能点换序时缺少稳定资源身份)；同名资源按出现次序认领，现有测试仅覆盖不同名删行 | 先确认清单同名业务语义，再用虚构清单复现，核对权限与资源对应关系 |

规则待确认必须指定团队决策人。未复现或缺少业务依据的问题保留条件，不直接标记为已确认缺陷。
