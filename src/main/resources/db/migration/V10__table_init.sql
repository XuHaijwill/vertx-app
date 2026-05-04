-- ----------------------------
-- 11. Dictionary Type Table
-- ----------------------------
drop table if exists sys_dict_type cascade;
create table sys_dict_type
(
  dict_id           BIGSERIAL PRIMARY KEY,
  dict_name        varchar(100)   default '',
  dict_type        varchar(100)   default '',
  status           char(1)        default '0',
  create_by        varchar(64)    default '',
  create_time      timestamp,
  update_by        varchar(64)    default '',
  update_time      timestamp,
  remark           varchar(500)   default null,
  unique (dict_type)
);

COMMENT ON TABLE sys_dict_type IS 'Dictionary Type Table';
COMMENT ON COLUMN sys_dict_type.dict_id IS 'Dictionary Primary Key';
COMMENT ON COLUMN sys_dict_type.dict_name IS 'Dictionary Name';
COMMENT ON COLUMN sys_dict_type.dict_type IS 'Dictionary Type';
COMMENT ON COLUMN sys_dict_type.status IS 'Status (0 Normal, 1 Disabled)';
COMMENT ON COLUMN sys_dict_type.create_by IS 'Creator';
COMMENT ON COLUMN sys_dict_type.create_time IS 'Create Time';
COMMENT ON COLUMN sys_dict_type.update_by IS 'Updater';
COMMENT ON COLUMN sys_dict_type.update_time IS 'Update Time';
COMMENT ON COLUMN sys_dict_type.remark IS 'Remark';

insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(1,  'User Gender', 'sys_user_sex',        '0', 'admin', CURRENT_TIMESTAMP, 'User gender list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(2,  'Menu Status', 'sys_show_hide',       '0', 'admin', CURRENT_TIMESTAMP, 'Menu status list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(3,  'System Switch', 'sys_normal_disable',  '0', 'admin', CURRENT_TIMESTAMP, 'System switch list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(4,  'Task Status', 'sys_job_status',      '0', 'admin', CURRENT_TIMESTAMP, 'Task status list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(5,  'Task Group', 'sys_job_group',       '0', 'admin', CURRENT_TIMESTAMP, 'Task group list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(6,  'System Yes/No', 'sys_yes_no',          '0', 'admin', CURRENT_TIMESTAMP, 'System yes/no list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(7,  'Notice Type', 'sys_notice_type',     '0', 'admin', CURRENT_TIMESTAMP, 'Notice type list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(8,  'Notice Status', 'sys_notice_status',   '0', 'admin', CURRENT_TIMESTAMP, 'Notice status list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(9,  'Operation Type', 'sys_oper_type',       '0', 'admin', CURRENT_TIMESTAMP, 'Operation type list');
insert into sys_dict_type (dict_id, dict_name, dict_type, status, create_by, create_time, remark) values(10, 'System Status', 'sys_common_status',   '0', 'admin', CURRENT_TIMESTAMP, 'Login status list');


-- ----------------------------
-- 12. Dictionary Data Table
-- ----------------------------
drop table if exists sys_dict_data cascade;
create table sys_dict_data
(
  dict_code         BIGSERIAL PRIMARY KEY,
  dict_sort        integer        default 0,
  dict_label       varchar(100)   default '',
  dict_value       varchar(100)   default '',
  dict_type        varchar(100)   default '',
  css_class        varchar(100)   default null,
  list_class       varchar(100)   default null,
  is_default       char(1)        default 'N',
  status           char(1)        default '0',
  create_by        varchar(64)    default '',
  create_time      timestamp,
  update_by        varchar(64)    default '',
  update_time      timestamp,
  remark           varchar(500)   default null
);

COMMENT ON TABLE sys_dict_data IS 'Dictionary Data Table';
COMMENT ON COLUMN sys_dict_data.dict_code IS 'Dictionary Code';
COMMENT ON COLUMN sys_dict_data.dict_sort IS 'Dictionary Sort';
COMMENT ON COLUMN sys_dict_data.dict_label IS 'Dictionary Label';
COMMENT ON COLUMN sys_dict_data.dict_value IS 'Dictionary Value';
COMMENT ON COLUMN sys_dict_data.dict_type IS 'Dictionary Type';
COMMENT ON COLUMN sys_dict_data.css_class IS 'CSS Class (Style Extension)';
COMMENT ON COLUMN sys_dict_data.list_class IS 'Table Display Style';
COMMENT ON COLUMN sys_dict_data.is_default IS 'Is Default (Y Yes, N No)';
COMMENT ON COLUMN sys_dict_data.status IS 'Status (0 Normal, 1 Disabled)';
COMMENT ON COLUMN sys_dict_data.create_by IS 'Creator';
COMMENT ON COLUMN sys_dict_data.create_time IS 'Create Time';
COMMENT ON COLUMN sys_dict_data.update_by IS 'Updater';
COMMENT ON COLUMN sys_dict_data.update_time IS 'Update Time';
COMMENT ON COLUMN sys_dict_data.remark IS 'Remark';

insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(1,  1,  'Male',     '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', CURRENT_TIMESTAMP, 'Gender male');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(2,  2,  'Female',   '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', CURRENT_TIMESTAMP, 'Gender female');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(3,  3,  'Unknown',  '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', CURRENT_TIMESTAMP, 'Gender unknown');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(4,  1,  'Show',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', CURRENT_TIMESTAMP, 'Show menu');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(5,  2,  'Hide',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Hide menu');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(6,  1,  'Normal',   '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', CURRENT_TIMESTAMP, 'Normal status');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(7,  2,  'Disabled', '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Disabled status');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(8,  1,  'Normal',   '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', CURRENT_TIMESTAMP, 'Normal status');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(9,  2,  'Paused',   '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Paused status');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(10, 1,  'Default',  'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', CURRENT_TIMESTAMP, 'Default group');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(11, 2,  'System',   'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', CURRENT_TIMESTAMP, 'System group');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(12, 1,  'Yes',      'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', CURRENT_TIMESTAMP, 'System default yes');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(13, 2,  'No',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'System default no');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(14, 1,  'Notice',   '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', CURRENT_TIMESTAMP, 'Notice');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(15, 2,  'Announcement', '2',   'sys_notice_type',     '',   'success', 'N', '0', 'admin', CURRENT_TIMESTAMP, 'Announcement');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(16, 1,  'Normal',   '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', CURRENT_TIMESTAMP, 'Normal status');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(17, 2,  'Closed',   '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Closed status');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(18, 99, 'Other',    '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', CURRENT_TIMESTAMP, 'Other operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(19, 1,  'Create',   '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', CURRENT_TIMESTAMP, 'Create operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(20, 2,  'Update',   '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', CURRENT_TIMESTAMP, 'Update operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(21, 3,  'Delete',   '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Delete operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(22, 4,  'Grant',    '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', CURRENT_TIMESTAMP, 'Grant operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(23, 5,  'Export',   '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', CURRENT_TIMESTAMP, 'Export operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(24, 6,  'Import',   '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', CURRENT_TIMESTAMP, 'Import operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(25, 7,  'Force Logout', '7',   'sys_oper_type',       '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Force logout operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(26, 8,  'Generate Code', '8',  'sys_oper_type',       '',   'warning', 'N', '0', 'admin', CURRENT_TIMESTAMP, 'Generate operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(27, 9,  'Clear Data', '9',     'sys_oper_type',       '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Clear operation');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(28, 1,  'Success',  '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', CURRENT_TIMESTAMP, 'Success status');
insert into sys_dict_data (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark) values(29, 2,  'Failed',   '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', CURRENT_TIMESTAMP, 'Failed status');


-- ----------------------------
-- 5. Menu Permission Table
-- ----------------------------
drop table if exists sys_menu cascade;
create table sys_menu (
  menu_id           BIGSERIAL PRIMARY KEY,
  menu_name         varchar(50)    not null,
  parent_id         bigint         default 0,
  order_num         integer        default 0,
  path              varchar(200)   default '',
  component         varchar(255)   default null,
  query             varchar(255)   default null,
  route_name        varchar(50)    default '',
  is_frame          integer        default 1,
  is_cache          integer        default 0,
  menu_type         char(1)        default '',
  visible           char(1)        default '0',
  status            char(1)        default '0',
  perms             varchar(100)   default null,
  icon              varchar(100)   default '#',
  create_by         varchar(64)    default '',
  create_time       timestamp,
  update_by         varchar(64)    default '',
  update_time       timestamp,
  remark            varchar(500)   default ''
);

COMMENT ON TABLE sys_menu IS 'Menu Permission Table';
COMMENT ON COLUMN sys_menu.menu_id IS 'Menu ID';
COMMENT ON COLUMN sys_menu.menu_name IS 'Menu Name';
COMMENT ON COLUMN sys_menu.parent_id IS 'Parent Menu ID';
COMMENT ON COLUMN sys_menu.order_num IS 'Display Order';
COMMENT ON COLUMN sys_menu.path IS 'Route Path';
COMMENT ON COLUMN sys_menu.component IS 'Component Path';
COMMENT ON COLUMN sys_menu.query IS 'Route Query';
COMMENT ON COLUMN sys_menu.route_name IS 'Route Name';
COMMENT ON COLUMN sys_menu.is_frame IS 'Is External Link (0 Yes, 1 No)';
COMMENT ON COLUMN sys_menu.is_cache IS 'Is Cache (0 Cache, 1 No Cache)';
COMMENT ON COLUMN sys_menu.menu_type IS 'Menu Type (M Directory, C Menu, F Button)';
COMMENT ON COLUMN sys_menu.visible IS 'Menu Status (0 Show, 1 Hide)';
COMMENT ON COLUMN sys_menu.status IS 'Menu Status (0 Normal, 1 Disabled)';
COMMENT ON COLUMN sys_menu.perms IS 'Permission Identifier';
COMMENT ON COLUMN sys_menu.icon IS 'Menu Icon';
COMMENT ON COLUMN sys_menu.create_by IS 'Creator';
COMMENT ON COLUMN sys_menu.create_time IS 'Create Time';
COMMENT ON COLUMN sys_menu.update_by IS 'Updater';
COMMENT ON COLUMN sys_menu.update_time IS 'Update Time';
COMMENT ON COLUMN sys_menu.remark IS 'Remark';

-- ----------------------------
-- Initialize Menu Data
-- ----------------------------
-- Level 1 Menus
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1', 'System Management', '0', '1', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', CURRENT_TIMESTAMP, 'System management directory');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('2', 'System Monitor', '0', '2', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', CURRENT_TIMESTAMP, 'System monitor directory');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('3', 'System Tools', '0', '3', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', CURRENT_TIMESTAMP, 'System tools directory');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('4', 'RuoYi Official', '0', '4', 'http://ruoyi.vip', null, '', '', 0, 0, 'M', '0', '0', '', 'guide',    'admin', CURRENT_TIMESTAMP, 'RuoYi official website');
-- Level 2 Menus
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('100',  'User Management', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', CURRENT_TIMESTAMP, 'User management menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('101',  'Role Management', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', CURRENT_TIMESTAMP, 'Role management menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('102',  'Menu Management', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', CURRENT_TIMESTAMP, 'Menu management menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('103',  'Dept Management', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', CURRENT_TIMESTAMP, 'Dept management menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('104',  'Post Management', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', CURRENT_TIMESTAMP, 'Post management menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('105',  'Dict Management', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', CURRENT_TIMESTAMP, 'Dict management menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('106',  'Config Setting', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', CURRENT_TIMESTAMP, 'Config setting menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('107',  'Notice', '1',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', CURRENT_TIMESTAMP, 'Notice menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('108',  'Log Management', '1',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', CURRENT_TIMESTAMP, 'Log management menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('109',  'Online Users', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', CURRENT_TIMESTAMP, 'Online users menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('110',  'Scheduled Tasks', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', CURRENT_TIMESTAMP, 'Scheduled tasks menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('111',  'Data Monitor', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', CURRENT_TIMESTAMP, 'Data monitor menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('112',  'Server Monitor', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', CURRENT_TIMESTAMP, 'Server monitor menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('113',  'Cache Monitor', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', CURRENT_TIMESTAMP, 'Cache monitor menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('114',  'Cache List', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', CURRENT_TIMESTAMP, 'Cache list menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('115',  'Form Builder', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', CURRENT_TIMESTAMP, 'Form builder menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('116',  'Code Generator', '3',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', CURRENT_TIMESTAMP, 'Code generator menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('117',  'System API', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', CURRENT_TIMESTAMP, 'System API menu');
-- Level 3 Menus
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('500',  'Operation Log', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', CURRENT_TIMESTAMP, 'Operation log menu');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('501',  'Login Log', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', CURRENT_TIMESTAMP, 'Login log menu');
-- User Management Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1000', 'User Query', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1001', 'User Add', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1002', 'User Edit', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1003', 'User Delete', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1004', 'User Export', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1005', 'User Import', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1006', 'Reset Password', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', CURRENT_TIMESTAMP, '');
-- Role Management Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1007', 'Role Query', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1008', 'Role Add', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1009', 'Role Edit', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1010', 'Role Delete', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1011', 'Role Export', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', CURRENT_TIMESTAMP, '');
-- Menu Management Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1012', 'Menu Query', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1013', 'Menu Add', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1014', 'Menu Edit', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1015', 'Menu Delete', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', CURRENT_TIMESTAMP, '');
-- Dept Management Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1016', 'Dept Query', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1017', 'Dept Add', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1018', 'Dept Edit', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1019', 'Dept Delete', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', CURRENT_TIMESTAMP, '');
-- Post Management Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1020', 'Post Query', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1021', 'Post Add', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1022', 'Post Edit', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1023', 'Post Delete', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1024', 'Post Export', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', CURRENT_TIMESTAMP, '');
-- Dict Management Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1025', 'Dict Query', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1026', 'Dict Add', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1027', 'Dict Edit', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1028', 'Dict Delete', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1029', 'Dict Export', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', CURRENT_TIMESTAMP, '');
-- Config Setting Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1030', 'Config Query', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1031', 'Config Add', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1032', 'Config Edit', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1033', 'Config Delete', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1034', 'Config Export', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', CURRENT_TIMESTAMP, '');
-- Notice Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1035', 'Notice Query', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1036', 'Notice Add', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1037', 'Notice Edit', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1038', 'Notice Delete', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', CURRENT_TIMESTAMP, '');
-- Operation Log Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1039', 'Operation Query', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1040', 'Operation Delete', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1041', 'Log Export', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', CURRENT_TIMESTAMP, '');
-- Login Log Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1042', 'Login Query', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1043', 'Login Delete', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1044', 'Log Export', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1045', 'Account Unlock', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', CURRENT_TIMESTAMP, '');
-- Online Users Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1046', 'Online Query', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1047', 'Batch Logout', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1048', 'Single Logout', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', CURRENT_TIMESTAMP, '');
-- Scheduled Task Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1049', 'Task Query', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1050', 'Task Add', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1051', 'Task Edit', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1052', 'Task Delete', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1053', 'Status Change', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1054', 'Task Export', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', CURRENT_TIMESTAMP, '');
-- Code Generator Buttons
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1055', 'Generate Query', '116', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1056', 'Generate Edit', '116', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1057', 'Generate Delete', '116', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1058', 'Import Code', '116', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1059', 'Preview Code', '116', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', CURRENT_TIMESTAMP, '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark) values('1060', 'Generate Code', '116', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', CURRENT_TIMESTAMP, '');
