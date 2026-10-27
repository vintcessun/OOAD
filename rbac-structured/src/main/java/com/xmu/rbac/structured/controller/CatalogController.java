package com.xmu.rbac.structured.controller;

import com.xmu.rbac.structured.dto.CatalogDtos.ActionView;
import com.xmu.rbac.structured.dto.CatalogDtos.CompanyView;
import com.xmu.rbac.structured.dto.CatalogDtos.CreatePermission;
import com.xmu.rbac.structured.dto.CatalogDtos.CreateResource;
import com.xmu.rbac.structured.dto.CatalogDtos.DeptNode;
import com.xmu.rbac.structured.dto.CatalogDtos.MenuNode;
import com.xmu.rbac.structured.dto.CatalogDtos.PermissionView;
import com.xmu.rbac.structured.dto.CatalogDtos.ResourceView;
import com.xmu.rbac.structured.dto.CatalogDtos.SaveMenu;
import com.xmu.rbac.structured.dto.CatalogDtos.SystemView;
import com.xmu.rbac.structured.dto.ImportReport;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.PageResult;
import com.xmu.rbac.structured.service.BizException;
import com.xmu.rbac.structured.service.CatalogService;
import com.xmu.rbac.structured.service.ImportService;
import com.xmu.rbac.structured.service.MenuService;
import com.xmu.rbac.structured.service.OrgService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** 权限管理（资源、操作、权限）、菜单管理、组织浏览与数据导入。 */
@RestController
public class CatalogController {

    // 从拦截器写入的请求属性获取真实操作者，传给业务过程做范围检查或记录审计。
    private static final String OP = PermissionInterceptor.ATTR_OPERATOR;

    private final CatalogService catalog;
    private final MenuService menus;
    private final OrgService org;
    private final ImportService imports;

    public CatalogController(CatalogService catalog, MenuService menus, OrgService org, ImportService imports) {
        this.catalog = catalog;
        this.menus = menus;
        this.org = org;
        this.imports = imports;
    }

    // ---------------------------------------------------------------- 资源、操作、权限

    /** D09：资源页左侧系统/模块树、新建资源的级联选项，以及授予权限弹窗的系统筛选项。 */
    @GetMapping("/systems")
    public ApiResponse<List<SystemView>> systems() {
        return ApiResponse.ok(catalog.systems());
    }

    /**
     * D10：资源页查询；菜单表单也用 type=MENU 查询候选资源。
     * 支持系统、类型、编码/名称关键字，不支持按 moduleId 筛选；page 默认 0，size 默认 50。
     */
    @GetMapping("/resources")
    public ApiResponse<PageResult<ResourceView>> resources(@RequestParam(required = false) Long systemId,
                                                           @RequestParam(required = false) String type,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(catalog.resources(systemId, type, keyword, page, size));
    }

    /** D11：新建资源，可同时为 actionCodes 中的已有操作创建权限；此动作不把权限授予角色。 */
    @PostMapping("/resources")
    public ApiResponse<ResourceView> createResource(@RequestAttribute(OP) Operator op,
                                                    @RequestBody CreateResource req) {
        return ApiResponse.ok(catalog.createResource(op, req));
    }

    /** D12：查询操作词汇表，既显示在“操作”页签，也作为新建资源/权限表单的选项。 */
    @GetMapping("/actions")
    public ApiResponse<List<ActionView>> actions() {
        return ApiResponse.ok(catalog.actions());
    }

    /**
     * D13：权限目录与授予弹窗共用的分页查询，可按系统、资源 ID、权限码/资源名筛选。
     * 返回权限定义，不表示当前用户已拥有这些权限；授予角色时 Service 仍要检查授权上界。
     */
    @GetMapping("/permissions")
    public ApiResponse<PageResult<PermissionView>> permissions(@RequestParam(required = false) Long systemId,
                                                               @RequestParam(required = false) Long resourceId,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(catalog.permissions(systemId, resourceId, keyword, page, size));
    }

    /** D14：“为资源新增操作”；前端先将资源编码查为 resourceId，再提交已有 actionCode。 */
    @PostMapping("/permissions")
    public ApiResponse<PermissionView> createPermission(@RequestAttribute(OP) Operator op,
                                                        @RequestBody CreatePermission req) {
        return ApiResponse.ok(catalog.createPermission(op, req));
    }

    // ---------------------------------------------------------------- 菜单

    /** F：登录后取本人可见菜单；Service 按挂接资源的 :view 权限过滤，空目录随之裁剪。 */
    @GetMapping("/menus/me")
    public ApiResponse<List<MenuNode>> myMenus(@RequestAttribute(OP) Operator op) {
        return ApiResponse.ok(menus.myMenus(op));
    }

    /** F：菜单管理页面需要完整配置树，与 myMenus 返回的本人可见菜单树用途不同。 */
    @GetMapping("/menus")
    public ApiResponse<List<MenuNode>> allMenus() {
        return ApiResponse.ok(menus.all());
    }

    /** F/D 交叉：新增菜单，可挂接 D 中的 MENU 资源；保存菜单不等于授予接口权限。 */
    @PostMapping("/menus")
    public ApiResponse<MenuNode> createMenu(@RequestAttribute(OP) Operator op, @RequestBody SaveMenu req) {
        return ApiResponse.ok(menus.create(op, req));
    }

    /** F：编辑菜单位置、资源、路由等信息；Service 检查父节点、防环及挂接资源类型。 */
    @PatchMapping("/menus/{id}")
    public ApiResponse<MenuNode> updateMenu(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                            @RequestBody SaveMenu req) {
        return ApiResponse.ok(menus.update(op, id, req));
    }

    /** F：删除菜单配置；有子菜单时拒绝，不删除所引用的资源或角色权限。 */
    @DeleteMapping("/menus/{id}")
    public ApiResponse<Void> deleteMenu(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        menus.delete(op, id);
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------------- 组织与导入

    /** E/D 交叉：返回可见公司，供角色创建表单选择所属公司，也供各管理页面显示公司名称。 */
    @GetMapping("/companies")
    public ApiResponse<List<CompanyView>> companies(@RequestAttribute(OP) Operator op) {
        return ApiResponse.ok(org.companies(op));
    }

    /** E/C 交叉：按公司取可见部门树，供组织浏览、指派适用部门选择等功能使用。 */
    @GetMapping("/departments/tree")
    public ApiResponse<List<DeptNode>> departmentTree(@RequestAttribute(OP) Operator op,
                                                      @RequestParam Long companyId) {
        return ApiResponse.ok(org.tree(op, companyId));
    }

    /**
     * E：组织页上传部门表与员工表，参数是 multipart 文件及公司信息。
     * Controller 只负责打开/关闭输入流，组织数据解析与落库由 ImportService 完成。
     */
    @PostMapping("/imports/organization")
    public ApiResponse<ImportReport> importOrganization(@RequestAttribute(OP) Operator op,
                                                        @RequestParam String companyCode,
                                                        @RequestParam String companyName,
                                                        @RequestPart("departments") MultipartFile departments,
                                                        @RequestPart("employees") MultipartFile employees) {
        try (InputStream d = departments.getInputStream(); InputStream e = employees.getInputStream()) {
            return ApiResponse.ok(imports.importOrganization(op, companyCode, companyName, d, e));
        } catch (IOException ex) {
            throw BizException.badRequest("读取上传文件失败");
        }
    }

    /**
     * E/D 交叉：上传功能权限清单，同步权限目录和模板角色的业务授权（含 selfOnly）。
     * 与 D11/D14 只新建定义不同，导入也可能回收清单已去掉的模板授权；返回后需核对异常报告。
     */
    @PostMapping("/imports/permissions")
    public ApiResponse<ImportReport> importPermissions(@RequestAttribute(OP) Operator op,
                                                       @RequestPart("file") MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return ApiResponse.ok(imports.importPermissions(op, in));
        } catch (IOException ex) {
            throw BizException.badRequest("读取上传文件失败");
        }
    }
}
