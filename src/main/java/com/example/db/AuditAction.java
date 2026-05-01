package com.example.db;

/**
 * 审计日志操作类型枚举。
 * 值与数据库 CONSTRAINT 一致，AUDIT_UPDATE/AUDIT_DELETE 携带 old/new_value。
 */
public enum AuditAction {
    AUDIT_CREATE("AUDIT_CREATE"),  // 新增
    AUDIT_UPDATE("AUDIT_UPDATE"),  // 更新
    AUDIT_DELETE("AUDIT_DELETE"); // 删除

    private final String value;

    AuditAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AuditAction fromString(String text) {
        if (text == null) return null;
        for (AuditAction a : values()) {
            if (a.value.equalsIgnoreCase(text) || a.name().equalsIgnoreCase(text)) {
                return a;
            }
        }
        return null;
    }
}
