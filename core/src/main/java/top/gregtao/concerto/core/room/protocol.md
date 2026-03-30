# Concerto Protocol

前两者需自行实现 Bridge，可参考 ServerMusicAgentManager 以及 MusicRoomManager
其他指令的注册可通过不同平台提供的命令注册机制实现，例如 Minecraft 的 CommandDispatcher，或其他命令系统。

## 1. ServerMusicAgent.Command

这些命令用于客户端与服务端的点歌机代理（Music Agent）进行通信。

| Command 枚举  | Payload 格式     | 语义（Semantics）                                                                         |
|-------------|----------------|---------------------------------------------------------------------------------------|
| `NEW_VOTE`  | `""` (空字符串)    | **发起切歌投票**：请求服务端点歌机发起一个新的“切歌”投票。如果当前没有播放或者正在投票中，服务端会拒绝。                               |
| `VOTE`      | `"1"` 或 `"0"`  | **参与投票**：对当前的切歌请求进行投票。`"1"` 代表同意 (Yes)，`"0"` 代表反对 (No)。                               |
| `ADD_MUSIC` | 序列化后的 JSON 字符串 | **添加歌曲**：将具体的歌曲对象（`Music`）转换为 JSON 字符串（通过 `MusicJsonParsers.to(music)`），发送给点歌机加入播放队列。 |

## 2. MusicRoom.Command

这些命令用于客户端与服务端的音乐房间（Music Room）基础功能进行通信及状态同步。

| Command 枚举 | Payload 格式                                         | 语义（Semantics）                                                       |
|------------|----------------------------------------------------|---------------------------------------------------------------------|
| `CREATE`   | `""` (空字符串)                                        | **创建房间**：请求服务端创建一个新的音乐房间，服务端会将创建者设为房主。                              |
| `JOIN`     | 目标房间的 UUID 字符串，例如 `"123e4567-e89b-12d3..."`        | **加入房间**：请求加入一个指定 UUID 的音乐房间。                                       |
| `REMOVE`   | 房间的 UUID 字符串，或空字符串                                 | **解散房间**：由达到权限（房主）的玩家发起，强制解散该房间，服务端会通知所有成员退出。                       |
| `QUIT`     | 房间的 UUID 字符串                                       | **退出房间**：请求主动离开当前所在的音乐房间。如果房主发起则会自动退化为 `REMOVE`。                    |
| `SYNC`     | JSON 补丁（Patch）字符串，例如 `{"members": {"playerA": 2}}` | **状态同步**：用于在客户端和服务端双向同步 `MusicRoomState` 状态的变化，只有权限级别的玩家允许发送同步到服务端。 |
| `SET_OP`   | 目标玩家的名称（String），例如 `"Steve"`                       | **设置/取消管理员**：给房间内的某个成员提升至管理员权限（权限级别变为 2），或降级。                       |

## 3. 其他指令注册

建议实现如下的服务器命令注册，以便管理员能够直接通过服务器命令控制点歌机：

```
agent reset: ServerMusicAgent.INSTANCE.reset();
cut: ServerMusicAgent.INSTANCE.schedulePlayNext(0, false);
stop: ServerMusicAgent.INSTANCE.stop();
start: ServerMusicAgent.INSTANCE.start();
```