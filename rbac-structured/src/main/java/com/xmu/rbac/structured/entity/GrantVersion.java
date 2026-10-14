package com.xmu.rbac.structured.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 授权数据版本号，只有一行（id = 1）。授权缓存的键带上它，授权数据一变它就加一。 */
@Entity
@Table(name = "sys_grant_version")
public class GrantVersion {
    @Id
    public Integer id;
    public long version;
}
