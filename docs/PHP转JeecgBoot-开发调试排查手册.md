> 面向 PHP 开发者的 JeecgBoot 上手手册 —— 基于本机 `E:\Java\JeecgBoot`（3.9.5 精简版）实测整理。
> 重点：**怎么写、怎么调、怎么查**，不铺概念。所有路径、行号、端口都来自这台机器当前状态。

---

## 0. 先认清这个仓库（30 分钟，别急着写代码）

### 0.1 技术底座

| 项 | 值 |
| --- | --- |
| 版本 | JeecgBoot **3.9.5**（前端 package.json 同名） |
| 后端 | **Spring Boot 4.1.0** + Java **17** + **`jakarta.*`** 命名空间（不是 `javax.*`） |
| ORM | MyBatis-Plus 3.5.16（`BaseMapper` / `ServiceImpl`） |
| 鉴权 | Shiro 3.0 + JWT，权限与 token 都落 Redis |
| 数据库 | MySQL 8.0，库名 `jeecg-boot`（143 张表，已有数据） |
| 迁移 | Flyway（`flyway/sql/mysql/V3.9.5_*.sql`），但 **autoconfigure 里排除**，由代码单独管理（`application-dev.yml:103-105`） |
| 前端 | Vue 3 + Vite 6 + TypeScript + Ant Design Vue 4 + Pinia |
| 接口文档 | Knife4j → `http://localhost:8080/jeecg-boot/doc.html` |

### 0.2 模块地图（这就是全部，只有 4 个）

```
jeecg-boot/
├── jeecg-boot-base-core/            # 框架核心：Shiro/JWT、MyBatis-Plus 配置、Result、AOP、JeecgController 基类
├── jeecg-module-system/
│   ├── jeecg-system-api/            # local-api（单体直接调用）/ cloud-api（Feign）
│   ├── jeecg-system-biz/            # 用户/角色/菜单/字典等业务实现
│   └── jeecg-system-start/          # ★ 启动模块：JeecgSystemApplication + 全部 yml
├── jeecg-boot-module/
│   ├── jeecg-module-demo/           # ★ 示例代码，你要抄的就是它
│   └── jeecg-boot-module-airag/     # AI/RAG
└── jeecg-server-cloud/              # 微服务（-P SpringCloud 才编）
```

### 0.3 ⚠️ 三个必须知道的"坑/事实"

1. **这是精简版，Online 低代码后端不在里面。**
   前端有完整 `src/views/super/online/**`，会请求 `/online/cgform/*`；数据库 `sys_permission` 里也确实有「低代码开发 / Online表单开发」菜单（591 条权限记录）。
   但后端**没有任何 `/online/**` 控制器** → 点这些菜单页面能出、数据全 404。
   **结论：写业务 = 照抄 `jeecg-module-demo` 手写 CRUD**，别在 Online 上浪费时间。
2. **前端的菜单/路由来自数据库，不是路由文件。**
   后端返回 `sys_permission` → 前端按 `component` 字符串去 `src/views/**` 里找文件（`src/router/helper/routeHelper.ts:108` 的 `dynamicImport`）。
   所以"新页面 404/白屏"99% 是 `component` 写错或文件路径不匹配，不是路由没注册。
3. **没有 PHP 那种"改完刷新就生效"。**
   Java 改**方法体**可以 HotSwap 生效；改**字段/注解/新增类**必须重启。前端 Vite HMR 是秒级。

### 0.4 本机环境（已就绪，别再装）

| 组件 | 值 |
| --- | --- |
| JDK 17 | `E:\Java\tools\jdk-17.0.20.1+1`（JAVA_HOME 已配） |
| Maven 3.9.9 | `E:\Java\tools\apache-maven-3.9.9`，本地仓库 `E:\Java\maven-repo`，阿里云镜像 |
| MySQL 8.0.29 | `127.0.0.1:3306`，`root/root`，库 `jeecg-boot` |
| Redis 5 | `127.0.0.1:6379`，无密码 |
| 启动脚本 | `E:\Java\scripts\start-all.bat` / `start-backend.bat` / `start-frontend.bat` |

**当前状态（我查过）**：MySQL、Redis、前端 3100 都在跑；**后端 8080 没起**。先跑 `E:\Java\scripts\start-backend.bat`。

访问：前端 `http://localhost:3100`（`admin` / `123456`）；后端 `http://localhost:8080/jeecg-boot`；Druid 监控 `/jeecg-boot/druid`（`admin/123456`）。

---

## 1. 心智模型转换（PHP → Java/Spring）

这一个表能省你三天：

| 你在 PHP 里熟悉的 | JeecgBoot 里对应 | 关键差异 |
| --- | --- | --- |
| `index.php` 入口 + 路由 | `DispatcherServlet` + `@RestController` `@RequestMapping` | 路由是**类上注解**扫出来的，不在配置文件 |
| `composer.json` / vendor | `pom.xml` / `~/.m2` + `E:\Java\maven-repo` | 多模块父子 pom，改完记得 `mvn -pl ... -am` |
| Laravel/ThinkPHP 的 ORM | MyBatis-Plus `BaseMapper` + XML | 两种写法混用：单表用 Wrapper，复杂 SQL 写 XML |
| `migration` | Flyway `V3.9.5_x__xxx.sql` | 本项目只在启动模块的 `flyway/sql/mysql` 下 |
| 中间件 middleware | Filter / Interceptor / AOP 切面 | 登录校验在 `JwtFilter`，日志在 `@AutoLog` 切面 |
| `session` | **JWT + Redis** | 后端无状态，token 在 header `X-Access-Token` |
| Blade / Twig 模板 | Vue3 SFC（`.vue`） | 前后端彻底分离，模板不存在了 |
| 数组啥都装 | `Entity` / `VO` / `DTO` 强类型 | 编译期就报错，这是好事 |
| `var_dump` / `dd()` | 断点 + `log.info` | 见第 5 节 |
| 全局函数随手调 | **依赖注入**（`@Autowired` 或构造注入） | 不要 `new`，要什么声明什么 |

**五条铁律**（违反就会写得很痛）：

1. Controller 只做：接参数 → 调 Service → 包 `Result` 返回。**业务逻辑一行都不要写在 Controller**。
2. 一个表 = 5 个文件：`Entity`、`Mapper`、`Mapper.xml`、`IService`、`ServiceImpl`，再加 `Controller`。
3. 时间字段统一 `Date` + `yyyy-MM-dd HH:mm:ss`（Jackson 已全局配好）。
4. 主键 `String` 类型 + `@TableId(type = IdType.ASSIGN_ID)`（雪花 ID，不是自增）。
5. 所有对外接口返回 `Result<T>`，前端只认 `code === 200`。

---

## 2. Day 1：跑起来 + 会"看"（半天）

### 2.1 启动

```bat
E:\Java\scripts\start-services.bat     :: MySQL + Redis
E:\Java\scripts\start-backend.bat      :: 后端 8080，看到 "Application Jeecg-Boot is running!" 才算成功
cd /d E:\Java\JeecgBoot\jeecgboot-vue3
pnpm dev                               :: 前端 3100（现在已在跑）
```

IDEA 方式：打开 `E:\Java\JeecgBoot\jeecg-boot`，SDK 选 JDK 17，运行
`jeecg-module-system/jeecg-system-start/src/main/java/org/jeecg/JeecgSystemApplication.java`。

### 2.2 立刻做三件事（都是"排查基本功"）

1. **打开 SQL 日志**：`jeecg-system-start/src/main/resources/application-dev.yml:175`
   把 `#log-impl: org.apache.ibatis.logging.stdout.StdOutImpl` 取消注释 → 每条 SQL + 参数直接打在控制台。**这是你以后 80% 排查的起点。**
2. **打开浏览器 DevTools 登录一次**，看 `POST /jeecgboot/sys/login`：
   请求 → `{username, password, captcha, checkKey}`；响应 → `{code:200, result:{token, userInfo}}`；token 落在 `localStorage`。
3. **点开 `doc.html`**，找到 `/sys/login`、`/test/jeecgDemo/list`，直接在页面上调一次。

### 2.3 日志在哪

`logback-spring.xml` 里 `LOG_HOME = ../logs`（**相对启动工作目录**）：
IDEA 里跑 → `E:\Java\JeecgBoot\jeecg-boot\jeecg-module-system\logs\`；
`start-backend.bat` 里跑 → 看脚本 `cd` 到哪个目录，上一级的 `logs\`。

文件：`jeecgboot-yyyy-MM-dd.0.log`（业务）、`error-log.html`（错误汇总，出问题先看这个）。

> 验收：能说出"我这条请求最后打了哪条 SQL"。

---

## 3. Day 2：读通一条完整链路（最关键的一天）

以**单表 DEMO** 为标本（这是官方留给你抄的标准答案）：

| 层 | 文件 |
| --- | --- |
| 表 | `jeecg_demo` |
| Controller | `jeecg-module-demo/.../demo/test/controller/JeecgDemoController.java` |
| Entity | `.../demo/test/entity/JeecgDemo.java` |
| Mapper + XML | `.../demo/test/mapper/JeecgDemoMapper.java`、`mapper/xml/JeecgDemoMapper.xml` |
| Service | `.../demo/test/service/IJeecgDemoService.java` + `impl/JeecgDemoServiceImpl.java` |
| 前端页面 | `jeecgboot-vue3/src/views/system/examples/demo/index.vue` |
| 前端字段/搜索 | 同目录 `demo.data.ts` |
| 前端接口 | 同目录 `demo.api.ts` |

### 3.1 一次"列表查询"的完整链路（背下来）

```
index.vue  useListPage({ api: list })
   ↓ defHttp.get('/test/jeecgDemo/list', {pageNo, pageSize, ...搜索条件})
   ↓ Vite 代理 /jeecgboot → http://localhost:8080/jeecg-boot   (.env.development:9)
   ↓ JwtFilter 校验 X-Access-Token（Redis 里查）
   ↓ JeecgDemoController.list(JeecgDemo, pageNo, pageSize, req)
        QueryGenerator.initQueryWrapper(entity, request.getParameterMap())  ← 自动把请求参数拼成 where
        jeecgDemoService.page(page, queryWrapper)                          ← MyBatis-Plus 分页
   ↓ JeecgDemoMapper / XML
   ↓ MySQL
   ↑ Result.OK(IPage)  → {code:200, success:true, result:{records, total, ...}}
   ↑ axios 响应拦截器判 code===200，否则弹错误
   ↑ BasicTable 渲染
```

四个"框架魔法"，理解它们就理解了一半：

1. **`QueryGenerator.initQueryWrapper`**：URL 上带 `name=abc`、`age_begin=18&age_end=30`、`createTime_ge=...` 会自动变成 SQL 条件，不用你写 if。
2. **`JeecgController<T, IService>` 基类**：直接白送你 `exportXls` / `importExcel`（AutoPoi），Controller 继承它就有。
3. **`Result<T>`**：`Result.OK(data)`、`Result.OK("保存成功", data)`、`Result.error("xxx")`。
4. **`@AutoLog(value="...")`**：方法级注解，自动写 `sys_log` 操作日志；`@PermissionData(pageComponent=...)` 控制数据权限（只查自己部门等）。

> 验收：不看代码，能口头讲出上面这条链路，并指出分页参数从哪来。

---

## 4. Day 3-4：动手写第一个模块（照抄改造）

目标：一个「客户表 demo_customer」的完整 CRUD（列表/新增/编辑/删除/导出）。

### 4.1 建表

```sql
CREATE TABLE demo_customer (
  id          varchar(36)  NOT NULL COMMENT '主键',
  name        varchar(100) NOT NULL COMMENT '客户名称',
  phone       varchar(20)  DEFAULT NULL COMMENT '电话',
  amount      decimal(12,2) DEFAULT '0.00' COMMENT '金额',
  status      int          DEFAULT '1' COMMENT '状态',
  create_by   varchar(50)  DEFAULT NULL,
  create_time datetime     DEFAULT NULL,
  update_by   varchar(50)  DEFAULT NULL,
  update_time datetime     DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';
```

> 字段命名用 `snake_case`，Java 用 `camelCase`，MyBatis-Plus 自动映射（`application-dev.yml:172 table-underline: true`）。
> 建表走 SQL 就好；**不要**改 `jeecgboot-mysql-5.7.sql`，那是初始化脚本。

### 4.2 后端五件套（新建包 `org.jeecg.modules.demo.customer`）

**Entity**

```java
@Data
@TableName("demo_customer")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public class DemoCustomer implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    @Excel(name = "客户名称", width = 20)
    private String name;
    private String phone;
    private BigDecimal amount;
    private Integer status;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
}
```

**Mapper**（继承就有 CRUD，不用写 SQL）

```java
public interface DemoCustomerMapper extends BaseMapper<DemoCustomer> {}
```

**Service / Impl**

```java
public interface IDemoCustomerService extends IService<DemoCustomer> {}
@Service
public class DemoCustomerServiceImpl extends ServiceImpl<DemoCustomerMapper, DemoCustomer> implements IDemoCustomerService {}
```

**Controller**（照 `JeecgDemoController` 抄，删掉用不上的）

```java
@Slf4j
@Tag(name = "客户管理")
@RestController
@RequestMapping("/demo/customer")
public class DemoCustomerController extends JeecgController<DemoCustomer, IDemoCustomerService> {
    @Autowired private IDemoCustomerService service;

    @GetMapping("/list")
    public Result<IPage<DemoCustomer>> list(DemoCustomer q,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<DemoCustomer> w = QueryGenerator.initQueryWrapper(q, req.getParameterMap());
        w.orderByDesc("create_time");
        return Result.OK(service.page(new Page<>(pageNo, pageSize), w));
    }

    @PostMapping("/add")
    @AutoLog(value = "客户-新增")
    public Result<?> add(@RequestBody DemoCustomer e) {
        e.setCreateTime(new Date());
        service.save(e);
        return Result.OK("添加成功");
    }

    @PutMapping("/edit")
    @AutoLog(value = "客户-编辑")
    public Result<?> edit(@RequestBody DemoCustomer e) {
        service.updateById(e.setUpdateTime(new Date()));
        return Result.OK("编辑成功");
    }

    @DeleteMapping("/delete")
    public Result<?> delete(@RequestParam String id) { service.removeById(id); return Result.OK("删除成功"); }

    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestParam String ids) {
        service.removeByIds(Arrays.asList(ids.split(","))); return Result.OK("批量删除成功");
    }

    @GetMapping("/queryById")
    public Result<DemoCustomer> queryById(@RequestParam String id) { return Result.OK(service.getById(id)); }

    // exportXls / importExcel 由基类 JeecgController 提供，需要时自己写两个方法转发
}
```

> 注意：`JeecgDemoController` 用的是 `com.alibaba.fastjson`（1.x 命名）——**新代码请用 `com.alibaba.fastjson2`**，别抄那行 import。

### 4.3 前端三件套（新建 `src/views/demo/customer/`）

`customer.api.ts`

```ts
import { defHttp } from '/@/utils/http/axios';
enum Api {
  list = '/demo/customer/list',
  add = '/demo/customer/add',
  edit = '/demo/customer/edit',
  delete = '/demo/customer/delete',
  deleteBatch = '/demo/customer/deleteBatch',
  exportXls = '/demo/customer/exportXls',
}
export const getList = (params) => defHttp.get({ url: Api.list, params });
export const saveOrUpdate = (params, isUpdate) =>
  defHttp[isUpdate ? 'put' : 'post']({ url: isUpdate ? Api.edit : Api.add, params });
export const deleteOne = (id) => defHttp.delete({ url: Api.delete, params: { id } }, { joinParamsToUrl: true });
```

`customer.data.ts`

```ts
import { BasicColumn, FormSchema } from '/@/components/Table';
export const columns: BasicColumn[] = [
  { title: '客户名称', dataIndex: 'name', width: 160 },
  { title: '电话', dataIndex: 'phone', width: 120 },
  { title: '金额', dataIndex: 'amount', width: 120 },
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
];
export const searchFormSchema: FormSchema[] = [
  { label: '客户名称', field: 'name', component: 'Input', colProps: { span: 6 } },
];
```

`index.vue`

```vue
<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button type="primary" @click="handleAdd">新增</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="[
          { label: '编辑', onClick: handleEdit.bind(null, record) },
          { label: '删除', color: 'error', popConfirm: { title: '确认删除?', confirm: handleDelete.bind(null, record) } },
        ]" />
      </template>
    </BasicTable>
    <DemoCustomerModal @register="registerModal" @success="reload" />
  </div>
</template>
<script lang="ts" setup>
  import { BasicTable, TableAction } from '/@/components/Table';
  import { useListPage } from '/@/hooks/system/useListPage';
  import { useModal } from '/@/components/Modal';
  import { columns, searchFormSchema } from './customer.data';
  import { getList, deleteOne } from './customer.api';
  import DemoCustomerModal from './CustomerModal.vue';

  const [registerModal, { openModal }] = useModal();
  const { tableContext } = useListPage({
    tableProps: { api: getList, columns, canResize: false, formConfig: { schemas: searchFormSchema } },
  });
  const [registerTable, { reload }] = tableContext;
  const handleAdd = () => openModal(true, { isUpdate: false });
  const handleEdit = (r) => openModal(true, { record: r, isUpdate: true });
  const handleDelete = (r) => deleteOne(r.id).then(() => reload());
</script>
```

> 弹窗表单直接抄 `views/system/examples/demo/DemoModal.vue`，把字段名换掉即可。

### 4.4 挂到菜单上（关键一步，绝大多数"页面打不开"都在这）

打开前端 **系统管理 → 菜单管理 → 新增**：

| 字段 | 值 | 说明 |
| --- | --- | --- |
| 菜单标题 | 客户管理 | |
| 菜单类型 | 一级菜单 | |
| 访问路径 | `/demo/customer` | 浏览器地址栏 |
| **组件路径** | `demo/customer/index` | ★ 相对 `src/views/`，**不带 `.vue`、不带开头 `/`** |
| 授权标识 | `demo:customer:list` | 后端可用 `@RequiresPermissions` 校验 |

保存后**给角色授权**（系统管理 → 角色管理 → 授权），然后**退出重登**（菜单是登录时拉的）。

等价的 SQL（不想点页面时用，字段已在 `sys_permission` 里核对过）：

```sql
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, menu_type,
       sort_no, always_show, is_leaf, keep_alive, hidden, hide_tab, del_flag, rule_flag,
       status, perms_type, create_time)
VALUES (REPLACE(UUID(),'-',''), '0', '客户管理', '/demo/customer', 'demo/customer/index',
        '1', '1', 10.00, 0, 1, 0, 0, 0, 0, 0, '1', '1', NOW());
```

### 4.5 需要"免登录"的接口

两种方式，二选一：

- 方法/类上加 `@IgnoreAuth`（`org.jeecg.config.shiro.IgnoreAuth`），启动时由 `IgnoreAuthPostProcessor` 收集。
- 配置白名单：`application-dev.yml:293` `jeecg.shiro.excludeUrls: /demo/customer/public/**,...`（逗号分隔，重启生效）。

### 4.6 验收清单

- [ ] `doc.html` 里能看到 `/demo/customer/*` 并调通
- [ ] 前端菜单出现、点开有数据、能增删改
- [ ] 控制台能看到 SQL 日志里的 `select ... from demo_customer`
- [ ] 导出 Excel 能下载

---

## 5. Day 5：调试四件套（这是你问的重点之一）

### 5.1 后端断点（IDEA）

| 手法 | 怎么用 | 什么时候用 |
| --- | --- | --- |
| 行断点 | 点行号左侧 | 常规 |
| 条件断点 | 右键断点 → Condition 写 `"admin".equals(e.getName())` | 循环/高频接口，避免停几万次 |
| 异常断点 | Run → View Breakpoints → 勾 `Any Exception`（加包过滤 `org.jeecg`） | 不知道哪抛的 |
| 求值 | 断点停下后 `Alt+F8`（Evaluate Expression） | 临时跑一段代码/看 SQL 拼装结果 |
| 调用栈 | Debug 面板 Frames | 搞不清谁调了 Service |
| 观察点 | 字段上 Field Watchpoint | 某字段被谁改了 |
| 热替换 | 改方法体后 `Ctrl+F9` / Build → Reload Changed Classes | 改逻辑不想重启 |

**远程/进程附加调试**（用 `java -jar` 跑的时候）：

```bat
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar jeecg-system-start-3.9.5.jar
```

IDEA：Run → Edit Configurations → Remote JVM Debug，host `localhost` port `5005`。

### 5.2 前端调试

- **Vite 代理**：`.env.development:9` 把 `/jeecgboot` 转到 `8080/jeecg-boot`。Network 面板看到的是 `localhost:3100/jeecgboot/...`，这是正常的，**不是跨域问题**。
- **源码断点**：DevTools → Sources → `src/views/...`（vite 有 sourcemap，直接下断点）。
- **请求断言**：Network 里看 Request Headers 有没有 `X-Access-Token`；Response 看 `code/success/message`。
- **Vue Devtools**：看 Pinia（`app-user` / `app-permission`）里的路由与权限，比猜快得多。
- **错误被吃掉**：`defHttp` 默认 `errorMessageMode: 'message'`，非 200 会弹 toast 但不抛到控制台；排查时在 `src/utils/http/axios/index.ts` 的响应拦截器里临时加 `console.log(response)`。

### 5.3 SQL 与数据库

| 手段 | 地址/开关 |
| --- | --- |
| 控制台 SQL | `application-dev.yml:175` 打开 `log-impl` |
| Druid 监控（慢 SQL、连接池） | `http://localhost:8080/jeecg-boot/druid`，`admin/123456` |
| 直连查库 | `E:\Java\tools\mysql-8.0.29-winx64\bin\mysql.exe -uroot -proot jeecg-boot` |
| 数据权限过滤后 SQL | 打开 SQL 日志，看 where 里多出来的 `create_by in (...)`，那是 `@PermissionData` 加的 |

> 小技巧：`@PermissionData` 会让"明明有数据却查不到"发生，排查时先把这个注解注释掉对比一次。

### 5.4 Redis

token、验证码、字典缓存、在线用户都在 Redis：

```bat
E:\Java\tools\redis\redis-cli.exe -p 6379
> keys *token*            :: 看登录态
> get <key>
> keys *sys:cache:*      :: 字典/缓存
```

"登录后立刻掉线/401" 十有八九是 Redis 没起或 key 被清。

---

## 6. Day 6：排查手册（症状 → 定位 → 处理）

| 症状 | 最可能原因 | 怎么定位 | 处理 |
| --- | --- | --- | --- |
| 登录返回 500 / 一直转圈 | 后端没起、DB/Redis 连不上 | 看后端控制台第一条异常；`netstat -ano \| findstr :8080` | 起后端；查 `application-dev.yml:147` 账号密码 |
| 登录成功但页面一直跳登录 | Redis 没起 / token 存不进 | Redis `keys *` 空 | 起 Redis，重新登 |
| 任意接口 **401** | 没带 token / token 过期 / 被 Shiro 拦 | DevTools 看 `X-Access-Token`；后端日志 `JwtFilter` | 重新登录；新接口需要免登录就加 `@IgnoreAuth` 或 `excludeUrls` |
| 接口 **403**/`没有权限` | 角色没授权该菜单/按钮 perms | `sys_permission` + 角色授权页 | 角色管理里勾上，退出重登 |
| 菜单点了**白屏**、控制台 `Cannot read ... undefined` | `component` 路径与文件不匹配 | 看后端返回的菜单 `component` 值；对照 `src/views` 实际路径 | 改成 `demo/customer/index` 这种形式；文件名不要 `.vue`/`.tsx` 重名 |
| 菜单**根本不显示** | 未授权 / `del_flag=1` / 未重登 | `sys_permission` 查该行；角色授权表 | 授权后退出重登 |
| 接口 **404** | 路径拼错 / context-path 漏了 `/jeecg-boot` / 模块没被扫描 | `doc.html` 搜接口；`@RequestMapping` 前缀 | 补齐前缀；确认包在 `org.jeecg.modules.**` 下 |
| 接口 **500**，日志 `Invalid bound statement` | Mapper 方法没有对应 XML/语句 | 看 `mapper-locations: classpath*:org/jeecg/**/xml/*Mapper.xml` | XML 放对目录、`namespace` 写全限定名、方法名一致 |
| 500：`Unknown column 'xxx'` | 实体字段与表不一致 / 驼峰映射 | SQL 日志里看真实列名 | 改字段名或加 `@TableField` |
| 新增报 `Field 'id' doesn't have a default value` | 主键没用 `ASSIGN_ID` | 看实体 `@TableId` | 加 `@TableId(type = IdType.ASSIGN_ID)` |
| 分页 `total` 一直是 0 | 没配分页插件 / 返回类型不是 `IPage` | 看返回 JSON 结构 | 用 `service.page(...)`，别自己 `list()` 再拼 |
| 时间差 8 小时 / 中文乱码 | 时区、字符集 | `serverTimezone=Asia/Shanghai`、`utf8mb4` | 已有配置，别乱改 URL |
| 启动报 `Flyway validate failed` | 手工改过库、校验和不符 | 报错里有版本号 | 开发库可 `flyway repair` 或临时 `enabled: false`（仅本地） |
| `Access denied for user 'root'@'localhost'` | 密码/主机不匹配 | `mysql -uroot -proot` 手连 | 改 `application-dev.yml:147-149` |
| 上传 413 | `max-file-size: 10MB` | `application-dev.yml:28-29` | 调大；OSS 类型在 `jeecg.uploadType` |
| Lombok 报"找不到 getter" | IDEA 没开注解处理 | Settings → Build → Compiler → Annotation Processors | 勾 Enable + 装 Lombok 插件 |
| 抄老教程编译不过（`javax.servlet`） | 本项目是 **jakarta** | 看 import | 全换 `jakarta.*` |
| 端口占用 | 8080 被占 | `netstat -ano \| findstr :8080` | 杀进程或改 `server.port` |
| Online 表单/代码生成菜单 404 | **后端模块不在本仓库**（见 0.3） | 搜 `RequestMapping("/online`） | 放弃 Online，手写；或用第 7 节的 GUI 生成器 |

---

## 7. 代码生成的两条现实路径

1. **Web 端「Online 表单开发 → 代码生成」按钮：本仓库不可用**（后端 `/online/cgform/**` 缺失）。别在这上面耗时间。
2. **仓库自带的 GUI 生成器可用**：
   - 单表：`org.jeecg.codegenerate.JeecgOneGUI`（Swing 窗口，读库表 → 生成后端 + 前端代码）
   - 一对多：`org.jeecg.codegenerate.JeecgOneToMainUtil`（改 `main` 里的表名/实体名/包名后直接跑）
   
   生成物是**模板代码**，仍需人工改；但它能帮你把"五件套 + vue 页面"的骨架一次拉出来，比手抄快。

---

## 8. 七日计划（每天 2-3 小时，都有可验收产出）

| 天 | 干什么 | 验收 |
| --- | --- | --- |
| D1 | 第 0-2 节：认清仓库、启动、打开 SQL 日志、登录抓一次 `/sys/login` | 能指着控制台说"这条 SQL 是我点的哪个按钮" |
| D2 | 第 3 节：精读 demo 单表前后端 8 个文件，画出链路图 | 白纸画出请求链路 + 说出 4 个框架魔法 |
| D3 | 第 4.1-4.2：建表 + 后端五件套，doc.html 调通 5 个接口 | 新增/查询/删除在 doc.html 里成功 |
| D4 | 第 4.3-4.4：前端三件套 + 菜单 + 授权 | 页面上能增删改查、导出 Excel |
| D5 | 第 5 节：给 list/edit 打断点、条件断点、开 Druid 看慢 SQL | 停在自己的断点上并看到 `queryWrapper` 拼出的 SQL |
| D6 | 第 6 节：**故意制造** 3 个故障（去掉 `@IgnoreAuth` 造 401、改错 component 造白屏、删 XML 造 500），按表定位 | 每个故障 3 分钟内定位到根因 |
| D7 | 加一个业务规则：列表只查自己创建的（`@PermissionData`）、加一个自定义查询接口、写一条 Flyway 迁移 | 规则生效且能在 SQL 日志里看到差异 |

> 学习节奏建议：**D1-D4 必须跑通**，这是"能干活"的门槛；D5-D6 是"能独立排障"；第 7 天开始看 `jeecg-system-biz` 里的真实业务（`SysUserServiceImpl` 是好教材，用户/角色/部门关系复杂但规范）。

---

## 9. 源码阅读顺序（按这个顺序，别乱翻）

1. `org/jeecg/JeecgSystemApplication.java` —— 启动类，看 `@SpringBootApplication(scanBasePackages)`
2. `application-dev.yml` —— 配置全貌（数据源/Redis/MyBatis/Shiro/上传/AI）
3. `config/shiro/ShiroConfig.java` + `filters/JwtFilter.java` —— 认证怎么进来的（`ShiroConfig:99` 是白名单，`:109` 是登录接口）
4. `common/api/vo/Result.java` + `common/system/base/controller/JeecgController.java` —— 返回包装、基类能力
5. `config/mybatis/MybatisPlusSaasConfig.java` + `MybatisInterceptor.java` —— 分页插件、多租户、公共字段填充
6. `modules/demo/test/**` —— 标准答案（第 3 节）
7. `modules/system/entity/SysUser.java` + `service/impl/SysUserServiceImpl.java` —— 真实业务复杂度
8. `aspect/annotation/AutoLog.java` + 对应切面 —— AOP 怎么用

**可以暂时跳过的**：`jeecg-server-cloud/**`（微服务）、`airag`、`jmreport/jimubi`（报表，jar 引入）、`super/online` 相关前端。

---

## 10. 速查卡

```bat
:: 起停
E:\Java\scripts\start-all.bat
E:\Java\scripts\stop-services.bat

:: 后端编译（单模块 + 依赖）
cd /d E:\Java\JeecgBoot\jeecg-boot
mvn clean package -pl jeecg-boot-base-core -am
mvn clean package -DskipTests
cd jeecg-module-system\jeecg-system-start && mvn spring-boot:run

:: 前端
cd /d E:\Java\JeecgBoot\jeecgboot-vue3
pnpm dev
pnpm build

:: 排查
netstat -ano | findstr :8080
E:\Java\tools\mysql-8.0.29-winx64\bin\mysql.exe -uroot -proot jeecg-boot
E:\Java\tools\redis\redis-cli.exe -p 6379
```

**关键地址**

| 用途 | 地址 |
| --- | --- |
| 前端 | http://localhost:3100 （admin/123456） |
| 后端根 | http://localhost:8080/jeecg-boot |
| 接口文档 | http://localhost:8080/jeecg-boot/doc.html |
| Druid 监控 | http://localhost:8080/jeecg-boot/druid （admin/123456） |
| 日志 | `<启动目录>\..\logs\jeecgboot-yyyy-MM-dd.0.log` |

**配置坐标（`jeecg-system-start/src/main/resources/application-dev.yml`）**

| 内容 | 行号 |
| --- | --- |
| 端口 / context-path | 1-6 |
| 文件上传大小 | 28-29 |
| 数据源 | 106-150 |
| Redis | 155-161 |
| MyBatis-Plus（含 SQL 日志开关） | 162-177 |
| 上传类型 / 路径 | 282-290 |
| **Shiro 免登录白名单** | 293 |

---

## 附：给 PHP 开发者的三条心态建议

1. **别找"框架源码全貌"**，JeecgBoot 是用 Spring Boot 拼装 + 大量 jar（报表/AI/工作流），你只需要吃透 `base-core` 的 6 个类和 `demo` 模块的 5 个文件。
2. **先会用注解，再懂原理**。`@AutoLog`、`@PermissionData`、`@RequiresPermissions` 先用起来，AOP 原理等 D7 之后再看。
3. **慢就是快**：把第 3 节的链路图画出来贴显示器上，之后每次改代码都问"我在链路的哪一层"。这一条比看十篇博客有用。
