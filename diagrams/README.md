# UML 模型与图

课程**明确建议采用 StarUML**（https://staruml.io/）。本目录存放模型源文件与导出图。

## 目录

```
diagrams/
├── rbac.mdj                 手工维护：StarUML 原生 ER 图 + 用例总图 + 领域模型
├── src/                     Mermaid 参考源文件（用例图、领域模型导出图的来源）
│   ├── er-diagram.mmd       数据库结构参考；当前 ER 导出图以 rbac.mdj 为准
│   ├── usecase-overall.mmd  用例总图（Mermaid 无用例图类型，用 flowchart 表示）
│   └── domain-model.mmd     领域模型（01-srs.md §3.1）
├── export/                  SVG / PNG，供文档引用
│   ├── er-diagram.svg/.png   本地已有的 StarUML 原生导出
│   ├── usecase-overall.svg/.png  Mermaid 导出，待 StarUML 内核对
│   └── domain-model.svg/.png     Mermaid 导出，待 StarUML 内核对
└── tools/
    ├── gen-mdj.mjs          初始化生成器与结构校验（纯 Node，无依赖）
    ├── gen-mdj.test.mjs     原生文字标注、实体重叠和引用校验的回归测试
    ├── mermaid-config.json  中文字体等渲染配置
    └── puppeteer-config.json 指定本机 Chrome（Chromium 下载失败时用）
```

## 校验与导出

日常编辑使用 StarUML，保存后从仓库根目录运行：

```powershell
node diagrams/tools/gen-mdj.mjs --check-only
node --test diagrams/tools/gen-mdj.test.mjs
```

校验覆盖 JSON、唯一 `_id`、可解析的 `$ref`、父子关系、视图引用和实体等节点的重叠。StarUML 原生的 `UMLTextView` 是独立文字标注，不要求 `model`，也不把其空白边界当成实体节点；其中已有的引用仍会检查。脚本校验不能代替 StarUML 打开模型后的显示检查。

**用例总图、领域模型的 Mermaid 导出图**（在仓库外的临时目录装一次 mermaid-cli，不要把 node_modules 放进仓库）：

```powershell
# 临时目录里：npm i @mermaid-js/mermaid-cli@11.4.2 puppeteer@23.11.1（设 $env:PUPPETEER_SKIP_DOWNLOAD='true' 可跳过下载 Chromium）
$d = "D:\__大三上\ooad\OOAD\diagrams" # 替换为自己的仓库路径
foreach ($n in 'usecase-overall','domain-model') { foreach ($ext in 'svg','png') {
  .\node_modules\.bin\mmdc.cmd -i "$d\src\$n.mmd" -o "$d\export\$n.$ext" `
    -c "$d\tools\mermaid-config.json" -p "$d\tools\puppeteer-config.json" -w 2000 -s 2 -b white
} }
```

`puppeteer-config.json` 写的是 Chrome 的默认安装路径，机器不同时自行修改；能正常下载 Chromium 时可去掉 `-p`。

**重新初始化 StarUML 模型**（仅用于独立参考文件）：

```powershell
node diagrams/tools/gen-mdj.mjs --out <临时目录>/rbac-reference.mdj
```

生成器按 [staruml-samples](https://github.com/staruml/staruml-samples) 的 `ERDExample.mdj`、`UMLExample.mdj` 编写，`--samples <样例目录>` 可对生成的参考文件比对结构；原生保存的模型可能包含样例未覆盖的视图类型。

> ⚠️ **不带 `--check-only` 或 `--out` 运行生成器会覆盖 `rbac.mdj`，丢失已选用的原生 ER 图与手工布局。** 当前模型已转为手工维护。ER 图应从 StarUML 导出，不要用 Mermaid 输出覆盖。

## 工作方式

文档撰写阶段用 **Mermaid** 内嵌在 Markdown 里，方便 GitHub 渲染与文本 diff。数据库结构以迁移脚本为准，StarUML 图以 `rbac.mdj` 为编辑来源；导出图的来源见上方目录。

**提交给老师之前，图必须在 StarUML 中核对并导出**：
1. 课程明确建议使用 StarUML，交付物应符合课程要求；
2. StarUML 能表达 Mermaid 表达不了的东西——用例图的 `<<include>>` / `<<extend>>`、构造型、多重性标注、约束等。

2026-10-18 合并时，保留本地已由 StarUML 7.1.1 原生保存的 ER 模型与 PNG/SVG，并加入 `afa01a6` 的用例总图、领域模型。ER 图含 **14 张表、105 个字段、17 个外键**，与迁移脚本及远端 ER 模型结构一致；中文标题、布局、联合唯一约束说明和试用水印均保留。**组合后的模型尚未在 StarUML 中重新打开，用例和领域模型仍待原生验证。** 提交前须有人：
- [ ] 用 StarUML 打开组合后的 `rbac.mdj`，确认三张图都能正常显示；
- [ ] 调整布局（生成的连线是直线，部分会穿过其他元素）；
- [ ] 从 StarUML 导出用例总图、领域模型的 PNG/SVG，替换对应 Mermaid 导出图；ER 图如有修改也同步导出。
- [ ] 10/21 V4 删除了 `sys_user.expires_at`（docs/12 R-21）：在 StarUML 的 ER 图里删掉 `sys_user` 的 `expires_at` 列（字段数 105 → 104），重新导出 `er-diagram.png/svg`。Mermaid 源 `src/er-diagram.mmd`、`src/domain-model.mmd` 与生成器已同步；`domain-model` 导出图已重新生成。

`.mdj` 是文本 JSON，但嵌套深、`_id` 互相引用，**冲突难以手工合并**。因此：

> ⚠️ **同一时间只能有一人编辑 `.mdj`。** 编辑前在群里知会一声，改完立即提交。
> 若确实需要并行，按包拆成多个 `.mdj` 文件（`rbac-usecase.mdj`、`rbac-domain.mdj`、`rbac-class.mdj`…）。

## 需要绘制的图清单

### 迭代一（10/27 检查）

- [x] 用例总图（`01-srs.md` §4.1–§4.2）——保留远端 Mermaid 导出与生成模型，待 StarUML 内核对
- [ ] 分包用例图 × 2（访问控制、授权服务）
- [x] 领域模型类图（`01-srs.md` §3.1）——保留远端 Mermaid 导出与生成模型，待 StarUML 内核对
- [x] ER 图（`03-database.md` §2，按当前迁移脚本）——保留本地 StarUML 原生模型与导出图
- [ ] 数据流图 DFD Level 0 / 1 / 2（`06-detail-structured.md` §2）
- [ ] 结构图 Structure Chart（`06-detail-structured.md` §2.4）
- [ ] 授权判定活动图
- [ ] 组织架构示意图

### 迭代二（11/24 检查）

- [ ] 面向对象设计类图（`07-detail-oo.md` §2）
- [ ] 时序图：授权判定、分配角色、建立继承关系、缓存失效
- [ ] 角色继承 DAG 示例图
- [ ] 部署图

### 迭代三（12/22 检查）

- [ ] 约束求值类图
- [ ] 状态图：用户状态、稿件状态
- [ ] 包图 / 组件图（三个范式模块的依赖关系）

## 命名约定

- 文件名：`<类型>-<主题>.png`，如 `sequence-cache-invalidation.png`
- 图内文字：**中文**（与文档一致）
- 导出分辨率：≥ 150 DPI，确保打印清晰
