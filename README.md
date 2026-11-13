# TicMouse プロトタイプ 0.3

Wear OS 向けマウスアプリ「TicMouse」の UI プロトタイプ。
デザインは C案ハイブリッド（中央レバー＋外周リング）のレイアウト 0.3／グラファイト基調の計器デザイン。

- パッケージ: `com.example.ticmouseproto`
- versionCode 3 / versionName `0.3-prototype`
- minSdk 30 / targetSdk 33 / compileSdk 34
- 画面内カーソルはローカルデモ。スマホ／PC への送信は未実装（実機動作も未検証）

## Android Studio で開く（android-studio ブランチ）

1. Android Studio で **Open** → このフォルダを選択
2. Gradle Sync が始まる（AGP 8.5.2 / Gradle 8.7、依存ライブラリなし＝Android フレームワークのみ）
3. そのまま Wear OS エミュレータ／実機へ Run できる構成

プロジェクト構成:

```
settings.gradle / build.gradle        ルート設定（プラグイン版数）
app/build.gradle                      namespace・SDK・バージョン定義
app/src/main/AndroidManifest.xml      権限と Activity（package 属性等は build.gradle 側へ移行済み）
app/src/main/java/com/example/ticmouseproto/
    MouseView.java                    描画＋タッチ処理＋HID登録テスト（本体）
    MainActivity.java                 Activity（View を全画面表示）
gradle/wrapper/gradle-wrapper.properties  Gradle 8.7 を指定
gradlew / gradlew.bat                 起動スクリプト
docs/design-c.md                      C案の設計書
build-apk.sh                          従来の手組みビルド（Gradle なし版・参考用に残置）
```

注意:

- `gradle/wrapper/gradle-wrapper.jar` は、アップロード手段がテキストのみ対応のため GitHub には含めていない。
  初回 Sync でラッパー不足の警告が出た場合は、Android Studio の案内に従うか、ターミナルで
  `gradle wrapper --gradle-version 8.7` を実行すると生成される。
- このブランチの Gradle ビルド検証は Muse 側の環境権限（Direct network protocols の other_tcp = Deny で
  Gradle デーモンのローカル通信が遮断される）により未実施。プロジェクト構成は標準形式で作成している。

## 画面レイアウト（0.3）

- 上段：左／右クリックボタン（金属プレート風、左＝濃いグレー緑 #26302A／右＝濃いグレー青 #26303D、陰とグラデーションで立体感）
- 中央：ハイブリッド操作部（直径 65%）。外周リング＝精密移動（十字ステップ）、中央ノブ＝レバー（アナログ移動）
- ノブは金属ダイヤル風（放射グラデーション＋ローレットの刻み）。倒すとノブが指に追従し、離すと中央へ戻る
- 下段：ステータスバー。左に接続状態ドット＋「LOCAL DEMO」、右に「SCR 74%」
- 色調：背景 #101216、パネル #1B1E23、文字 #C9CDD2、アクセント #8FA3BF

## 操作

- 中央ノブをドラッグ：レバー操作（倒し量の2乗で速度、離すと即停止）
- 外周リングをタップ／長押し：1ステップ移動（長押しで連続＋加速）
- 左／右ボタン：タップ＝クリック、素早い2回＝ダブルクリック、長押し＝ドラッグ開始／終了
- クリックしながらレバーを倒すドラッグ移動にも対応（マルチタッチ）
- ステータスバーを 0.6 秒長押し：テストパネル（Bluetooth HID 登録テスト／感度・ステップ調整）

## 従来方式でのビルド（参考）

Gradle を使わない手組みビルドも残してある（JDK 17 + Android SDK 34、デバッグ署名）。

```
./build-apk.sh   # ticmouse-proto.apk が生成される
```

## 実機へのインストール（無線 ADB の例）

1. 時計：設定 → システム → デバイス情報 → ビルド番号を7回タップ → 開発者オプションで ADB デバッグ／ワイヤレスデバッグ ON
2. スマホと時計を同じネットワークに接続（時計は 2.4GHz 帯のみ対応）
3. Wear Installer 2 等で APK を送信

## 既知の未検証事項

- TicWatch Pro 5 Enduro（Wear OS 4.0）で Bluetooth HID 登録が通るか
- 実機での描画・タッチ感度（数値はすべて仮置き）
