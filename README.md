# HomesPlugin

[English](#english) | [日本語](#japanese)

<a name="english"></a>
## English

**HomesPlugin** is a robust and user-friendly Home Management Plugin for Minecraft servers (Spigot/Paper).
This plugin allows players to set multiple homes, manage them via a GUI, share them publicly with others, and includes a full-featured TPA system.

### ✨ Features

*   **Home Management**:
    *   Set homes with `/sethome <name>`.
    *   Teleport to homes with `/home <name>`.
    *   Delete homes with `/delhome <name>`.
*   **GUI Interface**:
    *   Manage homes intuitively using a chest GUI via `/homes`.
    *   Visit other players' public homes using `/vhome <player>`.
*   **TPA System (Teleport Request)**:
    *   Send teleport requests to other players.
    *   Supports `/tpa` (Teleport to player) and `/tpahere` (Teleport player to you).
    *   Interactive chat buttons for **[Accept]** and **[Deny]**.
    *   Features include cooldowns, warmup time (5s), and movement cancellation.
*   **Back Command**:
    *   Return to your previous location or death point using `/back`.
*   **Economy Support**:
    *   Integration with Vault to charge for setting homes, teleporting, etc.
*   **Fully Configurable**:
    *   All messages and settings can be customized in `config.yml`.

### 📖 Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/sethome <name>` | Set a home at your current location. | — |
| `/delhome <name>` | Delete a specific home. | — |
| `/home <name>` | Teleport to a specific home. | — |
| `/homes` | Open your home management GUI. | — |
| `/homes reload` | Reload the plugin configuration. | `homes.reload` |
| `/vhome <player>` | Open another player's public home list. | — |
| `/tpa <player>` | Request to teleport to another player. | — |
| `/tpahere <player>` | Request another player to teleport to you. | — |
| `/tpaccept` | Accept a teleport request. | — |
| `/tpdeny` | Deny a teleport request. | — |
| `/tpcancel <player>` | Cancel a sent teleport request. | — |
| `/tpatoggle` | Toggle receiving teleport requests. | — |
| `/tpaignore <player>` | Ignore teleport requests from a specific player. | — |
| `/back` | Teleport to your previous location or death point. | — |

### ⚙️ Configuration

You can configure settings in `config.yml`.

```yaml
settings:
  language: ja           # Language file: lang/<language>.yml (ja / en bundled)

  # Base home limit applied to everyone (including OPs) without a limit permission.
  default-home-limit: 3
  # Override per player/group with LuckPerms (not in config):
  #   homes.limit.<number>  e.g. homes.limit.10 -> up to 10 homes
  #   homes.limit.unlimited -> no limit

  teleport:
    delay: 3             # Warmup in seconds (0 = instant)
    confirm-unsafe: true # Dangerous home: true = ask & teleport when player types "confirm", false = cancel
    safe-search:         # Advanced: range used to find a safe landing spot
      radius: 2          # Horizontal radius (blocks)
      vertical: 3        # Vertical range (blocks)

  tpa:
    enabled: true        # Enable TPA system
    cooldown: 60         # TPA cooldown in seconds
  back:
    enabled: true        # Enable /back command
  update-check:
    enabled: true        # Notify OPs when a Modrinth update is available
```

Per-player home limits are controlled by permissions (e.g. via LuckPerms): grant `homes.limit.<number>` or `homes.limit.unlimited`. Without one, the player uses `default-home-limit`.

### 📥 Installation

1.  Download the `HomesPlugin.jar`.
2.  Place it in your server's `plugins` folder.
3.  (Optional) Install Vault and an Economy plugin (like EssentialsX) for economy features.
4.  Restart your server.

### 👤 Developer

Developed by **naonao**.

---

<a name="japanese"></a>
## 日本語

**HomesPlugin** は、Minecraft Spigot/Paper サーバー向けの多機能ホーム管理 & テレポートプラグインです。
ホームの設定、GUIによる管理、経済連携、そしてプレイヤー間のテレポート（TPA）機能をこれ1つで提供します。

### ✨ 主な機能

*   **ホーム管理**:
    *   `/sethome <名前>` で現在地をホームに設定。
    *   `/home <名前>` で設定したホームへテレポート。
    *   `/delhome <名前>` でホームを削除。
*   **GUI操作**:
    *   `/homes` でGUIを開き、クリック操作でホーム一覧を確認・テレポート可能。
    *   他人の公開ホームへの訪問機能 (`/vhome`)。
*   **TPA (テレポートリクエスト)**:
    *   プレイヤー間でのテレポート申請・承認機能。
    *   `/tpa` (相手の場所へ行く) と `/tpahere` (相手を呼ぶ) に対応。
    *   チャットの【承認】【拒否】ボタンで簡単操作。
    *   クールダウン設定、テレポート前の待機時間（5秒）、移動キャンセル機能付き。
*   **Back機能**:
    *   `/back` でテレポート前の場所や死亡地点に戻ることが可能。
*   **経済連携**:
    *   Vaultプラグインと連携し、ホーム設定やテレポートにコストを設定可能。
*   **完全な日本語対応**:
    *   メッセージは `config.yml` ですべてカスタマイズ可能。

### 📖 コマンド一覧

| コマンド | 説明 | 権限 |
| --- | --- | --- |
| `/sethome <名前>` | 現在地をホームとして設定します。 | — |
| `/delhome <名前>` | 特定のホームを削除します。 | — |
| `/home <名前>` | 特定のホームにテレポートします。 | — |
| `/homes` | ホーム管理 GUI を開きます。 | — |
| `/homes reload` | プラグインの設定を再読み込みします。 | `homes.reload` |
| `/vhome <プレイヤー>` | 他のプレイヤーの公開ホームリストを開きます。 | — |
| `/tpa <プレイヤー>` | 相手に自分のテレポートリクエストを送ります（相手の場所へ行く）。 | — |
| `/tpahere <プレイヤー>` | 相手を自分の場所に呼ぶリクエストを送ります（カモン）。 | — |
| `/tpaccept` | 届いているリクエストを承認します。 | — |
| `/tpdeny` | 届いているリクエストを拒否します。 | — |
| `/tpcancel <プレイヤー>` | 送ったリクエストをキャンセルします。 | — |
| `/tpatoggle` | TPAの受信拒否設定を切り替えます。 | — |
| `/tpaignore <プレイヤー>` | 特定のプレイヤーからのTPAを無視します。 | — |
| `/back` | 直前の場所（または死亡地点）に戻ります。 | — |

### ⚙️ 設定 (config.yml)

```yaml
settings:
  language: ja           # 表示言語ファイル lang/<language>.yml (ja / en 同梱)

  # 全員共通の基本ホーム上限 (権限が無ければ OP もこの値)
  default-home-limit: 3
  # 個別に変えたい場合は LuckPerms 等で権限付与 (configでは設定しません):
  #   homes.limit.<数字>    例: homes.limit.10 → 最大10個
  #   homes.limit.unlimited → 無制限

  teleport:
    delay: 3             # テレポート待機秒数 (0で即時)
    confirm-unsafe: true # 危険な場所: true=確認してチャットconfirmで強行, false=中止
    safe-search:         # 【上級者向け】安全な着地点の探索範囲
      radius: 2          # 水平方向の半径(ブロック)
      vertical: 3        # 上下方向の範囲(ブロック)

  tpa:
    enabled: true        # TPA機能を有効にするか
    cooldown: 60         # TPAのクールダウン（秒）
  back:
    enabled: true        # Back機能を有効にするか
  update-check:
    enabled: true        # Modrinthの更新をOPに通知するか
```

### 📥 インストール

1.  `HomesPlugin.jar` をサーバーの `plugins` フォルダに配置します。
2.  サーバーを再起動します。
3.  必要に応じて `plugins/HomesPlugin/config.yml` を編集してください。

### 📦 ビルド方法

Mavenを使用しています。

```bash
mvn clean package
```

### 👤 開発者

**naonao** によって開発されました。

## 📜 ライセンス

MIT License
