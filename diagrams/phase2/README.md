# RBAC1 二阶段项目总览

> 阅读基线：2026-11-19；Git HEAD：`57deb4a1cf91da197f3f30687839afca8bf7b815`。本文来自当前源码、固定教师 API 和数据库迁移的静态分析。本次未运行业务测试或部署，接口存在不等于本轮验收通过。

建议先打开 [离线总览页](index.html)，支持图形缩放、接口筛选、模型展开和源码定位；不依赖网络。

1. [总体用例图](01-usecases.md)：权限中心业务边界与课程协作。
2. [ER 与完整数据字典](02-data-model.md)：十五表、全部外键、关键约束。
3. [OpenAPI 解读](03-openapi.md)：68 个中心操作、80 个课程操作、协议与边界。
4. [架构总览](04-architecture.md)：七服务、四 Maven 模块、双后端与目录职责。

## 图与源文件

图位于 assets/，提供 SVG 与导出的 PNG；src/ 提供可编辑 PlantUML / Mermaid；布局与网页由 tools/generate.cjs 可重复生成。图中的组件/关系来自当前源码和部署定义，不借用一期图。

- [来源及 SHA-256](data/provenance.json)
- [操作 JSON](data/openapi-operations.json) / [CSV](data/openapi-operations.csv)
- [表和外键 JSON](data/schema.json)
- [静态核对结果](data/verification.json)

## 重生成

在仓库根执行 `node diagrams/phase2/tools/generate.cjs`。需要 Node.js 与 `yaml` 包；可以通过环境变量 `YAML_MODULE` 指定已安装的包目录，避免修改项目依赖。浏览器 PNG 导出与页面验证使用 tools/render.cjs，需要 playwright-core 和本地 Chromium/Edge；通过 PLAYWRIGHT_MODULE、BROWSER_PATH 指定。

本目录以实际 DDL、OAS、Controller、网关、接入层与部署配置为依据。详见 data/provenance.json。本次只执行资料提取、静态一致性与可视化验证，没有重新执行业务验收。
