import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const here = path.dirname(fileURLToPath(import.meta.url));
const modelPath = path.join(here, '..', 'rbac.mdj');
const original = fs.readFileSync(modelPath);

function check(t, change) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rbac-mdj-'));
  t.after(() => {
    assert.equal(path.dirname(dir), path.resolve(os.tmpdir()));
    assert.ok(path.basename(dir).startsWith('rbac-mdj-'));
    fs.rmSync(dir, { recursive: true, force: true });
  });
  const model = JSON.parse(original);
  const er = model.ownedElements.find((e) => e._type === 'ERDDataModel');
  const diagram = er.ownedElements.find((e) => e._type === 'ERDDiagram');
  change?.(diagram);
  const input = path.join(dir, 'model.mdj');
  const content = JSON.stringify(model);
  fs.writeFileSync(input, content);
  const result = spawnSync(process.execPath, [path.join(here, 'gen-mdj.mjs'), '--check-only', '--out', input], {
    encoding: 'utf8',
  });
  assert.ifError(result.error);
  assert.equal(fs.readFileSync(input, 'utf8'), content, '只校验不能重写模型');
  return { status: result.status, output: result.stdout + result.stderr };
}

test('接受 StarUML 原生文字标注及其空白边界，保留已保存的模型', (t) => {
  const result = check(t);
  assert.equal(result.status, 0, result.output);
  assert.deepEqual(fs.readFileSync(modelPath), original);
});

test('仍拒绝两个实体节点重叠', (t) => {
  const result = check(t, (diagram) => {
    const [a, b] = diagram.ownedViews.filter((v) => v._type === 'ERDEntityView');
    b.left = a.left;
    b.top = a.top;
  });
  assert.equal(result.status, 1);
  assert.match(result.output, /ERDEntityView .* 与 ERDEntityView .* 重叠/);
});

test('仍拒绝实体视图缺少模型引用', (t) => {
  const result = check(t, (diagram) => {
    delete diagram.ownedViews.find((v) => v._type === 'ERDEntityView').model;
  });
  assert.equal(result.status, 1);
  assert.match(result.output, /ERDEntityView .* 缺少 model/);
});

test('仍检查文字标注中的失效引用', (t) => {
  const result = check(t, (diagram) => {
    diagram.ownedViews.find((v) => v._type === 'UMLTextView').model = { $ref: 'missing-model' };
  });
  assert.equal(result.status, 1);
  assert.match(result.output, /引用不存在：missing-model/);
});
