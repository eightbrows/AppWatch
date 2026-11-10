# AppWatch（アプリ見守当番）

[日本語](#日本語) | [English](#english)

## 日本語

スマートフォンに慣れていない人の端末で、インストールされたアプリを定期的に記録し、不要なアプリをすぐに削除できるようにする Android アプリです。家族など手助けする人（管理者）が、対面で設定と確認を行います。

- **本人**：トップ画面とホーム画面のウィジェットで「確認が必要なアプリ」を見て、不要なら削除します。
- **管理者**：アプリごとに「問題なし／未確認／要注意」を決め、入れてほしくないアプリを先に登録し、期間を選んで変化を確かめます。書き出したデータを別の端末に取り込んで見ることもできます。

### 主な機能

- 定期チェック（30分〜6時間ごと）と、アプリの追加・削除・更新・無効化・署名の変化の記録
- 「確認が必要なアプリ」の表示と、ボタン1つで Android の削除画面へ
- ホーム画面のウィジェット（4×2）
- 管理者用：アプリの確認状態、注意アプリの事前登録（Google Play の「共有」から）、履歴と比較
- データの書き出し・取り込み（閲覧のみ／この端末に復元）、閲覧専用の使い方
- 日本語・英語、ライト・ダークのテーマ

### 動作環境

Android 8.0 以上

### インストール

1. [Releases](https://github.com/eightbrows/AppWatch/releases) から最新の `AppWatch-YYYYMMDD-RNN.apk` を端末にダウンロードします。
2. ダウンロードした APK を開いて入れます。Google Play 以外からのアプリなので、Android の確認で、ダウンロードに使ったアプリ（ブラウザやファイルアプリ）に「不明なアプリのインストール」を許可して下さい。
3. 初回設定で「見守る端末」（本人の端末）か「閲覧専用」（管理者の端末）を選びます。

### 使う権限

| 権限 | 理由 |
| --- | --- |
| インストールされたアプリの一覧の取得 | 端末のアプリを記録し、変化を見つけるため |
| アプリの削除の要求 | 「削除する」から Android の削除画面を開くため（削除は毎回 Android の確認を通ります） |
| 電池の最適化からの除外の要求 | 定期チェックが止まらないようにするため（設定は利用者が選びます） |

定期チェック（WorkManager）が使う権限（端末の起動後の再開など）も含まれます。インターネットの権限はありません。

### データとプライバシー

- インターネットに接続しません。記録はすべて端末の中にあり、外部に送りません。
- 書き出したファイル（`.appwatch`）は暗号化されません。対面での近距離共有など、本人と管理者だけが使う方法で受け渡して下さい。

### 資料

- 仕様書：[docs/spec.md](docs/spec.md)
- 公式サイト：https://eightbrows.github.io/

### ライセンス

[Apache License 2.0](LICENSE)

## English

AppWatch is an Android app for people who aren't used to smartphones. It regularly records the installed apps and makes it easy to delete apps that aren't needed. A helper, such as a family member, sets it up and reviews the apps in person.

- **User**: sees the apps that need review on the main screen and the home screen widget, and deletes them if they aren't needed.
- **Helper**: marks each app as OK, Not reviewed or Caution, pre-registers apps that shouldn't be installed, and checks the changes over a chosen period. Exported data can also be imported and viewed on another device.

### Features

- Periodic checks (every 30 minutes to 6 hours) that record added, removed, updated and disabled apps and signature changes
- A list of apps that need review, with one button to open Android's uninstall screen
- A home screen widget (4×2)
- For helpers: app status, pre-registering apps (from Google Play's "Share"), and history
- Export and import of data (view only, or restore to this device), and a view-only mode
- Japanese and English, light and dark themes

### Requirements

Android 8.0 or later

### Install

1. Download the latest `AppWatch-YYYYMMDD-RNN.apk` from [Releases](https://github.com/eightbrows/AppWatch/releases) to the device.
2. Open the APK to install it. It doesn't come from Google Play, so when Android asks, allow "Install unknown apps" for the app you downloaded it with (a browser or a file app).
3. In the first setup, choose "Monitored device" (the user's device) or "View only" (the helper's device).

### Permissions

| Permission | Why |
| --- | --- |
| See installed apps | To record the apps on the device and find changes |
| Request app deletion | To open Android's uninstall screen from "Delete" (Android always asks to confirm) |
| Request no battery optimization | To keep periodic checks from stopping (the user chooses the setting) |

The permissions used by periodic checks (WorkManager), such as restarting after the device boots, are also included. There is no internet permission.

### Data and privacy

- AppWatch never connects to the internet. All records stay on the device and are never sent anywhere.
- Exported files (`.appwatch`) aren't encrypted. Pass them only in ways that just the user and the helper use, such as sharing nearby in person.

### Documents

- Specification (Japanese): [docs/spec.md](docs/spec.md)
- Website: https://eightbrows.github.io/

### License

[Apache License 2.0](LICENSE)
