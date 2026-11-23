package com.xmu.rbac.ddd.application;

/** 显式边界 JavaBean，敏感身份字段不参与复制。 */
public class RoleCommand {
    private String systemCode;
    private String code;
    private String name;
    private String description;

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String value) {
        systemCode = value;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String value) {
        code = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        name = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

}
