package com.xmu.rbac.structured;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构适应度函数：课程规定的 Controller + Service + Mapper 三层与结构化设计约束，
 * 写成会失败的测试。约束被破坏时构建直接失败（docs/02-architecture.md §2.4）。
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.xmu.rbac.structured");
    }

    /** 只有三层，外加 entity / dto / config 三个辅助包；不允许再出现 dao、func 之类的层。 */
    @Test
    void onlyTheThreeLayersAndHelpers() {
        classes().should().resideInAnyPackage(
                        "com.xmu.rbac.structured",
                        "..controller..", "..service..", "..mapper..", "..entity..", "..dto..", "..config..")
                .check(classes);
    }

    /** 依赖方向：Controller → Service → Mapper。Controller 不能绕过 Service 直接访问数据。 */
    @Test
    void dependenciesPointDownward() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .check(classes);
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..config..")
                .check(classes);
        noClasses().that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..config..")
                .check(classes);
        noClasses().that().resideInAnyPackage("..entity..", "..dto..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..mapper..")
                .check(classes);
    }

    /** 技术栈：持久化只用 JPA，不能出现 MyBatis。 */
    @Test
    void noMyBatis() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage("org.apache.ibatis..", "org.mybatis..")
                .check(classes);
    }

    /** 结构化设计：实体只是数据结构——公有字段，没有业务方法（只有编译器生成的构造器）。 */
    @Test
    void entitiesAreDataOnly() {
        methods().that().areDeclaredInClassesThat().resideInAPackage("..entity..")
                .should().notBePublic()
                .orShould().beStatic()
                .allowEmptyShould(true)
                .check(classes);
    }

    /** 结构化设计：Service 是无状态的处理过程——字段只能是注入的依赖或常量（final）。 */
    @Test
    void servicesAreStateless() {
        fields().that().areDeclaredInClassesThat().resideInAPackage("..service..")
                .and().areDeclaredInClassesThat().areAnnotatedWith(org.springframework.stereotype.Service.class)
                .should().beFinal()
                .check(classes);
    }

    /** 不用继承组织业务逻辑：每个 Service 直接继承 Object。 */
    @Test
    void servicesDoNotInherit() {
        classes.stream()
                .filter(c -> c.getPackageName().endsWith(".service"))
                .filter(c -> c.isAnnotatedWith(org.springframework.stereotype.Service.class))
                .forEach(c -> org.junit.jupiter.api.Assertions.assertEquals("java.lang.Object",
                        c.getRawSuperclass().map(s -> s.getName()).orElse("java.lang.Object"), c.getName()));
    }
}
