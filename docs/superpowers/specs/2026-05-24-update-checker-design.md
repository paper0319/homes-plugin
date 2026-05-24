# Update Checker 設計仕様

- 日付: 2026-05-24
- 対象機能: Modrinth で公開された新バージョンを検出し、OP のログイン時に通知する
- 関連ファイル: `HomesPlugin.java`, `config.yml`

## 1. 目的・背景

サーバー運営者 (OP) が、プラグインの更新があることに気づかないままになるのを防ぐ。Modrinth に新バージョンが公開されたら、サーバー起動時に 1 回だけチェックし、ログインした OP に通知する。

## 2. 要件サマリ

- サーバー起動時に Modrinth API を 1 回非同期で叩いて最新バージョンを取得
- pom.xml の現バージョンと比較し、最新が新しければ「更新あり」状態をメモリに保持
- 以降、`PlayerJoinEvent` で `isOp()` の人が入ってきたら 4 行のチャット通知を表示
- 通知済み管理はしない（OP 毎回表示）
- API エラー時は `getLogger().warning(...)` のみ、OP には何も出さない
- `settings.update-check.enabled = false` で全機能スキップ

## 3. アーキテクチャ・コンポーネント

### 新規クラス
- `com.example.homes.manager.UpdateChecker`
  - `Listener` を実装
  - `HomesPlugin.onEnable()` で生成・`registerEvents`・`checkAsync()` を呼ぶ
  - 内部状態: `volatile String latestVersion`, `volatile boolean updateAvailable`
  - `PlayerJoinEvent` ハンドラで OP に通知

### 既存クラスの変更
- `HomesPlugin`
  - フィールド `updateChecker` 追加
  - `onEnable()` で初期化・登録・`checkAsync()` 呼び出し（`settings.update-check.enabled` が true のとき）

## 4. データフロー

```
HomesPlugin.onEnable()
  └─ if (settings.update-check.enabled)
       └─ updateChecker = new UpdateChecker(this)
          getServer().getPluginManager().registerEvents(updateChecker, this)
          updateChecker.checkAsync()
               │
               │ Bukkit.getScheduler().runTaskAsynchronously
               ▼
        HTTP GET https://api.modrinth.com/v2/project/BmLjlw32/version
            ?loaders=["paper"]&game_versions=["1.21","1.21.1",...]
        Headers:
          User-Agent: naonao0319/homes-plugin (<currentVersion>)
          Accept: application/json
          Timeout: 10 秒
               │
               ├─ 200 OK & JSON parse 成功
               │     最初の要素 (Modrinth は新着順) の version_number を取得
               │     現在の plugin.getDescription().getVersion() と比較
               │     新しければ latestVersion = X, updateAvailable = true
               │
               └─ 失敗 (network / status >= 400 / JSON error)
                     getLogger().warning("Update check failed: ...")
                     updateAvailable は false のまま

PlayerJoinEvent
  └─ if (updateAvailable && player.isOp())
       4 行のメッセージ (Adventure Components) を送信
```

## 5. メッセージレイアウト

OP に表示する 4 行（Adventure Components）：

```
[homes] 更新があります！
現在のバージョン 1.10.1
新しいバージョン 1.11.0
【Modrinthで表示】       ← クリックで https://modrinth.com/plugin/BmLjlw32/version/{latestVersion}
```

3 行目の `【Modrinthで表示】` は `ClickEvent.openUrl(...)` を付与。ホバーで「Modrinth で開く」と表示。

## 6. バージョン比較ロジック

`compareSemver(String current, String latest) -> int`

- ピリオドで分割し、各セグメントを `Integer.parseInt` で比較
- セグメント数が違う場合、短い方を 0 でパディング (`1.10` を `1.10.0` 扱い)
- 数値パース失敗 → 例外を上層で catch → "比較不能" として更新通知しない（log warning）

例:
- `compareSemver("1.10.1", "1.11.0")` → -1 (新版あり)
- `compareSemver("1.10.1", "1.10.1")` →  0 (同じ)
- `compareSemver("1.10.1", "1.10.0")` → +1 (現行の方が新しい、通知なし)

## 7. 設定キー (config.yml)

```yaml
settings:
  # ... 既存 ...
  update-check:
    enabled: true
```

```yaml
messages:
  # ... 既存 ...
  update-available-header: "&6[homes] &a更新があります！"
  update-available-current: "&7現在のバージョン &f{current}"
  update-available-latest: "&7新しいバージョン &e{latest}"
  update-available-link: "&b【Modrinthで表示】"
  update-available-link-hover: "&7Modrinth で開く"
```

## 8. エラーハンドリング・エッジケース

- **`settings.update-check.enabled = false`**: チェック自体を行わない、リスナーも登録しない
- **HTTP 接続失敗 / timeout (10秒)**: `Logger.warning` のみ
- **HTTP 4xx/5xx**: `Logger.warning` のみ
- **JSON パース失敗**: 同上
- **API レスポンスが空配列** (Paper/1.21.x に該当するバージョンがない): `Logger.info` のみ、通知なし
- **バージョン文字列が semver でない** (例: `1.10.0-SNAPSHOT`): SNAPSHOT 等の suffix を切り捨ててから比較。失敗したら warning ログのみ
- **現行 ≧ 最新**: 通知しない
- **API レスポンスに `version_number` キーが無い**: warning ログのみ
- **`Player.isOp()` が false**: 通知しない（普通のプレイヤーには出さない）

## 9. 実装範囲外 (YAGNI)

- 定期再チェック（cron 的なもの）
- 通知済み OP フラグ（毎回表示で OK）
- バージョン情報の永続化（再起動でリセット）
- アップデート自動ダウンロード
- ベータ / pre-release の通知

## 10. テスト計画（手動）

Paper サーバーで以下を確認:

- [ ] 起動時に Modrinth API が呼ばれログに警告は出ない（成功時無音）
- [ ] 現バージョンと Modrinth 最新が一致 → OP ログイン時に通知が出ない
- [ ] `pom.xml` を一時的に古い値（例 `1.0.0`）に変更してビルド → OP ログイン時に 4 行通知 + リンクが出る
- [ ] リンクをクリックして Modrinth のページが開く
- [ ] OP でないプレイヤー → 通知なし
- [ ] `settings.update-check.enabled: false` → 起動時に API 呼ばない、OP に通知出ない
- [ ] ネット切断状態で起動 → warning ログのみ、OP には通知無し
