/**
 * 数据结构：与表一一对应的记录，只有公有字段，没有任何方法。
 * 结构化设计中数据与处理分离，判断与计算都写在 service 包的处理过程里（docs/06-detail-structured.md §3）。
 * 实体之间不建对象关联（@ManyToOne 等），只保存外键 id，跨表数据由 Mapper 的查询取出。
 */
package com.xmu.rbac.structured.entity;
