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

    @GetMapping("/systems")
    public ApiResponse<List<SystemView>> systems() {
        return ApiResponse.ok(catalog.systems());
    }

    @GetMapping("/resources")
    public ApiResponse<PageResult<ResourceView>> resources(@RequestParam(required = false) Long systemId,
                                                           @RequestParam(required = false) String type,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(catalog.resources(systemId, type, keyword, page, size));
    }

    @PostMapping("/resources")
    public ApiResponse<ResourceView> createResource(@RequestAttribute(OP) Operator op,
                                                    @RequestBody CreateResource req) {
        return ApiResponse.ok(catalog.createResource(op, req));
    }

    @GetMapping("/actions")
    public ApiResponse<List<ActionView>> actions() {
        return ApiResponse.ok(catalog.actions());
    }

    @GetMapping("/permissions")
    public ApiResponse<PageResult<PermissionView>> permissions(@RequestParam(required = false) Long systemId,
                                                               @RequestParam(required = false) Long resourceId,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(catalog.permissions(systemId, resourceId, keyword, page, size));
    }

    @PostMapping("/permissions")
    public ApiResponse<PermissionView> createPermission(@RequestAttribute(OP) Operator op,
                                                        @RequestBody CreatePermission req) {
        return ApiResponse.ok(catalog.createPermission(op, req));
    }

    // ---------------------------------------------------------------- 菜单

    @GetMapping("/menus/me")
    public ApiResponse<List<MenuNode>> myMenus(@RequestAttribute(OP) Operator op) {
        return ApiResponse.ok(menus.myMenus(op));
    }

    @GetMapping("/menus")
    public ApiResponse<List<MenuNode>> allMenus() {
        return ApiResponse.ok(menus.all());
    }

    @PostMapping("/menus")
    public ApiResponse<MenuNode> createMenu(@RequestAttribute(OP) Operator op, @RequestBody SaveMenu req) {
        return ApiResponse.ok(menus.create(op, req));
    }

    @PatchMapping("/menus/{id}")
    public ApiResponse<MenuNode> updateMenu(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                            @RequestBody SaveMenu req) {
        return ApiResponse.ok(menus.update(op, id, req));
    }

    @DeleteMapping("/menus/{id}")
    public ApiResponse<Void> deleteMenu(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        menus.delete(op, id);
        return ApiResponse.ok(null);
    }

    // ---------------------------------------------------------------- 组织与导入

    @GetMapping("/companies")
    public ApiResponse<List<CompanyView>> companies(@RequestAttribute(OP) Operator op) {
        return ApiResponse.ok(org.companies(op));
    }

    @GetMapping("/departments/tree")
    public ApiResponse<List<DeptNode>> departmentTree(@RequestAttribute(OP) Operator op,
                                                      @RequestParam Long companyId) {
        return ApiResponse.ok(org.tree(op, companyId));
    }

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
