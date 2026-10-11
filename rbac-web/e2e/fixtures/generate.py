"""
生成端到端测试用的三份 xlsx：格式与课程提供的原表一致，人名、电话全部虚构。
真实数据含个人信息，不能进仓库。用法：python generate.py（需要 openpyxl），输出到本目录。
"""
import os
from openpyxl import Workbook

HERE = os.path.dirname(os.path.abspath(__file__))
BRANCHES = ['道路工程分公司', '桥梁工程分公司']
SUBS = ['综合办公室', '财务部', '第一项目经理部']


def save(rows, name, merges=()):
    wb = Workbook()
    ws = wb.active
    for r in rows:
        ws.append(r)
    for m in merges:
        ws.merge_cells(m)
    wb.save(os.path.join(HERE, name))


# 部门信息表：部门名称、部门经理、上级部门
depts = [['部门名称', '部门经理', '上级部门'], ['总公司', '测试甲', None], ['财务部', '测试乙', '总公司'],
         ['公司领导', '测试甲', '总公司']]
for b in BRANCHES:
    depts.append([b, f'{b[:2]}经理', '总公司'])
    for s in SUBS:
        depts.append([f'{b}-{s}', f'{b[:2]}{s[:2]}长', b])
save(depts, 'departments.xlsx')

# 员工信息表：姓名、用户名、分公司、部门、岗位、电话号码
emps = [['姓名', '用户名', '分公司', '部门', '岗位', '电话号码']]
n = 0


def emp(name, branch, dept, pos):
    global n
    n += 1
    emps.append([name, f'E2E{n:04d}', branch, dept, pos, f'1390000{n:04d}'])


emp('测试甲', '总公司', '公司领导', '董事长')
emp('测试乙', '总公司', '财务部', '部长')
for b in BRANCHES:
    emp(f'{b[:2]}经理', b, b, '经理')          # 分公司直属人员
    for s in SUBS:
        emp(f'{b[:2]}{s[:2]}长', b, s, '部长')
        for i in range(1, 4):
            emp(f'{b[:2]}{s[:2]}员{i}', b, s, '专员')
save(emps, 'employees.xlsx')

# 功能权限清单：标题行 + 表头；系统、模块列用合并单元格
perms = [['市政公司应用软件功能与权限清单', None, None, None, None, None, None, None, None],
         ['软件系统', '功能模块', '功能点', '功能说明', '系统管理员', '公司领导', '部门经理', '项目经理', '普通员工'],
         ['OA协同办公系统', '公文管理', '收文登记与流转', '说明', '全部', '查看', '查看/编辑', '查看', '查看'],
         [None, None, '发文拟稿与审核', '说明', '全部', '查看/审批', '查看/编辑/审批', '查看', '查看/新增'],
         ['人力资源管理系统', '员工信息管理', '员工台账管理', '说明', '全部', '查看', '查看/编辑', '查看', '查看/编辑本人'],
         [None, '考勤与休假管理', '考勤管理', '说明', '全部', '查看', '查看/编辑', '查看', '查看/编辑本人']]
save(perms, 'permissions.xlsx', merges=['A3:A4', 'B3:B4', 'A5:A6'])
print('ok', len(depts) - 1, 'depts', len(emps) - 1, 'employees')
