---
AIGC:
    Label: "1"
    ContentProducer: 001191440300708461136T1XGW3
    ProduceID: 79fc19b16435b8c4ea41fe8176de0d34_a79ffa77d02e11f1acb8525400638852
    ReservedCode1: e/oSnI45lCr75CUUtp09xFhxYi4T+6aJP09VNiYiIltDnTXamvs3jOIL0dGP2LqbVdjYgcCv/aqT1AiE2X3vl9bD3aIXm43pOm4K9x94LNOBlwgONPZSiphdCzbtTRnJP0XX1Nl0KxTHMvwrzWZUrqyooQst9wJf+t9b9D3ZqOJ8J8L7sOB+qRGKSj4=
    ContentPropagator: 001191440300708461136T1XGW3
    PropagateID: 79fc19b16435b8c4ea41fe8176de0d34_a79ffa77d02e11f1acb8525400638852
    ReservedCode2: e/oSnI45lCr75CUUtp09xFhxYi4T+6aJP09VNiYiIltDnTXamvs3jOIL0dGP2LqbVdjYgcCv/aqT1AiE2X3vl9bD3aIXm43pOm4K9x94LNOBlwgONPZSiphdCzbtTRnJP0XX1Nl0KxTHMvwrzWZUrqyooQst9wJf+t9b9D3ZqOJ8J8L7sOB+qRGKSj4=
---

# JeecgBoot 本地开发环境说明

> 搭建时间：2026-10-25　项目版本：JeecgBoot 3.9.5　分支：master（浅克隆，depth=1）

## 一、项目位置

| 项 | 路径 |
| --- | --- |
| 项目根目录 | `E:\Java\JeecgBoot` |
| 后端（Spring Boot + Maven 多模块） | `E:\Java\JeecgBoot\jeecg-boot` |
| 前端（Vue3 + Vite） | `E:\Java\JeecgBoot\jeecgboot-vue3` |
| 数据库脚本 | `E:\Java\JeecgBoot\jeecg-boot\db\jeecgboot-mysql-5.7.sql` |
| 启动脚本目录 | `E:\Java\scripts` |

仓库为浅克隆（不含完整历史），后续如需完整历史执行：`git fetch --unshallow`

## 二、已安装组件

| 组件 | 版本 | 路径 | 说明 |
| --- | --- | --- | --- |
| JDK | 17.0.20.1 (Temurin) | `E:\Java\tools\jdk-17.0.20.1+1` | JAVA_HOME 已配置（用户级） |
| Maven | 3.9.9 | `E:\Java\tools\apache-maven-3.9.9` | 走阿里云镜像；本地仓库 `E:\Java\maven-repo` |
| MySQL | 8.0.29 | `E:\Java\tools\mysql-8.0.29-winx64` | 免安装版，数据目录 `E:\Java\tools\mysql-data` |
| Redis | 5.0.14.1 | `E:\Java\tools\redis` | Windows 版，端口 6379 |
| Node.js | v24.16.0（原已装） | 系统 | 满足 Node 20.19+ 要求 |
| pnpm | 9.15.9 | npm 全局 | 满足 pnpm 9+ 要求 |
| Git | 2.49.0（原已装） | 系统 | — |

环境变量（用户级，重开终端生效）：

- `JAVA_HOME = E:\Java\tools\jdk-17.0.20.1+1`
- `MAVEN_HOME / M2_HOME = E:\Java\tools\apache-maven-3.9.9`
- `Path` 前置 `%JAVA_HOME%\bin`、`%MAVEN_HOME%\bin`

## 三、数据库与缓存

| 项 | 值 |
| --- | --- |
| MySQL 地址 | 127.0.0.1:3306 |
| 库名 | `jeecg-boot`（已导入 143 张表 + 初始化数据） |
| 用户名 / 密码 | `root` / `root` |
| 字符集 | utf8mb4 / utf8mb4_general_ci |
| Redis 地址 | 127.0.0.1:6379，无密码，db=0 |

> 密码 `root` 是为本地开发新建实例时设定的初始密码，与项目 `application-dev.yml` 默认值一致，生产环境请务必更换。

后端配置文件：`jeecg-boot\jeecg-module-system\jeecg-system-start\src\main\resources\application-dev.yml`（默认值已与本机环境匹配，无需修改）

## 四、启动方式

脚本均位于 `E:\Java\scripts`：

| 脚本 | 作用 |
| --- | --- |
| `start-all.bat` | 一键启动：自动拉起 MySQL/Redis + 后端 + 前端（推荐） |
| `start-services.bat` | 仅启动 MySQL + Redis |
| `start-backend.bat` | 仅启动后端（Maven spring-boot:run） |
| `start-frontend.bat` | 仅启动前端（pnpm dev） |
| `stop-services.bat` | 停止 MySQL + Redis |

访问地址：

- 前端：http://localhost:3100 （账号 `admin` / 密码 `123456`）
- 后端：http://localhost:8080/jeecg-boot
- 接口文档：http://localhost:8080/jeecg-boot/doc.html

当前状态：MySQL 与 Redis 已在后台运行；前端/后端已停止，执行 `start-all.bat` 即可。

## 五、已完成的验证

| 验证项 | 结果 |
| --- | --- |
| 项目克隆 | 成功，101 MB，目录结构完整 |
| 数据库导入 | 成功，143 张表，sys_user / sys_role 数据完整 |
| 后端编译 | `mvn clean install -DskipTests` 成功，产出 `jeecg-system-start-3.9.5.jar`（365 MB） |
| 后端启动 | 成功，8080 监听，日志出现 `Application Jeecg-Boot is running!` |
| 前端依赖 | `pnpm install` 成功，1603 个包 |
| 前端启动 | 成功，3100 端口，首页返回 200 |
| 前后端连通 | 前端 200、后端 doc.html 200 |

## 六、已做的配置调整

1. `jeecgboot-vue3\.env.development`：`VITE_USE_MOCK` 由 `true` 改为 `false`，让前端直连本地后端（原文件备份为 `.env.development.bak`）。
2. Maven 镜像已切换阿里云公共仓库：`C:\Users\Administrator\.m2\settings.xml`。

## 七、未配置项（按需开启）

| 功能 | 缺失依赖 | 说明 |
| --- | --- | --- |
| AI 知识库 / RAG | pgvector（PostgreSQL） | `application-dev.yml` 中 `jeecg.ai-rag`，官方推荐 Docker 起 `pgvector`，参考 https://help.jeecg.com/aigc/config |
| AI 对话 / 大模型 | API Key | `jeecg.ai-chat.apiKey` 当前为 `??`，需填 DeepSeek 等服务商的 Key |
| 邮件发送 | SMTP 密码 | `spring.mail.password` 当前为 `??` |

本机已装 Docker Desktop，但守护进程未运行；如需 Docker 方案，可先启动 Docker Desktop 再执行 `docker compose up -d`（项目根目录已带 `../docker-compose.yml`）。

## 八、常见问题

1. **8080 / 3100 / 3306 端口被占用**：`netstat -ano | findstr :8080` 查 PID，确认无其他 Java/Node 进程占用。
2. **MySQL 起不来**：查看 `E:\Java\tools\mysql-logs\error.log`；若提示 data 目录不可用，检查是否有残留进程占用数据目录。
3. **前端登录失败**：确认后端已出现 `Application Jeecg-Boot is running!`，且 `.env.development` 中 `VITE_USE_MOCK=false`。
4. **Maven 下载缓慢/失败**：确认 `C:\Users\Administrator\.m2\settings.xml` 中的阿里云镜像生效，本地仓库在 `E:\Java\maven-repo`（已缓存约 577 MB 依赖）。
5. **IDEA 开发**：IDEA 中打开 `E:\Java\JeecgBoot\jeecg-boot`，Project SDK 选 JDK 17，Maven 用户设置文件指向 `C:\Users\Administrator\.m2\settings.xml`，运行类 `jeecg-system-start` 模块下的 `org.jeecg.JeecgSystemApplication`。
*（内容由AI生成，仅供参考）*
