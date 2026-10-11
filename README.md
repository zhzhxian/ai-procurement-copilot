# 用 Spring AI 2.0 跑通了第一个流式对话服务

## 踩的坑
### 1. Docker：配镜像加速源 ⚠️ 国内必做
你的 Docker 已经装好了（29.1.2），守护进程也正常。 D2 只剩一件事：配镜像加速源。 不配的话，之后拉任何镜像都会超时。

问题：为什么必须配
不配加速源时，拉镜像会报这种错：

```shell
failed to resolve reference "docker.io/pgvector/pgvector:pg16":
failed to do request: Head "https://registry-1.docker.io/v2/...": context deadline exceeded
```

这是 Docker Hub 在国内被墙，不是你的操作问题。国内没有加速源，拉 Docker Hub 就是不通。

解决：3 步，5 分钟
① 打开 Docker Desktop → 右上角齿轮 ⚙️ Settings → 左侧选 Docker Engine

② 右边是 JSON 编辑器，把内容整体替换成下面这段（已合并你原有的 builder 配置，语法已用解析器验证合法）：

```json
{
  "builder": {
    "gc": {
      "defaultKeepStorage": "20GB",
      "enabled": true
    }
  },
  "experimental": false,
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

③ 点右下角 Apply & Restart，等菜单栏鲸鱼图标变绿。

验证是否生效：

docker info | grep -A 3 "Registry Mirrors"

能看到你填的三个地址，就说明生效了。
### 2. 流式输出乱码的问题
浏览器里看到的是这种：

```html
data: ã€Š
data: ç§‹
data: æ—¥
```

先说结论：接口本身是对的，数据也是对的。这只是"浏览器直接打开 URL 看"这一种方式下的显示问题——换成 EventSource 就正常。不要为它改服务端代码。

为什么会这样

《 在 UTF-8 里是字节 E3 80 8A，按 Latin-1 解释就显示成 ã€Š；秋 → ç§‹；日 → æ—¥。字节是对的，只是解码方式错了。
响应头是 Content-Type: text/event-stream，不带 charset。浏览器对没有 charset 的 text/*，会回退到老的 Latin-1 默认。
produces = "text/event-stream;charset=UTF-8" 改不动这个头。 实测证明：Spring MVC 处理 Flux 返回值的 SSE 处理器会把 Content-Type 强制写死成 text/event-stream，丢弃你指定的 charset。（2026-10-11 实测，别在这上面浪费时间。）
第一步：先确认数据是对的

curl -N -G "http://localhost:8080/chat/stream" --data-urlencode "message=写一首关于秋天的诗"

看到中文正常 → 服务没问题，收工。

✅ 正确的验证／使用方式：用 EventSource 写一个 5 行页面
新建 src/main/resources/static/sse.html：

```html
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><title>SSE Test</title></head>
<body>
<pre id="out"></pre>
<script>
  const msg = encodeURIComponent("写一首关于秋天的诗");
  const es = new EventSource(`/chat/stream?message=${msg}`);
  es.onmessage = e => document.getElementById('out').textContent += e.data;
  es.onerror = () => { es.close(); };
</script>
</body>
</html>
```

重启后访问 http://localhost:8080/sse.html —— 中文完全正常，而且这才是真实的客户端调用方式（浏览器规范规定 EventSource 一律按 UTF-8 解码）。

顺带解决了一个问题：这一页就是你 v1.0 那个"能看到的界面"的雏形。后面 W3 加引用来源、W5 做装饰，都是从这 5 行长出来的。

哪些客户端会有这个问题

客户端	中文是否正常
curl	✅ 正常
浏览器 EventSource / 前端 fetch 流	✅ 正常（规范强制 UTF-8）
浏览器直接打开 URL 看	❌ 乱码（仅此一种）

## 下一步
加多轮记忆和结构化输出
