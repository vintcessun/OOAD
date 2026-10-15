# UML 模型与图

课程**明确建议采用 StarUML**（https://staruml.io/）。本目录存放模型源文件与导出图。

## 目录

```
diagrams/
├── rbac.mdj                 StarUML 模型（由 tools/gen-mdj.mjs 生成）：ER图 / 用例总图 / 领域模型
├── src/                     Mermaid 源文件（导出图的来源）
│   ├── er-diagram.mmd       14 张表、17 个外键，与 V1–V3 迁移脚本一致
│   ├── usecase-overall.mmd  用例总图（Mermaid 无用例图类型，用 flowchart 表示）
│   └── domain-model.mmd     领域模型（01-srs.md §3.1）
├── export/                  导出的 SVG / PNG，供文档引用
│   ├── er-diagram.svg/.png
│   ├── usecase-overall.svg/.png
│   └── domain-model.svg/.png
└── tools/
    ├── gen-mdj.mjs          生成并校验 rbac.mdj（纯 Node，无依赖）
    ├── mermaid-config.json  中文字体等渲染配置
    └── puppeteer-config.json 指定本机 Chrome（Chromium 下载失败时用）
```

## 重新生成

**导出图**（在仓库外的临时目录装一次 mermaid-cli，不要把 node_modules 放进仓库）：

```powershell
# 临时目录里：npm i @mermaid-js/mermaid-cli@11.4.2 puppeteer@23.11.1（设 $env:PUPPETEER_SKIP_DOWNLOAD='true' 可跳过下载 Chromium）
$d = "D:\Scripts\OOAD\diagrams"
foreach ($n in 'er-diagram','usecase-overall','domain-model') { foreach ($ext in 'svg','png') {
  .\node_modules\.bin\mmdc.cmd -i "$d\src\$n.mmd" -o "$d\export\$n.$ext" `
    -c "$d\tools\mermaid-config.json" -p "$d\tools\puppeteer-config.json" -w 2000 -s 2 -b white
} }
```

`puppeteer-config.json` 写的是 Chrome 的默认安装路径，机器不同时自行修改；能正常下载 Chromium 时可去掉 `-p`。

**StarUML 模型**：

```powershell
node diagrams/tools/gen-mdj.mjs                         # 生成 rbac.mdj 并校验
node diagrams/tools/gen-mdj.mjs --check-only            # 只校验现有 rbac.mdj
node diagrams/tools/gen-mdj.mjs --samples <样例目录>    # 另与官方样例比对结构
```

校验内容：JSON 合法、`_id` 唯一、`$ref` 全部可解析、`_parent` 与实际嵌套一致、视图的 `model` / `head` / `tail` 合法、同图节点不重叠。`--samples` 指向放有 [staruml-samples](https://github.com/staruml/staruml-samples) 中 `ERDExample.mdj`、`UMLExample.mdj` 的目录，逐类型比对键集合与子视图结构——生成器的结构就是照这两个样例写的。

> ⚠️ **重新运行 `gen-mdj.mjs` 会覆盖在 StarUML 里调整过的布局。** 队友在 StarUML 中调整并保存后，`rbac.mdj` 即转为手工维护，生成器只用于初始化或重置。

## 工作方式

文档撰写阶段用 **Mermaid** 内嵌在 Markdown 里——GitHub 直接渲染、可随文本一起 diff、改动成本低。`src/*.mmd` 与 `export/` 是这些图的独立副本。

**提交给老师之前，图必须在 StarUML 中核对并导出**：
1. 课程明确建议使用 StarUML，交付物应符合课程要求；
2. StarUML 能表达 Mermaid 表达不了的东西——用例图的 `<<include>>` / `<<extend>>`、构造型、多重性标注、约束等。

`rbac.mdj` 目前由脚本生成，**本机未安装 StarUML，尚未实际打开验证**。提交前须有人：
- [ ] 用 StarUML 打开 `rbac.mdj`，确认三张图都能正常显示；
- [ ] 调整布局（生成的连线是直线，部分会穿过其他元素）；
- [ ] 从 StarUML 重新导出 PNG（File → Export Diagram As → PNG），覆盖 `export/` 中对应文件。

`.mdj` 是文本 JSON，但嵌套深、`_id` 互相引用，**冲突难以手工合并**。因此：

> ⚠️ **同一时间只能有一人编辑 `.mdj`。** 编辑前在群里知会一声，改完立即提交。
> 若确实需要并行，按包拆成多个 `.mdj` 文件（`rbac-usecase.mdj`、`rbac-domain.mdj`、`rbac-class.mdj`…）。

## 需要绘制的图清单

### 迭代一（10/27 检查）

- [x] 用例总图（`01-srs.md` §4.1–§4.2）——Mermaid 导出 + 生成的 StarUML 模型，待 StarUML 内核对
- [ ] 分包用例图 × 2（访问控制、授权服务）
- [x] 领域模型类图（`01-srs.md` §3.1）——Mermaid 导出 + 生成的 StarUML 模型，待 StarUML 内核对
- [x] ER 图（`03-database.md` §2，按 V1–V3 迁移脚本）——Mermaid 导出 + 生成的 StarUML 模型，待 StarUML 内核对
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
