package com.xmu.rbac.structured.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_menu")
public class Menu {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long parentId;
    /** 空 = 目录节点 */
    public Long resourceId;
    public String menuName;
    public String routePath;
    public String componentKey;
    public String icon;
    public int sortOrder;
}
