#!/usr/bin/env python3
"""Deploy-time acceptance. No credentials/tokens or employee rows are logged."""
import argparse
import hashlib
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

def save(path, obj):
    os.makedirs(os.path.dirname(os.path.abspath(path)), mode=0o700, exist_ok=True)
    temp = path + '.tmp'
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write('\n')
    os.chmod(temp, 0o600)
    os.replace(temp, path)

def load(path, default=None):
    if os.path.exists(path):
        with open(path) as f:
            return json.load(f)
    return default

class Acceptance:
    def __init__(self, args):
        self.args = args
        self.base = args.base_url.rstrip('/')
        self.report = {'base_url': self.base, 'checks': [], 'complete': False}
        os.makedirs(args.report_dir, mode=0o700, exist_ok=True)
        os.chmod(args.report_dir, 0o700)
        self.state_path = os.path.join(args.report_dir, 'state.json')
        self.state = load(self.state_path, {'users': [], 'roles': [], 'imports': {}})
        self.root = None

    def persist(self):
        save(self.state_path, self.state)

    def check(self, name, condition):
        self.report['checks'].append({'name': name, 'pass': bool(condition)})
        if not condition:
            raise RuntimeError('Assertion failed: ' + name)
        print('PASS:', name, flush=True)

    def request(self, method, path, token=None, body=None, files=None,
                expected=200, code=None, label=None, timeout=45):
        headers = {}
        payload = None
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if files:
            boundary = 'OOAD' + secrets.token_hex(16)
            chunks = []
            for field, filename in files.items():
                with open(filename, 'rb') as f:
                    content = f.read()
                chunks += [
                    ('--' + boundary + '\r\nContent-Disposition: form-data; name="' +
                     field + '"; filename="' + os.path.basename(filename) +
                     '"\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n').encode(),
                    content, b'\r\n']
            chunks.append(('--' + boundary + '--\r\n').encode())
            payload = b''.join(chunks)
            headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
        elif body is not None:
            payload = json.dumps(body).encode()
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(self.base + path, data=payload, headers=headers, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=timeout)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.getcode()
            result = json.loads(response.read().decode())
        if expected is not None:
            wanted_code = code if code is not None else (0 if expected == 200 else None)
            self.check(label or (method + ' ' + path.split('?')[0]),
                       status == expected and (wanted_code is None or result.get('code') == wanted_code))
        return status, result

    def api(self, method, path, token=None, body=None, **kwargs):
        return self.request(method, path, token, body, **kwargs)[1].get('data')

    def login(self, username, password):
        return self.api('POST', '/auth/login', body={'username': username, 'password': password},
                        label='Login ' + username)

    def root_login(self):
        credentials = load(self.args.credentials,
                           {'username': 'root', 'password': 'Admin@123'})
        candidates = [credentials.get('password')]
        if credentials.get('pending_password'):
            candidates.insert(0, credentials['pending_password'])
        authenticated = None
        password = None
        for candidate in dict.fromkeys(candidates):
            status, result = self.request('POST', '/auth/login',
                body={'username': 'root', 'password': candidate}, expected=None)
            if status == 200 and result.get('code') == 0:
                authenticated, password = result['data'], candidate
                break
        self.check('Root login', authenticated is not None)
        self.root = authenticated['token']
        if authenticated.get('mustChangePwd'):
            self.request('GET', '/users', self.root, expected=403, code=40304,
                         label='First-login password restriction')
            new_password = credentials.get('pending_password') or ('Root@' + secrets.token_hex(16))
            credentials.update({'username': 'root', 'password': password,
                                'pending_password': new_password})
            save(self.args.credentials, credentials)
            old_token = self.root
            self.api('POST', '/auth/password', self.root,
                     {'oldPassword': password, 'newPassword': new_password},
                     label='Root initial password changed')
            authenticated = self.login('root', new_password)
            self.root = authenticated['token']
            self.request('GET', '/auth/me', old_token, expected=401, code=40101,
                         label='Old root token rejected after password change')
            credentials = {'username': 'root', 'password': new_password}
        else:
            credentials = {'username': 'root', 'password': password}
        save(self.args.credentials, credentials)
        self.check('Root no longer requires password change',
                   not authenticated.get('mustChangePwd'))
        self.api('GET', '/users?size=1', self.root, label='Root management access')

    def do_import(self, name, path, files):
        hashes = {}
        for field, file_path in files.items():
            with open(file_path, 'rb') as f:
                hashes[field] = hashlib.sha256(f.read()).hexdigest()
        previous = self.state['imports'].get(name)
        report_path = os.path.join(self.args.report_dir, name + '-import.json')
        if previous:
            self.check(name + ' source files unchanged', previous['hashes'] == hashes)
            if previous['status'] != 'complete':
                raise RuntimeError(name + ' import outcome unknown; inspect server before retry')
            report = load(report_path)
            self.check(name + ' saved import report present', report is not None)
            return report
        self.state['imports'][name] = {'status': 'pending', 'hashes': hashes}
        self.persist()
        report = self.api('POST', path, self.root, files=files, timeout=590,
                          label=name + ' course import')
        save(report_path, report)
        self.state['imports'][name]['status'] = 'complete'
        self.persist()
        return report

    def import_course(self):
        directory = self.args.imports_dir
        permissions = self.do_import('permissions', '/imports/permissions',
                                      {'file': os.path.join(directory, 'permissions.xlsx')})
        for name, expected in {'systems': 18, 'modules': 115, 'resources': 359,
                               'permissions': 1795, 'rolePermissions': 3190}.items():
            self.check('Permission import ' + name, permissions['counts'].get(name) == expected)
        self.check('One known permission-cell anomaly', len(permissions['anomalies']) == 1)
        organization = self.do_import('organization',
            '/imports/organization?' + urllib.parse.urlencode({'companyCode':'SZ','companyName':'市政公司'}),
            {'departments': os.path.join(directory, 'departments.xlsx'),
             'employees': os.path.join(directory, 'employees.xlsx')})
        for name, expected in {'departmentsInSheet':111, 'employeesInSheet':10000,
                               'managersLinked':109}.items():
            self.check('Organization import ' + name, organization['counts'].get(name) == expected)
        self.check('Two known manager-name anomalies', len(organization['anomalies']) == 2)
        self.report['import_counts'] = {'permissions':permissions['counts'],
                                        'organization':organization['counts']}
        self.report['anomaly_counts'] = {'permissions':1, 'organization':2}
        company = next(c for c in self.api('GET', '/companies', self.root)
                       if c['companyCode'] == 'SZ')
        self.department = company['rootDepartmentId']
        self.company_id = company['id']
        tree = self.api('GET', '/departments/tree?companyId=' + str(company['id']), self.root)
        def flatten(nodes):
            result=[]
            for node in nodes:
                result.append(node)
                result.extend(flatten(node.get('children', [])))
            return result
        nodes = flatten(tree)
        self.check('Imported department tree contains 111 departments', len(nodes)==111)
        employees = self.api('GET', '/users?departmentId=' + str(self.department) + '&size=1', self.root)
        # Only course employees exist before smoke users are created.
        self.check('Imported company contains 10000 active employees', employees['total']==10000)
        sample = employees['items'][0]
        self.check('Employee department belongs to imported company',
                   sample['departmentId'] in {n['id'] for n in nodes})
        roles = self.api('GET', '/users/' + str(sample['id']) + '/roles', self.root)
        self.check('Imported sample employee has no assigned roles', roles == [])
        all_roles = self.api('GET', '/roles', self.root)
        templates = [r for r in all_roles if r['roleCode'] in
                     {'SYS_ADMIN','COMPANY_LEADER','DEPT_MANAGER','PROJECT_MANAGER','EMPLOYEE'}]
        self.check('Five course role templates have no assigned users',
                   len(templates)==5 and all(r['userCount']==0 for r in templates))

    def cleanup(self):
        if not self.root:
            return
        failures=[]
        for user_id in list(self.state['users']):
            try:
                roles=self.api('GET','/users/'+str(user_id)+'/roles',self.root)
                for role in roles:
                    self.api('DELETE','/users/'+str(user_id)+'/roles/'+str(role['assignmentId'])+
                             '?reason=deployment-acceptance-cleanup',self.root)
                self.api('DELETE','/users/'+str(user_id)+'?reason=deployment-acceptance-cleanup',self.root)
                self.state['users'].remove(user_id)
                self.persist()
            except Exception:
                failures.append('user ' + str(user_id))
        for role_id in list(self.state['roles']):
            try:
                self.api('DELETE','/roles/'+str(role_id),self.root)
                self.state['roles'].remove(role_id)
                self.persist()
            except Exception:
                failures.append('role ' + str(role_id))
        self.check('Acceptance users/roles cleaned up', not failures)
        if failures:
            raise RuntimeError('Cleanup incomplete; see state.json')

    def smoke(self):
        suffix = time.strftime('%Y%m%d%H%M%S') + secrets.token_hex(2)
        names = {'admin':'accept_admin_'+suffix, 'user':'accept_user_'+suffix}
        password = 'Test@'+secrets.token_hex(12)
        users={}
        for kind,name in names.items():
            user=self.api('POST','/users',self.root,{
                'username':name,'realName':'Deployment acceptance '+kind,
                'departmentId':self.department,'userType':'EMPLOYEE',
                'employeeNo':name,'password':password})
            users[kind]=user['id']
            self.state['users'].append(user['id']);self.persist()
        admin_role=next(r for r in self.api('GET','/roles?keyword=RBAC_ADMIN',self.root)
                        if r['roleCode']=='RBAC_ADMIN')
        self.api('POST','/users/'+str(users['admin'])+'/roles',self.root,{
            'roleId':admin_role['id'],'scopeDepartmentId':self.department,
            'reason':'Deployment acceptance admin'})
        reader=self.api('POST','/roles',self.root,{
            'roleCode':('ACCEPT_MENU_'+suffix).upper(),'roleName':'Deployment acceptance reader',
            'description':'Temporary acceptance role'})
        self.state['roles'].append(reader['id']);self.persist()
        permission=next(p for p in self.api('GET','/permissions?keyword=rbac%3Amenu%3Aview',self.root)['items']
                        if p['permissionCode']=='rbac:menu:view')
        self.api('POST','/roles/'+str(reader['id'])+'/permissions',self.root,
                 {'permissionIds':[permission['id']]})
        admin_token=self.login(names['admin'],password)['token']
        user_token=self.login(names['user'],password)['token']
        self.request('GET','/auth/me',expected=401,code=40101,label='Unauthenticated request rejected')
        self.request('GET','/users',user_token,expected=403,code=40301,label='User cannot list employees')
        self.request('GET','/menus',user_token,expected=403,code=40301,label='User initially cannot read menu tree')
        decision=self.api('POST','/authz/check',user_token,
                          {'permission':'rbac:menu:view','targetDeptId':self.department})
        self.check('Self authorization query returns allowed=false', decision['allowed'] is False)
        assignment=self.api('POST','/users/'+str(users['user'])+'/roles',admin_token,{
            'roleId':reader['id'],'scopeDepartmentId':self.department,
            'reason':'Deployment acceptance grant'})
        self.api('GET','/menus',user_token,label='Same user token can access granted menu permission')
        self.request('GET','/users',user_token,expected=403,code=40301,label='Grant does not confer employee management')
        decision=self.api('POST','/authz/check',user_token,
                          {'permission':'rbac:menu:view','targetDeptId':self.department})
        self.check('Granted permission allowed immediately',decision['allowed'] is True)
        self.api('DELETE','/users/'+str(users['user'])+'/roles/'+str(assignment['assignmentId'])+
                 '?reason=deployment-acceptance-revoke',admin_token)
        self.request('GET','/menus',user_token,expected=403,code=40301,label='Same token denied immediately after revoke')
        decision=self.api('POST','/authz/check',user_token,
                          {'permission':'rbac:menu:view','targetDeptId':self.department})
        self.check('Revoked permission denied immediately',decision['allowed'] is False)
        self.api('POST','/auth/logout',user_token)
        self.request('GET','/auth/me',user_token,expected=401,code=40101,label='Logged-out token rejected')
        for event in ('ROLE_ASSIGN','ROLE_REVOKE'):
            logs=self.api('GET','/audit-logs?'+urllib.parse.urlencode({
                'eventType':event,'actor':names['admin'],'size':10}),self.root)
            self.check('Audit contains '+event, logs['total']>=1)
        logs=self.api('GET','/audit-logs?'+urllib.parse.urlencode({
            'eventType':'AUTH_FAILED','actor':names['user'],'size':10}),self.root)
        self.check('Audit contains denied permission checks',logs['total']>=1)
        self.api('POST','/auth/logout',admin_token)

    def run(self):
        try:
            self.root_login()
            if self.state['users'] or self.state['roles']:
                self.cleanup()
            self.import_course()
            try:
                self.smoke()
            finally:
                self.cleanup()
            metrics=self.api('GET','/dashboard',self.root)
            self.check('Final active user count is 10001',metrics['userCount']==10001)
            self.report['complete']=True
        except Exception as error:
            self.report['error_type']=type(error).__name__
            self.report['error']=str(error) if isinstance(error,RuntimeError) else 'Operation failed; inspect server logs without exposing credentials'
            raise
        finally:
            save(os.path.join(self.args.report_dir,'summary.json'),self.report)

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--base-url',required=True)
    p.add_argument('--imports-dir',required=True)
    p.add_argument('--credentials',required=True)
    p.add_argument('--report-dir',required=True)
    args=p.parse_args()
    os.umask(0o077)
    try:
        Acceptance(args).run()
    except Exception as e:
        print('FAILED:',str(e) if isinstance(e,RuntimeError) else type(e).__name__,file=sys.stderr)
        return 1
    print('Acceptance complete; credentials saved only on server')
    return 0

if __name__=='__main__':
    sys.exit(main())
