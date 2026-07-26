<div align="center">
  <img src="icon-large.png" alt="Concerto" width="256">
  <h1>Concerto</h1>
  <p><b>Minecraft 游戏内音乐播放器 — Fabric / NeoForge 客户端，Paper 服务端插件</b></p>
  <p>
    <a href="https://modrinth.com/mod/A0VZd1kW"><img src="https://img.shields.io/modrinth/dt/A0VZd1kW?logo=modrinth&label=Modrinth" alt="Modrinth 下载量"></a>
    <a href="https://github.com/GregTaoo/Concerto/releases"><img src="https://img.shields.io/github/v/release/GregTaoo/Concerto?logo=github&label=Release" alt="GitHub Release"></a>
    <img src="https://img.shields.io/badge/Minecraft-1.21.6%20--%201.21.9-62b47a?logo=minecraft" alt="Minecraft 1.21.6 - 1.21.9">
    <img src="https://img.shields.io/badge/Java-21+-f89820?logo=openjdk&logoColor=white" alt="Java 21+">
    <img src="https://img.shields.io/badge/加载器-Fabric%20%7C%20NeoForge%20%7C%20Paper-8a2be2" alt="Fabric | NeoForge | Paper">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"></a>
  </p>
  <p><a href="README.md">English</a> | 简体中文</p>
</div>

> 不用切出游戏就能听歌：本地文件、网络直链，以及网易云音乐、QQ 音乐、酷狗音乐。还可以和其他玩家进同一间音乐室实时同步"一起听"，或者开启全服点播，让整个服务器轮流点歌。

> **本文档对应 `dev` 分支（Minecraft 1.21.6 – 1.21.9，模组版本 2.1.0）。** 其他 Minecraft 版本在各自的 `mc*` 分支上维护，细节可能略有出入。

---

## ✨ 功能亮点

- **多来源，一个播放器** —— 网易云音乐、QQ 音乐、酷狗音乐、本地音频文件（支持拖放导入）、HTTP 直链，都在同一条播放队列里。
- **重写的播放引擎** —— 拖动进度条即可精确跳转，边下边播、断点续传，音频后端可在 JavaSound 与 OpenAL 之间切换。支持 MP3、OGG、FLAC、WAV。
- **音乐室（一起听）** —— 同一房间的成员实时共享播放状态：同一首歌、同一进度、同步暂停。房间可以命名、公开展示，在房间列表界面一键加入，也可以凭 UUID 加入。
- **全服点播（KTV 模式）** —— 玩家给全服排队点歌，可以发起投票切歌；没人点歌时自动播放服务器预设歌单。
- **VIP 共享与预设歌单** —— 服主把平台 Cookie 部署到服务端后，全服玩家都能通过服务器的账号听 VIP 歌曲；还可以向所有客户端下发整理好的歌单。
- **跨平台** —— 客户端支持 Fabric 和 NeoForge；插件服可用专属 Paper 插件，模组客户端在插件服上同样能用全部服务端功能。

---

## 📦 安装

| 平台 | 需要安装 |
| --- | --- |
| **Fabric 客户端** | `Concerto-mc<版本>-fabric-<模组版本>.jar` + [Fabric API](https://modrinth.com/mod/fabric-api) |
| **NeoForge 客户端** | `Concerto-mc<版本>-neoforge-<模组版本>.jar` |
| **Paper 服务端** | `Concerto-mc<版本>-paper-<模组版本>.jar` 放进 `plugins/` |

所有运行数据（配置、Cookie、缓存、歌单）都在游戏或服务端目录旁的 `Concerto/` 文件夹里。

单人游戏和纯净服务器可以正常当本地播放器用；音乐室、音乐分享、全服点播这些联机功能需要服务端也装有 Concerto（Fabric、NeoForge 或 Paper 任一均可）。

---

## ⌨️ 快捷键

以下按键都可以在 **选项 → 按键控制** 里改绑。

| 默认按键 | 功能 |
| :---: | --- |
| `I` | 打开 Concerto 主菜单 |
| `U` | 打开播放列表界面 |
| `N` | 播放下一首 |
| `P` | 暂停 / 继续 |

---

## 🖱️ 界面与操作

### 主菜单（`I`）
所有功能的入口：播放列表、三大平台的曲库浏览（网易云 / QQ / 酷狗）、搜索、按 URL 或路径添加音乐、本地歌单、预设电台、音乐室、审核队列和选项。

### 播放列表界面（`U`）
- 直接从文件管理器**拖放**音频文件或整个文件夹即可导入。
- **双击**条目播放，单击选中。
- 底部一排按钮：下一首 / 播放 / 删除 / 播放顺序（顺序、随机、倒序、单曲循环）/ 暂停 / 详情 / 清空。
- **音量按钮**会弹出滑条，点击界面其他位置自动收起；音量即调即生效，并写入配置。
- 顶部搜索框用于筛选当前列表。

### 播放器界面
- 拖动进度条**跳转播放位置**，拖动过程中会显示目标位置预览。
- 显示标题、歌手、来源、封面，流媒体曲目会显示缓冲状态。

### 音乐室界面
- **发现视图**：列出服务器上公开（可见）的房间，一键加入；也可以在这里输入房名创建房间、凭 UUID 加入、刷新列表或进入全服点播。
- **管理视图**（已在房间内时）：成员列表、改名、切换*可见* / *可加入*、给成员授权、解散或退出房间。

### 选项界面
游戏内选项覆盖播放音量（含"跟随主音量"）、**OpenAL 输出**开关（下一首开始生效）、HUD 各元素的显隐、滚动文字速度、网易云音质等，与 `client_config.json` 一一对应（见下文）。

---

## 📜 指令一览

### 客户端播放控制 —— `/concerto`（别名 `/music`）

`/music` 别名可通过客户端配置项 `registerMusicCommand` 关闭。

| 指令 | 说明 |
| --- | --- |
| `/concerto pause` | 暂停 / 继续 |
| `/concerto start` | 启动播放器（未运行时） |
| `/concerto stop` | 停止播放 |
| `/concerto clear` | 停止播放并清空播放列表 |
| `/concerto restart` | 重启播放器核心（用于从卡死状态恢复） |
| `/concerto reload` | 重载配置和本地歌单，并重启播放器 |
| `/concerto save` | 把当前曲目存入本地缓存（仅限可缓存来源） |
| `/concerto like` / `dislike` | 收藏 / 取消收藏当前曲目（仅网易云音乐） |
| `/concerto download-current` | 下载当前曲目及歌词到 `Concerto/` |
| `/concerto download-all` | 下载当前播放列表中的全部曲目 |
| `/concerto export-as-playlist` | 把当前播放列表导出为 JSON 预设歌单（`Concerto/local_playlists`） |
| `/concerto clean-cache` | 清空客户端缓存（音乐、图片、校验文件） |

### 音乐室与全服点播 —— `/musicroom`（需要服务端装有 Concerto）

| 指令 | 说明 |
| --- | --- |
| `/musicroom create [房名]` | 创建（可命名）并加入房间，房间 UUID 自动复制到剪贴板；受服务端 `musicRoomCommandPermission` 权限限制 |
| `/musicroom join <uuid>` | 凭 UUID 加入房间 |
| `/musicroom quit` | 退出当前房间或全服点播队列 |
| `/musicroom remove` | 解散自己创建的房间 |
| `/musicroom members` | 查看房主和成员列表 |
| `/musicroom op <玩家>` | 给成员授予 / 收回播放控制权（仅房主可用） |
| `/musicroom agent join` / `quit` | 加入 / 退出全服点播（KTV 模式） |
| `/musicroom agent add` | 把自己正在播放的曲目推送到全服点播队列 |
| `/musicroom agent vote` | 对当前点播曲目发起切歌投票 |
| `/musicroom agent vote <true\|false>` | 在进行中的投票里投赞成 / 反对票 |

### 玩家间音乐分享 —— `/sharemusic`

| 指令 | 说明 |
| --- | --- |
| `/sharemusic to <玩家 \| @a>` | 把当前曲目分享给指定玩家；`@a` 为全服广播（开启审核时会先进入管理员审核队列）。会暴露本地文件路径的来源出于安全考虑无法分享 |
| `/sharemusic accept <uuid>` | 接受一条分享邀请 |
| `/sharemusic reject <uuid>` | 拒绝一条分享邀请 |
| `/sharemusic reject all` | 拒绝全部待处理邀请 |
| `/sharemusic list <页码>` | 分页查看待处理邀请（带可点击的接受 / 拒绝按钮） |

### 服务端管理 —— `/concerto-server`

除 `fetch-radios` 所有人可用外，其余子指令都需要 2 级权限（OP）。

| 指令 | 说明 |
| --- | --- |
| `/concerto-server audit <uuid>` | 通过一条待审核的全服广播请求 |
| `/concerto-server audit reject <uuid>` | 拒绝一条广播请求 |
| `/concerto-server audit reject all` | 拒绝全部待审核请求 |
| `/concerto-server audit list <页码>` | 分页查看审核队列（带可点击的接受 / 拒绝按钮） |
| `/concerto-server reload` | 重载服务端配置、预设电台和平台 Cookie |
| `/concerto-server reload-cookie` | 只重载平台 Cookie 文件 |
| `/concerto-server clean-cache` | 清空服务端缓存 |
| `/concerto-server fetch-radios` | 把服务器预设电台同步到自己的客户端 |
| `/concerto-server agent reset` | 彻底重置全服点播队列 |
| `/concerto-server agent cut` | 强制切掉当前点播曲目 |
| `/concerto-server agent stop` / `start` | 停止 / 重新开启 KTV 模式 |

---

## 📖 使用指南

### 和朋友"一起听"
1. `/musicroom create 我的房间`（或在音乐室界面操作），UUID 会自动进剪贴板。
2. 把 UUID 发给朋友（`/musicroom join <uuid>`）；或者把房间设为*可见* + *可加入*，让它出现在所有人的房间列表里。
3. 有控制权的成员切歌、拖进度、暂停，其他成员都会实时跟随。用 `/musicroom op` 决定谁有控制权。

### 开一场全服 KTV
1. 保持 `server_config.json` 里的 `serverMusicAgent` 开启；`agentInviteWhenJoin` 开启时玩家进服就会收到邀请。
2. 玩家用 `/musicroom agent join` 进入队列，用 `/musicroom agent add` 点歌（间隔受 `musicAgentAddTimeLimit` 限制）。
3. 歌不合口味？任何队列内玩家都能 `/musicroom agent vote` 发起切歌投票，其他人用 `/musicroom agent vote true`（或 `false`）表态。
4. 没人点歌时，服务器自动播放预设电台（如已配置）。

### 与全服共享 VIP 账号
1. 先在自己客户端上登录平台（扫码或在登录界面粘贴 Cookie）。
2. 把客户端 `Concerto/` 里生成的 `.cookie` 文件复制到服务端的 `Concerto/` 目录。
3. 开启 `musicAgentUseShared` 让点播统一走服务器账号解析，然后执行 `/concerto-server reload-cookie`。

### 下发预设歌单
把导出的歌单 JSON（见 `/concerto export-as-playlist`）放到服务端 `Concerto/preset_radios/` 下；其中 `music_agent.json` 是 KTV 模式空闲时的兜底歌单。客户端会自动收到电台列表，也可以用 `/concerto-server fetch-radios` 手动拉取。

### 缓存与下载
播放本身不会往音乐缓存里写任何东西——缓存永远是显式操作：`/concerto save` 缓存当前曲目，`download-current` / `download-all` 连歌词一起下载为永久副本。缓存总量由 `maxCacheSize` 限制。

---

## ⚙️ 配置说明

配置文件为 JSON 格式，位于 `Concerto/` 目录，**每次启动会按解析后的值重写**——请在游戏关闭时编辑，或直接使用游戏内选项界面。

### `client_config.json`

**播放与联机**

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `playerVolume` | `1.0` | 播放器音量，`0.0` – `1.0` |
| `playerVolumeFollowsMaster` | `true` | 音量再乘以 Minecraft 主音量 |
| `playbackBackend` | `"JAVASOUND"` | 音频后端：`"JAVASOUND"` 或 `"OPENAL"`，切换后下一首生效 |
| `neteaseMusicQuality` | `"HIRES"` | 网易云音质：`"STANDARD"`、`"HIGHER"`、`"EXHIGH"`、`"LOSSLESS"`、`"HIRES"` |
| `maxCacheSize` | `100000000` | 缓存上限（字节，音乐与图片各自计算） |
| `handshakeRequired` | `true` | 使用服务端功能前要求完成 Concerto 握手校验 |
| `confirmAfterReceived` | `true` | 收到分享先询问（改为 `false` 则自动接受） |
| `joinAgentWhenInvited` | `false` | 进服时自动接受全服点播邀请 |
| `registerMusicCommand` | `true` | 注册 `/music` 别名 |
| `printRequestResults` | `false` | 打印平台 API 响应（调试用） |
| `kuGouMusicLite` | `false` | 使用酷狗概念版接口 |
| `autoGetKuGouDailyVIP` | `false` | 自动领取酷狗每日 VIP |

**HUD 显示**

每个 HUD 元素都有 `display*` 开关、位置、对齐方式（`LEFT` / `CENTER` / `RIGHT`）和 ARGB 颜色：

| 元素 | 相关配置项 |
| --- | --- |
| 歌词 | `displayLyrics`、`lyricsPosition`（默认 `0.5,1-100`）、`lyricsAlignment`、`lyricsColor` |
| 副歌词（翻译） | `displaySubLyrics`、`subLyricsPosition`（默认 `0.5,1-89`）、`subLyricsAlignment`、`subLyricsColor` |
| 曲目信息 | `displayMusicDetails`、`musicDetailsPosition`（默认 `1-30,0+5`）、`musicDetailsAlignment`、`musicDetailsColor` |
| 时间与进度条 | `displayTimeProgress`、`timeProgressPosition`（默认 `1-30,0+15`）、`timeProgressAlignment`、`timeProgressTextColor`、`timeProgressColor`、`timeProgressBgColor` |
| 封面 | `displayCoverImg`、`coverImgPosition`（默认 `1-25,0`）、`coverImgSize`、`coverImgInCircle`、`coverImgRotate` |
| 其他 | `textShadow`、`hideWhenChat`、`scrollingTextSpeed` |

- **位置格式**：`<横向比例>±<像素>,<纵向比例>±<像素>`。如 `0.5,1-100` 表示水平居中、距底边 100 像素；`1-30,0+15` 表示距右边 30 像素、距顶边 15 像素。
- **颜色格式**：`#AARRGGBB`，如 `#ffffffff` 为不透明纯白，`#ff00aaaa` 为不透明青色。

### `server_config.json`

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `auditionRequired` | `true` | `@a` 全服广播需要 OP 通过 `/concerto-server audit` 审核 |
| `serverMusicAgent` | `true` | 开启全服点播（KTV 模式） |
| `agentInviteWhenJoin` | `true` | 玩家进服时发送点播邀请 |
| `musicRoomCommandPermission` | `2` | 创建音乐室所需的最低权限等级 |
| `musicAgentAddTimeLimit` | `60` | 单个玩家两次点歌的最小间隔（秒） |
| `musicAgentUseShared` | `true` | 点播曲目统一通过服务端自己的 Cookie 解析 |
| `kuGouMusicLite` | `false` | 服务端使用酷狗概念版接口（会同步给客户端） |

---

## ❓ 常见问题

**服务端的 Cookie 怎么配置？**
先在自己客户端上登录，然后把客户端 `Concerto/` 里的 `.cookie` 文件复制到服务端同名目录，再执行 `/concerto-server reload-cookie`。这些文件等同于完整的账号凭据，务必保管好。

**网易云扫码登录后一直报错？**
网易云的风控对新设备、异地登录非常敏感。建议放弃扫码 / 密码登录，直接把浏览器里已正常登录的完整 Cookie 粘贴进来。

**QQ 音乐过几天就解析失败？**
QQ 音乐的授权有效期本来就短，过期是正常现象。在服务端更新 Cookie 文件后执行 `/concerto-server reload-cookie` 即可。

**音频后端选哪个？**
默认的 `JAVASOUND` 足够稳。如果遇到卡顿或输出设备不对，可以试试 `OPENAL`——它走游戏自带的声音引擎（LWJGL OpenAL）。切换后从下一首开始生效。

**某些歌拖进度条很慢？**
没有内嵌 seek 表的流（大多数 FLAC 流、部分电台源）跳转时需要从头解码到目标位置。缓冲范围内的前向跳转和小幅回退都很快。

**为什么 M4A/AAC 放不了？**
当前引擎没有 M4A/AAC 解码器，属于有意不支持。请转成 MP3、OGG、FLAC 或 WAV。

**播放器卡死 / 出错后没声音？**
`/concerto restart` 会重启播放器核心且不动播放列表。如果界面渲染异常、封面黑块，先 `/concerto clean-cache`；仍不行就在游戏关闭状态下删除 `Concerto/cache` 目录。

**音乐室 / 分享指令提示功能不可用？**
这些功能需要服务端装有 Concerto（Fabric、NeoForge 或 Paper 插件）。在纯净服务器上，模组仍可作为本地播放器正常使用。

**音量调了没反应或自己变小？**
如果开着 `playerVolumeFollowsMaster`，实际音量是 `playerVolume × 主音量`——记得检查 Minecraft 自己的音乐音量设置。

---

## 🛡️ 协议与声明

- ⚠️ 请妥善保管 `Concerto/` 里的 `.cookie` 文件，切勿分享或提交到仓库。
- ⚖️ 本项目仅供学习交流，使用前请阅读[协议](LICENSE)。**未授权在任何收费或商业性质平台发布。**
- 🤝 酷狗音乐支持由 [ming-sc](https://github.com/ming-sc) 贡献。感谢所有[支持者](supporters.md)。

## 🚀 依赖的开源项目

[soundlibs (mp3spi / tritonus)](https://github.com/pdudits/soundlibs) ·
[JustFLAC](https://github.com/drogatkin/JustFLAC) ·
[java-vorbis-support](https://github.com/Trilarion/java-vorbis-support) ·
[jaudiotagger](https://github.com/marcoc1712/jaudiotagger) ·
[QR Code generator (Project Nayuki)](https://www.nayuki.io/page/qr-code-generator-library) ·
[Gson](https://github.com/google/gson) ·
[Fabric API](https://github.com/FabricMC/fabric)
