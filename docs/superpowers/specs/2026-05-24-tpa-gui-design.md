# TPA GUI 設計仕様

- 日付: 2026-05-24
- 対象機能: `/tpa` および `/tpahere` を引数なしで実行したときに開く GUI
- 関連ファイル: `HomesPlugin.java`, `TpaManager.java`, `gui/HomeGUI.java`, `gui/ConfirmGUI.java`, `config.yml`

## 1. 目的・背景

現状、`/tpa` を引数なしで実行すると「使用法: /tpa <プレイヤー名>」というメッセージのみ返る。これをチェスト GUI に置き換え、オンラインプレイヤーの頭をクリックして TPA / TPAHere を視覚的に選べるようにする。

## 2. 要件サマリ

- `/tpa` と `/tpahere` を引数なしで実行 → 同一の GUI を開く（引数ありの従来動作は変更しない）
- 表示対象: 自分以外のオンラインプレイヤー全員（TPA 拒否中の人も含む。送信時に既存ロジックが弾く）
- メイン GUI はラージサイズ (54 スロット)
- 外枠を青の板ガラス (`BLUE_STAINED_GLASS_PANE`) で囲む
- 一番下の中央 (スロット 49) に「更新」ボタン
- 一番下の右 (スロット 53) に「次へ」ボタン（次ページがあるときのみ）
- 一番下の左 (スロット 45) に「前へ」ボタン（前ページがあるときのみ）
- 頭クリック → 27 スロットのサブメニューを開く
- サブメニュー: TPAHere (11) / 対象プレイヤーの頭 (13) / TPA (15) / 戻る (22)
- サブメニューも青ガラスで枠を描く
- 自分の頭は一覧に出ない

## 3. アーキテクチャ・コンポーネント

### 新規クラス
- `com.example.homes.gui.TpaGUI` — オンライン一覧 GUI
- `com.example.homes.gui.TpaActionGUI` — TPA/TPAHere 選択サブメニュー GUI

### `HomeGUI` 流儀に従う
- どちらも `Listener` を実装し、`HomesPlugin.onEnable()` で生成後 `registerEvents` で常時登録
- 既存の `ConfirmGUI` 方式（一回限り登録）は使わない

### 状態管理
- `TpaGUI`: 各プレイヤーの現在ページを `Map<UUID viewer, Integer page>` でフィールド保持
- `TpaActionGUI`: 現在選択中の対象を `Map<UUID viewer, UUID target>` でフィールド保持
- プレイヤーがログアウトしたときのクリーンアップは `PlayerQuitEvent` で行う（両クラスで listener として処理）

### 既存クラスの変更
- `HomesPlugin`
  - `tpaGUI`, `tpaActionGUI` フィールド追加
  - `onEnable()` で初期化と `registerEvents`
  - `onCommand` の `/tpa` および `/tpahere` 分岐で `args.length == 0` のときは `tpaGUI.open(player)` を呼ぶ
  - `/tpa` `/tpahere` の `return false;` (使用法メッセージ) は撤去
- `TpaManager`: 変更不要（既存の `sendRequest` をそのまま使う）

## 4. データフロー

```
/tpa (no args) ────┐
                   ├──► TpaGUI.open(viewer)
/tpahere (no args)─┘         │
                             │ Bukkit.getOnlinePlayers() から自分を除外
                             │ ページ分割 (28人/ページ)
                             │ チェスト 54 スロットを表示
                             ▼
                       プレイヤー頭クリック
                             │
                             ▼
                  TpaActionGUI.open(viewer, target)
                             │ targetUuid を保存
                             ▼
              ┌──────────────┼────────────────┐
            TPA            TPAHere           戻る
              │              │                │
              ▼              ▼                ▼
       sendRequest(   sendRequest(       TpaGUI.open(
        viewer,        viewer,            viewer)
        target,        target,
        TPA)           TPAHERE)
              │              │
              ▼              ▼
       closeInventory()  closeInventory()

更新ボタン → ページを 1 にリセットして再描画 (再 open)
次へ/前へ → ページ更新して再描画
```

## 5. レイアウト

### TpaGUI (54 スロット)

| スロット | 内容 |
|---|---|
| 0..8 | 青ガラス（上辺） |
| 9, 17 | 青ガラス（左右） |
| 10..16 | プレイヤー頭 (7 個) |
| 18, 26 | 青ガラス |
| 19..25 | プレイヤー頭 (7 個) |
| 27, 35 | 青ガラス |
| 28..34 | プレイヤー頭 (7 個) |
| 36, 44 | 青ガラス |
| 37..43 | プレイヤー頭 (7 個) |
| 45 | 「← 前へ」(ARROW) または青ガラス（前ページなし時） |
| 46..48 | 青ガラス |
| 49 | 「🔄 更新」(EMERALD_BLOCK) |
| 50..52 | 青ガラス |
| 53 | 「次へ →」(ARROW) または青ガラス（次ページなし時） |

- 最大表示: 28 人/ページ
- タイトル: `&aTPAリクエスト一覧 [{page}/{totalPages}]`
- プレイヤー頭は `PLAYER_HEAD` を `SkullMeta.setOwningPlayer(OfflinePlayer)` で設定
- 頭の displayName: `&e{playerName}`
- 頭の lore: `&7クリックして選択`

### TpaActionGUI (27 スロット)

| スロット | 内容 |
|---|---|
| 0..8 | 青ガラス（上辺） |
| 9, 17 | 青ガラス |
| 10 | 青ガラス |
| 11 | 「/TPAHere」(LIME_WOOL) |
| 12 | 青ガラス |
| 13 | 対象プレイヤー頭 |
| 14 | 青ガラス |
| 15 | 「/TPA」(LIGHT_BLUE_WOOL) |
| 16 | 青ガラス |
| 18..21 | 青ガラス |
| 22 | 「← 戻る」(ARROW) |
| 23..26 | 青ガラス |

- タイトル: `&a{player}にリクエスト`
- TPA ボタン lore: `&7あなたが {player} のところへ`
- TPAHere ボタン lore: `&7{player} をあなたのところへ`

## 6. 設定キー (config.yml に追加)

```yaml
gui:
  # ... 既存のキー ...
  tpa:
    title: "&aTPAリクエスト一覧"
    title-page-suffix: " [{page}/{total}]"
    border-material: BLUE_STAINED_GLASS_PANE
    border-name: " "  # 空表示
    refresh-button:
      material: EMERALD_BLOCK
      name: "&a🔄 更新"
      lore:
        - "&7オンラインリストを再取得"
    prev-button:
      material: ARROW
      name: "&a← 前のページ"
    next-button:
      material: ARROW
      name: "&a次のページ →"
    head:
      name: "&e{player}"
      lore:
        - "&7クリックして選択"
    empty-message: "&7他にオンラインのプレイヤーがいません"
  tpa-action:
    title: "&a{player}にリクエスト"
    border-material: BLUE_STAINED_GLASS_PANE
    tpa-button:
      material: LIGHT_BLUE_WOOL
      name: "&b/TPA"
      lore:
        - "&7あなたが {player} のところへ移動"
    tpahere-button:
      material: LIME_WOOL
      name: "&a/TPAHere"
      lore:
        - "&7{player} をあなたのところへ呼ぶ"
    back-button:
      material: ARROW
      name: "&c← 戻る"

messages:
  # ... 既存のキー ...
  tpa-no-online-players: "&c他にオンラインのプレイヤーがいません。"
```

`messages.usage-tpa` / `usage-tpahere` は存在しないため新規追加なし。`/tpa` `/tpahere` の `args == 0` 分岐は GUI 起動に変更。

## 7. エラーハンドリング・エッジケース

- **`settings.tpa.enabled = false`**: GUI を開かず `tpa-feature-disabled` を送信（既存ロジック流用）
- **オンラインプレイヤーが自分のみ (open 時)**: GUI を開かず `tpa-no-online-players` を送信
- **更新後にオンラインが自分のみになった場合**: GUI を閉じて `tpa-no-online-players` を送信
- **サブメニュー操作中に対象がログアウト**: クリック時 `Bukkit.getPlayer(uuid) == null` チェック → `player-not-found` 送信 → メイン GUI に戻る
- **クールダウン中**: `TpaManager.sendRequest` 内の既存クールダウンチェックで弾かれメッセージが出る。GUI 側は気にしない
- **TPA 拒否中の相手にリクエスト**: 同上、既存ロジックでメッセージ
- **インベントリ操作禁止**: `event.setCancelled(true)` で全クリック制御、`InventoryDragEvent` も抑止
- **タイトル一致判定**: `HomeGUI` と同じく `PlainTextComponentSerializer` でプレーンテキスト比較
- **viewer ログアウト時の状態クリーンアップ**: `PlayerQuitEvent` で `Map` から削除

## 8. 実装する順序の目安

1. `config.yml` にデフォルト設定を追加
2. `TpaGUI` クラス: 描画ロジック、ページ管理、クリックハンドラ
3. `TpaActionGUI` クラス: 描画ロジック、クリックハンドラ、target Map 管理
4. `HomesPlugin` フィールド・初期化・登録、`/tpa` `/tpahere` 分岐の置き換え
5. `PlayerQuitEvent` でのクリーンアップ
6. 動作確認（後述）

## 9. テスト計画（手動）

Paper サーバーで以下を確認:

- [ ] `/tpa` を引数なしで実行 → GUI が開く
- [ ] `/tpahere` を引数なしで実行 → 同じ GUI が開く
- [ ] `/tpa <名前>` `/tpahere <名前>` が従来通り動く
- [ ] GUI 内に自分の頭が表示されない
- [ ] 自分以外が 29 人以上オンラインのとき (= 1 ページの 28 人を超える)、ページネーションが動く
- [ ] 1 ページのときは「次へ」「前へ」が出ない
- [ ] 「更新」を押すとページ 1 に戻り、新規参加者が表示される
- [ ] 頭クリック → サブメニューが開く
- [ ] サブメニューの TPA ボタン → 自分がその相手の元へ行く TPA リクエストが飛ぶ
- [ ] サブメニューの TPAHere ボタン → 相手を呼ぶリクエストが飛ぶ
- [ ] サブメニューの「戻る」 → メイン GUI に戻る
- [ ] サブメニュー操作中に対象がログアウト → エラーメッセージが出てメイン GUI に戻る
- [ ] `settings.tpa.enabled: false` のとき `/tpa` (引数なし) → 機能無効化メッセージのみ
- [ ] オンライン 1 人 (自分のみ) → 「他にオンラインのプレイヤーがいません」メッセージ
- [ ] GUI でアイテム取り出し操作ができない（クリックがキャンセルされる）
