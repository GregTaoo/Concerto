# Concerto


基于 Fabric 的 Minecraft 内置音乐播放器。 支持本地文件、网络资源、网易云音乐、QQ音乐。支持多人游戏音乐室（一起听）、歌曲点播（KTV模式）、服务端预设歌单、共享 VIP 歌曲等功能。

**本模组仅供学习使用，不得非法利用本模组。请在使用前阅读 [协议](LICENSE)。**

-----------------------------
### [支持者名单](supporters.md)

-----------------------------


**Wiki 基准模组版本：1.3.7**

目前已知支持版本：1.21.5, 1.21.4, 1.21.1, 1.20.6, 1.20.1, 1.19.4, 1.18.2, 1.17.1, 1.16.5，其余版本未经测试

### 安全提醒！

请不要泄露 `Concerto` 文件夹中的 `.cookie` 文件！

### 默认热键

1. `U`: 打开播放队列管理界面
2. `I`: 打开 Concerto 主菜单
3. `N`: 播放下一首
4. `P`: 暂停/继续播放

### 指令

1. `/concerto`（客户端指令，可选别名 `/music`，详见 客户端配置文件）

   `pause`: 暂停/继续播放

   `start`: 开始播放

   `stop`: 停止播放

   `skip`: 播放下一首音乐

   `skip [编号]`: 播放第 `[编号]` 首音乐

   `cut`: 切歌/删除当前播放歌曲

   `clear`: 停止播放并清除播放队列

   `mode [normal|random|reversed|loop]`: 切换播放模式为 正序/随机/倒序/循环

   `reload`: 重新加载播放队列和客户端配置文件

   `restart`: 重新启动播放器（可修复卡死）

   `list [页码]`: 显示第 `[页码]` 页播放队列（10条歌曲一页）

   `save`: 缓存当前歌曲（详见 可缓存歌曲）

   `like`: 喜欢当前歌曲（详见 喜欢歌曲）

   `dislike`: 取消喜欢当前歌曲

   `download-current`: 下载当前歌曲到 `Concerto` 文件夹

   `download-all`: 下载播放队列中的所有歌曲到 `Concerto` 文件夹

   `export-as-playlist`: 导出当前播放队列为服务端预设歌单格式 `JSON`（详见 服务端预设歌单）


2. `/concerto-server`（服务端指令，仅限管理员）

   `audit [UUID]`: 审核通过分享给所有人的音乐（详见 `/sharemusic` 指令）

   `audit reject [UUID]`: 拒绝分享给所有人的音乐

   `audit reject all`: 拒绝所有分享给所有人的音乐

   `audit list [页码]`: 列出第 `[页码]` 页的待审歌曲清单

   `reload`: 重新加载服务端配置文件（详见 服务端配置文件）

   `fetch-radios`: 技术性指令，同步服务端预设歌单到本地（允许普通用户使用）

   `agent reset`: 重新加载服务端歌曲点播队列

   `agent cut`: 强制让服务端点播队列播放下一首/切歌


3. `/sharemusic`（客户端指令，服务端不安装 Concerto 也可使用，非音乐室功能）

   `to [玩家|@a]`: 分享当前播放的歌曲给 `[玩家]` 或所有人（若服务端未安装 Concerto 则通过 `/msg` 指令发送），只能分享被认为安全的音乐（详见 安全的音乐来源）

   `accept [UUID]`: 接受编号为 `[UUID]` 的音乐分享

   `reject [UUID]`: 拒绝编号为 `[UUID]` 的音乐分享

   `reject all`: 拒绝所有音乐分享

   `list [页码]`: 显示第 `[页码]` 页的待接受音乐分享列表


4. `/musicroom`（客户端指令，服务端必须安装 Concerto 才能正常使用）

   `create`: 新建并加入音乐室，并自动复制音乐室的 UUID，默认需要管理员（详见 音乐室、服务端配置文件）

   `join [UUID]`: 加入编号为 `[UUID]` 的音乐室

   `quit`: 退出当前音乐室

   `members`: 显示当前所在音乐室的成员

   `op [玩家]`: 仅限音乐室创建者可用，给予/剥夺 `[玩家]` 的音乐室管理员权限

   `agent join`: 参与服务器音乐点播（详见 服务器音乐点播）

   `agent quit`: 退出服务器音乐点播

   `agent query`: 查询服务器音乐点播待播队列

   `agent add`: 向服务器音乐点播队列添加客户端当前播放的音乐

   `agent vote`: 发起切歌投票

   `agent vote [true/false]`: 给当前进行的切歌投票投 赞成/反对


### 本地播放队列

- 按 `U` 打开播放队列管理界面；
- 单击选中音乐，再点击播放按钮（或直接双击某条音乐）开始播放
- 单击选中音乐，再点击详情按钮查看音乐具体信息（内有**服务器点歌**按钮）
- 可将多个文件/文件夹拖入 播放队列管理界面/添加音乐界面 进行添加

### 服务端预设歌单

- 使用 `/concerto export-as-playlist` 或歌单详情页面按钮导出歌单
- 将导出的 `JSON` 文件放置于 `Concerto/preset_radios` 文件夹下
- 若文件名为 `music_agent.json` 将作为音乐点播室空闲时候的歌单
- 可用 `/concerto-server reload` 重新加载最新添加的预设歌单
- 玩家可在 Concerto 主菜单里看到服务端预设歌单的入口

### 服务端配置文件

- 文件位于 `Concerto/server_config.json`
- `auditionRequired`: 通过 `/sharemusic` 分享给所有人的音乐是否需要审核（默认需要）
- `serverMusicAgent`：启用服务端歌曲点播功能（默认启用）
- `agentInviteWhenJoin`：主动邀请进入服务器的玩家加入歌曲点播（默认启用）
- `musicRoomCommandPermission`：音乐室创建的最低权限要求（默认为2）
- `musicAgentAddTimeLimit`: 同一玩家两次歌曲点播时间间隔（默认为60秒）
- `musicAgentUseShared`: 歌曲点播使用服务端解析播放链接（详见 服务器音乐点播，默认启用）

### 音乐分享（非一起听功能）

- 若服务器安装了 Concerto，则会隐藏地向服务器发送请求，否则将直接调用 `/msg` 指令（发送给个人）或直接发送在公屏（发送给所有人）。
- 服主可在服务端配置中配置审核分享给所有人的音乐。
- 分享的音乐需要对方确认才会在对方的客户端播放。
- 只允许分享被认为是安全的音乐（详见 安全的音乐来源）

### 音乐室

- 音乐室内所有人的音乐播放行为都会受到任意音乐室管理员的影响，例如：切换歌曲/暂停播放。
- 建议不要设置太多管理员以避免争抢问题。
- 创建音乐室时会自动复制 UUID 到剪贴板。
- 只允许分享被认为是安全的音乐（详见 安全的音乐来源）

### 服务器音乐点播

- 选中音乐条目，点击详情按钮，歌曲详情页面内有服务器点歌按钮。若服务可用，则按钮可用。
- 可用 `/musicroom agent vote` 指令发起投票/参与投票
- 服主可在本地登录VIP账号后将 `Concerto` 文件夹下cookie文件复制到服务端，并开启服务端配置选项中的 `musicAgentUseShared`，即可实现VIP歌曲点播。（若服务端运行过程中复制进去，需要重启服务端）
- 若存在文件 `Concerto/preset_radios/music_agent.json` 将作为音乐点播室空闲时候的歌单，若有玩家在播放这里面的歌曲时点歌，将会自动切歌

### 客户端配置文件

- 文件位于 `Concerto/client_config.json`
- `confirmAfterReceived`: 自动接受所有接收到的音乐分享（默认关闭）
- `hideWhenChat`: 在聊天栏打开时隐藏歌词/歌曲信息（默认启用）
- `printRequestResults`: 输出HTTP请求返回的数据；安全起见，除非有必要，否则最好不要启用（默认关闭）
- `maxCacheSize`: 最大缓存大小（默认 100MB）
- `joinAgentWhenInvited`: 被服务器邀请后，自动加入服务器点歌（默认关闭）
- `registerMusicCommand`: 注册 `/music` 为 `/concerto` 指令的别名（默认启用）
- `scrollingTextSpeed`: 滚动显示文字的速度（默认1.0）
- `neteaseMusicQuality`: 网易云音乐音频的质量（"STANDARD","HIGHER","EXHIGH","LOSSLESS","HIRES"，默认"HIRES"）
- `displayLyrics`: 是否显示歌词（默认启用）
- `lyricsPosition`: 歌词显示位置（格式详见下方 位置格式配置，默认为 `0.5,1-70`）
- `lyricsAlignment`: 歌词显示靠左/居中/靠右（"LEFT","CENTER","RIGHT"，默认居中）
- `lyricsColor`: 歌词颜色（格式详见下方 颜色配置，默认为 `#ff00aaaa`）
- `displaySubLyrics`: 是否显示翻译歌词 （默认启用）
- `subLyricsPosition`: 翻译歌词显示位置（格式详见下方 位置格式配置，默认为 `0.5,1-60`）
- `subLyricsAlignment`: 翻译歌词显示靠左/居中/靠右（"LEFT","CENTER","RIGHT"，默认居中）
- `subLyricsColor`: 翻译歌词颜色（格式详见下方 颜色配置，默认为 `#ffffaa00`）
- `displayMusicDetails`: 显示歌曲具体信息（默认启用）
- `musicDetailsPosition`: 歌曲具体信息显示位置（格式详见下方 位置格式配置，默认为`1-5,0+5`）
- `musicDetailsAlignment`: 歌曲具体信息靠左/居中/靠右（"LEFT","CENTER","RIGHT"，默认靠右）
- `musicDetailsColor`: 歌曲具体信息颜色（格式详见下方 颜色配置，默认为 `#ffffffff`）
- `displayTimeProgress`: 显示歌曲播放进度（默认启用）
- `timeProgressPosition`: 歌曲播放进度显示位置（格式详见下方 位置格式配置，默认为 `1-5,0+15`）
- `timeProgressAlignment`: 歌曲播放进度显示靠左/居中/靠右（"LEFT","CENTER","RIGHT"，默认靠右）
- `timeProgressTextColor`: 歌曲播放进度文字颜色（格式详见下方 颜色配置，默认为 `#ffffffff` ）
- `timeProgressColor`: 歌曲播放进度条颜色（格式详见下方 颜色配置，默认为 `#ff0155bc` ）
- `timeProgressBgColor`:  歌曲播放进度条背景颜色（格式详见下方 颜色配置，默认为 `#ffa1c7f6`）
- `textShadow`: 歌词/音乐信息显示文字阴影（默认开启）

### 位置格式配置

基本格式：`[横向比例](+/-)[横向偏移],[纵向比例](+/-)[纵向偏移]`，若偏移量为0可省略（包括加减号）

例如，`0.5,1-70` 表示屏幕横向中心点底部向上70像素的位置，`1-5,0+5` 表示屏幕右上角分别向下向左5像素的位置

### 颜色配置

基本格式：`(0x|#)[8位16进制数]`，每两位分别表示ARGB，建议使用在线拾色器（若复制的只有6位，请手动在前面加上两位 `ff`），请注意最前面需要加 `0x` 或 `#`

例如，`#ffffffff` 表示完全不透明的纯白色，`#ff00aaaa` 表示完全不透明的青色

### 可缓存歌曲

即网易云音乐，QQ音乐以及其他网络资源

### 喜欢音乐

仅支持网易云音乐

### 安全的音乐来源

即网易云音乐和QQ音乐，不含本地资源、其他网络资源

### Q & A

- Q: 网易云音乐用密码或验证码登录时出现“当前登录存在安全风险”
- A: 网易云风控导致，请优先考虑使用二维码登录


- Q: QQ音乐无法查看个人歌单/无法播放
- A: 只能重新登录，目前无解决方法，每次开游戏都得重新登录

### 使用的开源项目

- [java-stream-player](https://github.com/goxr3plus/java-stream-player)
- [ZXing](https://github.com/zxing/zxing)
- [soundlibs](https://github.com/pdudits/soundlibs)
- [JustFlac](https://github.com/drogatkin/JustFLAC)
- [java-vorbis-support](https://github.com/Trilarion/java-vorbis-support)
- [jaudiotagger](https://github.com/marcoc1712/jaudiotagger)
- [Fabric](https://github.com/FabricMC/fabric)
- [Fabric Loader](https://github.com/FabricMC/fabric-loader)
- [Fabric API](https://github.com/FabricMC/fabric-api)
- [Yarn](https://github.com/FabricMC/yarn)
- [gradle](https://github.com/gradle/gradle)
