#!/usr/bin/env python3
"""Static validation + REAL Flyway migration on the dedicated MySQL container only.
Requires Java 21, cached Maven Flyway 10.10.0/MySQL/Jackson/Gson jars,
PyYAML and openapi-spec-validator. Recreates ONLY rbac1_schema_contract_test.
Never touches rbac_test or application/production schemas.
"""
import json, os, re, subprocess, sys, tempfile
from pathlib import Path
import yaml
from openapi_spec_validator import validate_spec
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'rbac-contract/src/main/resources'
CONTAINER='rbac-phase2-test-mysql'
DATABASE='rbac1_schema_contract_test'
def run(args, **kwargs):return subprocess.run(args,check=True,**kwargs)
def sql(query, expect_failure=False):
    # Root secret stays inside the dedicated container, not arguments or output.
    p=subprocess.run(['docker','exec','-i',CONTAINER,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N '+DATABASE],input=query,text=True,encoding='utf8',capture_output=True)
    if expect_failure:
        assert p.returncode!=0, 'Constraint unexpectedly accepted invalid data: '+query
    elif p.returncode:
        raise RuntimeError(p.stderr)
    return p.stdout.strip()
def main():
    run([sys.executable,str(ROOT/'tools/generate-rbac1-contract.py'),'--check'])
    doc=yaml.safe_load((RES/'openapi/rbac-api.yaml').read_text(encoding='utf8'));validate_spec(doc)
    assert doc['paths']['/api/v1/roles/{id}']['patch']['x-required-permission']=='rbac:role:edit'
    assert doc['paths']['/api/v1/roles/{id}/status']['put']['x-required-permission']=='rbac:role:status', 'Role status API must require its own capability'
    for path, method, permission in [
        ('/departments', 'post', 'edit'), ('/departments/{id}', 'patch', 'edit'),
        ('/departments/{id}/assignable-roles', 'put', 'edit'),
        ('/departments/{id}/status', 'put', 'status'), ('/departments/{id}', 'delete', 'delete'),
    ]:
        assert doc['paths']['/api/v1'+path][method]['x-required-permission']=='rbac:department:'+permission
    print('OpenAPI semantic/$ref/operationId validation PASS')
    java=str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    version=subprocess.check_output([java,'-version'],stderr=subprocess.STDOUT,text=True,encoding='utf8',errors='replace')
    major=re.search(r'(?:version\s+\"|openjdk\s+)(\d+)',version)
    if not major or int(major.group(1))<21:
        raise RuntimeError('Java 21+ required; set JAVA_HOME before recreating the dedicated test schema')
    info=json.loads(subprocess.check_output(['docker','inspect',CONTAINER],text=True))[0]
    bindings=info['HostConfig']['PortBindings']['3306/tcp']
    assert any(b['HostPort']=='13306' and b['HostIp'] in ['127.0.0.1','::1'] for b in bindings), 'Unexpected MySQL instance; abort'
    # Only this exact disposable schema is ever dropped.
    run(['docker','exec',CONTAINER,'sh','-c',f'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS {DATABASE}; CREATE DATABASE {DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"'])
    env=os.environ.copy()
    env['RBAC_CONTRACT_TEST_PASSWORD']=next(x.split('=',1)[1] for x in info['Config']['Env'] if x.startswith('MYSQL_ROOT_PASSWORD='))
    repo=Path.home()/'.m2/repository'
    groups=['org/flywaydb/flyway-core/10.10.0','org/flywaydb/flyway-mysql/10.10.0','com/mysql/mysql-connector-j','com/fasterxml/jackson/core/jackson-core','com/fasterxml/jackson/core/jackson-databind','com/fasterxml/jackson/core/jackson-annotations','com/fasterxml/jackson/dataformat/jackson-dataformat-toml','com/google/code/gson/gson']
    jars=[]
    for group in groups:
        candidates=[p for p in (repo/group).glob('**/*.jar') if not any(s in p.name for s in ['-sources','-javadoc'])]
        assert candidates, 'Missing cached dependency '+group
        # Prefer one recent cached version, never mix all versions of a dependency.
        jars.append(sorted(candidates,key=lambda p:tuple(int(v) if v.isdigit() else v for v in p.parent.name.split('.')))[-1])
    source='''import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
class ContractMigrationTest {
 public static void main(String[] args) throws Exception {
  String url="jdbc:mysql://127.0.0.1:13306/rbac1_schema_contract_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
  String password=System.getenv("RBAC_CONTRACT_TEST_PASSWORD");
  var config=Flyway.configure().dataSource(url,"root",password).locations("filesystem:"+args[0]).cleanDisabled(true);
  if(config.target("2").load().migrate().migrationsExecuted!=2) throw new AssertionError("Expected V1/V2 baseline");
  try(var connection=DriverManager.getConnection(url,"root",password); var stmt=connection.createStatement()) {
   stmt.executeUpdate("INSERT INTO sys_role(system_id,code,name,status) VALUES(1,'FIX_EDIT_ONLY','Edit only','ENABLED')");
   stmt.executeUpdate("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.system_id=r.system_id WHERE r.code='FIX_EDIT_ONLY' AND p.kind='API' AND p.code IN ('rbac:role:edit','rbac:department:edit')");
  }
  if(config.target("3").load().migrate().migrationsExecuted!=1) throw new AssertionError("Expected V3 upgrade only");
  try(var connection=DriverManager.getConnection(url,"root",password); var stmt=connection.createStatement();
      var legacy=stmt.executeQuery("SELECT COUNT(*) FROM sys_api_operation WHERE system_id=2 AND operation_id='lookupIdentityUser'")) {
   if (!legacy.next() || legacy.getInt(1)!=1) throw new AssertionError("V3 must have the previous lookup");
  }
  if(config.target("4").load().migrate().migrationsExecuted!=1) throw new AssertionError("Expected V4 upgrade only");
  if(config.target("5").load().migrate().migrationsExecuted!=1) throw new AssertionError("Expected V5 upgrade only");
  try(var connection=DriverManager.getConnection(url,"root",password); var stmt=connection.createStatement()) {
   stmt.executeUpdate("INSERT INTO sys_role(system_id,code,name,status,identity_role) VALUES(2,'LEGACY_LAB','Legacy lab','ENABLED','teacher'),(2,'LEGACY_DISABLED','Legacy disabled','DISABLED','teacher')");
   stmt.executeUpdate("INSERT INTO sys_role(system_id,code,name,status) VALUES(1,'teacher','Same code, no qualification','ENABLED')");
   stmt.executeUpdate("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.system_id=r.system_id WHERE r.code='LEGACY_LAB' AND p.kind='API' AND p.code='course:access'");
   stmt.executeUpdate("INSERT INTO sys_department_allowed_role(department_id,role_id) SELECT 1,id FROM sys_role WHERE code='LEGACY_LAB'");
   stmt.executeUpdate("INSERT INTO sys_user(username,subject,name,password_hash,department_id,status) VALUES('legacy-lab','legacy-lab','Legacy lab','UNINITIALIZED',1,'ACTIVE')");
   stmt.executeUpdate("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.code='LEGACY_LAB' WHERE u.username='legacy-lab'");
  }
  Flyway f=config.target("latest").load();
  if(f.migrate().migrationsExecuted!=2) throw new AssertionError("Expected V6/V7 upgrade");
  f.validate();var second=f.migrate();if(second.migrationsExecuted!=0)throw new AssertionError("Repeat migration not idempotent");
  System.out.println("Flyway migrate V1/V2, upgrade V3 through V7, validate, repeat migrate PASS");
 }
}'''
    with tempfile.TemporaryDirectory(prefix='rbac1-contract-') as temp:
        src=Path(temp)/'ContractMigrationTest.java';src.write_text(source,encoding='utf8')
        run([java,'-cp',os.pathsep.join(map(str,jars)),str(src),str(RES/'db/migration')],env=env)
    assert sql('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE \'sys_%\';')=='15'
    assert sql("SELECT password_hash FROM sys_user WHERE id=1;")=='UNINITIALIZED'
    assert sql('SELECT COUNT(*) FROM sys_user_role;')=='2'
    assert sql("SELECT r.code FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='legacy-lab';")=='LEGACY_LAB'
    assert sql("SELECT COUNT(*) FROM sys_department_allowed_role ar JOIN sys_role r ON r.id=ar.role_id WHERE ar.department_id=1 AND r.code='LEGACY_LAB';")=='1'
    assert sql("SELECT p.code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.code='LEGACY_LAB';")=='course:access'
    assert sql("SELECT COUNT(*) FROM sys_role_hierarchy;")=='0'
    assert sql("SELECT CONCAT(r.id,' ',r.code,' ',COUNT(rp.id)) FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id WHERE r.system_id=2 AND r.id IN (4,5,6) GROUP BY r.id,r.code ORDER BY r.id;")=='4 teacher 53\n5 student 21\n6 admin 79'
    assert sql("SELECT COUNT(*) FROM sys_role WHERE system_id=1 AND name='Same code, no qualification' AND code LIKE 'legacy-role-%';")=='1'
    assert sql("SELECT p.code FROM sys_api_operation a JOIN sys_permission p ON p.id=a.permission_id WHERE a.system_id=1 AND a.operation_id='setRoleStatus';")=='rbac:role:status'
    assert sql("SELECT p.code FROM sys_api_operation a JOIN sys_permission p ON p.id=a.permission_id WHERE a.system_id=1 AND a.operation_id='updateRole';")=='rbac:role:edit'
    assert sql("SELECT r.code FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.system_id=1 AND p.kind='API' AND p.code='rbac:role:status' ORDER BY r.code;")=='RBAC_ADMIN\nSUPER_ADMIN'
    assert sql("SELECT CONCAT(a.operation_id,' ',p.code) FROM sys_api_operation a JOIN sys_permission p ON p.id=a.permission_id WHERE a.system_id=1 AND a.operation_id IN ('createDepartment','updateDepartment','replaceDepartmentAssignableRoles','setDepartmentStatus','deleteDepartment') ORDER BY a.operation_id;")=="\n".join([
        'createDepartment rbac:department:edit', 'deleteDepartment rbac:department:delete',
        'replaceDepartmentAssignableRoles rbac:department:edit', 'setDepartmentStatus rbac:department:status',
        'updateDepartment rbac:department:edit',
    ])
    assert sql("SELECT CONCAT(r.code,' ',COUNT(*)) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE p.system_id=1 AND ((p.kind='API' AND p.code IN ('rbac:department:status','rbac:department:delete')) OR (p.kind='UI' AND p.code='rbac:ui:setDepartmentStatus')) GROUP BY r.code ORDER BY r.code;")=='RBAC_ADMIN 3\nSUPER_ADMIN 3'
    assert sql("SELECT CONCAT(a.operation_id,' ',ui.code,' ',menu.code) FROM sys_ui_api_mapping m JOIN sys_api_operation a ON a.id=m.api_operation_id JOIN sys_ui_resource ui ON ui.id=m.ui_resource_id JOIN sys_ui_resource menu ON menu.id=ui.parent_id WHERE a.system_id=1 AND a.operation_id IN ('setDepartmentStatus','deleteDepartment') ORDER BY a.operation_id;")=="\n".join([
        'deleteDepartment rbac:ui:deleteDepartment rbac:ui:departments:view',
        'setDepartmentStatus rbac:ui:setDepartmentStatus rbac:ui:departments:view',
    ])
    assert sql('SELECT version FROM sys_grant_version WHERE id=1;')=='4'
    assert sql('SELECT COUNT(*) FROM sys_api_operation WHERE system_id=2;')=='80'
    assert sql("SELECT required_role FROM sys_api_operation WHERE system_id=2 AND operation_id='copyCourse';")=='teacher'
    assert sql("SELECT COUNT(*) FROM sys_api_operation WHERE system_id=1;")=='68'
    assert sql("SELECT COUNT(*) FROM sys_api_operation WHERE system_id=2 AND access_policy='PROTECTED';")=='79'
    assert sql("SELECT CONCAT(operation_id,' ',method,' ',path_template) FROM sys_api_operation WHERE system_id=2 AND access_policy='PUBLIC';")=='courseLogin POST /login'
    assert sql("SELECT COUNT(*) FROM sys_api_operation WHERE system_id=2 AND operation_id='lookupIdentityUser';")=='0'
    assert sql("SELECT COUNT(*) FROM sys_ui_api_mapping WHERE api_operation_id=137;")=='0'
    assert sql("SELECT COUNT(*) FROM sys_api_operation WHERE system_id=1 AND access_policy='AUTHENTICATED' AND required_role='admin' AND operation_id IN ('listIdentityDepartments','listIdentityDepartmentUsers','lookupIdentityUser');")=='3'
    assert sql("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.system_id=2 AND r.code='teacher' AND p.kind='API' AND p.code NOT IN ('course:access','audit:read','audit:write','CRS');")=='0'
    assert sql("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.system_id=2 AND r.id=5 AND p.kind='API' AND p.code NOT IN ('course:access','audit:write');")=='0'
    assert sql("SELECT COUNT(*) FROM sys_api_operation a WHERE a.system_id=2 AND a.permission_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE r.system_id=2 AND r.code='admin' AND rp.permission_id=a.permission_id);")=='0'
    assert sql("SELECT COUNT(*) FROM sys_ui_resource WHERE system_id=1 AND type='MENU';")=='12'
    assert sql("SELECT COUNT(*) FROM sys_api_operation a WHERE access_policy='PROTECTED' AND NOT EXISTS(SELECT 1 FROM sys_ui_api_mapping m WHERE m.api_operation_id=a.id);")=='0'
    # Exact physical columns must match the frozen schema, after applying all migrations.
    contract=(ROOT/'docs/schema-contract.adoc').read_text(encoding='utf8')
    for table,definition in re.findall(r'^\|(sys_\w+) \|(.+)$',contract,re.M):
        expected=[part.strip().split()[0] for part in definition.split('；')[0].split(',') if part.strip()]
        actual=sql(f'SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=\'{table}\' ORDER BY ordinal_position;').splitlines()
        # Inline SQL declarations UNIQUE(system_id,code) contain commas; take columns before UNIQUE.
        expected=[x for x in expected if not x.startswith('UNIQUE')]
        assert actual==expected,(table,actual,expected)
    invalid=[
      "INSERT INTO sys_system(code,name,status) VALUES('invalid','invalid','BAD');",
      "INSERT INTO sys_role(system_id,code,name,status) VALUES(2,'invalid','invalid','BAD');",
      "INSERT INTO sys_permission(system_id,code,name,kind,status) VALUES(1,'invalid','invalid','INVALID','ENABLED');",
      "INSERT INTO sys_user_role(user_id,role_id) VALUES(999999999,1);",
      "INSERT INTO sys_user_role(user_id,role_id) VALUES(1,1);",
      "INSERT INTO sys_role_hierarchy(senior_role_id,junior_role_id) VALUES(1,1);",
      "INSERT INTO sys_grant_version(id,version) VALUES(2,0);",
      "INSERT INTO sys_api_operation(system_id,operation_id,method,path_template,access_policy) VALUES(2,'invalid','GET','/invalid','PROTECTED');",
      "INSERT INTO sys_ui_api_mapping(ui_resource_id,api_operation_id) SELECT ui_resource_id,api_operation_id FROM sys_ui_api_mapping LIMIT 1;"
    ]
    for query in invalid:sql(query,expect_failure=True)
    sql('START TRANSACTION; INSERT INTO sys_user(subject,username,password_hash,name,department_id,status) VALUES(\'test-reservation\',\'test-reservation\',\'UNINITIALIZED\',\'Test\',1,\'ACTIVE\'); SELECT LAST_INSERT_ID(); ROLLBACK;')
    assert int(sql("SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='sys_user';"))>=10000
    assert sql('SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;')=='7'
    print(f'MySQL {DATABASE}: 15 exact-column tables, all catalogs/grants/mappings, root placeholder, ID reservation and {len(invalid)} negative constraint tests PASS')
if __name__=='__main__':main()
