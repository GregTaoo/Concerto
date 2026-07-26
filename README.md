<div align="center">
  <img src="icon-large.png" alt="Concerto" width="256">
  <h1>Concerto</h1>
  <p><b>An in-game music player for Minecraft — Fabric / NeoForge client, Paper server plugin</b></p>
  <p>
    <a href="https://modrinth.com/mod/A0VZd1kW"><img src="https://img.shields.io/modrinth/dt/A0VZd1kW?logo=modrinth&label=Modrinth" alt="Modrinth Downloads"></a>
    <a href="https://github.com/GregTaoo/Concerto/releases"><img src="https://img.shields.io/github/v/release/GregTaoo/Concerto?logo=github&label=Release" alt="GitHub Release"></a>
    <img src="https://img.shields.io/badge/Minecraft-1.21.6%20--%201.21.9-62b47a?logo=minecraft" alt="Minecraft 1.21.6 - 1.21.9">
    <img src="https://img.shields.io/badge/Java-21+-f89820?logo=openjdk&logoColor=white" alt="Java 21+">
    <img src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge%20%7C%20Paper-8a2be2" alt="Fabric | NeoForge | Paper">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"></a>
  </p>
</div>

> Play local files, direct URLs, and streaming platforms — NetEase Cloud Music, QQ Music, KuGou Music — without leaving the game. Listen together with other players in synchronized music rooms, or let the whole server queue songs KTV-style.

> **This README documents the `dev` branch (Minecraft 1.21.6 – 1.21.9, mod version 2.1.0).** Builds for other Minecraft versions live on their own `mc*` branches and may differ in detail.

---

## ✨ Features

- **Many sources, one player** — NetEase Cloud Music, QQ Music, KuGou Music, local audio files (drag & drop), and direct HTTP links.
- **Rebuilt playback engine** — precise seeking by dragging the progress bar, progressive streaming with resume, and a choice of two audio backends (JavaSound or OpenAL). Supported formats: MP3, OGG, FLAC, WAV.
- **Music rooms (listen together)** — members of a room share playback in real time: the same track, the same position, the same pauses. Rooms can be named, listed publicly, and joined from a browser screen or by UUID.
- **Server-wide queue (KTV mode)** — players queue songs for the whole server, vote to skip, and the server falls back to preset radios when the queue is empty.
- **VIP sharing & preset playlists** — a server owner can deploy platform cookies so every player streams through the server's account, and can ship curated playlists to all clients.
- **Cross-platform** — Fabric and NeoForge on the client; a dedicated Paper plugin lets modded clients use every server feature on plugin servers.

---

## 📦 Installation

| Platform | What to install |
| --- | --- |
| **Fabric client** | `Concerto-mc<version>-fabric-<mod version>.jar` + [Fabric API](https://modrinth.com/mod/fabric-api) |
| **NeoForge client** | `Concerto-mc<version>-neoforge-<mod version>.jar` |
| **Paper server** | `Concerto-mc<version>-paper-<mod version>.jar` into `plugins/` |

All runtime data (configs, cookies, caches, playlists) lives in the `Concerto/` folder next to the game or server directory.

The mod is fully usable in singleplayer / on vanilla servers; server-side features (music rooms, sharing, the server queue) additionally require Concerto on the server (Fabric, NeoForge, or Paper).

---

## ⌨️ Hotkeys

All hotkeys can be rebound in **Options → Controls**.

| Default key | Action |
| :---: | --- |
| `I` | Open the Concerto index (main menu) |
| `U` | Open the playlist screen |
| `N` | Play the next track |
| `P` | Pause / resume |

---

## 🖱️ Screens & Controls

### Index screen (`I`)
The hub for everything: playlist, platform browsers (NetEase / QQ / KuGou), search, adding music by URL or path, local playlists, preset radios, music rooms, audition queue, and options.

### Playlist screen (`U`)
- **Drag & drop** audio files or whole folders from your file manager to import them.
- **Double-click** an entry to play it; single click selects.
- Bottom row: next / play / delete / order (normal, random, reversed, loop) / pause / info / clear.
- The **volume button** pops up a slider (click anywhere else to dismiss). Volume changes apply instantly and are saved to the config.
- The search box filters the current playlist.

### Player screen
- Drag the progress bar to **seek**; a preview of the target position is shown while dragging.
- Shows title, artist, source, cover art, and buffering state for streamed tracks.

### Music rooms screen
- **Discovery view**: lists the server's public (visible) rooms with one-click join, plus fields to create a named room, join by UUID, refresh, or enter the server queue.
- **Management view** (when you are in a room): member list, rename, toggle *visible* / *joinable*, grant co-op permission, and dissolve or leave the room.

### Options screen
In-game options cover playback volume (with "follow master volume"), the **OpenAL output** toggle (takes effect from the next track), HUD element visibility, scrolling text speed, NetEase audio quality, and more. Everything maps 1:1 to `client_config.json` (see below).

---

## 📜 Commands

### Client player control — `/concerto` (alias `/music`)

The `/music` alias can be disabled with the `registerMusicCommand` client config option.

| Command | Description |
| --- | --- |
| `/concerto pause` | Toggle pause / resume |
| `/concerto start` | Start the player if it is not running |
| `/concerto stop` | Stop playback |
| `/concerto clear` | Stop playback and clear the playlist |
| `/concerto restart` | Restart the player core (recovers from a stuck state) |
| `/concerto reload` | Reload configs, local playlists, and restart the player |
| `/concerto save` | Cache the current track locally (cacheable sources only) |
| `/concerto like` / `dislike` | Like / unlike the current track (NetEase Cloud Music only) |
| `/concerto download-current` | Download the current track and its lyrics to `Concerto/` |
| `/concerto download-all` | Download every track in the current playlist |
| `/concerto export-as-playlist` | Export the current playlist as a JSON preset playlist (`Concerto/local_playlists`) |
| `/concerto clean-cache` | Wipe the client cache (music, images, verification files) |

### Music rooms & server queue — `/musicroom` (requires Concerto on the server)

| Command | Description |
| --- | --- |
| `/musicroom create [name]` | Create (optionally named) and join a room; the room UUID is copied to your clipboard. Gated by the server's `musicRoomCommandPermission` |
| `/musicroom join <uuid>` | Join a room by UUID |
| `/musicroom quit` | Leave the current room or the server queue |
| `/musicroom remove` | Dissolve the room you own |
| `/musicroom members` | Show the room owner and member list |
| `/musicroom op <player>` | Toggle a member's playback-control permission (owner only) |
| `/musicroom agent join` / `quit` | Enter / leave the server-wide queue (KTV mode) |
| `/musicroom agent add` | Push your currently playing track onto the server queue |
| `/musicroom agent vote` | Start a skip vote for the current queued track |
| `/musicroom agent vote <true\|false>` | Cast your vote in the running skip vote |

### Player-to-player sharing — `/sharemusic`

| Command | Description |
| --- | --- |
| `/sharemusic to <player \| @a>` | Share the current track with one player, or with everyone (`@a` goes through the server audit queue when audition is enabled). Sources that expose local file paths are refused as unsafe |
| `/sharemusic accept <uuid>` | Accept a share invitation |
| `/sharemusic reject <uuid>` | Reject a share invitation |
| `/sharemusic reject all` | Reject all pending invitations |
| `/sharemusic list <page>` | List pending invitations (clickable accept / reject buttons) |

### Server administration — `/concerto-server`

All subcommands require permission level 2 (OP) except `fetch-radios`, which everyone may use.

| Command | Description |
| --- | --- |
| `/concerto-server audit <uuid>` | Approve a pending `@a` broadcast request |
| `/concerto-server audit reject <uuid>` | Reject a pending broadcast request |
| `/concerto-server audit reject all` | Reject every pending request |
| `/concerto-server audit list <page>` | List the audit queue (clickable accept / reject buttons) |
| `/concerto-server reload` | Reload the server config, preset radios, and platform cookies |
| `/concerto-server reload-cookie` | Reload only the platform cookie files |
| `/concerto-server clean-cache` | Wipe the server-side cache |
| `/concerto-server fetch-radios` | Send the server's preset radios to your client |
| `/concerto-server agent reset` | Reset the server queue completely |
| `/concerto-server agent cut` | Force-skip the current queued track |
| `/concerto-server agent stop` / `start` | Stop / restart KTV mode |

---

## 📖 How-to

### Listen together in a music room
1. `/musicroom create My Room` (or use the music rooms screen) — the UUID lands in your clipboard.
2. Hand the UUID to friends (`/musicroom join <uuid>`), or mark the room *visible* + *joinable* so it appears in everyone's room browser.
3. Whatever the controlling members play, seek, or pause is mirrored to every member in real time. Use `/musicroom op` to decide who may control playback.

### Run a server-wide queue (KTV mode)
1. Keep `serverMusicAgent` enabled in `server_config.json`; players are invited on join when `agentInviteWhenJoin` is on.
2. Players enter with `/musicroom agent join` and queue their current track with `/musicroom agent add` (rate-limited by `musicAgentAddTimeLimit`).
3. Anyone in the queue can start a skip vote: `/musicroom agent vote`, then others confirm with `/musicroom agent vote true` (or `false`).
4. When the queue is empty the server plays its preset radios, if configured.

### Share your VIP account with the server
1. Log into the platform on your own client (QR code or cookie paste in the platform's login screen).
2. Copy the resulting `.cookie` files from your client's `Concerto/` folder into the server's `Concerto/` folder.
3. Enable `musicAgentUseShared` so queued tracks are resolved through the server's account, then `/concerto-server reload-cookie`.

### Ship preset playlists
Put exported playlist JSON files (see `/concerto export-as-playlist`) on the server under `Concerto/preset_radios/`; the special file `music_agent.json` is what KTV mode falls back to. Clients receive the radios automatically, or on demand via `/concerto-server fetch-radios`.

### Cache & downloads
Playback itself never writes to the music cache — caching is always explicit: `/concerto save` for the current track, `download-current` / `download-all` for permanent copies with lyrics. Cache size is bounded by `maxCacheSize`.

---

## ⚙️ Configuration

Config files are JSON, live in `Concerto/`, and are rewritten from the parsed values on every start — edit them while the game is closed, or use the in-game options screen.

### `client_config.json`

**Playback & networking**

| Key | Default | Meaning |
| --- | --- | --- |
| `playerVolume` | `1.0` | Player volume, `0.0` – `1.0` |
| `playerVolumeFollowsMaster` | `true` | Multiply by Minecraft's master volume |
| `playbackBackend` | `"JAVASOUND"` | Audio backend: `"JAVASOUND"` or `"OPENAL"`. Changing it takes effect from the next track |
| `neteaseMusicQuality` | `"HIRES"` | NetEase quality: `"STANDARD"`, `"HIGHER"`, `"EXHIGH"`, `"LOSSLESS"`, `"HIRES"` |
| `maxCacheSize` | `100000000` | Cache budget in bytes (music and images each) |
| `handshakeRequired` | `true` | Verify the Concerto handshake before using server features |
| `confirmAfterReceived` | `true` | Ask before accepting an incoming share (`false` auto-accepts) |
| `joinAgentWhenInvited` | `false` | Auto-accept the server's KTV invitation on join |
| `registerMusicCommand` | `true` | Register the `/music` alias |
| `printRequestResults` | `false` | Log platform API responses (debugging) |
| `kuGouMusicLite` | `false` | Use the KuGou "concept/lite" endpoints |
| `autoGetKuGouDailyVIP` | `false` | Claim KuGou's daily VIP automatically |

**HUD**

Each HUD element has a `display*` switch, a position, an alignment (`LEFT` / `CENTER` / `RIGHT`), and ARGB colors:

| Element | Keys |
| --- | --- |
| Lyrics | `displayLyrics`, `lyricsPosition` (default `0.5,1-100`), `lyricsAlignment`, `lyricsColor` |
| Sub-lyrics (translation) | `displaySubLyrics`, `subLyricsPosition` (default `0.5,1-89`), `subLyricsAlignment`, `subLyricsColor` |
| Track details | `displayMusicDetails`, `musicDetailsPosition` (default `1-30,0+5`), `musicDetailsAlignment`, `musicDetailsColor` |
| Time & progress bar | `displayTimeProgress`, `timeProgressPosition` (default `1-30,0+15`), `timeProgressAlignment`, `timeProgressTextColor`, `timeProgressColor`, `timeProgressBgColor` |
| Cover art | `displayCoverImg`, `coverImgPosition` (default `1-25,0`), `coverImgSize`, `coverImgInCircle`, `coverImgRotate` |
| Misc | `textShadow`, `hideWhenChat`, `scrollingTextSpeed` |

- **Position format**: `<x fraction>±<pixels>,<y fraction>±<pixels>`. `0.5,1-100` = horizontally centered, 100 px above the bottom edge; `1-30,0+15` = 30 px from the right, 15 px from the top.
- **Color format**: `#AARRGGBB`, e.g. `#ffffffff` opaque white, `#ff00aaaa` opaque teal.

### `server_config.json`

| Key | Default | Meaning |
| --- | --- | --- |
| `auditionRequired` | `true` | `@a` broadcasts need OP approval via `/concerto-server audit` |
| `serverMusicAgent` | `true` | Enable the server-wide queue (KTV mode) |
| `agentInviteWhenJoin` | `true` | Invite players to the queue when they join |
| `musicRoomCommandPermission` | `2` | Minimum permission level to create a music room |
| `musicAgentAddTimeLimit` | `60` | Seconds a player must wait between queue submissions |
| `musicAgentUseShared` | `true` | Resolve queued tracks through the server's own cookies |
| `kuGouMusicLite` | `false` | Use the KuGou lite endpoints server-side (also pushed to clients) |

---

## ❓ FAQ

**How do I set up cookies on a server?**
Log in on your own client first, then copy the `.cookie` files from the client's `Concerto/` folder to the server's. Run `/concerto-server reload-cookie` afterwards. Keep these files private — they are full account credentials.

**NetEase keeps erroring after a QR-code login.**
NetEase's risk control flags logins from new devices/locations aggressively. Skip QR/password login and paste the complete cookie of an existing, working browser session instead.

**QQ Music stops resolving after a few days.**
QQ Music cookies expire quickly by design. Refresh the cookie files on the server and run `/concerto-server reload-cookie`.

**Which audio backend should I pick?**
`JAVASOUND` is the safe default. `OPENAL` routes audio through the game's own sound engine (LWJGL OpenAL) — try it if JavaSound stutters or picks the wrong output device. The switch applies from the next track.

**Seeking is slow on some tracks.**
Streams without an embedded seek table (most FLAC streams, some radio sources) have to decode from the beginning of the file to reach the target position. Nearby backward seeks and all forward seeks within the buffer are fast.

**Why won't my M4A/AAC files play?**
M4A/AAC has no decoder in the current engine and is intentionally unsupported. Convert to MP3, OGG, FLAC, or WAV.

**The player is stuck / silent after an error.**
`/concerto restart` resets the player core without touching your playlist. If screens render wrongly or covers are black, `/concerto clean-cache`, and as a last resort delete `Concerto/cache` while the game is closed.

**Room / sharing commands say the feature is unavailable.**
Those features need Concerto on the server (Fabric, NeoForge, or the Paper plugin). On vanilla servers the mod still works as a purely local player.

**My volume resets or ignores the slider.**
If `playerVolumeFollowsMaster` is on, the effective volume is `playerVolume × master volume` — check Minecraft's own Music & Sounds settings too.

---

## 🛡️ License & Notes

- ⚠️ Keep the `.cookie` files in `Concerto/` private — never share or commit them.
- ⚖️ This project is for learning and personal use. See the [License](LICENSE) before use. **Re-publishing on paid or commercial platforms is not authorized.**
- 🤝 KuGou Music support contributed by [ming-sc](https://github.com/ming-sc). Thanks to all [supporters](supporters.md).

## 🚀 Built On

[soundlibs (mp3spi / tritonus)](https://github.com/pdudits/soundlibs) ·
[JustFLAC](https://github.com/drogatkin/JustFLAC) ·
[java-vorbis-support](https://github.com/Trilarion/java-vorbis-support) ·
[jaudiotagger](https://github.com/marcoc1712/jaudiotagger) ·
[QR Code generator (Project Nayuki)](https://www.nayuki.io/page/qr-code-generator-library) ·
[Gson](https://github.com/google/gson) ·
[Fabric API](https://github.com/FabricMC/fabric)
