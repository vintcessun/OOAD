# 人工审查问题记录

问题按证据登记。任务书中的反例是待验证问题，不是已确认缺陷。使用[完整模板](templates.md)，关联API卡、代码、独立预期及复核结果。

| 编号 | 类型 | 优先级 | API/文件 | 主审 | 复核 | 证据 | 状态/后续任务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| HR-001 | 已确认缺陷 | P1 | `POST /users/{id}/roles`、`POST /assignments/batch`；`AssignmentService.checkOne` | C | 待填 | [problems/C](problems/C/problems.md)；修复前超级管理员跨公司授权 400 `CROSS_COMPANY`；修复后 `ManagementApiTest.assignmentsMayCrossCompaniesWithinManagementScope`、`AcceptanceTest.t19_…` 通过 | 已修复，待复核：取消「适用部门须与用户同公司」，管理范围与授权上界不变（docs/12 R-20，docs/15 DBG-5） |

规则待确认必须指定团队决策人。未复现或缺少业务依据的问题保留条件，不直接标记为已确认缺陷。
