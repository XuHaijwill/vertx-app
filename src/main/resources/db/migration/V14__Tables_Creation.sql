-- ================================================================
-- V14__Tables_Creation.sql — RBAC tables (PostgreSQL)
-- Converted from MySQL version
-- ================================================================
-- Conversion notes:
--   • bigint(20)  -> BIGINT (PostgreSQL ignores length parameter)
--   • int(4)      -> INTEGER
--   • varchar(n)  -> VARCHAR(n)
--   • char(1)     -> CHAR(1)
--   • datetime    -> TIMESTAMP
--   • longblob    -> TEXT (used for HTML content)
--   • tinyint(1)  -> SMALLINT
--   • auto_increment -> GENERATED ALWAYS AS IDENTITY
--   • engine=innodb / auto_increment=N -> removed (Postgres uses SEQUENCE)
--   • sysdate()   -> CURRENT_TIMESTAMP
--   • comment '...' -> moved to separate COMMENT ON statements
-- ================================================================


-- ----------------------------
-- 7. Group-to-menu association table (Group 1 - N Menu)
-- ----------------------------
DROP TABLE IF EXISTS sys_group_menu CASCADE;
CREATE TABLE sys_group_menu (
  group_name   VARCHAR(255) NOT NULL,
  menu_id      BIGINT NOT NULL,
  PRIMARY KEY (group_name, menu_id)
);

COMMENT ON TABLE sys_group_menu IS 'Mapping between roles and menus';
COMMENT ON COLUMN sys_group_menu.group_name IS 'Role identifier';
COMMENT ON COLUMN sys_group_menu.menu_id IS 'Menu identifier';

-- Seed: group-to-menu associations
INSERT INTO sys_group_menu (group_name, menu_id) VALUES
('Ldap_Admin',1),('Ldap_User',2);



-- ----------------------------
-- 17. Notices table
-- ----------------------------
DROP TABLE IF EXISTS sys_notice CASCADE;
CREATE TABLE sys_notice (
  notice_id       INTEGER         NOT NULL GENERATED ALWAYS AS IDENTITY,
  notice_title    VARCHAR(50)     NOT NULL,
  notice_type     CHAR(1)         NOT NULL,
  notice_content  TEXT             DEFAULT NULL,
  status          CHAR(1)          DEFAULT '0',
  create_by       VARCHAR(64)      DEFAULT '',
  create_time     TIMESTAMP,
  update_by       VARCHAR(64)      DEFAULT '',
  update_time     TIMESTAMP,
  remark          VARCHAR(255)     DEFAULT NULL,
  PRIMARY KEY (notice_id)
);

COMMENT ON TABLE sys_notice IS 'Notices and announcements';
COMMENT ON COLUMN sys_notice.notice_id      IS 'Notice ID';
COMMENT ON COLUMN sys_notice.notice_title   IS 'Notice title';
COMMENT ON COLUMN sys_notice.notice_type    IS 'Notice type (1=notice, 2=announcement)';
COMMENT ON COLUMN sys_notice.notice_content IS 'Notice content (HTML/text)';
COMMENT ON COLUMN sys_notice.status         IS 'Status (0=active, 1=closed)';
COMMENT ON COLUMN sys_notice.create_by      IS 'Created by';

-- Seed: example notices
INSERT INTO sys_notice (notice_id, notice_title, notice_type, notice_content, status, create_by, create_time, update_by, update_time, remark) VALUES
(1, 'Reminder: New release available (2018-07-01)', '2', 'Release notes and highlights', '0', 'admin', CURRENT_TIMESTAMP, '', NULL, 'Administrator'),
(2, 'Maintenance notice: Scheduled maintenance (2018-07-01)', '1', 'System maintenance details', '0', 'admin', CURRENT_TIMESTAMP, '', NULL, 'Administrator'),
(3, 'About the open-source framework', '1', '<p><strong>Project overview</strong></p><p>The open-source framework provides a ready-made backend scaffold for enterprise applications, offering features such as user management, role management, department management, menu management, parameter management, dictionary management, job scheduling, monitoring, login and operation logs, code generation, multi-data-source support, data permission, internationalization, Redis caching, Docker deployment and more.</p><p>Official site: <a href="http://ruoyi.vip">http://ruoyi.vip</a></p>', '0', 'admin', CURRENT_TIMESTAMP, '', NULL, 'Administrator');


-- ----------------------------
-- 18. Notice read records
-- ----------------------------
DROP TABLE IF EXISTS sys_notice_read CASCADE;
CREATE TABLE sys_notice_read (
  read_id       BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
  notice_id     INTEGER NOT NULL,
  user_id       BIGINT NOT NULL,
  read_time     TIMESTAMP NOT NULL,
  PRIMARY KEY (read_id),
  CONSTRAINT uk_user_notice UNIQUE (user_id, notice_id)
);

COMMENT ON TABLE sys_notice_read IS 'Records of notices marked as read by users';
COMMENT ON COLUMN sys_notice_read.read_id    IS 'Read record primary key';
COMMENT ON COLUMN sys_notice_read.notice_id  IS 'Notice ID';
COMMENT ON COLUMN sys_notice_read.user_id    IS 'User ID';
COMMENT ON COLUMN sys_notice_read.read_time  IS 'Time when read';


-- ----------------------------
-- 19. Code generation metadata table
-- ----------------------------
DROP TABLE IF EXISTS gen_table CASCADE;
CREATE TABLE gen_table (
  table_id          BIGINT          NOT NULL GENERATED ALWAYS AS IDENTITY,
  table_name        VARCHAR(200)     DEFAULT '',
  table_comment     VARCHAR(500)     DEFAULT '',
  sub_table_name    VARCHAR(64)      DEFAULT NULL,
  sub_table_fk_name VARCHAR(64)      DEFAULT NULL,
  class_name        VARCHAR(100)     DEFAULT '',
  tpl_category      VARCHAR(200)     DEFAULT 'crud',
  tpl_web_type      VARCHAR(30)      DEFAULT '',
  package_name      VARCHAR(100),
  module_name       VARCHAR(30),
  business_name     VARCHAR(30),
  function_name     VARCHAR(50),
  function_author   VARCHAR(50),
  form_col_num      INTEGER          DEFAULT 1,
  gen_type          CHAR(1)          DEFAULT '0',
  gen_path          VARCHAR(200)     DEFAULT '/',
  options           VARCHAR(1000),
  create_by         VARCHAR(64)      DEFAULT '',
  create_time       TIMESTAMP,
  update_by         VARCHAR(64)      DEFAULT '',
  update_time       TIMESTAMP,
  remark            VARCHAR(500)     DEFAULT NULL,
  PRIMARY KEY (table_id)
);

COMMENT ON TABLE gen_table IS 'Code generation configuration table';
COMMENT ON COLUMN gen_table.table_id       IS 'Identifier';
COMMENT ON COLUMN gen_table.table_name     IS 'Table name';
COMMENT ON COLUMN gen_table.table_comment  IS 'Table description';
COMMENT ON COLUMN gen_table.class_name     IS 'Entity class name';
COMMENT ON COLUMN gen_table.tpl_category   IS 'Template category (crud=single-table, tree=tree-table)';
COMMENT ON COLUMN gen_table.package_name  IS 'Package name for generated code';
COMMENT ON COLUMN gen_table.module_name    IS 'Module name';
COMMENT ON COLUMN gen_table.business_name IS 'Business name used in generation';
COMMENT ON COLUMN gen_table.function_name IS 'Function name used in generation';
COMMENT ON COLUMN gen_table.function_author IS 'Author for generated code';
COMMENT ON COLUMN gen_table.gen_type      IS 'Generation output type (0=zip, 1=custom path)';
COMMENT ON COLUMN gen_table.gen_path      IS 'Generation path (default: project path)';
COMMENT ON COLUMN gen_table.create_by     IS 'Created by';


-- ----------------------------
-- 20. Fields for code generation tables
-- ----------------------------
DROP TABLE IF EXISTS gen_table_column CASCADE;
CREATE TABLE gen_table_column (
  column_id         BIGINT          NOT NULL GENERATED ALWAYS AS IDENTITY,
  table_id          BIGINT,
  column_name       VARCHAR(200),
  column_comment    VARCHAR(500),
  column_type       VARCHAR(100),
  java_type         VARCHAR(500),
  java_field        VARCHAR(200),
  is_pk             CHAR(1),
  is_increment      CHAR(1),
  is_required       CHAR(1),
  is_insert         CHAR(1),
  is_edit           CHAR(1),
  is_list           CHAR(1),
  is_query          CHAR(1),
  query_type        VARCHAR(200)     DEFAULT 'EQ',
  html_type         VARCHAR(200),
  dict_type         VARCHAR(200)     DEFAULT '',
  sort              INTEGER,
  create_by         VARCHAR(64)      DEFAULT '',
  create_time       TIMESTAMP,
  update_by         VARCHAR(64)      DEFAULT '',
  update_time       TIMESTAMP,
  PRIMARY KEY (column_id)
);

COMMENT ON TABLE gen_table_column IS 'Fields for code generation metadata';
COMMENT ON COLUMN gen_table_column.column_id      IS 'Identifier';
COMMENT ON COLUMN gen_table_column.table_id       IS 'Parent table identifier';
COMMENT ON COLUMN gen_table_column.column_name    IS 'Column name';
COMMENT ON COLUMN gen_table_column.column_comment IS 'Column description';
COMMENT ON COLUMN gen_table_column.column_type   IS 'Column type';
COMMENT ON COLUMN gen_table_column.java_type     IS 'Java type';
COMMENT ON COLUMN gen_table_column.java_field    IS 'Java field name';
COMMENT ON COLUMN gen_table_column.is_pk          IS 'Primary key flag (1=yes)';
COMMENT ON COLUMN gen_table_column.is_increment   IS 'Auto-increment flag (1=yes)';
COMMENT ON COLUMN gen_table_column.is_required   IS 'Required flag (1=yes)';
COMMENT ON COLUMN gen_table_column.is_insert     IS 'Insertable flag (1=yes)';
COMMENT ON COLUMN gen_table_column.is_edit        IS 'Editable flag (1=yes)';
COMMENT ON COLUMN gen_table_column.is_list        IS 'List field flag (1=yes)';
COMMENT ON COLUMN gen_table_column.is_query      IS 'Queryable flag (1=yes)';
COMMENT ON COLUMN gen_table_column.query_type    IS 'Query type (EQ, NE, GT, LT, RANGE, etc.)';
COMMENT ON COLUMN gen_table_column.html_type     IS 'Display/input type (text, textarea, select, checkbox, radio, date)';
COMMENT ON COLUMN gen_table_column.dict_type      IS 'Dictionary type';
COMMENT ON COLUMN gen_table_column.sort           IS 'Sort order';
COMMENT ON COLUMN gen_table_column.create_by     IS 'Created by';
