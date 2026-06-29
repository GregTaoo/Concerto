<div align="center">
  <img src="icon-large.png" alt="Concerto" width="256">
  <h1>Concerto</h1>
  <p><b>Built-in Minecraft Music Player for Fabric / NeoForge / Paper</b></p>
</div>

> Supports local music, online resources, and streaming platforms like NetEase Cloud Music, QQ Music, Kugou Music. Features a "Listen Together" function for multiplayer sessions.

---

## 🌟 Key Features

- **Multi-Platform Audio Parsing**: Supports NetEase Cloud Music, QQ Music, Kugou Music, as well as loading local files and direct online link playback.
- **Music Room (Listen Together)**: Join the same music room with other players on the server; playback progress, track switching, and pause operations are synchronized in real-time for all members.
- **Global On-Demand (KTV Mode)**: Servers can enable an on-demand feature where players can queue up songs. Supports initiating "skip song" votes to bypass music that doesn't align with the majority's preference.
- **VIP Sharing & Server Presets**: Server owners can configure their personal platform support to the server, sharing VIP song access with all players. Additionally, it supports uploading preset playlists that automatically play when there are no on-demand tasks.
- **Cross-Platform Compatibility**: Client supports Fabric and NeoForge, while the server supports a dedicated Paper plugin version, enabling cross-platform interoperability.

---

## 🛡️ Notes and Acknowledgements

- ⚠️ **Security Reminder**: Please keep your `.cookie` files in the `Concerto` folder secure and do not share them with others.
- ⚖️ **Agreement and Disclaimer**: This mod is for learning and exchange purposes only. It is strictly forbidden to use it for illegal purposes. Please read the [License](LICENSE) carefully before use. This mod is **not authorized for release on any paid or commercial platforms**. Those who violate this will bear legal responsibility. If there is any infringement, please contact us for removal.
- 🤝 **Special Thanks**: Kugou Music related code contributed by [ming-sc](https://github.com/ming-sc). Thanks to all [supporters](supporters.md).

> **Wiki Base Mod Version: `2.0.0`**

---

## ⌨️ Hotkeys

| Key | Function |
| :---: | --- |
| `U` | Open Playlist Management Interface |
| `I` | Open Concerto Main Menu |
| `N` | Play Next Song |
| `P` | Pause / Resume Playback |

---

## 📜 Command Reference

### 1. Client-Side Commands `/concerto` (Alias: `/music`)
| Command | Description |
| --- | --- |
| `pause` / `start` / `stop` | Pause, start, or stop playback |
| `clear` | Stop playback and clear the current playlist |
| `reload` / `restart` | Reload configuration and playlist data / Restart the player core (to fix freezes) |
| `save` | Cache the current song (only for supported sources like NetEase Cloud Music, etc.) |
| `like` / `dislike` | Like / Unlike the current song (NetEase Cloud Music only) |
| `download-current` | Download the current song and its lyrics to the local `Concerto` folder |
| `download-all` | Download all songs and lyrics in the current playback queue |
| `export-as-playlist` | Export the current cache queue as a preset playlist in the `JSON` format required by the server |
| `clean-cache` | Clear all cache and verification files (including cookies) in the client's `Concerto/cache` directory |

### 2. Music Room Commands `/musicroom` (Requires Concerto installed on the server)
| Command | Description |
| --- | --- |
| `create` | Create and join a dedicated music room; the corresponding UUID will be automatically copied upon success (subject to server permission restrictions) |
| `join [UUID]` / `quit` | Join a music room with the specified UUID / Leave the current music room or the on-demand lobby |
| `remove` / `members` | Disband the current music room / View the list of members in the current music room |
| `op [player]` | Grant or revoke management (skip song) privileges to a specified player (creator of the room only) |
| `agent join` / `quit` | Join or leave the server's public on-demand queue system |
| `agent add` | Push the currently playing music to the server's public on-demand queue |
| `agent vote [true/false]` | Initiate a collective skip song vote / Vote to approve or reject the current skip song vote |

### 3. P2P Music Sharing Commands `/sharemusic` (Direct network connection between clients)
| Command | Description |
| --- | --- |
| `to [player or @a]` | Share the currently playing content with a specific player. If the mod is not installed on the server, the command will fall back to the vanilla messaging channel to attempt delivery. Only whitelisted platform sources are supported. |
| `accept [UUID]` / `reject` | Accept / Reject a specific music sharing invitation (followed by UUID or type `all` to reject all requests) |
| `list [page number]` | View the list of pending music sharing invitations |

### 4. Server Management Commands `/concerto-server` (Admin use only)
| Command | Description |
| --- | --- |
| `audit [UUID]` / `reject` | Approve or reject music broadcasting share requests for the entire server (followed by `all` to reject all pending requests) |
| `audit list [page number]` | List the current queue of songs awaiting server administrator review for broadcast |
| `reload` / `reload-cookie` | Reload server configuration / Reload cookie files for each platform |
| `fetch-radios` | Synchronize preset playlists from the server to the local client (also usable by regular users) |
| `clean-cache` | Clear server cache data and cached cookie information |
| `agent reset` / `cut` | Completely reset the public on-demand queue / Force skip the current song for all players on the server |
| `agent stop` / `start` | Stop the music on-demand mode / Restart the on-demand mode |

---

## 📖 Advanced Usage Guide

### 🎧 Immersive Group "Listen Together" Music Rooms
- Members in the same music room will have their song skips, playback progress adjustments, and pause operations synchronized in real-time.
- It is recommended to manage management command permissions centrally to prevent frequent conflicts over skip song control by many players.
- By sending the UUID generated when creating a music room to other players, they can quickly join via the command.

### 🎵 Queue Management Interface
- Pressing the `U` key will bring up a detailed playback queue management panel, which supports dragging and dropping external audio files and folders from the file explorer for import.
- The panel fully supports double-clicking for quick song switching and allows you to select a target song to initiate a "push to on-demand" request to the server.
- Playlist configuration files generated via the export command will be stored by default in `Concerto/local_playlists`, facilitating subsequent cross-device synchronization management.

### 📡 Comprehensive On-Demand Mode and Preset Configuration
- When the global server on-demand mode is enabled, if a player-submitted song in the queue is not to your liking, you can initiate a vote to skip it at any time via the command.
- **Server Preset Playlists**: Server administrators can configure organized playlist files into `Concerto/preset_radios/music_agent.json` on the server. This way, when no one is requesting songs, the server can automatically play preset music.
- **VIP Library Streaming Mechanism**: Enable `musicAgentUseShared` in the server's `server_config.json` and deploy the `.cookie` files of platforms where the administrator has VIP privileges. Then, all server members can directly request VIP songs through the server.

### 📨 Peer-to-Peer Music Sharing Between Players
- When there is a need to share the currently playing music with a specific player, you can use the `/sharemusic` command. Due to security mechanisms, some local music that directly reads paths may be flagged as unsafe and cannot be shared.

---

## ⚙️ Configuration File Parameter Explanations

### Server (`server_config.json`)
- `auditionRequired`: Whether music broadcast sharing to the entire server requires administrator approval (default `true`).
- `serverMusicAgent`: Whether to enable the server's global song on-demand feature (default `true`).
- `agentInviteWhenJoin`: Whether to automatically send an invitation to the on-demand lobby when a player joins the server (default `true`).
- `musicRoomCommandPermission`: The minimum permission level required for a player to create a music room (default `2`).
- `musicAgentAddTimeLimit`: The minimum interval time between two on-demand song submissions by a player (default `60` seconds).
- `musicAgentUseShared`: Whether to force the server to fetch and share direct links when using global on-demand (default `true`).
- `kuGouMusicLite`: Whether to use the lite version channel when parsing Kugou Music resources (default `false`).

### Client (`client_config.json`)
**【 Core Controls 】**
- `confirmAfterReceived`: Whether to automatically accept music shares received from others (default enabled).
- `joinAgentWhenInvited`: Whether to automatically join the global on-demand invitation from the server (default disabled).
- `neteaseMusicQuality`: Global requested NetEase Cloud Music audio quality level (options: `"STANDARD"`, `"HIGHER"`, `"EXHIGH"`, `"LOSSLESS"`, default `"HIRES"`).
- `handshakeRequired`: Whether to force a connection verification handshake when connecting to the server (default enabled).
- `maxCacheSize`: The maximum space allowed for music and image caching (default `100MB`).
- `hideWhenChat`: Whether to automatically hide on-screen player information when the player opens the chat box (default enabled).

**【 HUD Display Layout 】**
All on-screen notification elements (lyrics, progress, cover art, etc.) can be enabled or disabled by modifying the configuration file, e.g., `displayLyrics: true/false`.
- **Coordinate Specification**: Use `[horizontal proportion](+/-)[pixel offset],[vertical proportion](+/-)[pixel offset]`. For example, `0.5,1-70` means center-aligned and 70 pixels from the bottom; `1-30,0+15` means 30 pixels from the right edge and 15 pixels from the top edge.
- **Color Specification**: Text and progress bar colors use ARGB hexadecimal codes. Format: `#ffffffff` (fully opaque pure white), `#ff00aaaa` (fully opaque cyan).

---

## ❓ Common Troubleshooting and FAQs

#### **1. How to correctly configure the server's Cookie data?**
Please log in locally and copy the cookie folder to the server.

#### **2. NetEase Cloud Music fails to work properly and gives continuous errors after logging in with a QR code?**
NetEase Cloud Music has strict security risk control policies. Frequent high-frequency requests from different devices in different locations can cause the environment to be flagged and abnormal logins to be intercepted. It is recommended that you abandon password or QR code login methods and directly copy the complete web Cookie information from your current successful login to bypass environmental detection.

#### **3. QQ Music information updates fail or loading errors occur.**
Due to the platform's API mechanism, QQ Music authorization has a strict lifespan. Some data may expire and become invalid after a few days. If such errors affect normal on-demand playback, the server owner should promptly update the relevant configuration files on the server and execute `/concerto-server reload-cookie` to refresh.

#### **4. Incomplete loading interface and rendering anomalies in specific HUD components.**
This is likely caused by corrupted display cache. First, please use the `/concerto clean-cache` command to clear the client cache. If the issue persists and the interface is still black, please completely close the game, manually navigate to `.minecraft/Concerto/cache`, delete all related files, and then restart the game to regenerate them.

---

## 🚀 Acknowledgements for Open Source Projects Used
*[java-stream-player](https://github.com/goxr3plus/java-stream-player)* | *[ZXing](https://github.com/zxing/zxing)* | *[soundlibs](https://github.com/pdudits/soundlibs)* | *[JustFlac](https://github.com/drogatkin/JustFLAC)* | *[java-vorbis-support](https://github.com/Trilarion/java-vorbis-support)* | *[jaudiotagger](https://github.com/marcoc1712/jaudiotagger)* | *[Fabric API ](https://github.com/FabricMC)*
