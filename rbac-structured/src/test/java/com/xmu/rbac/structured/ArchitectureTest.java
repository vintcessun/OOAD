package com.xmu.rbac.structured;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构适应度函数（02-architecture.md §2.4）：把架构约束写成会失败的测试，
 * 约束被破坏时构建直接失败，而不是等评审时才发现。
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.xmu.rbac.structured");
    }

    /**
     * 判定内核不依赖框架（02 §2.2）。
     * io.jsonwebtoken（jjwt）是库而不是框架：它不控制调用流程、不要求注解或容器，
     * TokenFunctions 用它和用 java.util 没有区别，因此不在禁止之列。
     */
    @Test
    void kernelDoesNotDependOnFrameworks() {
        noClasses().that().resideInAnyPackage("..func..", "..data..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "org.apache.ibatis..", "org.mybatis..")
                .check(classes);
    }

    /** 依赖方向单向：web → func → dao → data，config 只做装配。 */
    @Test
    void dependenciesPointOneWay() {
        noClasses().that().resideInAPackage("..dao..")
                .should().dependOnClassesThat().resideInAnyPackage("..func..", "..web..", "..config..")
                .check(classes);
        noClasses().that().resideInAPackage("..func..")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..config..")
                .check(classes);
        noClasses().that().resideInAPackage("..data..")
                .should().dependOnClassesThat().resideInAnyPackage("..func..", "..dao..", "..web..", "..config..")
                .check(classes);
    }

    /** 结构化准入标准第 2、3 条（06 §1.2）：函数模块无状态、不参与继承。 */
    @Test
    void functionModulesAreStatelessAndFinal() {
        fields().that().areDeclaredInClassesThat().resideInAPackage("..func..")
                .should().beStatic().andShould().beFinal()
                .check(classes);
        classes().that().resideInAPackage("..func..").and().areNotInterfaces()
                .should().haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
                .check(classes);
    }

    /** 准入标准第 1 条：数据类型是贫血的 record。 */
    @Test
    void dataTypesAreRecords() {
        classes().that().resideInAPackage("..data..").and().areTopLevelClasses()
                .should().beRecords()
                .check(classes);
    }
}
