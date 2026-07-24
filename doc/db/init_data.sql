-- ============================================================
-- 初始化数据脚本
-- 包含：角色、菜单、角色-菜单关联、默认管理员账号、字典数据
-- 使用 INSERT IGNORE 确保重复执行不会报错
-- 
-- 默认管理员密码：admin123 (BCrypt加密)
-- 如数据库中已有数据运行此脚本无副作用（IGNORE）
-- ============================================================

-- 1. 角色数据
INSERT IGNORE INTO sys_role (id, role_code, role_name, status) VALUES
(1, 'SUPER_ADMIN', '超级管理员', 1),
(2, 'GROUP_ADMIN', '部落组管理员', 1),
(3, 'LEAGUE_ADMIN', '赛事管理员', 1),
(4, 'MEMBER', '普通成员', 1),
(5, 'VISITOR', '游客', 1);

-- 2. 菜单数据（顶级菜单 menuType=0，子菜单 menuType=1）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, permission, sort, icon) VALUES
( 1, '数据看板',   0, 0, '/dashboard',     'dashboard:view',        10, 'Odometer'),
( 2, '部落管理',   0, 0, '/clan',          'clan:view',             20, 'OfficeBuilding'),
( 3, '部落战管理', 0, 0, '/war',           'war:view',              30, 'DataAnalysis'),
( 4, '联赛管理',   0, 0, '/league',        'league:view',           40, 'Trophy'),
( 5, '系统管理',   0, 0, '/system',        'system:manage',         50, 'Setting'),
( 6, '部落',       1, 2, '/clan/crud',     'clan:list',              1, NULL),
( 7, '部落成员',   1, 2, '/clan/member',   'clan:member:list',       2, NULL),
( 8, '部落战',     1, 3, '/war/crud',      'war:list',               1, NULL),
( 9, '部落战战绩', 1, 3, '/war/record',    'war:record:list',        2, NULL),
(10, '联赛',       1, 4, '/league/crud',   'league:list',            1, NULL),
(11, '部落成绩',   1, 4, '/league/score',  'league:score:list',      2, NULL),
(12, '联赛战绩',   1, 4, '/league/record', 'league:record:list',     3, NULL),
(13, '联赛报名',   1, 4, '/league/signup', 'league:signup:list',     4, NULL),
(14, '部落群组',   1, 5, '/clan/group',    'group:list',             1, NULL),
(15, '用户管理',   1, 5, '/sys/user',      'sys:user:list',          2, NULL),
(16, '角色管理',   1, 5, '/sys/role',      'sys:role:list',          3, NULL),
(17, '菜单管理',   1, 5, '/sys/menu',      'sys:menu:list',          4, NULL),
(18, '字典管理',   1, 5, '/dict',          'sys:dict:list',          5, NULL),
(19, '入组申请',   1, 5, '/clan/group/apply', 'group:apply:list',    6, NULL);

-- 3. 角色-菜单关联
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
-- 超级管理员：全部菜单
(1, 1),(1, 2),(1, 3),(1, 4),(1, 5),(1, 6),(1, 7),(1, 8),(1, 9),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15),(1,16),(1,17),(1,18),(1,19),
-- 部落组管理员：看不到角色管理和菜单管理，但可处理入组申请
(2, 5),(2,14),(2,15),(2,18),(2,19),
(2, 2),(2, 6),(2, 7),
(2, 3),(2, 8),(2, 9),
(2, 4),(2,10),(2,12),(2,13),
-- 系统管理对部落组管理员可见
(2, 1),
-- 游客：仅能看到入组申请菜单
(5, 19);

-- 4. 默认管理员账号（密码为 admin123 的 BCrypt 哈希，首次登录后建议修改）
INSERT IGNORE INTO sys_user (id, username, password, nickname, group_no, status) VALUES
(1, 'admin', '$2a$10$ZR0/nhgoX72xMj3dQRFDmujQAPHbjUVGVjCruxf1GRQH0xHGI0tcS', '超级管理员', NULL, 1);

-- 5. 用户-角色关联
INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- 6. 数据字典分组
INSERT IGNORE INTO dict_group (id, group_code, group_name, status) VALUES
(1, 'war_type',      '部落战类型', 1),
(2, 'league_type',   '联赛类型',   1),
(3, 'member_role',   '成员职位',   1),
(4, 'war_result',    '对战结果',   1),
(5, 'signup_status', '报名状态',   1),
(6, 'league_tier',   '联赛段位',   1);

-- 7. 数据字典条目
INSERT IGNORE INTO dict_item (id, group_code, item_value, item_name, sort, status) VALUES
-- 部落战类型
(1,  'war_type',     'normal',   '普通战',   1, 1),
(2,  'war_type',     'league',   '联赛',     2, 1),
-- 联赛类型
(3,  'league_type',  'clan_war', '部落战联赛', 1, 1),
(4,  'league_type',  'friendly', '友谊赛',   2, 1),
-- 成员职位
(5,  'member_role',  'leader',    '首领',    1, 1),
(6,  'member_role',  'co_leader', '副首领',  2, 1),
(7,  'member_role',  'elder',     '长老',    3, 1),
(8,  'member_role',  'member',    '成员',    4, 1),
-- 对战结果
(9,  'war_result',  'win',  '胜利', 1, 1),
(10, 'war_result',  'lose', '失败', 2, 1),
(11, 'war_result',  'draw', '平局', 3, 1),
-- 报名状态
(12, 'signup_status', '1', '未报名',   1, 1),
(13, 'signup_status', '2', '备选报名', 2, 1),
(14, 'signup_status', '3', '主动报名', 3, 1),
-- 联赛段位（value 1~18）
(15, 'league_tier', '1',  '铜杯III',    1,  1),
(16, 'league_tier', '2',  '铜杯II',     2,  1),
(17, 'league_tier', '3',  '铜杯I',      3,  1),
(18, 'league_tier', '4',  '银杯III',    4,  1),
(19, 'league_tier', '5',  '银杯II',     5,  1),
(20, 'league_tier', '6',  '银杯I',      6,  1),
(21, 'league_tier', '7',  '金杯III',    7,  1),
(22, 'league_tier', '8',  '金杯II',     8,  1),
(23, 'league_tier', '9',  '金杯I',      9,  1),
(24, 'league_tier', '10', '水晶杯III',  10, 1),
(25, 'league_tier', '11', '水晶杯II',   11, 1),
(26, 'league_tier', '12', '水晶杯I',    12, 1),
(27, 'league_tier', '13', '大师杯III',  13, 1),
(28, 'league_tier', '14', '大师杯II',   14, 1),
(29, 'league_tier', '15', '大师杯I',    15, 1),
(30, 'league_tier', '16', '冠军杯III',  16, 1),
(31, 'league_tier', '17', '冠军杯II',   17, 1),
(32, 'league_tier', '18', '冠军杯I',    18, 1);
