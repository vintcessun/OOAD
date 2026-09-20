# UML 模型与图

课程**明确建议采用 StarUML**（https://staruml.io/）。本目录存放模型源文件与导出图。

## 目录约定

```
diagrams/
├── rbac.mdj              StarUML 模型源文件（唯一事实来源）
└── export/               导出的 PNG/SVG，供文档引用
    ├── usecase-overall.png
    ├── domain-model.png
    ├── class-oo.png
    ├── sequence-authz.png
    ├── er-diagram.png
    └── ...
```

## 工作方式

文档撰写阶段用 **Mermaid / PlantUML** 内嵌在 Markdown 里——好处是 GitHub 直接渲染、可随文本一起做 diff、改动成本低。

**提交给老师之前，图必须在 StarUML 中重绘并导出**，理由有两条：
1. 课程明确建议使用 StarUML，交付物应符合课程要求；
2. StarUML 能表达 Mermaid 表达不了的东西——用例图的 `<<include>>` / `<<extend>>`、构造型、多重性标注、约束等。

`.mdj` 是二进制 JSON，**冲突难以合并**。因此：

> ⚠️ **同一时间只能有一人编辑 `.mdj`。** 编辑前在群里知会一声，改完立即提交。
> 若确实需要并行，按包拆成多个 `.mdj` 文件（`rbac-usecase.mdj`、`rbac-domain.mdj`、`rbac-class.mdj`…）。

## 需要绘制的图清单

### 迭代一（10/27 检查）

- [ ] 用例总图（`01-srs.md` 图 4-1）
- [ ] 分包用例图 × 2（访问控制、授权服务）
- [ ] 领域模型类图（`01-srs.md` §3.1）
- [ ] ER 图（`03-database.md` §2）
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
