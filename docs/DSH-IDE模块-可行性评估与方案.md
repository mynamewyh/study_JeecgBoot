# DSH 内置 IDE 模块 · 可行性评估与分阶段实现方案

> 评估日期：2026-11-01　依据：本机 `E:\dsh\resources\app.asar`（DSH 0.2.1-rc.5 实装）逐包解析，非文档臆测。
> 证据素材已提取到 `C:\Users\Administrator\AppData\Local\Temp\dsh-src\`（29 个关键包 package.json + 5 个源码入口），asar 解析脚本在 `%TEMP%\dsh-extract*.js`。

---

## 0. 结论先行

| 问题 | 结论 |
| --- | --- |
| 可行性 | **高**。DSH 已具备 90% 地基：文件系统缝（含 chokidar 监听）、子进程/终端、workspace API、插件管理器、HMR、工具注册管线。缺的只是"语言智能"（LSP/索引）和"IDE 面板"两块增量。 |
| 正确形态 | **不是改 core，而是新增一组 dsh-* 插件包**（IDE bundle），走插件管理器预装进默认 profile —— 这就是 DSH 自己的"内置"通道。 |
| 三个功能的难度 | 启动项目 ★（复用现成 PTY/子进程，0.5–1 周）→ 跳转调用 ★★（tree-sitter 索引起步，1–1.5 周）→ 代码补全 ★★★（词法 + LLM 起步，1–1.5 周；LSP 升级另计 2–3 周） |

## 1. DSH 实装架构（本机解析出来的，不是猜的）

- **整体**：Electron 桌面应用；宿主是 Node 进程 `@deepseek-ai/dsh-desktop-host`（ESM），插件框架是 **`@deepseek-ai/cordis` ~4.0.5**（Koishi 血统的服务注入式插件系统）。Web GUI 由 `dsh-host-webserver` 托管，客户端插件独立成包。
- **规模**：`dsh/node_modules/@deepseek-ai/` 下 **297 个 `dsh-*` 包**，全部是 npm 包形态，靠 `peerDependencies` 声明服务依赖（"缝 seam + 实现 impl"分层）。

**已有能力（IDE 模块直接复用的地基）**：

| 能力 | 包 | 与 IDE 模块的关系 |
| --- | --- | --- |
| 文件系统缝 | `dsh-fs`（抽象 `ctx.fs`）/ `dsh-fs-local`（依赖 **chokidar** 文件监听） | 代码索引的增量监听直接用 |
| 命令执行 | `dsh-shell` / `dsh-subprocess` + `dsh-bash-local` / `dsh-pwsh-local` | 「启动项目」的执行层 |
| 交互终端 | `dsh-api-terminal-controller`（@xterm/headless、PTY、屏幕恢复） | 运行输出面板 |
| 持久 PTY 工具 | `dsh-tool-terminal`（6 个模型可见工具 + jobs 集成） | 「启动项目」的现成范本 |
| 工具注册管线 | `dsh-tools`（Tool registry and execution pipeline） | 新工具的注册点 |
| 外挂工具先例 | `dsh-mcp-client`（把 MCP 服务器工具注册到 `ctx.tools`） | 证明"插件自带工具"通道畅通 |
| 工作区 API | `dsh-api-workspace-files`（受限读、目录列举、实时元数据，Remote 命名空间） | 编辑器面板的文件服务 |
| 插件管理 | `dsh-plugin-manager` / `dsh-plugin-market` / `cordis-plugin-loader` | 「内置」的分发通道 |
| 热更新 | `dsh-hmr`（宿主）/ `dsh-client-hmr`（Web 端 bundle 重载） | 开发环 |
| 进程外 SDK | `dsh-sdk-jsonrpc-server`（stdio JSON-RPC）+ `dsh-typert-protocol`（类型化 RPC） | 备选集成形态 |
| 权限/沙箱 | `dsh-sandbox` / `dsh-sandbox-policy` / `dsh-permission-presets` | 新工具的审批策略钩子 |

**代码级证据（关键两行，决定了集成接口怎么写）**：

```js
// dsh-mcp-client 源码：外部工具注册到模型可见工具表的真实写法
for (const [publicName, definition] of definitions)
  disposers.set(publicName, ctx.tools.register(definition));   // definition.call = (args, execution) => ...

// dsh-bash-local 源码：Cordis 4 服务注入 + 配置 schema 的真实写法
var LocalBashExecutor = class LocalBashExecutor extends ShellExecutor {
  static inject = ["subprocess"];                               // 声明依赖的服务
  static Config = z.object({ timeoutMs: z.number().default(12e4).volatile(), ... });  // schemastery schema
```

**缺口（要新建的）**：①语言智能（无任何 LSP/符号索引/补全源）；②编辑器面板（现有只有文件树 `dsh-client-ui-sidebar-files` 和终端 `dsh-client-ui-sidebar-terminal`，没有代码编辑视图）；③「项目运行配置」概念（现在只能裸命令）。

## 2. 三个功能的实现路径（各给候选 + 推荐）

### 2.1 启动项目（Run）★

| 方案 | 内容 | 代价 | 结论 |
| --- | --- | --- | --- |
| A（推荐） | 新包 `dsh-ide-run`：项目探测器（package.json scripts / pom.xml / gradle / Makefile）→ 运行配置（存 profile settings）→ 复用 `ctx.subprocess`/`dsh-tool-terminal` 执行 → 输出走 jobs/终端面板 | 零新增重依赖 | ✅ MVP 首选 |
| B | 不加代码，只靠现有 pwsh 工具 + prompt 约定 | 0 | ❌ 体验差，不算"内置" |

**关键依赖**：`dsh-tools`、`dsh-jobs`、`dsh-subprocess`、`schemastery`；客户端复用 `dsh-client-ui-sidebar-terminal` 的 xterm 组件。

### 2.2 跳转调用（Go to definition / references）★★

| 方案 | 内容 | 代价 | 结论 |
| --- | --- | --- | --- |
| A | **LSP 客户端**：`vscode-jsonrpc` + `vscode-languageclient/node`（纯 JS，Node 可用），spawn 语言服务器（TS: typescript-language-server；Java: jdtls；Python: pyright） | 各语言 server 体积/冷启动（jdtls 100MB+） | 二期升级 |
| B（推荐起步） | **tree-sitter 符号索引**：`tree-sitter` Node 绑定 + 语法包（typescript/java/python…）+ chokidar 增量建索引，自实现 definition/references | 语义精度不如 LSP，但零外部进程、毫秒级 | ✅ 一期 |
| C | universal-ctags 子进程 + tags 文件 | 最糙 | ❌ |

**关键依赖（B）**：`tree-sitter` + `tree-sitter-{javascript,typescript,java,python}`、`chokidar`（已在 dsh-fs-local 里）。

### 2.3 代码补全（Completion）★★★

| 方案 | 内容 | 代价 | 结论 |
| --- | --- | --- | --- |
| A | LSP completion（依赖 2.2-A） | 同 LSP | 三期 |
| B | **LLM 补全**：复用 `dsh-llm` 缝 + FIM（fill-in-middle）提示，防抖触发。DSH 已带 llm seam 与 API Key 管理 | 每次补全有 token 成本 + 延迟 | ✅ 与 C 组合成二期 |
| C | **词法补全**：tree-sitter 取当前文件符号 + 关键字表 | 零成本、基础 | ✅ 一期内嵌 |

**推荐路线：C（MVP 内嵌）→ B（LLM）→ A（LSP）渐进。**

## 3. 模块划分（新增包清单，按现有命名惯例 `packages/ide/*`）

**宿主半（Node）**：

| 包 | 职责 |
| --- | --- |
| `dsh-ide-core` | 缝定义：语言索引（SymbolIndex）、补全源（CompletionSource）、运行配置（RunConfig）三类服务接口 + policy 事件词汇 |
| `dsh-ide-index-local` | tree-sitter 索引实现：chokidar 增量、跨文件符号表、definition/references 查询 |
| `dsh-ide-lsp-local` | LSP 客户端（二期）：server 发现/下载/生命周期、JSON-RPC 转发 |
| `dsh-ide-run` | 项目探测 + 运行配置 + 子进程执行 + jobs 集成 |
| `dsh-ide-tools` | 把 `ide_complete` / `ide_definition` / `ide_references` / `ide_run` 注册到 `ctx.tools`（模型侧工具，schema 用 schemastery） |

**客户端半（Web，经 Client loader 挂载）**：

| 包 | 职责 |
| --- | --- |
| `dsh-client-ui-ide` | 编辑器面板（CodeMirror 6 或 Monaco）+ 跳转/悬停交互，挂右侧栏或工作区视图 |
| `dsh-client-ui-ide-run` | 运行按钮 + 输出/终端面板 |

**聚合**：`dsh-ide-bundle`（依赖以上 + `dsh-experimental-terminal-bundle`），作为市场条目/默认 profile 项。

## 4. 集成接口（对接到 DSH 的具体点位）

| 集成点 | 依据 | 用法 |
| --- | --- | --- |
| 模型侧工具 | `ctx.tools.register(definition)`（mcp-client 实测写法） | `dsh-ide-tools` 注册 4 个 IDE 工具，schema 走 schemastery，`call(args, execution)` 里接执行管线与沙箱 |
| 服务注入/配置 | `static inject = ["subprocess"]` + `Config = z.object({...})`（bash-local 实测写法） | 新插件声明依赖 `fs`、`subprocess`、`jobs` 等缝 |
| 客户端面板 | Client loader 挂载浏览器端插件（sidebar-terminal 源码注释「Mount the browser-only plugin through the Client loader」） | `dsh-client-ui-ide-*` 照此注册面板 |
| 开发热更新 | `dsh-hmr`（宿主）+ `dsh-client-hmr`（Web；`pnpm run dev:web` 重建 bundle 后无需刷新） | 开发期改客户端代码即时生效 |
| 内置分发 | `dsh-plugin-manager`（profile 级安装）+ `dsh-plugin-market`（市场索引条目） | bundle 发布后写进市场索引或打进默认 profile |
| 权限审批 | `dsh-sandbox-policy` 的 pre-execute 钩子 + `dsh-permission-presets` | `ide_run` 等敏感工具挂执行策略，避免绕过审批 |

## 5. 分阶段计划

| 阶段 | 内容 | 新增包 | 验收标准 | 预计 |
| --- | --- | --- | --- | --- |
| P0 | 本评估（已完成） | — | 本文件 | — |
| P1 | 启动项目 | `dsh-ide-run` + `dsh-ide-tools`（run）+ 客户端最小运行按钮 | 对 JeecgBoot 仓库：探测出 `pnpm dev` 与 `mvn spring-boot:run`，一键启动、输出回流、可停止 | 0.5–1 周 |
| P2 | 跳转调用 | `dsh-ide-core` + `dsh-ide-index-local` + `dsh-ide-tools`（definition/references）+ 面板跳转 | 在 `YingZhiRenScoreController` 里点 `service.page` 能跳到 `ServiceImpl`；`YingZhiRenScore` 字段→实体定义 | 1–1.5 周 |
| P3 | 代码补全 | 词法补全（内嵌 P2 面板）+ LLM FIM 补全 | 面板里敲代码出候选；模型可用 `ide_complete` | 1–1.5 周 |
| P4 | LSP 升级（可选） | `dsh-ide-lsp-local` | TS/Java 真·语义补全 + 引用 | 2–3 周 |
| P5 | 内置发布 | `dsh-ide-bundle` | 市场条目 + 默认 profile 启用；`dsh-plugin-inventory` 可见 | 0.5 周 |

**总工期（P1–P3 核心）**：约 3–4 周；加 P4 约 6–7 周。

## 6. 风险与易错点（多数在本机已验证过）

1. **绝对不要改 `E:\dsh\resources\app.asar`**——它是打包产物（本机实测：asar 头 + pickle 树 + 内容偏移），改动会被更新覆盖且破坏签名。插件一律走 profile 安装目录 + HMR，这是唯一正路。
2. **Cordis 版本要对齐**：新插件 peerDependencies 写 `@deepseek-ai/cordis ~4.0.5`，`static inject` 语法必须与 4.x 一致（老文档多写 3.x 的 `ctx.provide()`）。
3. **新工具必须走沙箱策略**：`ide_run` 等同 subprocess 能力，不接 `dsh-sandbox-policy` 会被策略拦截或触发审批风暴。
4. **Windows 编码/路径坑**：本机踩过一轮（GBK 控制台、`-D` 参数拆分、批处理吞 stdin），工具输出规范化要沿用 bash-local 的 `ENV_OVERRIDES`（NO_COLOR/TERM=dumb）思路。
5. **LSP 冷启动与体积**：jdtls 100MB+ 且首启 10s+，P4 才引入，P1–P3 别碰。
6. **tree-sitter 语法包**：native 绑定走 `tree-sitter` npm 的 prebuilt，Node 24 兼容性先验证（本机 Node v24.16.0）。

## 7. 需要你拍板的决策点

| # | 决策 | 候选 | 建议 |
| --- | --- | --- | --- |
| 1 | 补全技术路线 | A. 纯 LSP；B. 词法+LLM 起步、LSP 后置；C. 纯 LLM | ✅ B（本文件第 2.3 节路线） |
| 2 | 编辑器面板 | A. Monaco（VS Code 同款，重）；B. CodeMirror 6（轻，几百 KB） | ✅ B 起步 |
| 3 | 一期语言范围 | TS/JS、Java、Python 三选几 | ✅ TS + Java（贴合你 JeecgBoot 学习场景） |
| 4 | 「内置」形态 | A. 打进默认 profile 全量预装；B. 市场条目、手动启用 | ✅ 先 B 后 A（B 更安全） |
| 5 | 下一步动作 | A. 直接开工 P1（写 dsh-ide-run 骨架）；B. 先出 P1 的详细技术设计再动手 | ✅ 由你定 |

---

### 附：证据素材位置（供后续轮次复用）

- asar 解析脚本：`%TEMP%\dsh-extract.js`（列树）、`dsh-extract2/3.js`（提取 package.json）、`dsh-extract4.js`（提取源码）
- 已提取素材：`%TEMP%\dsh-src\`（29 个 package.json、bash-local/mcp-client/fs-local/terminal-ctrl 源码、_all-packages.txt 全量 297 包清单）
- 关键源码摘录（本文件第 1 节已引用）：`src_mcp-client.js`（ctx.tools.register）、`src_bash-local.js`（static inject + Config）

---

## 8. 实施进展（2026-11-01 更新）

### 8.1 重大事实修正：官方仓库已获取，且上游已有 LSP

- `github.com/deepseek-ai/deepseek-harness` 是**公开仓库**，已浅克隆到 `E:\Java\dsh-ide`（master，版本 0.2.2-alpha.2，20233 个文件）。
- **上游已实现「跳转调用」**：`packages/lsp/` 下已有 `lsp`（ctx.lsp 服务缝，definition/references/implementation/hover 四个归一化操作）、`lsp-stdio`（stdio 语言服务器驱动）、`tool-lsp`（模型可见 `lsp` 工具）。装机路径 = 把这三个包装进 profile + 配置扩展名→语言服务器命令映射，**不再需要自研 LSP 客户端**（本文件第 2.2 节方案 A 改为"接上游包"，原 P2/P4 大幅缩水）。
- 上游仍**没有**：代码补全（无 completion 操作）、项目运行配置概念、编辑器面板 —— 这些才是真正的增量。

### 8.2 P1「启动项目」已落地（commit `d4d31f39`）

`E:\Java\dsh-ide\packages\ide\` 下新增三包（严格照 tool-lsp / lsp 的包结构与代码惯例）：

| 包 | 内容 |
| --- | --- |
| `ide-run`（`@deepseek-ai/dsh-ide-run`） | `ctx.ideRun` 服务缝（`Service` 类 + 单槽 executor 注册表 + 结构化 `IdeRunError`）+ **零依赖纯函数探测器 `detect.ts`** |
| `ide-run-local`（`@deepseek-ai/dsh-ide-run-local`） | 本地实现：`ctx.fs` 适配成探测 IO、`ctx.subprocess` 执行（256KB 尾部截断采集、幂等 terminate） |
| `ide-tool-run`（`@deepseek-ai/dsh-ide-tool-run`） | 模型工具 `ide_run`（`defineTool`，detect/start/status/stop/logs 五操作 + 输出 schema + 纯渲染） |

### 8.3 已验证（本机实测）

- 探测器 `node` 直跑真实目录：`E:\Java\JeecgBoot` → 检出 2 项目（`jeecg-boot` Maven，`spring-boot:run` 置顶；`jeecgboot-vue3` pnpm，`dev` 置顶）；`E:\Java\dsh-ide` → 检出自身 monorepo（`dev:web`/`dev:desktop` 等）。
- 途中修掉一个真实缺陷：Maven 多模块仓库会被拆成 5 个"项目"，改为**检出即边界、不钻子目录**后正确归并。
- 6 个 src 文件 `node --check` 全过；3 个 package.json 合法。

### 8.4 P1 剩余（下轮）

1. ~~`pnpm install`（仓库级依赖）+ `tsc -b` typecheck~~ **已完成**（commit `6d6eec62`）：pnpm 11.28.5 过滤安装 854 包；TS 6.0.3 `tsc -b` 三包全绿。校准到的真实 API 事实：
   - `ctx.fs` / `ctx.subprocess` 类型增强需 `import type {} from '@deepseek-ai/dsh-fs'` / `'dsh-subprocess'`（tool-lsp 的 `import type {}` 模式即为此）；
   - `SubprocessOutcome` 的信号字段叫 **`signal`**（不是 exitSignal）；`SubprocessCollectedOutputs.stdout` 可选（collect 模式才有），读取是 `collected.stdout.readFrom(0).text`；
   - `defineTool` 参数 spec 里 **`required` 只接受字面量 `true`**——可选参数直接省略 `required` 字段，不能写 `required: false`；
   - 仓库开启 `exactOptionalPropertyTypes`，可选属性形态要用 `prop?: string | undefined`，输出值里 `null` 不能塞给可选 number 字段（用条件展开省略）。
2. ~~客户端运行按钮骨架：需要先建宿主侧 `dsh-api-ide-run-controller`~~ **宿主 API 桥已完成**（commit `a8002114`）：`packages/ide/api-ide-run-controller`（`IdeRunController extends TypertRemoteService`，5 个 `@Remote` 方法 + 生成式客户端命名空间 + `ctx.ideRunClient` 服务）。**全链路验证通过**：tsc 双面（host+client）全绿；仓库 `generate-typert` 跑通并把该包纳入第 37 个贡献者，产出了 `lib/typert.host.js` + `lib/typert.remote-client.d.ts`（`ideRun` 命名空间 5 方法签名与 TYPERT_REMOTE 描述符）。为此按仓库治理补齐：`SERVICE_WALK_EXEMPTIONS` 三条目、根 `tsconfig.host.json`/`tsconfig.client.json` 引用、`tsconfig.base.json` paths 映射（含 `/types`、`/detect` 子路径）。过程中踩到的 5 条生成器规则（Remote 边界类型必须出自公共非根子路径、agent 参数必须叫 agent、files 数组必须含 typert.host.d.ts、双面包必须带 `dsh.client` 块、边界类型需直接 import 自声明文件而非再导出）已全部内化。
3. ~~客户端 UI 包 `ui-ide-run`~~ **已完成**（commit `0d8601ed`）：`packages/ide/ui-ide-run`（`@deepseek-ai/dsh-client-ui-ide-run`）——composer-dock 插槽上的「运行项目」按钮，经 `ctx.ideRunClient` 调 `detect` 并显示项目数；locale 命名空间 + tsdown `clientBundle` 配置齐备。**typecheck 全绿**（对真实 slot 契约/ui-conversation 合约编译）。顺带补齐仓库治理：SERVICE_PAGE 映射（ideRun/ideRunController → workspace.md）、TYPE_LINK_EXEMPTIONS 12 条、全量 JSDoc（@param/@returns）——`gen-cordis-catalog` 重新跑通并把 catalog 写入了 `docs/subsystems/workspace.md` 与运行时 api-catalog。P1 五包全部就位。
4. ~~真实集成~~ **装载链路已验证，剩桌面重启验收**。过程中用独立 `ide-test` profile（`dsh.cmd --profile ide-test`，desktop 之外可引导）+ `--dump-config` 打通并修掉了 4 个加载器闸门：① tarball 内 package.json 的 **UTF-8 BOM** 会被 loader 拒（全部重打无 BOM）；② `dsh.bundle.patch` + `cordis.patch.yml`（`- insert: {id/name 加引号}`，@ 开头必须引号）——7 个包已在仓库源码补齐（commit `90fe9729`）；③ 版本兼容闸：`dsh plugin allow-version <pkg@ver> --dsh-version 0.2.1-rc.5 --accept-risk` 官方豁免（两个 profile 各 4 条，落盘 `compatibility.json`）；④ 纯库不能当 bundle（`dsh-ide-completion` 移出 bundles 只做依赖）。**合成树实测 102 条**，6 个插件层全部编入（`--dump-config`）。桌面 profile 同步修好（bundles 6 项 + 豁免 + 无 BOM 重装）。headless 任务跑不通（自定义 profile 无任务 runner），故**唯一剩余验证 = 用户重启桌面 App**。
5. P2 跳转调用：上游 `packages/lsp` 三件（lsp/lsp-stdio/tool-lsp）已 typecheck + 打包就绪。**语言服务器已供给**：`E:\Java\dsh-ide\language-servers\` 下 tsls（typescript-language-server 6.0.2 + typescript，已跑通 `--version`）、jdtls 1.57.0 + 专用 Temurin **JDK 21**（实测启动冒烟通过；最新快照 1.64 需 Java 25 已弃用）。**已全部装进两个 profile**（bundles 各 3 项 + lsp-stdio 的 servers patch + 3 条 allow-version 豁免，commit `129c093d`），`ide-test --dump-config` 实测 106 条、lsp-stdio 条目带配置生效。下次重启即可用 `lsp` 工具做 definition/references/hover。
6. P3 代码补全（开工）：`packages/ide/ide-completion`（`@deepseek-ai/dsh-ide-completion`）——零依赖纯词法补全器 `completeLexical(source, cursor, language, limit)`：本文件标识符前缀匹配 + 按游标距离打分 + camelCase 分段匹配 + Java/TS 关键字表。**已实测**（真实代码：`yingZhiRenS`→YingZhiRenScore/Controller、`FormSc`→FormSchema、`BasicC`→BasicColumn、`ctx.ideRun.st`→start/status/stop）。typecheck 全绿，commit `965d259b`。下一片：`ide-tool-complete` 模型工具 + LLM FIM 层。
8. P3 LLM FIM 层（完成）：`packages/ide/ide-tool-complete-llm`（`@deepseek-ai/dsh-ide-tool-complete-llm`）——`ide_complete_llm` 工具：游标上下文窗口提取（前后行 + `⟦CURSOR⟧` 标记）+ FIM 系统提示 + `ctx.llm.prepareCall/stream` 显式路由（配置 provider/model 必填，maxOutputTokens 512 / 120s 超时，走官方 deadline + BlockAssembler 模式）。typecheck 一次通过，全量回归 9 项目 + 两生成器全绿，commit `6c6af632`。已装进两个 profile（bundles + cordis.patch.yml 配 deepseek-official/deepseek-v4-pro），合成树显示条目含配置生效。**P3 全部完成：词法核心 + `ide_complete` + `ide_complete_llm` 三层**。
9. **真机验收（round 10–15，最终结果）**：P1 五件套全部实测通过——`ide_run detect`（JeecgBoot 双项目）、`ide_run start/status/logs/stop`（`mvn compile` 真实输出 29 模块、编译 222 源文件、stop code=0）；P3 三层通过——`ide_complete`（yingZhiRenS → YingZhiRenScore）与 `ide_complete_llm`（FIM 返回 `.page(page, queryWrapper);` 与文件一致）；P2 的 `lsp` 工具上线，**TS 跳转实测通过**（`Api` → `:4:6` enum 定义），**Java 跳转需把会话 workspace 切到 `E:\Java\JeecgBoot\jeecg-boot`**（jdtls 以会话根为工程根）。期间踩平：clientBundle 闭包工厂（`43deff41`）、Windows .cmd spawn（`5f1677d7`）、LLM 推理档位（`ad2b5210`）、客户端惰性解析 remote 命名空间（`eb4d09f5`）、lsp 串包 + workspace 依赖（确定性重打包 + deps 转 peers）、tsls 需 TS 5.9（TS 7 无 tsserver.js）。**关键发现：插件管理器支持热重载**——改 bundles/patch 后跑一次 `pnpm install --force` 即生效，不必重启 App。
