#!/usr/bin/env node
// 生成 StarUML 模型 diagrams/rbac.mdj（ER图 / 用例总图 / 领域模型），并做结构校验。纯 Node，无依赖。
//
//   node diagrams/tools/gen-mdj.mjs                       生成 + 校验
//   node diagrams/tools/gen-mdj.mjs --check-only          只校验已有的 rbac.mdj
//   node diagrams/tools/gen-mdj.mjs --samples <目录>      另外与官方样例逐类型比对键集合与子视图结构
//                                                        （样例：github.com/staruml/staruml-samples 的 ERDExample.mdj、UMLExample.mdj）
//   node diagrams/tools/gen-mdj.mjs --out <文件>          输出到别处
//
// 结构照搬 staruml-samples 中 ERDExample.mdj（ERDEntity / ERDColumn / ERDRelationship 及其视图）
// 与 UMLExample.mdj（UMLActor / UMLUseCase / UMLInclude / UMLGeneralization / UMLClass / UMLAttribute /
// UMLAssociation / UMLNoteView 及其视图）。nullable / unique / documentation 三个属性样例中未出现，
// 取自同仓库 StarUML-metamodel_v6.mdj 的元模型定义。
//
// 注意：重新生成会覆盖在 StarUML 里手工调整过的布局。

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const argVal = (k) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : undefined; };
const OUT = path.resolve(argVal('--out') ?? path.join(here, '..', 'rbac.mdj'));
const SAMPLES = argVal('--samples');
const CHECK_ONLY = args.includes('--check-only');

// ---------------------------------------------------------------------------
// 通用
// ---------------------------------------------------------------------------

let seq = 0;
// 样例中的 _id 是 14 字节的 base64（20 个字符，如 AAAAAAFLzoMENm7huq4=）。这里用固定前缀 + 计数器，保证每次生成结果相同、便于 diff。
function newId() {
  const b = Buffer.alloc(14);
  b.writeUInt32BE(0x00000000, 0);
  b.writeUInt16BE(0x0001, 4);
  b.writeBigUInt64BE(0x9a5e000000000000n + BigInt(++seq), 6);
  return b.toString('base64');
}
const ref = (o) => ({ $ref: o._id });
const FONT = 'Arial;13;0';
const FONT_B = 'Arial;13;1';
const textW = (s) => [...s].reduce((w, ch) => w + (ch.charCodeAt(0) > 0x2e80 ? 13 : 7.2), 0);

function rectCenter(r) { return [r.left + r.width / 2, r.top + r.height / 2]; }
// 从 a 的中心指向 b 的中心，与 a 的边框求交点
function clip(r, tx, ty) {
  const [cx, cy] = rectCenter(r);
  const dx = tx - cx, dy = ty - cy;
  if (dx === 0 && dy === 0) return [cx, cy];
  const s = Math.min(dx ? (r.width / 2) / Math.abs(dx) : Infinity, dy ? (r.height / 2) / Math.abs(dy) : Infinity);
  return [Math.round(cx + dx * s), Math.round(cy + dy * s)];
}
// 两个节点之间的直线；offset 用于同一对节点之间有多条边时错开
function linePoints(a, b, offset = 0) {
  if (a === b) {
    const r = a.left + a.width, bt = a.top + a.height, o = 30 + Math.abs(offset);
    return `${r}:${a.top + 20};${r + o}:${a.top + 20};${r + o}:${bt + o};${r - 40}:${bt + o};${r - 40}:${bt}`;
  }
  let [ax, ay] = rectCenter(a), [bx, by] = rectCenter(b);
  if (offset) {
    const dx = bx - ax, dy = by - ay, len = Math.hypot(dx, dy) || 1;
    const nx = -dy / len * offset, ny = dx / len * offset;
    ax += nx; ay += ny; bx += nx; by += ny;
  }
  const shift = (r, x, y) => ({ ...r, left: r.left + (x - rectCenter(r)[0]), top: r.top + (y - rectCenter(r)[1]) });
  const p1 = clip(shift(a, ax, ay), bx, by), p2 = clip(shift(b, bx, by), ax, ay);
  return `${p1[0]}:${p1[1]};${p2[0]}:${p2[1]}`;
}
function midOf(points) {
  const ps = points.split(';').map((p) => p.split(':').map(Number));
  const [x1, y1] = ps[0], [x2, y2] = ps[ps.length - 1];
  return [Math.round((x1 + x2) / 2), Math.round((y1 + y2) / 2)];
}

function labelView(parent, o = {}) {
  const v = { _type: 'LabelView', _id: newId(), _parent: ref(parent) };
  if (o.visible === false) v.visible = false;
  v.font = o.font ?? FONT;
  if (o.left !== undefined) v.left = o.left;
  if (o.top !== undefined) v.top = o.top;
  if (o.width !== undefined) v.width = o.width;
  v.height = 13;
  if (o.text !== undefined) v.text = o.text;
  if (o.horizontalAlignment !== undefined) v.horizontalAlignment = o.horizontalAlignment;
  if (o.wordWrap) v.wordWrap = true;
  return v;
}

function edgeLabel(edge, model, o) {
  const v = { _type: 'EdgeLabelView', _id: newId(), _parent: ref(edge) };
  if (model) v.model = ref(model);
  if (o.visible === false) v.visible = false;
  if (o.visible === null) v.visible = null;
  v.font = FONT;
  v.left = o.left; v.top = o.top;
  if (o.width !== undefined) v.width = o.width;
  v.height = 13;
  v.alpha = o.alpha; v.distance = o.distance;
  v.hostEdge = ref(edge);
  if (o.edgePosition !== undefined) v.edgePosition = o.edgePosition;
  if (o.text !== undefined) v.text = o.text;
  return v;
}

// UMLNameCompartmentView：stereotype / name / namespace / property 四个 LabelView（同 UMLExample.mdj）
function nameCompartment(view, model, name, box, ns, o = {}) {
  const c = { _type: 'UMLNameCompartmentView', _id: newId(), _parent: ref(view), model: ref(model) };
  const st = labelView(c, o.stereotype ? { left: box.left + 5, top: box.top + 5, width: box.width - 10, text: `«${o.stereotype}»` } : { visible: false, left: box.left, top: box.top });
  const nm = labelView(c, { font: FONT_B, left: box.left + 5, top: box.top + (o.stereotype ? 20 : 7), width: box.width - 10, text: name, wordWrap: o.wordWrap });
  const nsl = labelView(c, { visible: false, left: box.left, top: box.top, width: Math.max(80, textW(`(from ${ns})`)), text: `(from ${ns})` });
  const pr = labelView(c, { visible: false, left: box.left, top: box.top, horizontalAlignment: 1, wordWrap: o.wordWrap });
  c.subViews = [st, nm, nsl, pr];
  Object.assign(c, { font: FONT, left: box.left, top: box.top, width: box.width, height: box.height });
  Object.assign(c, { stereotypeLabel: ref(st), nameLabel: ref(nm), namespaceLabel: ref(nsl), propertyLabel: ref(pr) });
  // 键顺序与样例一致：subViews 在 font 之前
  return reorder(c, ['_type', '_id', '_parent', 'model', 'subViews']);
}
function hiddenCompartment(type, view, model, left, top) {
  return { _type: type, _id: newId(), _parent: ref(view), model: ref(model), visible: false, font: FONT, left, top, width: 10, height: 10 };
}
function reorder(o, first) {
  const r = {};
  for (const k of first) if (k in o) r[k] = o[k];
  for (const k of Object.keys(o)) if (!(k in r)) r[k] = o[k];
  return r;
}

// ---------------------------------------------------------------------------
// 1. ER 图（与 V1__rbac0_baseline.sql、V2__grant_version.sql、V3__reserve_internal_user_ids.sql 一致）
// ---------------------------------------------------------------------------
// 列：[列名, 类型, 标记, 说明]；标记：PK 主键，UK 唯一，N 可空，FK:表 外键
const TABLES = [
  ['sys_company', '公司', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['company_code', 'VARCHAR(32)', 'UK'],
    ['company_name', 'VARCHAR(128)', ''],
    ['created_at', 'DATETIME', '', '默认当前时间'],
  ]],
  ['sys_department', '部门（每家公司一棵树）。UK(company_id, parent_id, dept_name)', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['company_id', 'BIGINT', 'FK:sys_company'],
    ['parent_id', 'BIGINT', 'N FK:sys_department', '上级部门；根节点为空'],
    ['dept_name', 'VARCHAR(128)', ''],
    ['path', 'VARCHAR(255)', '', '物化路径 /祖先id/.../本id/'],
    ['level', 'INT', '', '根为 1'],
    ['manager_name', 'VARCHAR(64)', 'N', '部门表登记的负责人姓名'],
    ['manager_user_id', 'BIGINT', 'N FK:sys_user', '负责人对应的用户；只是组织信息，不产生管理权限'],
    ['sort_order', 'INT', '', '默认 0'],
    ['created_at', 'DATETIME', ''],
  ]],
  ['sys_user', '用户。V3：id 1–9999 预留给内部账号（root、业务系统账号），员工与临时用户自 10000 起自增', [
    ['id', 'BIGINT', 'PK', '1–9999 内部账号；10000 起员工（AUTO_INCREMENT = 10000）'],
    ['company_id', 'BIGINT', 'FK:sys_company'],
    ['department_id', 'BIGINT', 'FK:sys_department', '一个用户只属于一个部门'],
    ['username', 'VARCHAR(64)', 'UK', '登录名'],
    ['password_hash', 'VARCHAR(100)', '', 'BCrypt'],
    ['real_name', 'VARCHAR(64)', ''],
    ['user_type', 'VARCHAR(16)', '', 'EMPLOYEE | TEMPORARY | SYSTEM'],
    ['employee_no', 'VARCHAR(32)', 'N UK', '工号；临时用户没有'],
    ['phone', 'VARCHAR(20)', 'N'],
    ['position', 'VARCHAR(32)', 'N', '岗位；不参与鉴权'],
    ['status', 'VARCHAR(16)', '', 'ACTIVE | DISABLED | DELETED'],
    ['status_reason', 'VARCHAR(255)', 'N'],
    ['expires_at', 'DATETIME', 'N', '临时用户必填'],
    ['login_fail_count', 'INT', ''],
    ['locked_until', 'DATETIME', 'N'],
    ['must_change_pwd', 'TINYINT', ''],
    ['token_version', 'INT', '', '修改口令时加一'],
    ['created_at', 'DATETIME', ''],
    ['updated_at', 'DATETIME', ''],
    ['deleted_at', 'DATETIME', 'N'],
  ]],
  ['sys_software_system', '业务系统', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['system_code', 'VARCHAR(32)', 'UK', '权限码第一段'],
    ['system_name', 'VARCHAR(64)', ''],
    ['sort_order', 'INT', ''],
  ]],
  ['sys_function_module', '功能模块。UK(system_id, module_code)', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['system_id', 'BIGINT', 'FK:sys_software_system'],
    ['module_code', 'VARCHAR(32)', ''],
    ['module_name', 'VARCHAR(64)', ''],
    ['sort_order', 'INT', ''],
  ]],
  ['sys_resource', '资源', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['module_id', 'BIGINT', 'FK:sys_function_module'],
    ['resource_code', 'VARCHAR(96)', 'UK', '如 oa:m01:f02、rbac:user'],
    ['resource_name', 'VARCHAR(128)', ''],
    ['resource_type', 'VARCHAR(16)', '', 'BUSINESS | MENU | API'],
    ['description', 'VARCHAR(512)', 'N'],
    ['sort_order', 'INT', ''],
  ]],
  ['sys_action', '操作', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['action_code', 'VARCHAR(32)', 'UK'],
    ['action_name', 'VARCHAR(32)', ''],
    ['sort_order', 'INT', ''],
  ]],
  ['sys_permission', '权限 = 资源 × 操作。UK(resource_id, action_id)', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['resource_id', 'BIGINT', 'FK:sys_resource'],
    ['action_id', 'BIGINT', 'FK:sys_action'],
    ['permission_code', 'VARCHAR(128)', 'UK', 'resource_code:action_code'],
  ]],
  ['sys_role', '角色。company_id 为空 = 全局角色', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['company_id', 'BIGINT', 'N FK:sys_company'],
    ['role_code', 'VARCHAR(64)', 'UK'],
    ['role_name', 'VARCHAR(64)', ''],
    ['description', 'VARCHAR(255)', 'N'],
    ['status', 'VARCHAR(16)', '', 'ENABLED | DISABLED'],
    ['builtin', 'TINYINT', '', '内置角色不可删除'],
    ['created_at', 'DATETIME', ''],
    ['updated_at', 'DATETIME', ''],
  ]],
  ['sys_role_permission', '角色-权限。UK(role_id, permission_id)；fk_rp_role ON DELETE CASCADE', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['role_id', 'BIGINT', 'FK:sys_role'],
    ['permission_id', 'BIGINT', 'FK:sys_permission'],
    ['self_only', 'TINYINT', '', '只限本人'],
    ['granted_by', 'BIGINT', 'N', '授权人，只用于审计（无外键）'],
    ['granted_at', 'DATETIME', ''],
  ]],
  ['sys_user_role', '用户-角色指派。UK(user_id, role_id, scope_dept_id)', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['user_id', 'BIGINT', 'FK:sys_user'],
    ['role_id', 'BIGINT', 'FK:sys_role'],
    ['scope_dept_id', 'BIGINT', 'FK:sys_department', '适用部门（含下级）'],
    ['granted_by', 'BIGINT', 'N', '只用于审计（无外键）'],
    ['granted_at', 'DATETIME', ''],
    ['reason', 'VARCHAR(255)', 'N'],
  ]],
  ['sys_menu', '菜单', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['parent_id', 'BIGINT', 'N FK:sys_menu'],
    ['resource_id', 'BIGINT', 'N FK:sys_resource', '目录节点为空'],
    ['menu_name', 'VARCHAR(64)', ''],
    ['route_path', 'VARCHAR(128)', 'N'],
    ['component_key', 'VARCHAR(64)', 'N'],
    ['icon', 'VARCHAR(32)', 'N'],
    ['sort_order', 'INT', ''],
  ]],
  ['sys_audit_log', '审计日志（只插入，无外键）', [
    ['id', 'BIGINT', 'PK', '自增'],
    ['occurred_at', 'DATETIME(3)', ''],
    ['actor_id', 'BIGINT', 'N'],
    ['actor_name', 'VARCHAR(64)', 'N', '用户名快照'],
    ['client_ip', 'VARCHAR(45)', 'N'],
    ['event_type', 'VARCHAR(32)', ''],
    ['target_type', 'VARCHAR(16)', 'N'],
    ['target_id', 'VARCHAR(64)', 'N'],
    ['target_name', 'VARCHAR(128)', 'N'],
    ['detail', 'VARCHAR(1000)', 'N'],
    ['result', 'VARCHAR(16)', '', 'SUCCESS | DENIED | FAILED'],
    ['reason', 'VARCHAR(255)', 'N'],
    ['permission_code', 'VARCHAR(128)', 'N'],
    ['latency_us', 'INT', 'N'],
    ['cache_hit', 'TINYINT', 'N'],
  ]],
  ['sys_grant_version', '授权数据版本号（V2），只有一行 id = 1', [
    ['id', 'TINYINT', 'PK', '只有一行，id = 1'],
    ['version', 'BIGINT', ''],
  ]],
];
// 外键关系（与 DDL 的 17 个 CONSTRAINT 一一对应）：[父表, 子表, 子表列, 名称, 父端基数]
const FKS = [
  ['sys_company', 'sys_department', 'company_id', '拥有', '1'],
  ['sys_department', 'sys_department', 'parent_id', '上级', '0..1'],
  ['sys_user', 'sys_department', 'manager_user_id', '负责人', '0..1'],
  ['sys_company', 'sys_user', 'company_id', '雇佣', '1'],
  ['sys_department', 'sys_user', 'department_id', '所属', '1'],
  ['sys_software_system', 'sys_function_module', 'system_id', '包含', '1'],
  ['sys_function_module', 'sys_resource', 'module_id', '包含', '1'],
  ['sys_resource', 'sys_permission', 'resource_id', '资源', '1'],
  ['sys_action', 'sys_permission', 'action_id', '操作', '1'],
  ['sys_company', 'sys_role', 'company_id', '公司内角色', '0..1'],
  ['sys_role', 'sys_role_permission', 'role_id', '授予', '1'],
  ['sys_permission', 'sys_role_permission', 'permission_id', '被授予', '1'],
  ['sys_user', 'sys_user_role', 'user_id', '被指派', '1'],
  ['sys_role', 'sys_user_role', 'role_id', '指派', '1'],
  ['sys_department', 'sys_user_role', 'scope_dept_id', '适用部门', '1'],
  ['sys_resource', 'sys_menu', 'resource_id', '菜单挂资源', '0..1'],
  ['sys_menu', 'sys_menu', 'parent_id', '上级菜单', '0..1'],
];
// 布局：四列，每列自上而下
const ER_COLUMNS = [
  ['sys_company', 'sys_department', 'sys_audit_log'],
  ['sys_user', 'sys_user_role', 'sys_grant_version'],
  ['sys_role', 'sys_role_permission', 'sys_permission', 'sys_action'],
  ['sys_software_system', 'sys_function_module', 'sys_resource', 'sys_menu'],
];

function buildER(project) {
  const dm = { _type: 'ERDDataModel', _id: newId(), _parent: ref(project), name: '数据模型', ownedElements: [] };
  const dg = { _type: 'ERDDiagram', _id: newId(), _parent: ref(dm), name: 'ER图', defaultDiagram: true, ownedViews: [] };
  dm.ownedElements.push(dg);
  const ent = {}, col = {};
  for (const [tname, doc, cols] of TABLES) {
    const e = { _type: 'ERDEntity', _id: newId(), _parent: ref(dm), name: tname, documentation: doc, columns: [] };
    ent[tname] = e;
    for (const [cname, ctype, flags, cdoc] of cols) {
      const m = /^(\w+)(?:\((\d+)\))?$/.exec(ctype);
      const c = { _type: 'ERDColumn', _id: newId(), _parent: ref(e), name: cname, type: m[1], length: m[2] ?? 0 };
      const fl = flags.split(/\s+/).filter(Boolean);
      if (fl.includes('PK')) c.primaryKey = true;
      if (fl.some((f) => f.startsWith('FK:'))) c.foreignKey = true;
      if (fl.includes('N')) c.nullable = true;
      if (fl.includes('UK')) c.unique = true;
      if (cdoc) c.documentation = cdoc;
      c._fk = fl.find((f) => f.startsWith('FK:'))?.slice(3);
      e.columns.push(c);
      col[`${tname}.${cname}`] = c;
    }
    dm.ownedElements.push(e);
  }
  // referenceTo 指向父表主键列
  for (const c of Object.values(col)) { if (c._fk) c.referenceTo = ref(col[`${c._fk}.id`]); delete c._fk; }
  // 实体视图
  const views = {};
  const COLW = 330, GAP = 70;
  ER_COLUMNS.forEach((names, ci) => {
    let top = 40;
    for (const tname of names) {
      const e = ent[tname];
      const w = Math.max(220, Math.ceil(Math.max(textW(tname) + 20, ...e.columns.map((c) => textW(`${c.name}: ${c.type}${c.length ? `(${c.length})` : ''}`) + 50))));
      const left = 40 + ci * COLW, h = 23 + e.columns.length * 15 + 8;
      const v = { _type: 'ERDEntityView', _id: newId(), _parent: ref(dg), model: ref(e) };
      const nl = labelView(v, { font: FONT_B, left, top: top + 5, width: w, text: tname });
      const cc = { _type: 'ERDColumnCompartmentView', _id: newId(), _parent: ref(v), model: ref(e), subViews: [] };
      e.columns.forEach((c, i) => cc.subViews.push({ _type: 'ERDColumnView', _id: newId(), _parent: ref(cc), model: ref(c), font: FONT, left: left + 5, top: top + 28 + i * 15, width: w - 10, height: 13 }));
      Object.assign(cc, { font: FONT, left, top: top + 23, width: w, height: e.columns.length * 15 + 8 });
      v.subViews = [nl, cc];
      Object.assign(v, { font: FONT, left, top, width: w, height: h, nameLabel: ref(nl), columnCompartment: ref(cc) });
      views[tname] = v;
      dg.ownedViews.push(v);
      top += h + GAP;
    }
  });
  // 关系：ERDRelationship 归属父表实体（end1），end2 为子表；视图 tail = end1，head = end2（同 ERDExample.mdj）
  const pairCount = {};
  for (const [parent, child, column, name, card1] of FKS) {
    const pe = ent[parent], ce = ent[child];
    const r = { _type: 'ERDRelationship', _id: newId(), _parent: ref(pe), name: `${name}（${child}.${column}）` };
    r.end1 = { _type: 'ERDRelationshipEnd', _id: newId(), _parent: ref(r), reference: ref(pe) };
    if (card1 !== '1') r.end1.cardinality = card1;
    r.end2 = { _type: 'ERDRelationshipEnd', _id: newId(), _parent: ref(r), reference: ref(ce), cardinality: '0..*' };
    (pe.ownedElements ??= []).push(r);
    const key = [parent, child].sort().join('|');
    const k = (pairCount[key] = (pairCount[key] ?? 0) + 1);
    const offset = parent === child ? 0 : (k - 1) * 14;
    const tv = views[parent], hv = views[child];
    const points = linePoints(tv, hv, offset);
    const [mx, my] = midOf(points);
    const rv = { _type: 'ERDRelationshipView', _id: newId(), _parent: ref(dg), model: ref(r) };
    const n1 = edgeLabel(rv, null, { left: mx, top: my, width: textW(r.name), alpha: 1.5707963267948966, distance: 15, edgePosition: 1, text: r.name });
    const n2 = edgeLabel(rv, null, { left: mx, top: my, alpha: 0.5235987755982988, distance: 30, edgePosition: 2 });
    const n3 = edgeLabel(rv, null, { left: mx, top: my, alpha: -0.5235987755982988, distance: 30 });
    rv.subViews = [n1, n2, n3];
    Object.assign(rv, { font: FONT, head: ref(hv), tail: ref(tv), lineStyle: 1, points, nameLabel: ref(n1), tailNameLabel: ref(n2), headNameLabel: ref(n3) });
    dg.ownedViews.push(rv);
  }
  // 键顺序：name, documentation, ownedElements, columns（同样例）
  for (const e of Object.values(ent)) { const o = reorder(e, ['_type', '_id', '_parent', 'name', 'documentation', 'ownedElements', 'columns']); for (const k of Object.keys(e)) delete e[k]; Object.assign(e, o); }
  return dm;
}

// ---------------------------------------------------------------------------
// UML 公共：分类器视图（参与者 / 用例 / 类）、关联、泛化、包含、注释
// ---------------------------------------------------------------------------

function classifierView(kind, diagram, model, ns, box, attrs = []) {
  const type = { actor: 'UMLActorView', usecase: 'UMLUseCaseView', class: 'UMLClassView' }[kind];
  const v = { _type: type, _id: newId(), _parent: ref(diagram), model: ref(model) };
  let ncBox;
  if (kind === 'actor') ncBox = { left: box.left, top: box.top + 54, width: box.width, height: 25 };
  else if (kind === 'usecase') ncBox = { left: box.left + 15, top: box.top + (box.height - 25) / 2, width: box.width - 30, height: 25 };
  else ncBox = { left: box.left, top: box.top, width: box.width, height: 25 };
  const nc = nameCompartment(v, model, model.name, ncBox, ns, { wordWrap: kind !== 'class' });
  let ac;
  if (kind === 'class') {
    ac = { _type: 'UMLAttributeCompartmentView', _id: newId(), _parent: ref(v), model: ref(model), subViews: [] };
    attrs.forEach((a, i) => ac.subViews.push({ _type: 'UMLAttributeView', _id: newId(), _parent: ref(ac), model: ref(a), font: FONT, left: box.left + 5, top: box.top + 30 + i * 15, width: box.width - 10, height: 13, text: `+${a.name}`, horizontalAlignment: 0 }));
    Object.assign(ac, { font: FONT, left: box.left, top: box.top + 25, width: box.width, height: attrs.length * 15 + 10 });
  } else {
    ac = hiddenCompartment('UMLAttributeCompartmentView', v, model, box.left, box.top);
  }
  const oc = hiddenCompartment('UMLOperationCompartmentView', v, model, box.left, box.top);
  const rc = hiddenCompartment('UMLReceptionCompartmentView', v, model, box.left, box.top);
  const tc = hiddenCompartment('UMLTemplateParameterCompartmentView', v, model, box.left, box.top);
  v.subViews = [nc, ac, oc, rc, tc];
  let ec;
  if (kind === 'usecase') { ec = hiddenCompartment('UMLExtensionPointCompartmentView', v, model, box.left, box.top); v.subViews.push(ec); }
  Object.assign(v, { font: FONT, containerChangeable: true, left: box.left, top: box.top, width: box.width, height: box.height, nameCompartment: ref(nc) });
  if (kind !== 'class') Object.assign(v, { wordWrap: true, suppressAttributes: true });
  v.suppressOperations = true;
  Object.assign(v, { attributeCompartment: ref(ac), operationCompartment: ref(oc), receptionCompartment: ref(rc), templateParameterCompartment: ref(tc) });
  if (ec) v.extensionPointCompartment = ref(ec);
  return v;
}

// UMLAssociation 归属 end1 的元素；视图 tail = end1，head = end2（同 UMLExample.mdj）
function association(owner, other, o = {}) {
  const a = { _type: 'UMLAssociation', _id: newId(), _parent: ref(owner) };
  if (o.name) a.name = o.name;
  a.end1 = { _type: 'UMLAssociationEnd', _id: newId(), _parent: ref(a), reference: ref(owner) };
  if (o.m1) a.end1.multiplicity = o.m1;
  a.end2 = { _type: 'UMLAssociationEnd', _id: newId(), _parent: ref(a), reference: ref(other) };
  if (o.m2) a.end2.multiplicity = o.m2;
  (owner.ownedElements ??= []).push(a);
  return a;
}
function associationView(diagram, a, tailV, headV, o = {}) {
  const v = { _type: 'UMLAssociationView', _id: newId(), _parent: ref(diagram), model: ref(a) };
  const points = o.points ?? linePoints(tailV, headV);
  const [mx, my] = midOf(points);
  const L = (model, p) => edgeLabel(v, model, { left: mx, top: my, ...p });
  const name = L(a, a.name ? { width: textW(a.name), alpha: 1.5707963267948966, distance: 15, edgePosition: 1, text: a.name } : { visible: false, alpha: 1.5707963267948966, distance: 15, edgePosition: 1 });
  const st = L(a, { visible: null, alpha: 1.5707963267948966, distance: 30, edgePosition: 1 });
  const pr = L(a, { visible: false, alpha: -1.5707963267948966, distance: 15, edgePosition: 1 });
  const tRole = L(a.end1, { visible: false, alpha: 0.5235987755982988, distance: 30, edgePosition: 2 });
  const tProp = L(a.end1, { visible: false, alpha: 0.7853981633974483, distance: 40, edgePosition: 2 });
  const tMul = L(a.end1, a.end1.multiplicity ? { width: textW(a.end1.multiplicity), alpha: -0.5235987755982988, distance: 25, edgePosition: 2, text: a.end1.multiplicity } : { visible: false, alpha: -0.5235987755982988, distance: 25, edgePosition: 2 });
  const hRole = L(a.end2, { visible: false, alpha: -0.5235987755982988, distance: 30 });
  const hProp = L(a.end2, { visible: false, alpha: -0.7853981633974483, distance: 40 });
  const hMul = L(a.end2, a.end2.multiplicity ? { width: textW(a.end2.multiplicity), alpha: 0.5235987755982988, distance: 25, text: a.end2.multiplicity } : { visible: false, alpha: 0.5235987755982988, distance: 25 });
  const q1 = { _type: 'UMLQualifierCompartmentView', _id: newId(), _parent: ref(v), model: ref(a.end1), visible: false, font: FONT, left: mx, top: my, width: 10, height: 10 };
  const q2 = { _type: 'UMLQualifierCompartmentView', _id: newId(), _parent: ref(v), model: ref(a.end2), visible: false, font: FONT, left: mx, top: my, width: 10, height: 10 };
  v.subViews = [name, st, pr, tRole, tProp, tMul, hRole, hProp, hMul, q1, q2];
  Object.assign(v, {
    font: FONT, head: ref(headV), tail: ref(tailV), lineStyle: 1, points, showVisibility: true,
    nameLabel: ref(name), stereotypeLabel: ref(st), propertyLabel: ref(pr),
    tailRoleNameLabel: ref(tRole), tailPropertyLabel: ref(tProp), tailMultiplicityLabel: ref(tMul),
    headRoleNameLabel: ref(hRole), headPropertyLabel: ref(hProp), headMultiplicityLabel: ref(hMul),
    tailQualifiersCompartment: ref(q1), headQualifiersCompartment: ref(q2),
  });
  return v;
}
// 有向关系（UMLGeneralization / UMLInclude）：归属 source；视图 tail = source，head = target
function directed(type, source, target) {
  const r = { _type: type, _id: newId(), _parent: ref(source), source: ref(source), target: ref(target) };
  (source.ownedElements ??= []).push(r);
  return r;
}
function directedView(type, diagram, r, tailV, headV, stereotypeText) {
  const v = { _type: type, _id: newId(), _parent: ref(diagram), model: ref(r) };
  const points = linePoints(tailV, headV);
  const [mx, my] = midOf(points);
  const n = edgeLabel(v, r, { visible: false, left: mx, top: my, alpha: 1.5707963267948966, distance: 15, edgePosition: 1 });
  const s = edgeLabel(v, r, stereotypeText ? { left: mx, top: my, width: textW(stereotypeText), alpha: -1.5385490843842793, distance: 31, edgePosition: 1, text: stereotypeText } : { visible: null, left: mx, top: my, alpha: 1.5707963267948966, distance: 30, edgePosition: 1 });
  const p = edgeLabel(v, r, { visible: false, left: mx, top: my, alpha: -1.5707963267948966, distance: 15, edgePosition: 1 });
  v.subViews = [n, s, p];
  Object.assign(v, { font: FONT, head: ref(headV), tail: ref(tailV), lineStyle: 1, points, showVisibility: true, nameLabel: ref(n), stereotypeLabel: ref(s), propertyLabel: ref(p) });
  return v;
}
function noteViews(diagram, text, box, targetV) {
  const n = { _type: 'UMLNoteView', _id: newId(), _parent: ref(diagram), font: FONT, left: box.left, top: box.top, width: box.width, height: box.height, text };
  const l = { _type: 'UMLNoteLinkView', _id: newId(), _parent: ref(diagram), font: FONT, head: ref(targetV), tail: ref(n), points: linePoints(n, targetV) };
  return [n, l];
}

// ---------------------------------------------------------------------------
// 2. 用例总图（docs/01-srs.md §4.1.2 参与者、§4.1.4 / §4.2 用例）
// ---------------------------------------------------------------------------
const ACTORS = [
  ['U', '普通用户', '正式员工或临时用户：登录、改口令、查看自己的权限'],
  ['RA', '权限管理员', '持有 RBAC_ADMIN；在适用部门子树内管理用户与角色指派'],
  ['AU', '审计员', '持有 AUDITOR；只读审计日志与统计'],
  ['SA', '超级管理员', '内置账号 root；越过权限引擎；负责数据导入、全局角色与权限定义'],
  ['BIZ', '业务系统', '外部系统，调用鉴权接口（本阶段由外部模拟业务系统代表）'],
];
// [键, 名称, 需求编号, 列, 行]
const USECASES = [
  ['LOGIN', '登录', 'AUTH-001', 0, 0], ['LOGOUT', '登出', 'AUTH-002', 0, 1], ['PWD', '修改口令', 'AUTH-004', 0, 2],
  ['ME', '查看本人信息与权限', 'AUTH-003 / IDM-007', 0, 3], ['MENU', '获取动态菜单', 'MENU-001', 0, 4],
  ['EXPLAIN', '权限解释', 'AUTHZ-002', 1, 2], ['DECIDE', '授权判定', 'AUTHZ-003 / AUTHZ-004（判定过程）', 2, 3],
  ['AUTHZ', '鉴权', 'AUTHZ-001', 2, 5],
  ['USER', '管理用户', 'IDM-001 ~ IDM-007；用户编号 1–9999 预留给内部账号，员工自 10000 起（10/15 课堂要求）', 1, 8],
  ['ASSIGN', '分配角色', 'ASGN-001', 1, 5], ['REVOKE', '回收角色', 'ASGN-002；管理员可以回收本人的指派（10/15 课堂要求）；只删这一条，不级联', 1, 7],
  ['BATCH', '批量分配 / 回收', 'ASGN-003、ASGN-004', 1, 6],
  ['ROLE', '管理角色与角色授权', 'ROLE-001 ~ ROLE-007', 0, 7], ['MENUADM', '管理菜单', 'MENU-002', 0, 8],
  ['ORG', '浏览组织', 'ORG-001、ORG-002', 0, 9], ['PERMV', '查看权限定义', 'PERM-001 ~ PERM-004（查看）', 0, 10],
  ['AUDIT', '查询审计日志', 'AUDIT-002', 1, 9], ['DASH', '查看 Dashboard', 'OBSV-001', 1, 10], ['HEAT', '查看权限使用热力图', 'OBSV-002', 1, 11],
  ['PERMC', '新建资源与权限', 'PERM-002、PERM-004（新建）', 0, 12], ['IMPORG', '导入部门与员工', 'ORG-003', 1, 12], ['IMPPERM', '导入功能权限清单', 'ORG-004', 1, 13],
];
const ACTOR_UC = {
  U: ['LOGIN', 'LOGOUT', 'PWD', 'ME', 'MENU', 'EXPLAIN'],
  RA: ['USER', 'ASSIGN', 'REVOKE', 'BATCH', 'ROLE', 'MENUADM', 'ORG', 'PERMV', 'AUDIT', 'DASH', 'HEAT'],
  AU: ['AUDIT', 'DASH', 'HEAT'],
  SA: ['PERMC', 'IMPORG', 'IMPPERM'],
  BIZ: ['AUTHZ'],
};
const GENERALIZATIONS = [['RA', 'U'], ['AU', 'U'], ['SA', 'RA']];
const INCLUDES = [['AUTHZ', 'DECIDE'], ['EXPLAIN', 'DECIDE'], ['BATCH', 'ASSIGN'], ['BATCH', 'REVOKE']];

function buildUseCase(project) {
  const NS = '用例模型';
  const m = { _type: 'UMLModel', _id: newId(), _parent: ref(project), name: NS, ownedElements: [] };
  const dg = { _type: 'UMLUseCaseDiagram', _id: newId(), _parent: ref(m), name: '用例总图', ownedViews: [] };
  const subj = { _type: 'UMLUseCaseSubject', _id: newId(), _parent: ref(m), name: '权限中心' };
  m.ownedElements.push(dg, subj);
  const el = {}, vw = {};
  for (const [k, name, doc] of ACTORS) { el[k] = { _type: 'UMLActor', _id: newId(), _parent: ref(m), name, documentation: doc }; m.ownedElements.push(el[k]); }
  for (const [k, name, doc] of USECASES) { el[k] = { _type: 'UMLUseCase', _id: newId(), _parent: ref(m), name, documentation: doc }; m.ownedElements.push(el[k]); }

  // 布局：参与者在左（业务系统在右），系统边界内三列用例
  const SX = 260, SY = 40, UCW = 170, UCH = 46, COLG = 60, ROWH = 62;
  const rows = Math.max(...USECASES.map((u) => u[4])) + 1;
  const sBox = { left: SX, top: SY, width: 3 * UCW + 2 * COLG + 80, height: 50 + rows * ROWH + 20 };
  const sv = { _type: 'UMLUseCaseSubjectView', _id: newId(), _parent: ref(dg), model: ref(subj) };
  const snc = nameCompartment(sv, subj, subj.name, { left: sBox.left, top: sBox.top, width: sBox.width, height: 25 }, NS);
  sv.subViews = [snc];
  Object.assign(sv, { font: FONT, ...sBox, nameCompartment: ref(snc) });
  dg.ownedViews.push(sv);

  const actorPos = { U: [60, 120], RA: [60, 440], AU: [170, 700], SA: [60, 900], BIZ: [sBox.left + sBox.width + 80, SY + 50 + 5 * ROWH - 20] };
  for (const [k, name] of ACTORS) {
    const w = Math.max(70, Math.ceil(textW(name)) + 20);
    vw[k] = classifierView('actor', dg, el[k], NS, { left: actorPos[k][0], top: actorPos[k][1], width: w, height: 80 });
    dg.ownedViews.push(vw[k]);
  }
  for (const [k, , , c, r] of USECASES) {
    vw[k] = classifierView('usecase', dg, el[k], NS, { left: SX + 40 + c * (UCW + COLG), top: SY + 50 + r * ROWH, width: UCW, height: UCH });
    dg.ownedViews.push(vw[k]);
  }
  for (const [s, t] of GENERALIZATIONS) dg.ownedViews.push(directedView('UMLGeneralizationView', dg, directed('UMLGeneralization', el[s], el[t]), vw[s], vw[t]));
  for (const [ak, ucs] of Object.entries(ACTOR_UC)) for (const uk of ucs) dg.ownedViews.push(associationView(dg, association(el[ak], el[uk]), vw[ak], vw[uk]));
  for (const [s, t] of INCLUDES) dg.ownedViews.push(directedView('UMLIncludeView', dg, directed('UMLInclude', el[s], el[t]), vw[s], vw[t], '«include»'));
  const nx = sBox.left + sBox.width + 40;
  dg.ownedViews.push(...noteViews(dg, '可回收本人的指派（10/15 课堂要求）；只删这一条，不级联', { left: nx, top: vw.REVOKE.top - 10, width: 220, height: 45 }, vw.REVOKE));
  dg.ownedViews.push(...noteViews(dg, '用户编号 1–9999 预留给内部账号；员工与临时用户自 10000 起', { left: nx, top: vw.USER.top, width: 220, height: 45 }, vw.USER));
  return m;
}

// ---------------------------------------------------------------------------
// 3. 领域模型（docs/01-srs.md §3.1；RBAC0：无角色继承、无约束）
// ---------------------------------------------------------------------------
// [类名, 属性, 列, 行, 说明]
const CLASSES = [
  ['Company', ['companyCode', 'companyName'], 0, 0, '公司：多公司隔离的边界'],
  ['Department', ['deptName', 'path', 'level', 'managerName'], 1, 0, '部门：每家公司一棵树；path 为物化路径'],
  ['User', ['id', 'username', 'realName', 'userType', 'employeeNo', 'phone', 'status', 'expiresAt'], 2, 0, '用户：只属于一个部门；id 1–9999 预留给内部账号'],
  ['Assignment', ['scopeDept', 'grantedBy', 'reason'], 3, 0, '一次指派 = 用户 + 角色 + 适用部门；适用部门覆盖其全部下级'],
  ['AuditLog', ['谁', '时间', '在哪里', '对谁', '做了什么', '结果', '为什么'], 0, 1, '审计七要素；只插入'],
  ['Menu', ['menuName', 'routePath'], 1, 1, '菜单：叶子菜单挂一个 MENU 资源'],
  ['Role', ['roleCode', 'roleName', 'status', 'builtin'], 2, 1, '角色：只决定权限集合（RBAC0 无继承）'],
  ['RolePermission', ['selfOnly', 'grantedBy'], 3, 1, '角色-权限；selfOnly 表示只限本人'],
  ['SoftwareSystem', ['systemCode', 'systemName'], 0, 2, '业务系统'],
  ['FunctionModule', ['moduleCode', 'moduleName'], 1, 2, '功能模块'],
  ['Resource', ['resourceCode', 'resourceName', 'resourceType'], 2, 2, '资源：BUSINESS / MENU / API'],
  ['Permission', ['permissionCode'], 3, 2, '权限 = 资源 × 操作'],
  ['Action', ['actionCode', 'actionName'], 3, 3, '操作'],
];
// [end1, end2, 名称, end1 多重性, end2 多重性]
const CLASS_ASSOCS = [
  ['Company', 'Department', '', '1', '*'],
  ['Department', 'Department', '下级', '1', '*'],
  ['Department', 'User', '所属', '1', '*'],
  ['Company', 'Role', '公司内角色', '0..1', '*'],
  ['SoftwareSystem', 'FunctionModule', '', '1', '*'],
  ['FunctionModule', 'Resource', '', '1', '*'],
  ['Resource', 'Permission', '', '1', '*'],
  ['Action', 'Permission', '', '1', '*'],
  ['User', 'Assignment', '', '1', '*'],
  ['Role', 'Assignment', '', '1', '*'],
  ['Department', 'Assignment', '适用部门', '1', '*'],
  ['Role', 'RolePermission', '', '1', '*'],
  ['Permission', 'RolePermission', '', '1', '*'],
  ['Menu', 'Resource', '', '*', '0..1'],
  ['Menu', 'Menu', '上级', '*', '0..1'],
];

function buildDomain(project) {
  const NS = '领域模型';
  const m = { _type: 'UMLModel', _id: newId(), _parent: ref(project), name: NS, ownedElements: [] };
  const dg = { _type: 'UMLClassDiagram', _id: newId(), _parent: ref(m), name: '领域模型', defaultDiagram: true, ownedViews: [] };
  m.ownedElements.push(dg);
  const el = {}, vw = {};
  for (const [name, attrs, , , doc] of CLASSES) {
    const c = { _type: 'UMLClass', _id: newId(), _parent: ref(m), name, documentation: doc, attributes: [] };
    for (const a of attrs) c.attributes.push({ _type: 'UMLAttribute', _id: newId(), _parent: ref(c), name: a });
    el[name] = c;
    m.ownedElements.push(c);
  }
  const COLW = 300, CW = 190, RGAP = 110;
  const rowH = {};
  for (const [, attrs, , r] of CLASSES) rowH[r] = Math.max(rowH[r] ?? 0, 25 + attrs.length * 15 + 10 + 10);
  const rowTop = (r) => 40 + [...Array(r).keys()].reduce((s, i) => s + rowH[i] + RGAP, 0);
  for (const [name, , c, r] of CLASSES) {
    const cls = el[name];
    const h = 25 + cls.attributes.length * 15 + 10 + 10;
    vw[name] = classifierView('class', dg, cls, NS, { left: 40 + c * COLW, top: rowTop(r), width: CW, height: h }, cls.attributes);
    dg.ownedViews.push(vw[name]);
  }
  const pairCount = {};
  for (const [a, b, name, m1, m2] of CLASS_ASSOCS) {
    const as = association(el[a], el[b], { name: name || undefined, m1, m2 });
    const key = [a, b].sort().join('|');
    const k = (pairCount[key] = (pairCount[key] ?? 0) + 1);
    dg.ownedViews.push(associationView(dg, as, vw[a], vw[b], a === b ? {} : { points: linePoints(vw[a], vw[b], (k - 1) * 14) }));
  }
  const u = vw.User;
  dg.ownedViews.push(...noteViews(dg, '用户编号 1–9999 预留给内部账号（root、业务系统账号）；员工与临时用户自 10000 起', { left: u.left, top: u.top + u.height + 20, width: 160, height: 70 }, u));
  // 键顺序：attributes 放在 ownedElements 之后（同样例）
  for (const c of Object.values(el)) { const o = reorder(c, ['_type', '_id', '_parent', 'name', 'documentation', 'ownedElements', 'attributes']); for (const kk of Object.keys(c)) delete c[kk]; Object.assign(c, o); }
  return m;
}

// ---------------------------------------------------------------------------
// 校验
// ---------------------------------------------------------------------------
const isView = (t) => /View$/.test(t);
const isDiagram = (t) => /Diagram$/.test(t);
// 视图上「指向自己某个子视图」的引用字段
const SUBVIEW_REFS = ['nameLabel', 'columnCompartment', 'nameCompartment', 'attributeCompartment', 'operationCompartment', 'receptionCompartment', 'templateParameterCompartment', 'extensionPointCompartment', 'stereotypeLabel', 'namespaceLabel', 'propertyLabel', 'tailNameLabel', 'headNameLabel', 'tailRoleNameLabel', 'tailPropertyLabel', 'tailMultiplicityLabel', 'headRoleNameLabel', 'headPropertyLabel', 'headMultiplicityLabel', 'tailQualifiersCompartment', 'headQualifiersCompartment'];
// 样例里没出现、但 StarUML-metamodel_v6.mdj 中定义的属性
const METAMODEL_EXTRA = { ERDColumn: ['nullable', 'unique', 'documentation'], ERDEntity: ['documentation'], UMLActor: ['documentation'], UMLUseCase: ['documentation'], UMLClass: ['documentation'] };

function index(root) {
  const byId = new Map(), parentOf = new Map(), diagramOf = new Map(), errors = [];
  (function walk(o, parent, diagram) {
    if (Array.isArray(o)) return o.forEach((x) => walk(x, parent, diagram));
    if (!o || typeof o !== 'object' || o.$ref) return;
    let p = parent, d = diagram;
    if (o._type) {
      if (!o._id) errors.push(`${o._type} 缺少 _id`);
      else if (byId.has(o._id)) errors.push(`_id 重复：${o._id}`);
      else byId.set(o._id, o);
      parentOf.set(o, parent);
      if (isDiagram(o._type)) d = o;
      diagramOf.set(o, d);
      p = o;
    }
    for (const v of Object.values(o)) walk(v, p, d);
  })(root, null, null);
  return { byId, parentOf, diagramOf, errors };
}

function validate(root) {
  const { byId, parentOf, diagramOf, errors } = index(root);
  const err = (m) => errors.push(m);
  const res = (r) => byId.get(r?.$ref);
  for (const o of byId.values()) {
    const tag = `${o._type} ${o._id}`;
    // 所有 $ref 都能解析
    for (const [k, v] of Object.entries(o)) if (v && v.$ref !== undefined && !byId.has(v.$ref)) err(`${tag}.${k} 引用不存在：${v.$ref}`);
    // _parent 等于实际所在的父元素
    const actual = parentOf.get(o);
    if (o._type !== 'Project') {
      if (!o._parent) err(`${tag} 缺少 _parent`);
      else if (!actual || o._parent.$ref !== actual._id) err(`${tag} 的 _parent 与实际父元素不一致`);
    }
    if (isView(o._type)) {
      if (o.model) { const m = res(o.model); if (m && (isView(m._type) || isDiagram(m._type))) err(`${tag}.model 指向的不是模型元素`); }
      else if (!['LabelView', 'EdgeLabelView', 'UMLNoteView', 'UMLNoteLinkView'].includes(o._type)) err(`${tag} 缺少 model`);
      for (const k of ['head', 'tail', 'hostEdge']) if (o[k]) { const t = res(o[k]); if (t && (!isView(t._type) || diagramOf.get(t) !== diagramOf.get(o))) err(`${tag}.${k} 不是同一张图上的视图`); }
      for (const k of SUBVIEW_REFS) if (o[k]) { const t = res(o[k]); if (t && !(o.subViews ?? []).includes(t)) err(`${tag}.${k} 不是自己的子视图`); }
      if (o.points !== undefined && !/^-?\d+(\.\d+)?:-?\d+(\.\d+)?(;-?\d+(\.\d+)?:-?\d+(\.\d+)?)+$/.test(o.points)) err(`${tag}.points 格式不对`);
    }
    // 关系两端
    for (const k of ['end1', 'end2']) if (o[k] && !res(o[k].reference)) err(`${tag}.${k}.reference 无法解析`);
    if (o.source && !res(o.source)) err(`${tag}.source 无法解析`);
    if (o.target && !res(o.target)) err(`${tag}.target 无法解析`);
    if (o._type === 'ERDColumn' && o.foreignKey && !res(o.referenceTo)) err(`${tag} 外键列缺少 referenceTo`);
  }
  // 节点视图互不重叠（同一张图的顶层节点；系统边界框可以包含用例）
  for (const d of [...byId.values()].filter((o) => isDiagram(o._type))) {
    const nodes = d.ownedViews.filter((v) => v.width !== undefined && v.points === undefined && v._type !== 'UMLUseCaseSubjectView');
    for (let i = 0; i < nodes.length; i++) for (let j = i + 1; j < nodes.length; j++) {
      const a = nodes[i], b = nodes[j];
      if (a.left < b.left + b.width && b.left < a.left + a.width && a.top < b.top + b.height && b.top < a.top + a.height) err(`${d.name}：${a._type} ${a._id} 与 ${b._type} ${b._id} 重叠`);
    }
  }
  return { errors, byId };
}

// 与官方样例比对：每种 _type 的键 ⊆ 样例中该类型出现过的键（加元模型补充）；视图的子视图类型序列在样例中出现过
function signatures(root) {
  const keys = {}, seqs = {};
  (function walk(o) {
    if (Array.isArray(o)) return o.forEach(walk);
    if (!o || typeof o !== 'object') return;
    if (o._type) {
      (keys[o._type] ??= new Set());
      for (const k of Object.keys(o)) keys[o._type].add(k);
      if (o.subViews) {
        const s = o.subViews.map((v) => v._type).filter((t, i, a) => t !== a[i - 1] || !['ERDColumnView', 'UMLAttributeView', 'EdgeLabelView', 'LabelView'].includes(t)).join(',');
        (seqs[o._type] ??= new Set()).add(s);
      }
    }
    Object.values(o).forEach(walk);
  })(root);
  return { keys, seqs };
}
function compareWithSamples(root, dir) {
  const problems = [];
  const sk = {}, ss = {};
  for (const f of fs.readdirSync(dir).filter((f) => /^(ERDExample|UMLExample)\.mdj$/.test(f))) {
    const { keys, seqs } = signatures(JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')));
    for (const [t, s] of Object.entries(keys)) { sk[t] ??= new Set(); s.forEach((k) => sk[t].add(k)); }
    for (const [t, s] of Object.entries(seqs)) { ss[t] ??= new Set(); s.forEach((k) => ss[t].add(k)); }
  }
  if (!Object.keys(sk).length) return ['样例目录中没有 ERDExample.mdj / UMLExample.mdj'];
  const { keys, seqs } = signatures(root);
  for (const [t, s] of Object.entries(keys)) {
    if (t === 'Project') continue;
    if (!sk[t]) { problems.push(`类型 ${t} 在样例中不存在`); continue; }
    for (const k of s) if (!sk[t].has(k) && !(METAMODEL_EXTRA[t] ?? []).includes(k)) problems.push(`${t}.${k} 在样例中未出现`);
  }
  for (const [t, s] of Object.entries(seqs)) for (const q of s) if (!ss[t]?.has(q)) problems.push(`${t} 的子视图序列 [${q}] 在样例中未出现`);
  return problems;
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------
let root;
if (CHECK_ONLY) {
  root = JSON.parse(fs.readFileSync(OUT, 'utf8'));
} else {
  const project = { _type: 'Project', _id: newId(), name: 'RBAC0 权限中心', ownedElements: [] };
  project.ownedElements.push(buildER(project), buildUseCase(project), buildDomain(project));
  const text = JSON.stringify(project, null, '\t');
  fs.writeFileSync(OUT, text + '\n', 'utf8');
  root = JSON.parse(fs.readFileSync(OUT, 'utf8')); // 校验写出去的文件本身
  console.log(`已写入 ${OUT}（${(text.length / 1024).toFixed(0)} KB）`);
}
const { errors, byId } = validate(root);
const count = (t) => [...byId.values()].filter((o) => o._type === t).length;
console.log(`元素 ${byId.size} 个：ERDEntity ${count('ERDEntity')}、ERDColumn ${count('ERDColumn')}、ERDRelationship ${count('ERDRelationship')}、UMLActor ${count('UMLActor')}、UMLUseCase ${count('UMLUseCase')}、UMLClass ${count('UMLClass')}、UMLAssociation ${count('UMLAssociation')}`);
if (SAMPLES) {
  const p = compareWithSamples(root, path.resolve(SAMPLES));
  if (p.length) errors.push(...p.map((x) => '[样例比对] ' + x));
  else console.log('样例比对通过：键集合与子视图结构均在官方样例中出现过');
}
if (errors.length) { console.error(`校验失败 ${errors.length} 项：\n  ` + errors.slice(0, 50).join('\n  ')); process.exit(1); }
console.log('校验通过：JSON 合法、_id 唯一、$ref 全部可解析、_parent 与嵌套一致、视图 model/head/tail 合法、节点不重叠');
