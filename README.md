<div align="center">
  <img src="icon-large.png" alt="Concerto" width="256">
  <h1>Concerto</h1>
  <p><b>An in-game music player for Minecraft — Fabric / NeoForge client, Paper server plugin</b></p>
  <p>
    <a href="https://modrinth.com/mod/A0VZd1kW"><img src="https://img.shields.io/modrinth/dt/A0VZd1kW?logo=modrinth&label=Modrinth" alt="Modrinth Downloads"></a>
    <a href="https://github.com/GregTaoo/Concerto/releases"><img src="https://img.shields.io/github/v/release/GregTaoo/Concerto?logo=github&label=Release" alt="GitHub Release"></a>
    <img src="https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.20.6%20%7C%201.21.1%20%7C%201.21.6%20%7C%201.21.11%20%7C%2026.1.2%20%7C%2026.2-62b47a?logo=minecraft" alt="Minecraft 1.20.1, 1.20.6, 1.21.1, 1.21.6, 1.21.11, 26.1.2, and 26.2">
    <img src="https://img.shields.io/badge/Java-21+-f89820?logo=openjdk&logoColor=white" alt="Java 21+">
    <img src="https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge%20%7C%20Paper-8a2be2" alt="Fabric | NeoForge | Paper">
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue" alt="License"></a>
  </p>
  <p>English | <a href="README_zh.md">简体中文</a></p>
</div>

> Play local files, direct URLs, and music services – NetEase Cloud Music, QQ Music, and KuGou Music – without leaving the game. Listen together in shared music rooms, or let the whole server take turns choosing songs.

---

## ✨ Features

- **Many sources, one player** — NetEase Cloud Music, QQ Music, KuGou Music, local audio files (drag & drop), and direct HTTP links.
- **Smooth playback** – drag the progress bar to move through a track, keep listening while streams load, and choose an audio output option if the default does not suit your setup. Supported formats include MP3, OGG/Opus, FLAC, WAV, M4A/AAC, and AIFF.
- **Music rooms (listen together)** — members of a room share playback in real time: the same track, the same position, the same pauses. Rooms can be named, listed publicly, and joined from a browser screen or by UUID.
- **Server-wide queue (KTV mode)** — players queue songs for the whole server, vote to skip, and the server falls back to preset radios when the queue is empty.
- **Shared access & preset playlists** – server owners can configure their own supported music-service accounts for the server and share curated playlists with everyone.
- **Cross-platform** — Fabric and NeoForge on the client; a dedicated Paper plugin lets modded clients use every server feature on plugin servers.

---

## 📦 Installation

| Platform | What to install |
| --- | --- |
| **Fabric client** | `Concerto-mc<version>-fabric-<mod version>.jar` + [Fabric API](https://modrinth.com/mod/fabric-api) |
| **NeoForge client** | `Concerto-mc<version>-neoforge-<mod version>.jar` |
| **Paper server** | `Concerto-mc<version>-paper-<mod version>.jar` into `plugins/` |

Concerto stores its settings, playlists, downloads, and sign-in data in the `Concerto/` folder next to the game or server directory.

You can use Concerto as a local player in singleplayer and on servers that do not have it installed. Shared rooms, sharing, and the server-wide queue need Concerto on the server too (Fabric, NeoForge, or Paper).

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
Your starting point for playlists, music services (NetEase / QQ / KuGou), search, local files, links, music rooms, and settings.

### Playlist screen (`U`)
- **Drag & drop** audio files or whole folders from your file manager to import them.
- **Double-click** an entry to play it; single click selects.
- Bottom row: next / play / delete / order (normal, random, reversed, loop) / pause / info / clear.
- The **volume button** pops up a slider (click anywhere else to dismiss). Volume changes apply instantly and are saved to the config.
- The search box filters the current playlist.

### Playlist previews
- Select **Save to Local** to add the displayed playlist to **Local Playlists** immediately; it is also written as JSON under `Concerto/local_playlists/`.


### Player screen
- Drag the progress bar to **seek**; a preview of the target position is shown while dragging.
- Shows title, artist, source, cover art, and buffering state for streamed tracks.

### Music rooms screen
- **Discovery view**: lists the server's public (visible) rooms with one-click join, plus fields to create a named room, join by UUID, refresh, or enter the server queue.
- **Management view** (when you are in a room): member list, rename, toggle *visible* / *joinable*, grant co-op permission, and dissolve or leave the room.

### Options screen
Use the in-game options to adjust audio output, HUD visibility, scrolling-text speed, NetEase audio quality, and more. Audio output changes take effect from the next track.

---

## 📜 Commands

### Client player control – `/concerto` (alias `/music`)

The `/music` alias can be disabled with the `registerMusicCommand` client config option.

Most everyday actions are available from the screens above. Commands are optional shortcuts for players and server owners who prefer them.

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
1. In the server's Concerto settings, leave the server-wide queue enabled. You can also choose whether players receive an invitation when they join.
2. Players enter with `/musicroom agent join` and queue their current track with `/musicroom agent add` (rate-limited by `musicAgentAddTimeLimit`).
3. Anyone in the queue can start a skip vote: `/musicroom agent vote`, then others confirm with `/musicroom agent vote true` (or `false`).
4. When the queue is empty the server plays its preset radios, if configured.

### Share a music-service account with the server
1. Sign in to the music service on your own client.
2. Copy the `.cookie` files created in your client's `Concerto/` folder to the server's `Concerto/` folder. Treat these files like passwords.
3. In the server's Concerto settings, enable shared account access for the queue, then run `/concerto-server reload-cookie`.

### Ship preset playlists
Put exported playlist JSON files (see `/concerto export-as-playlist`) on the server under `Concerto/preset_radios/`; the special file `music_agent.json` is what KTV mode falls back to. Clients receive the radios automatically, or on demand via `/concerto-server fetch-radios`.

### Cache & downloads
Concerto only saves music when you ask it to: use `/concerto save` for the current track, or `download-current` / `download-all` for permanent copies with lyrics. The cache size can be adjusted in the settings.

---

## ⚙️ Configuration

You do not need to edit files for normal use: the in-game options screen covers the common choices. The reference below is for players and server owners who want more control. Settings files are in `Concerto/`; edit them only while the game is closed.

### `client_config.json`

**Playback & networking**

| Key | Default | Meaning |
| --- | --- | --- |
| `playerVolume` | `1.0` | Player volume, `0.0` – `1.0` |
| `playerVolumeFollowsMaster` | `true` | Multiply by Minecraft's master volume |
| `playbackBackend` | `"JAVASOUND"` | Audio backend: `"JAVASOUND"` or `"OPENAL"`. Changing it takes effect from the next track |
| `displayPlayerScreenCoverAndSpectrum` | `true` | Show cover art and the spectrum on the player screen; `false` uses the full-width lyrics view |
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

**How do I set up a music-service account on a server?**
Sign in on your own client first, then copy the `.cookie` files from the client's `Concerto/` folder to the server's. Run `/concerto-server reload-cookie` afterwards. Keep these files private: they grant access to your account.

**NetEase keeps erroring after a QR-code login.**
NetEase's risk control flags logins from new devices/locations aggressively. Skip QR/password login and paste the complete cookie of an existing, working browser session instead.

**QQ Music stops resolving after a few days.**
QQ Music cookies expire quickly by design. Refresh the cookie files on the server and run `/concerto-server reload-cookie`.

**Which audio output option should I pick?**
`JAVASOUND` is the recommended default. Try `OPENAL` if playback stutters or uses the wrong device. The change applies from the next track.

**Moving through a track is slow sometimes.**
Some streamed files and radio sources cannot jump directly to every position, so Concerto may need a moment to catch up. Moving within recently played audio is usually faster.

**Which local audio files can I play?**
Concerto supports MP3, OGG/Opus, FLAC, WAV, M4A/AAC, and AIFF. Support for unusual encodings inside those containers can vary, so converting a problematic file to MP3 or WAV is still a useful fallback.

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
