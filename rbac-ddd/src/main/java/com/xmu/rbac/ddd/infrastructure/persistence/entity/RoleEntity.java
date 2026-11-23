package com.xmu.rbac.ddd.infrastructure.persistence.entity;

import jakarta.persistence.*;

/** 仅供持久化适配器使用，不跨越应用边界。 */
@Entity
@Table(name = "sys_role")
public class RoleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "status")
    private String status;

    @Column(name = "builtin")
    private Boolean builtin;

    @Version
    @Column(name = "version")
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long value) {
        this.systemId = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        this.code = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        this.description = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String value) {
        this.status = value;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean value) {
        this.builtin = value;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long value) {
        this.version = value;
    }
}
