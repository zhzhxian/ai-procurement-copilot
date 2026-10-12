# ai-procurement-copilot

采购领域的 AI 助手，分两个版本做：v1.0 是企业知识库问答（RAG 全链路），v2.0 升级为能查商品、比价、生成询价单的智能采购 Agent。不做第二个项目，就在这一个仓库里往上长。

技术栈：Spring Boot 4.1.1 + Spring AI 2.0.1 + JDK 21，模型走 DeepSeek 的 OpenAI 兼容接口。

## 进度

| 阶段 | 周次 | 目标 | 状态 |
|---|---|---|---|
| W0 | 10/12 - 10/18 | 止损重启：跑通第一个流式对话服务 | 完成 |
| W1 | 10/19 - 10/25 | Spring AI 核心 API：System Prompt / 记忆 / Advisor / 结构化输出 | 代码完成，缺 D6、D7 |
| W2 | 10/26 - 11/01 | Tool Calling + Embedding / pgvector 入门 | 未开始 |
| W3 - W5 | 11/02 - 11/22 | RAG 全链路、检索优化、评测 → **v1.0 开源** | 未开始 |
| W6 - W10 | 11/23 - 12/27 | Agent / MCP / 工作流编排 → **v2.0** | 未开始 |
| W11 - W15 | 12/28 - 01/31 | 工程化（成本 / 延迟 / 可观测）+ 简历 + 面试冲刺 | 未开始 |

git log 和这张表是对得上的，一条条往下看就是全部过程：

```
5c57542 结构化输出的稳定性                                    ← W1 D5
0c043c6 结构化输出：从"说人话"到"给数据"                       ← W1 D4
ca26e4d 结构化输出：从"说人话"到"给数据"                       ← W1 D4
4766a3d 更新多轮对话笔记                                      ← W1 D3 收尾
74d4b00 解决多轮对话DEBUG日志问题，观察多轮对话advisors的原理    ← W1 D3
3a5ff4e 实现多轮对话记忆                                      ← W1 D2
bf538d7 添加系统提示词：让模型有固定的角色、边界和输出风格        ← W1 D1
0d1dd14 添加笔记                                             ← W0 D7
0fcf240 spring ai demo                                      ← W0 D3 - D5
```

### W1 还差的三件事

1. **全局异常处理（D6）**。现在 `/parse` 系列失败会直接 500 加堆栈，要改成返回结构化的错误信息（`PARSE_FAILED` / `MODEL_UNAVAILABLE` 这类）。顺带要想清楚三个问题，答案写进笔记：
   - 信息不全时，应该报错，还是返回字段为 null 的对象？取决于调用方是人还是下游程序。
   - 重试 3 次都失败、用户等了 10 秒，这个体验能接受吗？要不要加超时、降级成"返回原始文本自己看"？
   - 模型给了明显不合理的值（比如笔记本单价 800 元），拦不拦？答案是拦——模型的输出必须当外部不可信输入处理。
2. **成功率对比实验表（D5 Step 2）**。三个解析接口（normal / parse / pro）用同一批困难输入各跑 10 次，记录通过次数。这是 W1 最值钱的产出，面试直接用它。
3. **日志配置修正**。`application.yml` 现在是 `logging.level.org.springframework.ai: DEBUG`，这个打不出 prompt（Spring AI 抽象层不把日志传给底层 SDK）。要看到完整请求必须两个条件同时满足：显式注册 `SimpleLoggerAdvisor`（已经在 `memoryChatClient` 上）**并且**把级别设在 `org.springframework.ai.chat.client.advisor` 包上，还要用非流式接口观察（流式接口在另一个线程执行，抓不到聚合结果）。

## 快速开始

### 环境

| 组件 | 版本 / 选型 |
|---|---|
| JDK | 21 (LTS) |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1（`spring-ai-bom` 管版本） |
| Web 依赖 | `spring-boot-starter-webmvc`（Boot 4 的命名，就是原来的 starter-web） |
| 模型 | DeepSeek `deepseek-chat`，通过 `spring-ai-starter-model-openai` 接入 |
| 向量库 | PostgreSQL 16 + pgvector（Docker，W2 才用，容器可以先备好） |

### 配置与启动

API Key 用环境变量传，不落文件、不进 git：

```bash
export DEEPSEEK_API_KEY=sk-xxx
```

IDEA 里对应 Run → Edit Configurations → Environment variables。

```bash
mvn spring-boot:run
curl -G "http://localhost:8080/chat" --data-urlencode "message=你好"
```

三个已经踩过的配置坑：

- **加了 starter 就必须配 Key 才启动得起来**。Spring AI 2.0 启动时会把对话、向量、图像、语音所有模态的模型 Bean 都建出来，全都需要凭证，没配就报 `At least one credential source must be specified`。本地临时起来可以先填 `sk-placeholder`。
- **`base-url` 必须带 `/v1`**（`https://api.deepseek.com/v1`），底层 SDK 自己往后拼 `/chat/completions`，少了会 404。
- **模型名配置项是 `spring.ai.openai.chat.model`**，1.x 的 `options.*` 写法已废弃。

### 数据库（W2 才用）

Docker Hub 在国内不通，先把镜像加速源配上（Docker Desktop → Settings → Docker Engine，合并进现有 JSON 后 Apply & Restart）：

```json
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

然后起 pgvector：

```bash
docker run -d --name pgvector \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  -v pgvector-data:/var/lib/postgresql/data \
  pgvector/pgvector:pg16
```

本机还缓着 `milvusdb/milvus:v2.6.7` + `minio` + `etcd` 三个镜像，万一 pgvector 拉不动，W2/W3 直接换 Milvus，一个镜像都不用下。

## 接口清单

| 接口 | 参数 | 返回 | 干什么 | 学到的点 |
|---|---|---|---|---|
| `/chat` | message | String | 最朴素的一次问答，无状态 | ChatClient 三段式 |
| `/chat/stream` | message | Flux（SSE） | 流式输出，逐字返回 | 首字延迟、SSE 编码 |
| `/chat/pro` | message | String | 带 System Prompt 的采购助手 | 用提示词定角色、边界 |
| `/chat/memory` | conversationId, message | String | 同一个 conversationId 记得上文 | ChatMemory + Advisor 传参 |
| `/parse/normal` | text | String | 只让模型"说"，不做结构约束 | 对照组 |
| `/parse` | text | PurchaseRequirement | 模型输出直接映射成 Java 对象 | 结构化输出 `entity()` |
| `/parse/pro` | text | PurchaseRequirement | 上面再加 schema 校验 + 自纠正重试 | `validateSchema()` |

`/parse` 这三个是刻意留的递进对照：纯文本 → 结构化 → 结构化 + 校验。出问题时能一眼定位在哪一层。

两处和计划文档不一致的地方，以后翻文档别被绕晕：

- 计划里写的 `/parse/stable`，实际实现叫 `/parse/pro`。
- 计划里的 curl 示例参数是 `sessionId`，代码里 `@RequestParam` 叫 `conversationId`。

### 验证用的 curl

```bash
# 记忆：同一个 conversationId 两轮
curl -G "http://localhost:8080/chat/memory" --data-urlencode "conversationId=test01" --data-urlencode "message=我叫张三，采购部的"
curl -G "http://localhost:8080/chat/memory" --data-urlencode "conversationId=test01" --data-urlencode "message=我叫什么？在哪个部门？"
# 换一个 conversationId 再问，应该答不知道（会话隔离）

# 结构化输出：一条信息完整的口语输入
curl -G "http://localhost:8080/parse" --data-urlencode "text=我们要给研发部买50台笔记本电脑，每台预算不超过8000，下个月底前要到货，得能跑代码和虚拟机，最好有三年质保"

# 困难输入（对比实验用这三个）
curl -G "http://localhost:8080/parse/pro" --data-urlencode "text=想买点服务器"
curl -G "http://localhost:8080/parse/pro" --data-urlencode "text=上次那批电脑不太好用，这次换个牌子，大概二十来台吧"
```

## 代码结构

```
src/main/java/com/zzx/aiprocurementcopilot/
├── AiProcurementCopilotApplication.java
└── chat/
    ├── config/ChatClientConfig.java      两个 ChatClient Bean
    ├── controller/ChatController.java    7 个接口
    └── model/PurchaseRequirement.java    结构化输出的目标类型（record）
```

### 为什么是两个 ChatClient

`memoryChatClient` 上挂了 `MessageChatMemoryAdvisor`。`defaultAdvisors` 注册的 advisor 是**每个请求都会跑**的，而记忆 advisor 要求 context 里有 conversationId，没有就抛 `conversationId cannot be null`——`/parse` 这种不需要记忆的接口会被误伤。

错误的修法：给 `/parse` 也传一个固定 ID。这样所有解析请求共用同一段记忆，不同用户的需求描述会互相串味，这是正确性问题不是洁癖。

实际用的修法：按用途拆 Bean。`ChatClient.Builder` 是 prototype 作用域，可以建多个。

```java
@Bean
ChatClient plainChatClient(ChatClient.Builder builder) { ... }        // 无记忆：解析、后面的工具调用、RAG 问答

@Bean
ChatClient memoryChatClient(ChatClient.Builder builder, ChatMemory chatMemory) { ... }  // 有记忆：多轮对话
```

`ChatMemory` 用的是自动配置给的那个：`MessageWindowChatMemory` + 内存仓库，窗口 20 条。所以**应用一重启记忆就清空**，而且只在这一个进程里有效。这是后面 W9（记忆）要换掉的东西，现在先知道就好。

## 知识点

按遇到的顺序记。每条都能在代码里指到具体位置。

### 1. ChatClient 三段式

```java
chatClient.prompt()          // 起一个请求
    .system("...")           // 角色和规则，可选
    .user(message)           // 用户输入
    .call()                  // 同步调用；.stream() 是流式，前面组装方式完全一样
    .content();              // 取文本；.entity(Class) 取对象
```

### 2. System Prompt 是引导，不是强制

`/chat/pro` 里立了四条规矩（只答采购相关、不超过 3 句、不确定要说不确定、价格说明是参考区间）。实测：写诗这类越界请求大部分会被拒，**但有概率不听**。

记住这个概率——它正是后面需要结构化输出、需要校验重试的原因。模型的输出只能当"概率性正确"来用。

### 3. 记忆：注册和传参是分离的

```java
// 构建时注册一次
.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())

// 每次请求各自传
.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
```

`.advisors(a -> a.param(...))` **不注册任何 advisor**，只往本次请求的 context 里塞一个键值对，advisor 从 context 里读 conversationId 决定用哪段记忆。

这个设计的好处：一个 ChatClient 实例能同时服务所有会话，不用每个会话建一个 client。

验证方法：同一 conversationId 下问"我叫张三，采购部的"，再问"我叫什么"，能答出来；换一个 conversationId 就答不知道。

### 4. Advisor 链是洋葱模型

```
你的代码 → ChatClient → [Advisor 链 前置] → ChatModel → [Advisor 链 后置] → 返回
```

每个 advisor 拿到请求后，自己决定要不要调 `chain.nextCall(req)` 往下传，响应回来时再逐层往外走。用熟的东西类比：

| Spring AI | 用了很多年的东西 |
|---|---|
| `CallAdvisor.adviseCall(req, chain)` | `HandlerInterceptor.preHandle/postHandle` |
| `chain.nextCall(req)` | `filterChain.doFilter(req, resp)` |
| Advisor 链 | AOP `@Around` 通知链 |
| `getOrder()` | `@Order` / `Ordered` |

三个关键结论：

- **顺序由 `getOrder()` 决定，不由 defaultAdvisors 的书写顺序决定。** 值小的在外层，`HIGHEST_PRECEDENCE` 最外，`LOWEST_PRECEDENCE` 最内。
- **不调 `chain.nextCall()`，链就断在这里**，模型根本不会被调用，表现是拿到空响应然后找半天。类比忘了写 `filterChain.doFilter()`。
- **注册和传参分离**（见上一条），这是整个设计里最值得记的一点。

一个实测过的观测方法：看 `SimpleLoggerAdvisor` 打印出来的 prompt 里有没有包含记忆 advisor 插入的历史消息，就能反推它俩谁在链的外层。结论本身不重要，重要的是证明了这条链有顺序、能被观测，不是一张概念图。

调试配置的正确姿势（2026-10-11 实测踩过）：

```yaml
logging:
  level:
    org.springframework.ai.chat.client.advisor: DEBUG   # 注意是这个包，不是 org.springframework.ai
```

只设级别不够，必须显式注册 `SimpleLoggerAdvisor`；并且**用非流式接口观察**，流式接口的日志在另一个线程执行，抓不到。

### 5. 结构化输出：record + entity()

```java
public record PurchaseRequirement(
        String category,        // 品类
        Integer quantity,       // 数量
        BigDecimal unitBudget,  // 单件预算上限
        String deliveryDate,    // 期望交付时间 yyyy-MM-dd
        String usageScenario,   // 使用场景
        List<String> mustHave   // 必须满足的条件
) {}
```

**为什么用 record**：这条链路全程依赖反射——Spring AI 拿到 Class，反射读出字段名和类型，生成 JSON Schema 塞进 prompt，模型返回 JSON 后再反序列化回来。record 的组件名保留在 class 文件里、访问器命名规范、JDK 原生无代码生成环节，比 Lombok 在这条链路上可靠；另外 record 不可变，并发调用天然线程安全，可以直接丢日志和缓存。

两个注意点：

- record 的访问器是 `category()`，不是 `getCategory()`。手写代码会编译不过，Jackson 序列化不受影响。
- record 支持紧凑构造器，可以在对象构造那一刻做校验（数量必须大于 0、预算不能为负）。模型的输出是不可信输入，构造器是最自然的拦截点——这条留给 D6 一起做。

**`entity()` 的失败行为**：模型返回的不是合法 JSON 时，转换直接抛异常，不会返回 null。这就是 `/parse` 在没有校验时的表现。

### 6. validateSchema()：自纠正重试

`/parse/pro` 比 `/parse` 只多一个参数：

```java
.entity(PurchaseRequirement.class, entityParamSpec -> entityParamSpec.validateSchema());
```

我一开始对它的理解有三处不对，反编译 `spring-ai-client-chat-2.0.1.jar` 核对后修正：

| 原以为 | 实际 |
|---|---|
| 它生成 JSON Schema 给模型做约束 | 不是它的增量。无参的 `.entity(Class)` 同样会把 schema / 格式说明通过 `ChatClientAttributes` 交给 `ChatModelCallAdvisor`，由后者注入模型；两边都发 |
| 默认重试 3 次 | `maxRepeatAttempts=3` 是"额外重复"次数，总调用 = 1 + 3 = **最多 4 次** |
| 仍失败才抛异常 | advisor 自己不抛，把最后一次非法响应原样返回；抛异常的是之后的 `BeanOutputConverter` 转换 |

真实流程（反编译出来的结构）：

```java
// resolveAdvisorChain：回调执行后读开关
if (spec.isValidated()) {
    advisor = StructuredOutputValidationAdvisor.builder()
                .outputJsonSchema(converter.getJsonSchema())  // schema 由 record 字段类型推导
                .build();                                     // maxRepeatAttempts 默认 3
    chain = advisorChain.mutate().push(advisor).build();      // order 极大，紧贴模型调用那层
}

// adviseCall：主循环
for (remaining = 1 + maxRepeatAttempts; remaining > 0 && !valid; remaining--) {
    调模型;
    if (响应带 tool call) 跳过校验;
    if (!通过 schema 校验) {                                  // networknt 校验器，DRAFT_2020_12
        warn 日志;
        // 在原 user 消息尾部追加（原文保留，错误逐轮累积）：
        // "Output JSON validation failed because of: <具体错误>"
        带着改过的 prompt 重新调模型;
    }
}
token 用量逐轮累加（UsageAccumulator）;                       // 最终 usage 是这几轮之和
```

实践结论：

1. **只保结构，不保语义**。`deliveryDate` 是 String，模型给个"下周三"照样通过；`quantity` 给 99999 也通过。System Prompt 里"没提到的字段填 null、不要编造"这类要求，schema 拦不住。想收紧得给字段加约束和描述（如 `@JsonPropertyDescription`），可以先打一次 `getJsonSchema()` 看实际生成长什么样。
2. **成本按轮数翻**。重试是串行再调一次模型，token 逐轮累加，最坏 4 次调用，慢模型上延迟很明显。
3. **不支持流式**。`stream()` 上叠 `validateSchema()` 直接抛 `UnsupportedOperationException`。
4. **lambda 里改不了重试次数**。`EntityParamSpec` 只暴露两个开关（`validateSchema()` / `useProviderStructuredOutput()`），要改次数得自己构造 advisor 加进链。
5. **调试点**：logger `org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor` 开到 DEBUG，能看到 `Generated JSON Schema` / `Attempts left: N` / `JSON validation succeeded` 全过程。

三个接口横向对照：

| | /parse/normal | /parse | /parse/pro |
|---|---|---|---|
| 输出 | 字符串 | 对象 | 对象 |
| schema 发给模型 | 无 | 有 | 有 |
| 输出校验 | 无 | 无 | 有（DRAFT_2020_12） |
| 失败行为 | 不适用 | 直接抛异常 | 错误回灌 + 最多重试 3 次 |
| 最多调用模型 | 1 次 | 1 次 | 4 次 |

## 踩坑速查

| 现象 | 原因 | 解决 |
|---|---|---|
| 拉镜像 `context deadline exceeded` | Docker Hub 国内被墙 | 配镜像加速源；`registry-mirrors` 只对 docker.io 生效，ghcr.io / quay.io 要单独配 |
| Docker Desktop 起不来 | `daemon.json` 被写坏 | `cp ~/.docker/daemon.json.bak ~/.docker/daemon.json` 后重启，或点报错弹窗的 Reset |
| 启动报 `At least one credential source must be specified`（Bean 名常见 `openAiSdkAudioSpeechModel`） | 没配 API Key，而启动时会建所有模态的 Bean | 填占位 Key `sk-placeholder` 先起来，拿到真 Key 再换 |
| `401 / Incorrect API key` | 环境变量没读到，或 Key 有空格引号 | 确认 IDEA Run Configuration 里配了环境变量，改完要重启应用 |
| `404` 调 chat/completions | `base-url` 少了 `/v1` | 补上 |
| 模型名报错 | 配置项写错 | 用 `spring.ai.openai.chat.model` |
| `conversationId cannot be null` | `defaultAdvisors` 注册的 advisor 是全局的，不需要记忆的接口也被它拦 | 拆成 `plainChatClient` / `memoryChatClient` 两个 Bean，别用固定 ID 糊 |
| 设了 DEBUG 却看不到 prompt | 只设级别没用，且级别设错包 | 注册 `SimpleLoggerAdvisor` + 级别设在 `org.springframework.ai.chat.client.advisor`，用非流式接口看 |
| curl 传中文报错 | URL 没编码 | `curl -G "..." --data-urlencode "message=中文"` |
| 找不到 `Flux` | 缺 reactor-core | pom 加 `io.projectreactor:reactor-core` |
| `Port 8080 was already in use` | 端口被占 | `application.yml` 加 `server.port: 8081` |
| Maven 下载卡住 | 网络 | 配阿里云镜像（`~/.m2/settings.xml`） |

### SSE 中文乱码：一条完整的排查链

浏览器直接打开流式接口的 URL，看到的是这种：

```
data: ã€Š
data: ç§‹
data: æ—¥
```

接口和数据都是对的，只是"浏览器直接打开 URL"这一种方式下的显示问题。排查顺序值得记住：

1. **先确认字节**：`《` 的 UTF-8 字节是 `E3 80 8A`，按 Latin-1 解释就是 `ã€Š`。字节没错，是解码方式错了。
2. **再看响应头**：`Content-Type: text/event-stream` 不带 charset，浏览器对没有 charset 的 `text/*` 会回退到 Latin-1。
3. **最后判断客户端解码方式**：`curl -N` 正常，`EventSource` 正常（规范强制 UTF-8），只有"浏览器直接打开"这一种坏。

`produces = "text/event-stream;charset=UTF-8"` **改不动这个头**——Spring MVC 处理 Flux 返回值时会把 Content-Type 强制写死，丢弃 charset（2026-10-11 实测）。所以服务端不用改，用 `curl` 或 `src/main/resources/static/sse.html`（EventSource 测试页）验证即可。

这条链是 W0 留下的第一个真实排查素材：先看原始字节 → 再看协议头 → 最后看客户端行为，比背八股有用。

## 下一步

- **W1 收尾**：上面列的 D6 异常处理、D5 对比实验表、日志配置修正、笔记，然后 git push。
- **W2 预告**：Tool Calling（定义 3 个工具让模型自己决定调用）+ Embedding 选型 + pgvector 建表入库，跑通"问题 → 最相关片段"。
- 计划里没写的不做。想学 RAG 的时候就别顺手看微调，这是 16 周计划里的铁律之一。

## 这些内容能回答什么问题

| 已经积累的素材 | 对应的问题 |
|---|---|
| validateSchema 的机制 + 待做的成功率对比表 | 结构化输出怎么保证稳定性？模型乱输出怎么办？ |
| Advisor 洋葱模型、注册/传参分离、日志反推实验 | 你怎么理解 Spring AI 的 Advisor 机制？ |
| record + 反射 + JSON Schema 的关系 | 为什么用 record 而不是 Lombok？ |
| SSE 中文乱码的排查链 | 讲一个你真实排查过的疑难问题 |
| 两个 ChatClient 的拆分（对比"固定 sessionId"的错误修法） | 全局 advisor 的副作用怎么处理？ |
| System Prompt 边界实测（引导不是强制） | 怎么控制模型的行为边界？ |
