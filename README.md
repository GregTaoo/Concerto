<div align="center">
  <img src="icon-large.png" alt="Concerto" width="256">
  <h1>Concerto</h1>
  <p><b>基于 Fabric / NeoForge / Paper 的 Minecraft 内置音乐播放器</b></p>
</div>

> 支持本地音乐、网络资源，以及网易云音乐、QQ音乐、酷狗音乐等流媒体平台。具备多人联机“一起听”功能。

---

## 🌟 主要功能

- **多平台音频解析**：支持网易云音乐、QQ音乐、酷狗音乐，同时支持本地文件加载与网络直链播放。
- **音乐室（一起听）**：与服务器内的其他玩家进入同一音乐室，所有成员的播放进度、切换与暂停操作均会实时同步。
- **全服点播（KTV模式）**：服务器可开启点播功能，玩家自由排队点播歌曲。支持发起切歌投票，以跳过不符合多数人意愿的音乐。
- **VIP 共享 & 服务端预设**：服主可将个人支持平台配置至服务端，与全服玩家共享 VIP 权限歌曲。此外，还支持上传预设歌单，在无点播任务时自动播放。
- **跨平台兼容**：客户端支持 Fabric 和 NeoForge，服务端支持专属 Paper 插件版本，实现跨平台互通。

---

## 🛡️ 注意事项与致谢

- ⚠️ **安全提醒**：请妥善保管您 `Concerto` 文件夹中的 `.cookie` 文件，切勿随意分享他人。
- ⚖️ **协议与声明**：本模组仅供学习交流使用，严禁将其用于非法用途。使用前请仔细阅读 [协议](LICENSE)。本模组**未授权发布于任何收费或附带商业性质的平台**，若有违规发布者需自行承担法律责任。如有侵权，请联系删除。
- 🤝 **特别鸣谢**：酷狗音乐相关代码由 [ming-sc](https://github.com/ming-sc) 贡献。感谢所有 [支持者](supporters.md)。

> **Wiki 基准模组版本：`2.0.0`**

---

## ⌨️ 快捷操作热键

| 热键 | 功能说明 |
| :---: | --- |
| `U` | 打开播放队列管理界面 |
| `I` | 打开 Concerto 主菜单 |
| `N` | 播放下一首 |
| `P` | 暂停 / 继续播放 |

---

## 📜 指令参考

### 1. 客户端功能指​​令 `/concerto` （可使用别名 `/music`）
| 指令操作 | 功能说明 |
| --- | --- |
| `pause` / `start` / `stop` | 暂停、开始 或 停止播放 |
| `clear` | 停止播放并清空当前播放列表 |
| `reload` / `restart` | 重新加载配置及歌单数据 / 重新启动播放器核心（用于修复卡死） |
| `save` | 缓存当前歌曲（仅限支持缓存的音源，如网易云音乐等） |
| `like` / `dislike` | 收藏 / 取消收藏当前歌曲（仅限网易云音乐） |
| `download-current` | 将当前歌曲及相关歌词下载至本地 `Concerto` 文件夹 |
| `download-all` | 下载当前播放队列中的所有歌曲及歌词 |
| `export-as-playlist` | 导出当前缓存队列为服务端所需的预设歌单 `JSON` 格式 |
| `clean-cache` | 清理客户端 `Concerto/cache` 目录下的所有缓存及验证文件（含 cookie） |

### 2. 音乐室指令 `/musicroom` （需服务端安装 Concerto）
| 指令操作 | 功能说明 |
| --- | --- |
| `create` | 创建并加入专属音乐室，成功后将自动复制对应的 UUID（受服务端权限要求限制） |
| `join [UUID]` / `quit` | 加入对应 UUID 编号的音乐室 / 退出当前的音乐室或点播大厅 |
| `remove` / `members` | 解散当前音乐室 / 查看所在音乐室的成员列表 |
| `op [玩家]` | 赋予或撤销指定玩家的管理切歌权限（仅限房间创建者） |
| `agent join` / `quit` | 加入或退出服务器公共点播队列系统 |
| `agent add` | 将当前播放的音乐推送至服务器公共点播队列 |
| `agent vote [true/false]` | 发起集体切歌投票 / 对当前进行的切歌投票投出赞成或反对 |

### 3. P2P 音乐分享指令 `/sharemusic` （客户端间网络直连）
| 指令操作 | 功能说明 |
| --- | --- |
| `to [玩家或@a]` | 将播放内容分享给指定玩家。如服务端未安装模组，命令将回退至原版消息通道尝试推送。仅限白名单平台音源。 |
| `accept [UUID]` / `reject` | 接受 / 拒绝指定的音乐分享（后接 UUID 或输入 `all` 拒绝全部请求） |
| `list [页码]` | 查看待处理的音乐分享邀请列表 |

### 4. 服务端管理指令 `/concerto-server` （限管理员使用）
| 指令操作 | 功能说明 |
| --- | --- |
| `audit [UUID]` / `reject` | 同意或拒绝全服广播分享的音乐审核请求（后接 `all` 拒绝全部等待名单） |
| `audit list [页码]` | 列出当前等待服务端管理员审核的广播歌曲队列 |
| `reload` / `reload-cookie` | 重新读取服务端配置项 / 从文件中重新加载服务器授权 cookie 初始化 |
| `fetch-radios` | 向服务器请求最新的预设歌单数据至本地（普通用户亦适用） |
| `clean-cache` | 清理服务端缓存数据及无效的 cookie 信息 |
| `agent reset` / `cut` | 彻底重置公共点播队列 / 强制当前播放曲目进入下一首跳转 |
| `agent stop` / `start` | 中止音乐点播环境运作 / 重启点播系统恢复播放 |

---

## 📖 进阶使用说明

### 🎧 沉浸组团“一起听”音乐室
- 处在同一音乐室环境下的成员，切歌、调整播放进度、暂停操作可做到全局同步。
- 建议将管理指令权限统一收归，以防大量玩家频繁争夺切歌控制权。
- 通过传递音乐室生成的专属 UUID 即可让其他用户通过指令快速加入。

### 🎵 队列管理界面
- 按键 `U` 能够呼出详情面板，支持拖拽外部音频文件与文件夹执行导入识别操作。
- 面板功能完整支持双击切换及选取目标音乐直接发起向服务端的“推送点播”。
- 通过导出指令生成的歌单配置文件将默认储存于 `Concerto/local_playlists`，便于后续的跨设备同步管理。

### 📡 全方位点播模式与预设配置
- 当音乐室模式投射为全服务器级点播时，玩家提交排队申请如不符预期，可以合议发起投票切除功能。
- **预案播放调度**：服务端管理员可将配置妥当的预定歌单文件（`music_agent.json`）留存于服务端系统的相关目录下。作为后台预备库保障轮播需求不间断。
- **VIP 曲库串流**：开启服务端 `server_config.json` 中的 `musicAgentUseShared` 功能并部署管理员的高级账户 `.cookie` 授权资产，全服成员皆可享用免客户端繁琐绑定的点播曲库。

### 📨 限制级对等网络音乐传发
- 若有临时与指定对象发送乐曲的需求，请使用 `/sharemusic` 系列指令。部分本地私有音轨因被标记不安全无法通过传发机制转接。

---

## ⚙️ 配置文件参数说明

### 服务端 (`server_config.json`)
- `auditionRequired`: 限制全服分享申请强制经由审核（默认 `true`）。
- `serverMusicAgent`: 全局决定开启或关闭服务端音乐代理点歌功能（默认 `true`）。
- `agentInviteWhenJoin`: 登陆自动弹出大厅进入邀请（默认 `true`）。
- `musicRoomCommandPermission`: 开启基础房间所需预设权限（默认 `2`）。
- `musicAgentAddTimeLimit`: 限定玩家点播新歌曲的请求间隔周期（默认 `60` 秒）。
- `musicAgentUseShared`: 点播环境统一步调授权并提取链接（默认 `true`）。
- `kuGouMusicLite`: 限定解析服务端酷狗点播资源时的线路版本分配（默认 `false`）。

### 客户端 (`client_config.json`)
**【 核心控制 】**
- `confirmAfterReceived`: 分享接收提醒将转入免手动的信任同意执行模式（默认开启）。
- `joinAgentWhenInvited`: 接收伺服器统一广播后实施默认登入队列（默认关闭）。
- `neteaseMusicQuality`: 调控云端解析的质量层级（`"STANDARD"`, `"HIGHER"`, `"EXHIGH"`, `"LOSSLESS"`, 默认 `"HIRES"` 解析）。
- `handshakeRequired`: 启用安全链接协商（默认开启）。
- `maxCacheSize`: 分配给缓存空间占用定额（默认 `100MB`）。
- `hideWhenChat`: 检测输入状态以自动平减多余的显示控件（默认开启）。

**【 HUD 显示编排 】**
所有独立的元素组件支持由 `displayLyrics: true/false` 格式予以启用管理。
- **坐标规范**：遵循 `[横向比例](+/-)[偏置],[纵向比例](+/-)[偏置]`。诸如：`0.5,1-70` 表述的是垂直底部缩进定点。
- **色彩规范**：统一用 ARGB 16 进制表现。例列格式如： `#ffffffff` (纯白预设) , `#ff00aaaa` (显色青色) 。

---

## ❓ 常见问题排查与解答

#### **1. 如何配置 Cookie 数据？**
在相关数字平台网页端成功登录后，建议使用相应的网页扩展工具（如 **Cookie-Editor** 插件）将状态数据导出收录进文本档案，以此提供给模组进行鉴定。亦可采取网页前端的开发工具调取核心串列。

#### **2. 网易云通过二维码登录后不断报错无法应用？**
该平台异地风控介入严格。如果遇到频繁抛出错误警告甚至拒绝效用的状况。强烈推荐您转换改由手动抓取导入 `.cookie` 文件或尽可能只以扫描形式进行验证。

#### **3. QQ音乐信息更新缓慢或失效问题**
鉴于服务端下达的校验寿命短暂，QQ音源可能会数日后请求失效。发生后敬请服主及时对文本执行覆写更新工作，并输入 `/concerto-server reload-cookie` 引导服务刷新状态判定。

#### **4. 加载界面不全和特定组件黑屏**
此类属于显示缓存异常和渲染时序问题。如使用指令 `/concerto clean-cache` 无果，请关闭游戏端，手动删除 `.minecraft/Concerto/cache` 中的遗留索引档案，重构载入架构。

---

## 🚀 使用的开源项目鸣谢
*[java-stream-player](https://github.com/goxr3plus/java-stream-player)* | *[ZXing](https://github.com/zxing/zxing)* | *[soundlibs](https://github.com/pdudits/soundlibs)* | *[JustFlac](https://github.com/drogatkin/JustFLAC)* | *[java-vorbis-support](https://github.com/Trilarion/java-vorbis-support)* | *[jaudiotagger](https://github.com/marcoc1712/jaudiotagger)* | *[Fabric API ](https://github.com/FabricMC)*
