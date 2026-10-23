# 后端测试断言审查分工

69 个测试方法按当前实际声明列出。主审人检查独立预期、目标分支、失败后数据及范围外数据；跨业务方法由相关人核对自己片段，F 统筹公共结构和报告。阅读状态与执行状态分别填写。

| 主审 | 测试方法 | 阅读 | 执行 | 分支/断言/结果证据 |
| --- | --- | --- | --- | --- |
| A | [AcceptanceTest.java.t01_loginWithCorrectCredentialsGivesUsableToken](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L35) | 未读 | 未执行 | 待填写 |
| A | [AcceptanceTest.java.t02_wrongPasswordDisabledAndDeletedAccountsCannotLogin](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L44) | 未读 | 未执行 | 待填写 |
| A | [AcceptanceTest.java.t17_tokenIsRejectedAfterLogout](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L440) | 未读 | 未执行 | 待填写 |
| A | [ManagementApiTest.java.firstLoginMustChangePasswordAndOldTokensDie](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L26) | 未读 | 未执行 | 待填写 |
| A | [ManagementApiTest.java.fiveFailuresLockTheAccountEvenWhenConcurrent](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L45) | 未读 | 未执行 | 待填写 |
| A | [ManagementApiTest.java.invalidTokensAreRejected](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L73) | 未读 | 未执行 | 待填写 |
| A | [ManagementApiTest.java.undeclaredRoutesAreDeniedByDefault](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L630) | 未读 | 未执行 | 待填写 |
| A | [RedisFailureTest.java.revocationLookupFailsClosed](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L59) | 未读 | 未执行 | 待填写 |
| A | [RedisFailureTest.java.revocationLookupRetriesOnceBeforeFailingClosed](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L66) | 未读 | 未执行 | 待填写 |
| A | [RedisFailureTest.java.revocationOfExpiredTokenIsNotStored](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L74) | 未读 | 未执行 | 待填写 |
| B | [AcceptanceTest.java.t04_userWithoutRolesIsDeniedByDefault](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L106) | 已读 | 通过 | 默认拒绝/MISSING_PERMISSION；字段与专项审计缺口见 B 报告 §7.8 |
| B | [AcceptanceTest.java.t05_roleWithoutTargetPermissionIsDenied](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L116) | 已读 | 通过 | 精确权限匹配；approve 未直接断言 allowed=false |
| B | [AcceptanceTest.java.t06_multipleRolesAreUnioned](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L126) | 已读 | 通过 | 多角色权限并集；matched 与第二次 cacheHit 未断言 |
| B | [AcceptanceTest.java.t07_permissionInDepartmentADoesNotReachDepartmentB](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L137) | 已读 | 通过 | 本部门/下级允许，兄弟分支 OUT_OF_DATA_SCOPE |
| B | [AcceptanceTest.java.t07b_scopeBelongsToTheAssignmentNotToTheUnionOfRoles](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L152) | 已读 | 通过 | 能阻止跨指派拼接；reason/matched 未断言 |
| B | [AcceptanceTest.java.t11_superAdminBypassesEngineButMustBeActive](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L251) | 已读（需重写） | 通过 | 状态先于旁路；第二 SA 场景与唯一 root 规则冲突，见 HR-B-008 |
| B | [AcceptanceTest.java.t12_revokedRoleIsDeniedOnTheNextRequest](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L269) | 已读 | 通过 | 预热缓存后真实回收，下一次拒绝 |
| B | [AcceptanceTest.java.t12b_revocationDoesNotDependOnRedis](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L283) | 已读 | 通过 | 旧键保留、版本 +1、下一次 miss；未真实停 Redis |
| B | [AcceptanceTest.java.t20_explainAndCheckAlwaysAgree](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L509) | 已读 | 通过 | 七类结果一致；只比较彼此相等，缺逐案预期 |
| B | [AcceptanceTest.java.t20b_ordinaryUserCannotProbeOthersThroughAuthzApis](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L540) | 已读 | 通过 | 已知他人 403；未知主体、防探测审计和合法代查未覆盖 |
| B | [AcceptanceTest.java.t22_anotherReplicaSeesTheRevocationOnItsNextRequest](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L554) | 已读 | 通过 | 第二应用副本下一次拒绝；并发交错未覆盖 |
| B | [RedisFailureTest.java.grantCacheTreatsRedisFailureAsMiss](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L35) | 已读 | 通过 | Mapper 读写故障降级；缺端到端真宕机和 TTL 断言 |
| B | [RedisFailureTest.java.grantCacheRoundTripAndCorruptValue](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L47) | 已读 | 通过 | 空 GrantSet、坏 JSON、版本键；坏键不删除 |
| C | [AcceptanceTest.java.t03_temporaryUserHasNoEmployeeInfoAndIsDisabledByHand](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L69) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t08_adminAssignsRoleToSubordinate](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L165) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t09_adminCannotGrantToAnotherBranchAndItIsAudited](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L176) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t10_adminCannotGrantRoleWithPermissionsTheyLack](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L197) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t10b_selfOnlyGrantDoesNotLetAdminGrantTheFullPermission](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L216) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t14_removingAnAdminDoesNotCascadeToWhatTheyGranted](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L334) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t14b_adminCanRevokeTheirOwnAssignments](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L356) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t15_duplicateAssignmentCreatesNoSecondRecord](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L387) | 未读 | 未执行 | 待填写 |
| C | [AcceptanceTest.java.t16_batchWithOutOfScopeUsersFollowsTheDeclaredMode](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L402) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.userCrudWithinManagementScope](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L88) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.temporaryUserCannotGetAPhoneLater](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L163) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.internalAccountsUseTheReservedIdRange](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L175) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.batchStatusReportsPerUserResults](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L206) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.onlySuperAdminMayTouchASuperAdmin](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L225) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.userDetailShowsRolesAndEffectivePermissions](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L244) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.batchAssignByCompanyAndDepartmentThenRevoke](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L359) | 未读 | 未执行 | 待填写 |
| C | [ManagementApiTest.java.outOfScopeRevokeAndDisabledRoleAreRefused](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L409) | 未读 | 未执行 | 待填写 |
| C（B关联） | [ManagementApiTest.java.assignmentsMayCrossCompaniesWithinManagementScope](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L363) | 已读（B片段） | 通过 | 跨公司 scope、授权上界、本人 JWT 与代查边界；Decision/audit 字段缺口见 B 报告 §7.9 |
| D | [AcceptanceTest.java.t13_changingRolePermissionsAffectsAllHoldersOnTheNextRequest](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L311) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.roleLifecycleAndPermissions](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L264) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.roleHoldersAreLimitedToManagementScopeWithConsistentTotals](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L313) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.companyAdminManagesOnlyCompanyRoles](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L329) | 未读 | 未执行 | 待填写 |
| D | [ManagementApiTest.java.catalogResourcesActionsPermissions](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L431) | 未读 | 未执行 | 待填写 |
| E | [AcceptanceTest.java.t19_sameNamedDepartmentsInTwoCompaniesStaySeparate](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L472) | 未读 | 未执行 | 待填写 |
| E | [ExcelImportIntegrationTest.java.t21_importsOrganizationWithCorrectDepartmentOwnership](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L33) | 未读 | 未执行 | 待填写 |
| E | [ExcelImportIntegrationTest.java.t21_importsPermissionMatrixWithNormalizationAndAnomalies](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L111) | 未读 | 未执行 | 待填写 |
| E | [ExcelImportIntegrationTest.java.reimportingPermissionMatrixRevokesRemovedGrantsAndUpdatesSelfOnly](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L164) | 未读 | 未执行 | 待填写 |
| E | [ExcelImportIntegrationTest.java.rejectsWrongFiles](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L261) | 未读 | 未执行 | 待填写 |
| E | [ManagementApiTest.java.organizationTreeIsLimitedToScope](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L500) | 未读 | 未执行 | 待填写 |
| F | [ArchitectureTest.java.onlyTheThreeLayersAndHelpers](../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L30) | 未读 | 未执行 | 待填写 |
| F | [ArchitectureTest.java.dependenciesPointDownward](../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L39) | 未读 | 未执行 | 待填写 |
| F | [ArchitectureTest.java.noMyBatis](../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L56) | 未读 | 未执行 | 待填写 |
| F | [ArchitectureTest.java.entitiesAreDataOnly](../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L63) | 未读 | 未执行 | 待填写 |
| F | [ArchitectureTest.java.servicesAreStateless](../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L73) | 未读 | 未执行 | 待填写 |
| F | [ArchitectureTest.java.servicesDoNotInherit](../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L82) | 未读 | 未执行 | 待填写 |
| F | [ContractRoutesTest.java.readsAllOperationsOfTheContract](../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L30) | 未读 | 未执行 | 待填写 |
| F | [ContractRoutesTest.java.onlyLoginIsAnonymous](../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L35) | 未读 | 未执行 | 待填写 |
| F | [ContractRoutesTest.java.everyOperationDeclaresPermissionUnlessAnonymous](../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L44) | 未读 | 未执行 | 待填写 |
| F | [ContractRoutesTest.java.permissionCodesExistInBaselineMigration](../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L60) | 未读 | 未执行 | 待填写 |
| F | [ContractRoutesTest.java.unimplementedReportsContractOnlyRoutes](../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L73) | 未读 | 未执行 | 待填写 |
| F | [ContractRoutesTest.java.undeclaredReportsMissingAndPermissionlessRoutes](../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L81) | 未读 | 未执行 | 待填写 |
| F | [AcceptanceTest.java.t18_hiddenMenuDoesNotMeanTheApiIsOpen](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L451) | 未读 | 未执行 | 待填写 |
| F | [ManagementApiTest.java.menuManagementAndDynamicMenus](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L467) | 未读 | 未执行 | 待填写 |
| F | [ManagementApiTest.java.auditDashboardAndHeatmapComeFromRealEvents](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L520) | 未读 | 未执行 | 待填写 |
| F | [ManagementApiTest.java.abnormalAuditFilterMatchesDashboardCount](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L559) | 未读 | 未执行 | 待填写 |
| F | [ManagementApiTest.java.deniedAndFailedManagementOperationsAreAudited](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L587) | 未读 | 未执行 | 待填写 |
