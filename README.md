# SIT TOTP for Android

芝浦工業大学のMicrosoft Authenticator登録から取得したBase32シードを端末内に保存し、クイック設定タイルから6桁のTOTPを表示するAndroidアプリです。

## 動作

1. アプリを開き、Base32シードまたは `otpauth://` URIを入力します。
2. 「保存してタイルを追加」を押します。
3. Androidの確認画面で `SIT TOTP` クイック設定タイルを追加します。
4. タイルを押すと、一時的にオン表示になったあとすぐオフへ戻ります。
5. 現在のコードを自動的にクリップボードへコピーし、通知に10秒間表示します。
6. 通知の「コピー」ボタンを押すと、その時点の最新コードをもう一度コピーできます。

通知表示中にタイルを再度押した場合も、最新コードをコピーし、通知の表示期限をその時点から10秒に更新します。タイル操作時にActivityの起動、ホーム画面への移動、クイック設定パネルを閉じる処理は行いません。

TOTP方式は参照元の `SIT-TOTP-AutoFill` と同じ次の設定です。

- HMAC-SHA-256
- 6桁
- 30秒周期

## OnePlus / OPPO Live Alerts

Android 16では `Notification.ProgressStyle`、短い重要テキスト、promoted ongoing要求を使用します。ColorOS / OxygenOSがこの通知をLive Alertsとして扱う場合は、ステータス領域にもコードが表示されます。

ただし、Live Alertsへの昇格判断はOS・端末・通知設定に依存します。昇格されない場合も通常の通知としてコードを表示します。

## セキュリティ

- シードはAndroid Keystoreの非エクスポート可能なAES鍵を使い、AES-GCMで暗号化して保存します。
- シードをソースコード、Issue、ログへ出力しません。
- バックアップは無効です。
- コピーしたコードにはクリップボードの機密情報フラグを設定します。Android 13以上ではシステムのコピー確認にコードの内容を表示しません。
- コードはロック画面に表示される可能性があります。端末の通知設定で必要に応じて非表示にしてください。
- root権限を持つ攻撃者や、端末が完全に侵害された状況からシードを保護するものではありません。

## 必要環境

- Android 8.0（API 26）以上
- Android 13以上ではアプリからタイル追加を要求できます。Android 12以下ではクイック設定の編集画面から手動追加します。
- Android 13以上では通知権限が必要です。

## ビルド

Android Studioでプロジェクトを開くか、Gradle 9.4.1とJDK 17を用意して次を実行します。

```bash
gradle testDebugUnitTest assembleDebug
```

APKは次に生成されます。

```text
app/build/outputs/apk/debug/app-debug.apk
```

## パッケージ名

```text
com.atuy.sittotp
```

## 注意

TOTPシードはパスワードと同等の秘密情報です。第三者への送信、画面共有、Issueへの投稿、ソースコードへの記載をしないでください。
