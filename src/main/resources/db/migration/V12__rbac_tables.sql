-- V12__rbac_tables.sql
-- RuoYi-style RBAC tables: sys_role, sys_user_role, sys_role_menu
-- sys_menu.type = 'C' stores permission strings (perms column)

-- ============================================
-- sys_role: Role definition
-- ============================================
CREATE TABLE IF NOT EXISTS sys_role (
    role_id        BIGSERIAL PRIMARY KEY,
    role_name      VARCHAR(30)  NOT NULL COMMENT 'Role name',
    role_key       VARCHAR(100) NOT NULL COMMENT 'Role key (e.g. admin, common)',
    role_sort      INTEGER      NOT NULL DEFAULT 0 COMMENT 'Display order',
    data_scope     CHAR(1)      NOT NULL DEFAULT '5' COMMENT 'Data scope: 1=All, 2=Dept, 3=Dept+Sub, 4=Self, 5=Custom',
    menu_check_strictly BOOLEAN  NOT NULL DEFAULT TRUE COMMENT 'Menu tree cascade check',
    dept_check_strictly BOOLEAN  NOT NULL DEFAULT TRUE COMMENT 'Dept tree cascade check',
    status         CHAR(1)      NOT NULL DEFAULT '0' COMMENT 'Status: 0=Normal, 1=Disabled',
    del_flag       CHAR(1)      NOT NULL DEFAULT '0' COMMENT 'Delete flag: 0=Exist, 2=Deleted',
    create_by      VARCHAR(64)  DEFAULT '' COMMENT 'Creator',
    create_time    TIMESTAMP             DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_by      VARCHAR(64)  DEFAULT '' COMMENT 'Updater',
    update_time    TIMESTAMP             DEFAULT CURRENT_TIMESTAMP COMMENT 'Update time',
    remark         VARCHAR(500) DEFAULT '' COMMENT 'Remark'
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_role_key ON sys_role(role_key);
CREATE INDEX IF NOT EXISTS idx_sys_role_status ON sys_role(status);

-- ============================================
-- sys_user_role: User-Role mapping
-- ============================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id  BIGINT NOT NULL COMMENT 'User ID',
    role_id  BIGINT NOT NULL COMMENT 'Role ID',
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_user_role_user ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_role_role ON sys_user_role(role_id);

-- ============================================
-- sys_role_menu: Role-Menu/Permission mapping
-- ============================================
CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id  BIGINT NOT NULL COMMENT 'Role ID',
    menu_id  BIGINT NOT NULL COMMENT 'Menu ID',
    PRIMARY KEY (role_id, menu_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_role_menu_role ON sys_role_menu(role_id);
CREATE INDEX IF NOT EXISTS idx_sys_role_menu_menu ON sys_role_menu(menu_id);

-- ============================================
-- Seed: default admin role
-- ============================================
INSERT INTO sys_role (role_name, role_key, role_sort, status, create_by, remark)
VALUES ('Super Admin', 'admin', 1, '0', 'system', 'Super administrator — grants all permissions')
ON CONFLICT (role_key) DO NOTHING;

INSERT INTO sys_role (role_name, role_key, role_sort, status, create_by, remark)
VALUES ('Common User', 'common', 2, '0', 'system', 'Regular user — limited permissions')
ON CONFLICT (role_key) DO NOTHING;
