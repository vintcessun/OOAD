package com.xmu.rbac.structured.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_resource")
public class Resource {
    public static final String BUSINESS = "BUSINESS";
    public static final String MENU = "MENU";
    public static final String API = "API";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long moduleId;
    public String resourceCode;
    public String resourceName;
    public String resourceType = BUSINESS;
    public String description;
    public int sortOrder;
}
