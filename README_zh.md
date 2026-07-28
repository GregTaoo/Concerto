<div align="center">
  <img src="icon-large.png" alt="Concerto" width="256">
  <h1>Concerto</h1>
  <p><b>Minecraft 游戏内音乐播放器 — Fabric / NeoForge 客户端，Paper 服务端插件</b></p>
  <p>
    <a href="https://modrinth.com/mod/A0VZd1kW"><img src="https://img.shields.io/modrinth/dt/A0VZd1kW?logo=modrinth&label=Modrinth" alt="Modrinth 下载量"></a>
    <a href="https://github.com/GregTaoo/Concerto/releases"><img src="https://img.shields.io/github/v/release/GregTaoo/Concerto?logo=github&label=Release" alt="GitHub Release"></a>
    <img src="https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.20.6%20%7C%201.21.1%20%7C%201.21.6%20%7C%201.21.11%20%7C%2026.1.2%20%7C%2026.2-62b47a?logo=minecraft" alt="Minecraft 1.20.1、1.20.6、1.21.1、1.21.6、1.21.11、26.1.2 和 26.2">
    <img src="https://img.shields.io/badge/Java-21+-f89820?logo=openjdk&logoColor=white" alt="Java 21+">
    <img src="https://img.shields.io/badge/加载器-Fabric%20%7C%20NeoForge%20%7C%20Paper-8a2be2" alt="Fabric | NeoForge | Paper">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"></a>
  </p>
  <p><a href="README.md">English</a> | 简体中文</p>
</div>

> 不用切出游戏就能听歌：本地文件、网络直链，以及网易云音乐、QQ 音乐、酷狗音乐。还可以和朋友在同一间音乐室同步听歌，或开启全服点播，让大家轮流点歌。

---

## ✨ 功能亮点

- **多来源，一个播放器** —— 网易云音乐、QQ 音乐、酷狗音乐、本地音频文件（支持拖放导入）、HTTP 直链，都在同一条播放队列里。
- **顺畅播放** —— 拖动进度条即可跳转，流媒体加载时也能继续收听；如果默认输出不合适，还可切换音频输出方式。支持 MP3、OGG/Opus、FLAC、WAV、M4A/AAC 和 AIFF。
- **音乐室（一起听）** —— 同一房间的成员实时共享播放状态：同一首歌、同一进度、同步暂停。房间可以命名、公开展示，在房间列表界面一键加入，也可以凭 UUID 加入。
- **全服点播（KTV 模式）** —— 玩家给全服排队点歌，可以发起投票切歌；没人点歌时自动播放服务器预设歌单。
- **共享账号与预设歌单** —— 服主可以为服务器配置自己已登录的音乐服务账号，并把整理好的歌单分享给所有玩家。
- **跨平台** —— 客户端支持 Fabric 和 NeoForge；插件服可用专属 Paper 插件，模组客户端在插件服上同样能用全部服务端功能。

---

## 📦 安装

| 平台 | 需要安装 |
| --- | --- |
| **Fabric 客户端** | `Concerto-mc<版本>-fabric-<模组版本>.jar` + [Fabric API](https://modrinth.com/mod/fabric-api) |
| **NeoForge 客户端** | `Concerto-mc<版本>-neoforge-<模组版本>.jar` |
| **Paper 服务端** | `Concerto-mc<版本>-paper-<模组版本>.jar` 放进 `plugins/` |

Concerto 会把设置、歌单、下载内容和登录信息保存在游戏或服务端目录旁的 `Concerto/` 文件夹中。

单人游戏和未安装 Concerto 的服务器中，都可以把它当作本地播放器使用；音乐室、音乐分享和全服点播等联机功能则需要服务端也安装 Concerto（Fabric、NeoForge 或 Paper 均可）。

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
这是播放列表、网易云 / QQ / 酷狗曲库、搜索、本地文件、链接、音乐室和设置的统一入口。

### 播放列表界面（`U`）
- 直接从文件管理器**拖放**音频文件或整个文件夹即可导入。
- **双击**条目播放，单击选中。
- 底部一排按钮：下一首 / 播放 / 删除 / 播放顺序（顺序、随机、倒序、单曲循环）/ 暂停 / 详情 / 清空。
- **音量按钮**会弹出滑条，点击界面其他位置自动收起；音量即调即生效，并写入配置。
- 顶部搜索框用于筛选当前列表。

### 歌单预览
- 点击**保存到本地**，即可将当前歌单立即加入**本地歌单**；同时会写入 `Concerto/local_playlists/` 下的 JSON 文件。

### 播放器界面
- 拖动进度条**跳转播放位置**，拖动过程中会显示目标位置预览。
- 显示标题、歌手、来源、封面，流媒体曲目会显示缓冲状态。

### 音乐室界面
- **发现视图**：列出服务器上公开（可见）的房间，一键加入；也可以在这里输入房名创建房间、凭 UUID 加入、刷新列表或进入全服点播。
- **管理视图**（已在房间内时）：成员列表、改名、切换*可见* / *可加入*、给成员授权、解散或退出房间。

### 选项界面
可在游戏内选项中调整音频输出、HUD 显隐、滚动文字速度、网易云音质等。切换音频输出会从下一首开始生效。

---

## 📜 指令一览

### 客户端播放控制 —— `/concerto`（别名 `/music`）

`/music` 别名可通过客户端配置项 `registerMusicCommand` 关闭。

日常操作都可以在上面的界面中完成；以下指令只是为喜欢快捷操作的玩家和服主准备的补充。

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
1. 在服务端 Concerto 设置中保持全服点播开启；也可以选择是否在玩家进服时发送邀请。
2. 玩家用 `/musicroom agent join` 进入队列，用 `/musicroom agent add` 点歌（间隔受 `musicAgentAddTimeLimit` 限制）。
3. 歌不合口味？任何队列内玩家都能 `/musicroom agent vote` 发起切歌投票，其他人用 `/musicroom agent vote true`（或 `false`）表态。
4. 没人点歌时，服务器自动播放预设电台（如已配置）。

### 与全服共享音乐服务账号
1. 先在自己的客户端登录音乐服务。
2. 把客户端 `Concerto/` 中生成的 `.cookie` 文件复制到服务端的 `Concerto/` 目录。请像保管密码一样保管这些文件。
3. 在服务端 Concerto 设置中开启点播队列的共享账号访问，然后执行 `/concerto-server reload-cookie`。

### 下发预设歌单
把导出的歌单 JSON（见 `/concerto export-as-playlist`）放到服务端 `Concerto/preset_radios/` 下；其中 `music_agent.json` 是 KTV 模式空闲时的兜底歌单。客户端会自动收到电台列表，也可以用 `/concerto-server fetch-radios` 手动拉取。

### 缓存与下载
Concerto 只会在你主动操作时保存音乐：`/concerto save` 保存当前曲目，`download-current` / `download-all` 会连同歌词下载为长期保存的副本。缓存大小可在设置中调整。

---

## ⚙️ 配置说明

日常使用无需编辑文件，游戏内选项已覆盖常用设置。下面的内容仅供希望进一步调整的玩家和服主参考。设置文件位于 `Concerto/` 目录；如需编辑，请先关闭游戏。

### `client_config.json`

**播放与联机**

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `playerVolume` | `1.0` | 播放器音量，`0.0` – `1.0` |
| `playerVolumeFollowsMaster` | `true` | 音量再乘以 Minecraft 主音量 |
| `playbackBackend` | `"JAVASOUND"` | 音频后端：`"JAVASOUND"` 或 `"OPENAL"`，切换后下一首生效 |
| `displayPlayerScreenCoverAndSpectrum` | `true` | 在播放页面显示封面和频谱；`false` 时使用全宽歌词视图 |
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

**怎样在服务端配置音乐服务账号？**
先在自己的客户端登录，再把客户端 `Concerto/` 中的 `.cookie` 文件复制到服务端同名目录，然后执行 `/concerto-server reload-cookie`。这些文件可以访问你的账号，务必妥善保管。

**网易云扫码登录后一直报错？**
网易云的风控对新设备、异地登录非常敏感。建议放弃扫码 / 密码登录，直接把浏览器里已正常登录的完整 Cookie 粘贴进来。

**QQ 音乐过几天就解析失败？**
QQ 音乐的授权有效期本来就短，过期是正常现象。在服务端更新 Cookie 文件后执行 `/concerto-server reload-cookie` 即可。

**音频输出选哪个？**
推荐先使用默认的 `JAVASOUND`。如果遇到卡顿或输出设备不对，再试试 `OPENAL`。切换后从下一首开始生效。

**有些歌拖动进度条很慢？**
部分流媒体文件和电台无法直接跳到任意位置，因此 Concerto 可能需要一点时间追上进度；在刚刚播放过的片段之间移动通常会更快。

**本地音频文件支持哪些格式？**
Concerto 支持 MP3、OGG/Opus、FLAC、WAV、M4A/AAC 和 AIFF。同一种格式中较少见的编码方式可能仍有差异；遇到无法播放的文件时，可尝试转换为 MP3 或 WAV。

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
