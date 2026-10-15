# 代码级阅读导航

按主审人列出当前Java声明与行号。声明定位不替代完整控制流分析；JPA继承方法、注解事务和SQL应继续人工追踪。共享文件的方法责任以任务书及共享边界为准。

## A 身份认证与请求入口

### [rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java)

认证和装配A；Clock、Redis与运行语义B/F；重点：完整阅读参数、字段、调用方及状态变化

- [clock](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java#L30)
- [passwordEncoder](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java#L35)
- [jwtKey](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java#L40)
- [redisKeepAlive](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java#L50)
- [contractRoutes](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java#L57)
- [permissionInterceptor](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java#L62)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java)

addInterceptors、verifyEveryRouteDeclared及/error排除

- [WebConfig](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java#L26)
- [addInterceptors](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java#L32)
- [verifyEveryRouteDeclared](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java#L41)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ApiResponse.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ApiResponse.java)

完整阅读参数、字段、调用方及状态变化

- [ok](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ApiResponse.java#L8)
- [fail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ApiResponse.java#L12)
- [fail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ApiResponse.java#L16)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java)

认证四入口A；check/explain B；重点：完整阅读参数、字段、调用方及状态变化

- [LoginRequest](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L23)
- [PasswordRequest](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L26)
- [AuthController](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L33)
- [login](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L41)
- [logout](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L47)
- [me](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L55)
- [changePassword](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L60)
- [check](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L68)
- [explain](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L75)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java)

load/fromDocument、路由匹配、undeclared/unimplemented及权限码

- [Route](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L24)
- [ContractRoutes](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L30)
- [load](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L34)
- [fromDocument](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L44)
- [key](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L63)
- [find](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L68)
- [undeclared](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L73)
- [unimplemented](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L87)
- [permissionCodes](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L95)
- [size](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java#L105)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/GlobalExceptionHandler.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/GlobalExceptionHandler.java)

完整阅读参数、字段、调用方及状态变化

- [biz](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/GlobalExceptionHandler.java#L22)
- [badRequest](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/GlobalExceptionHandler.java#L28)
- [conflict](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/GlobalExceptionHandler.java#L34)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java)

preHandle、首次改密、@self、bearerToken、clientIp

- [PermissionInterceptor](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java#L45)
- [preHandle](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java#L55)
- [bearerToken](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java#L90)
- [clientIp](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java#L96)
- [reject](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java#L104)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Operator.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Operator.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java)

完整阅读参数、字段、调用方及状态变化

- [TokenRevocationMapper](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java#L23)
- [revoke](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java#L27)
- [isRevoked](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java#L34)
- [key](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java#L48)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java)

login、authenticate、logout、changePassword、loginFailed、meetsPolicy

- [LoginResult](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L29)
- [Authenticated](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L34)
- [AuthenticationService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L44)
- [login](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L60)
- [authenticate](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L95)
- [logout](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L116)
- [changePassword](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L123)
- [meetsPolicy](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L142)
- [loginFailed](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L150)
- [unauthenticated](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L156)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java)

完整阅读参数、字段、调用方及状态变化

- [BizException](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java#L26)
- [httpStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java#L32)
- [code](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java#L36)
- [badRequest](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java#L40)
- [notFound](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java#L44)
- [forbidden](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java#L48)
- [conflict](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java#L52)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java)

issue、parse、时钟、签名及有效期

- [TokenClaims](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java#L23)
- [TokenService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java#L28)
- [issue](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java#L32)
- [parse](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java#L45)

## B 权限判定与缓存一致性

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Decision.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Decision.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantRow.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantRow.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantSet.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantSet.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Scope.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Scope.java)

完整阅读参数、字段、调用方及状态变化

- [isEmpty](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Scope.java#L14)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/GrantVersion.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/GrantVersion.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java)

带版本缓存键、读取、写入、损坏值与Redis故障

- [GrantCacheMapper](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java#L34)
- [find](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java#L41)
- [save](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java#L51)
- [key](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java#L59)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantVersionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantVersionMapper.java)

current、bump与事务内更新

- [current](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantVersionMapper.java#L15)
- [bump](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantVersionMapper.java#L21)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java)

判定、范围与失效B；C/D/E复核各自调用；审计副作用F；重点：decide、subjectDenyReason、covers、loadGrants、scopeOf、requireInScope、bumpGrantVersion

- [AuthorizationService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L60)
- [LoadedGrants](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L73)
- [decide](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L82)
- [subjectDenyReason](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L156)
- [covers](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L173)
- [loadGrants](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L189)
- [bumpGrantVersion](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L213)
- [inReadWriteTransaction](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L219)
- [isSuperAdmin](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L226)
- [scopeOf](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L231)
- [inScope](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L244)
- [requireInScope](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L253)
- [holdsSuperAdmin](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L266)
- [preferUnrestricted](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L271)
- [allow](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L275)
- [result](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L286)
- [describeAssignments](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L293)
- [note](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L299)
- [elapsedMicros](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L305)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java)

check、permissionOf、resolveSubject、denied

- [AuthzQueryService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L31)
- [check](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L37)
- [permissionOf](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L45)
- [resolveSubject](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L59)
- [denied](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L84)

## C 用户编号与角色指派

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java)

完整阅读参数、字段、调用方及状态变化

- [UserController](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L38)
- [list](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L44)
- [create](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L56)
- [detail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L61)
- [update](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L66)
- [changeStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L72)
- [delete](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L78)
- [batchStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L85)
- [roles](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L90)
- [assign](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L95)
- [revoke](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L101)
- [permissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L109)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java)

指派投影C；授权展开B；角色持有者D；重点：完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java)

完整阅读参数、字段、调用方及状态变化

- [BatchDtos](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java#L8)
- [BatchAssign](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java#L22)
- [Failure](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java#L26)
- [BatchResult](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java#L33)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/PageResult.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/PageResult.java)

完整阅读参数、字段、调用方及状态变化

- [of](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/PageResult.java#L11)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java)

用户输入输出C；当前用户身份A；重点：完整阅读参数、字段、调用方及状态变化

- [UserDtos](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L9)
- [UserView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L12)
- [CreateUser](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L19)
- [UpdateUser](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L24)
- [ChangeStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L29)
- [BatchStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L33)
- [EffectivePermission](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L36)
- [UserDetail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L41)
- [Assign](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java#L45)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/User.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/User.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/UserRole.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/UserRole.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)

用户与内部编号C；登录查询/失败计数/重置A；导入定位E；重点：完整阅读参数、字段、调用方及状态变化

- [findByUsername](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L17)
- [existsByUsername](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L19)
- [existsByEmployeeNo](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L21)
- [countByStatusNot](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L23)
- [findAllUsernames](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L26)
- [recordLoginFailure](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L39)
- [resetLoginFailure](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L43)
- [nextInternalId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L47)
- [insertInternal](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L61)
- [findActiveInSubtree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L68)
- [findActiveInDepartments](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L71)
- [findByRealNameInSubtree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L79)
- [findByCompanyIdAndRealNameAndStatusNot](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java#L81)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)

指派写入/删除/批量与有效SA计数C；授权展开B；持有者查询D；重点：完整阅读参数、字段、调用方及状态变化

- [findAssignmentsOfUser](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L29)
- [findAssignmentsOfRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L34)
- [findAllAssignmentsOfRole](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L38)
- [findGrantsOfUser](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L49)
- [existsByUserIdAndRoleIdAndScopeDeptId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L51)
- [findByRoleIdAndUserIdIn](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L53)
- [countByRoleId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L55)
- [countActiveSuperAdmins](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L63)
- [insertForUsers](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L75)
- [deleteByIds](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java#L84)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java)

assign/revoke、batchAssign/batchRevoke、context/checkOne/checkRevoke、escalation、resolveTargets、chunks

- [AssignmentService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L68)
- [Context](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L87)
- [listOfUser](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L95)
- [assign](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L106)
- [revoke](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L144)
- [checkRevoke](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L170)
- [batchAssign](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L192)
- [batchRevoke](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L257)
- [context](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L322)
- [checkOne](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L334)
- [escalation](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L375)
- [Targets](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L393)
- [resolveTargets](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L396)
- [dept](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L440)
- [deptName](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L447)
- [normalizeMode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L452)
- [chunks](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L463)
- [failureException](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L471)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java)

用户与编号C；当前用户detail关联A；初始口令与导入关联E；重点：list/buildFilter/deptSubquery、detail、create/insertInternal、validateTypeFields、update、applyStatus、requireManageable

- [UserManagementService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L64)
- [initialPasswordHash](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L79)
- [list](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L86)
- [buildFilter](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L104)
- [deptSubquery](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L135)
- [detail](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L150)
- [create](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L170)
- [insertInternal](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L212)
- [validateTypeFields](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L224)
- [update](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L249)
- [changeStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L291)
- [delete](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L304)
- [batchStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L316)
- [applyStatus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L342)
- [statusFailure](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L370)
- [requireUser](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L381)
- [requireNotDeleted](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L385)
- [requireManageable](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L391)
- [deptNames](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L400)
- [toView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L406)
- [isBlank](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L411)
- [blankToNull](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L415)

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

## E 组织导入与数据库模型

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java)

组织与导入E；系统资源操作权限D；菜单F；重点：完整阅读参数、字段、调用方及状态变化

- [CatalogController](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L48)
- [systems](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L58)
- [resources](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L63)
- [createResource](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L72)
- [actions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L78)
- [permissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L83)
- [createPermission](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L92)
- [myMenus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L100)
- [allMenus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L105)
- [createMenu](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L110)
- [updateMenu](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L115)
- [deleteMenu](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L121)
- [companies](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L129)
- [departmentTree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L134)
- [importOrganization](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L140)
- [importPermissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L153)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/ImportReport.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/ImportReport.java)

完整阅读参数、字段、调用方及状态变化

- [Anomaly](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/ImportReport.java#L15)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Company.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Company.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Department.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Department.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findByCompanyCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java#L10)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findByCompanyIdOrderByLevelAscSortOrderAscIdAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java#L13)
- [findByCompanyIdAndParentIdIsNull](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java#L15)
- [findByCompanyIdAndParentIdAndDeptName](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java#L17)
- [findPathById](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java#L20)
- [findSubtree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java#L23)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)

importOrganization/importDepartments/importEmployees、locate/linkManagers、importPermissions/revokeMissing/parseCell、roleTemplate/resource/readSheet/findHeaderRow

- [ImportService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L113)
- [importOrganization](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L140)
- [importDepartments](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L173)
- [importEmployees](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L248)
- [locate](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L306)
- [linkManagers](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L325)
- [importPermissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L369)
- [revokeMissing](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L543)
- [ParsedCell](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L563)
- [parseCell](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L570)
- [roleTemplate](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L607)
- [system](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L618)
- [module](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L633)
- [resource](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L653)
- [sysName](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L677)
- [readSheet](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L682)
- [findHeaderRow](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L717)
- [text](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L732)
- [cell](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L740)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java)

companies、tree、祖先展示与子树过滤

- [OrgService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L32)
- [companies](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L39)
- [tree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L56)
- [build](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L79)

## F 菜单审计统计与工程验证

### [rbac-structured/src/main/java/com/xmu/rbac/structured/RbacStructuredApplication.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/RbacStructuredApplication.java)

完整阅读参数、字段、调用方及状态变化

- [main](../../rbac-structured/src/main/java/com/xmu/rbac/structured/RbacStructuredApplication.java#L9)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java)

完整阅读参数、字段、调用方及状态变化

- [StatsController](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java#L23)
- [auditLogs](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java#L29)
- [dashboard](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java#L44)
- [heatmap](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java#L49)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java)

统计类型F；AuthzRequest B；重点：完整阅读参数、字段、调用方及状态变化

- [StatsDtos](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java#L9)
- [AuthzRequest](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java#L17)
- [DashboardView](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java#L22)
- [Heatmap](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java#L28)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/AuditLog.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/AuditLog.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Menu.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Menu.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/package-info.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/package-info.java)

完整阅读参数、字段、调用方及状态变化

- 数据字段、默认值、常量与持久化注解；关联阅读DTO/schema及查询投影。

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java)

完整阅读参数、字段、调用方及状态变化

- [search](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L31)
- [countByEventTypeInAndOccurredAtGreaterThanEqual](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L40)
- [countByEventTypeAndOccurredAtGreaterThanEqual](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L42)
- [countAuthzCacheHits](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L48)
- [countAuthzWithCacheInfo](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L54)
- [findLatencyAt](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L62)
- [countAuthzWithLatency](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L68)
- [countAbnormal](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L82)
- [heatmap](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java#L94)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java)

完整阅读参数、字段、调用方及状态变化

- [findAllByOrderBySortOrderAscIdAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java#L10)
- [existsByParentId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java#L12)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java)

独立审计事务及事件F；各业务人复核实际调用时机与内容；重点：record、recordDecision、search、REQUIRES_NEW、truncate

- [AuditService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java#L30)
- [record](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java#L37)
- [recordDecision](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java#L61)
- [search](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java#L84)
- [truncate](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java#L90)
- [blankToNull](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java#L94)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java)

metrics、heatmap、percentileMs/percentileOffset、ratio与时间窗

- [DashboardService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L42)
- [metrics](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L55)
- [percentileMs](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L83)
- [percentileOffset](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L92)
- [ratio](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L96)
- [heatmap](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L105)

### [rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java)

myMenus、tree/build、apply、createsCycle、增改删

- [MenuService](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L32)
- [myMenus](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L42)
- [all](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L56)
- [create](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L61)
- [update](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L70)
- [delete](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L82)
- [apply](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L93)
- [createsCycle](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L115)
- [resourceCodes](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L126)
- [tree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L135)
- [build](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L149)
- [node](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L167)
