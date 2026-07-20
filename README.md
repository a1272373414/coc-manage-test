# COC 部落冲突数据后台管理系统

[![License](https://img.shields.io/github/license/WeixinCloud/wxcloudrun-express)](./LICENSE)
![Maven](https://img.shields.io/badge/maven-3.6.0-green)
![JDK](https://img.shields.io/badge/jdk-1.8-green)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.5.5-green)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.3.1-green)

基于 **微信云托管 + Spring Boot** 的《部落冲突》(Clash of Clans) 部落数据后台管理系统。提供登录鉴权、看板统计、列表管理、表单编辑等完整接口能力，支持多群组（多租户）数据隔离与角色权限控制。

> 后端设计详见 [design.txt](./design.txt)，数据库初始化脚本位于 [doc/db/](./doc/db/)。

---

## 一、功能特性

- **认证鉴权**：JWT Token 登录鉴权，BCrypt 密码加密，接口权限标识校验
- **多租户隔离**：基于 `group_no` 的群组级数据隔离（MyBatis-Plus 多租户插件自动拦截 SQL）
- **角色权限**：超级管理员 / 群主 / 普通管理员 / 部落成员 四级角色，菜单-权限矩阵控制
- **业务模块**：部落、成员、联赛、联赛报名与战绩、部落战与战绩的完整 CRUD
- **数据字典**：全局字典组 / 字典项管理，统一枚举取值
- **数据看板**：群组/部落/成员/联赛总数统计、部落战胜负统计、联赛排名与段位分布
- **通用能力**：统一响应封装、分页插件、逻辑删除、公共字段自动填充

## 二、技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.5.5 |
| 持久层 | MyBatis-Plus | 3.5.3.1 |
| 数据库 | MySQL | 云托管 MySQL |
| 鉴权 | JWT (jjwt) | 0.11.5 |
| 密码加密 | spring-security-crypto | - |
| 简化代码 | Lombok | 1.18.24 |
| 部署平台 | 微信云托管 | - |

## 三、快速开始

### 3.1 模板部署（推荐）

前往 [微信云托管快速开始页面](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/basic/guide.html)，选择 Java Springboot 模板，根据引导完成部署。

### 3.2 本地调试

1. 准备本地 MySQL 数据库，执行 [doc/db/init.sql](./doc/db/init.sql) 初始化表结构与初始数据
2. 修改 `src/main/resources/application-dev.yml` 中的数据源连接信息
3. 启动 `WxCloudRunApplication`，默认端口 `8080`
4. 详细调试指引参考 [微信云托管本地调试指南](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/guide/debug/)

### 3.3 实时开发

代码变动时无需重新构建和启动容器即可查看效果，参考 [微信云托管实时开发指南](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/guide/debug/dev.html)。

## 四、目录结构

```
.
├── Dockerfile                      容器构建文件（多阶段构建）
├── container.config.json           模板部署「服务设置」初始配置（二开请忽略）
├── pom.xml                         Maven 依赖配置
├── settings.xml                    Maven 镜像源配置
├── design.txt                      后端设计文档
├── doc/
│   ├── db.sql                      数据库说明
│   └── db/
│       ├── init.sql                数据库初始化脚本（建表 + 初始数据）
│       └── alter_sync_entity_columns.sql  字段同步脚本
└── src/main/
    ├── java/com/tencent/wxcloudrun/
    │   ├── WxCloudRunApplication.java    启动类
    │   ├── config/                       配置层（拦截器、MyBatis-Plus、JWT、用户上下文等）
    │   ├── controller/                   控制层（18 个 Controller）
    │   ├── service/                      业务层
    │   ├── mapper/                       MyBatis-Plus Mapper
    │   ├── entity/                       实体类（sys / biz / dict 三大模块）
    │   ├── dto/                          请求/响应对象
    │   └── dao/                          数据访问扩展
    └── resources/
        ├── application.yml               公共配置
        ├── application-dev.yml           开发环境配置
        ├── application-prod.yml          生产环境配置
        ├── mapper/                       MyBatis XML 映射
        └── static/                       静态资源
```

## 五、系统架构

```
Client(前端/接口)
   │
   ▼
[JWT 拦截器 + group_no 数据隔离拦截器]
   │
   ▼
Controller  →  Service / ServiceImpl  →  MyBatis-Plus Mapper  →  MySQL
   ▲
   │
Config (MyBatisPlusConfig：分页插件 + 多租户/group_no 隔离插件)
```

**核心配置类**：
- `MyBatisPlusConfig`：分页插件 + 多租户（group_no）隔离插件
- `JwtInterceptor` / `JwtUtil`：JWT 登录鉴权拦截器与工具类
- `WebConfig`：拦截器注册与白名单放行
- `UserContext` / `AuthUser`：当前登录用户上下文
- `AutoFillMetaObjectHandler`：created_at / updated_at 等公共字段自动填充
- `DataInitializer`：初始数据初始化
- `ApiResponse` / `PageResult`：统一响应与分页结果封装

## 六、API 接口

### 6.1 统一响应

所有接口统一返回 `ApiResponse`：

```json
{
  "code": 0,
  "errorMsg": "",
  "data": {}
}
```

分页接口 `data` 结构：`{ records: [...], total: 0, pageNum: 1, pageSize: 10 }`

### 6.2 鉴权约定

- 登录/注册接口开放访问；其余接口需请求头 `Authorization: Bearer <token>`
- 白名单：`/api/auth/login`、`/api/auth/register`、静态资源

### 6.3 接口清单（按模块）

| 模块 | 前缀 | 主要能力 | 权限 |
|------|------|---------|------|
| 认证 | `/api/auth` | 注册、登录、获取当前用户信息 | 开放/登录 |
| 系统 | `/api/sys` | 用户、角色、菜单、群组 CRUD | 超级管理员/群主 |
| 字典 | `/api/dict` | 字典组、字典项 CRUD | 超级管理员 |
| 部落 | `/api/clan` | 部落、成员 CRUD，成员联赛报名 | 群主/管理员/成员 |
| 联赛 | `/api/league` | 联赛、战绩、报名列表 CRUD | 群主/管理员/成员 |
| 部落战 | `/api/war` | 部落战、战绩 CRUD | 群主/管理员/成员 |
| 看板 | `/api/dashboard` | 总数统计、胜负统计、联赛排名 | 登录 |
| 计数器 | `/api/count` | 模板示例计数器（保留） | 开放 |

### 6.4 示例：登录

```bash
curl -X POST -H 'content-type: application/json' \
  -d '{"username":"admin","password":"123456"}' \
  http://localhost:8080/api/auth/login
```

### 6.5 示例：计数器（模板保留）

```bash
# 获取当前计数
curl http://localhost:8080/api/count

# 计数加一
curl -X POST -H 'content-type: application/json' \
  -d '{"action":"inc"}' \
  http://localhost:8080/api/count
```

## 七、角色权限

| 功能模块 | 超级管理员 | 群主 | 普通管理员 | 部落成员 |
|----------|:---------:|:----:|:---------:|:--------:|
| 群组 / 角色 / 菜单 / 字典管理 | ✅ | ❌ | ❌ | ❌ |
| 部落管理 | ✅(全部) | ✅(本群组) | ❌ | ❌ |
| 成员管理 | ✅(全部) | ✅ | ✅(本群组) | ❌ |
| 联赛 / 部落战管理 | ✅(全部) | ✅(本群组) | ✅(本群组) | 仅查看/报名 |
| 战绩录入 | ✅(全部) | ✅(本群组) | ✅(本群组) | 仅本人 |
| 数据看板 | ✅(全局) | ✅(本群组) | ✅(本群组) | ✅(本群组) |

**group_no 数据隔离**：
- 超级管理员 `group_no` 为空，可跨群组访问全部数据
- 其余角色仅能访问本群组业务数据，由 MyBatis-Plus 多租户插件在 SQL 层自动追加隔离条件

## 八、环境变量（生产部署）

如果不是通过微信云托管控制台部署模板代码，而是自行下载代码后手动新建服务部署，需在「服务设置」中补全以下环境变量：

| 变量名 | 说明 |
|--------|------|
| `MYSQL_ADDRESS` | MySQL 连接地址（含端口） |
| `MYSQL_USERNAME` | MySQL 用户名 |
| `MYSQL_PASSWORD` | MySQL 密码 |
| `MYSQL_DATABASE` | 数据库名（默认 `springboot_demo`） |
| `JWT_SECRET` | JWT 签名密钥（生产环境必须替换为强随机字符串，至少 32 位） |
| `JWT_EXPIRATION_MINUTES` | JWT 过期时间（分钟，默认 120） |

> 使用云托管内 MySQL 时，可在控制台 MySQL 页面获取连接信息。

## 九、数据库

- 字符集统一 `utf8mb4`，引擎 `InnoDB`
- 公共字段：`created_at` / `updated_at` / `created_by` / `updated_by` / `deleted`（逻辑删除）
- 业务表另含 `group_no` 隔离键
- 业务表关联以编号（`group_no` / `clan_no` / `league_no` / `war_no` / `member_no`）作为逻辑外键

主要表分组：
- **系统权限表**：`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`
- **数据字典表**：`dict_group`、`dict_item`
- **业务表**：`clan_group`、`clan`、`clan_member`、`league`、`league_signup`、`league_record`、`clan_war`、`clan_war_record`

完整 ER 关系图与字段定义详见 [design.txt](./design.txt)。

## 十、Dockerfile 最佳实践

请参考 [如何提高项目构建效率](https://developers.weixin.qq.com/miniprogram/dev/wxcloudrun/src/scene/build/speed.html)。

## License

[MIT](./LICENSE)
