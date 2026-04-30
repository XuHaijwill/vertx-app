
-- PostgreSQL-compatible migration: create configuration table and seed default values
DROP TABLE IF EXISTS sys_config;

CREATE TABLE sys_config (
  config_id  BIGSERIAL PRIMARY KEY,
  config_name VARCHAR(100) DEFAULT '' ,
  config_key  VARCHAR(100) DEFAULT '' ,
  config_value VARCHAR(500) DEFAULT '' ,
  config_type CHAR(1) DEFAULT 'N', -- system built-in (Y = yes, N = no)
  create_by VARCHAR(64) DEFAULT '' ,
  create_time TIMESTAMP WITHOUT TIME ZONE DEFAULT now(),
  update_by VARCHAR(64) DEFAULT '' ,
  update_time TIMESTAMP WITHOUT TIME ZONE DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL
);

-- Seed default configuration entries (English descriptions)
INSERT INTO sys_config (config_id, config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark) VALUES
 (1, 'Default skin name for main layout', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', now(), '', NULL, 'Default skins: blue skin-blue, green skin-green, purple skin-purple, red skin-red, yellow skin-yellow'),
 (2, 'User management - initial password', 'sys.user.initPassword', '123456', 'Y', 'admin', now(), '', NULL, 'Initial password 123456'),
 (3, 'Main layout - sidebar theme', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', now(), '', NULL, 'Dark theme: theme-dark; Light theme: theme-light'),
 (4, 'Account - captcha enabled', 'sys.account.captchaEnabled', 'true', 'Y', 'admin', now(), '', NULL, 'Whether captcha is enabled (true = enabled, false = disabled)'),
 (5, 'Account - allow user registration', 'sys.account.registerUser', 'false', 'Y', 'admin', now(), '', NULL, 'Whether registration is enabled (true enabled, false disabled)'),
 (6, 'Login - IP blacklist list', 'sys.login.blackIPList', '', 'Y', 'admin', now(), '', NULL, 'Login IP blacklist; multiple entries separated by ; supports patterns (* wildcard, CIDR subnets)'),
 (7, 'User management - initial password change policy', 'sys.account.initPasswordModify', '1', 'Y', 'admin', now(), '', NULL, '0: disabled; 1: prompt user to change initial password on login'),
 (8, 'User management - password update cycle (days)', 'sys.account.passwordValidateDays', '0', 'Y', 'admin', now(), '', NULL, 'Password update period in days; 0 = no limit; if >0 must be 1-364'),
 (9, 'User management - password character rules', 'sys.account.chrtype', '0', 'Y', 'admin', now(), '', NULL, '0:any;1:digits only;2:letters only;3:letters and digits;4:letters,digits and special characters (~!@#$%^&*()-=_+)');

