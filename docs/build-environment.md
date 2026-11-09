# ビルド環境

- JDK 17、Gradle Wrapper 9.8.2、Android Gradle Plugin 9.2.0、Kotlin 2.3.21。
- ビルドにはリポジトリの `./gradlew`（Windowsでは `.\gradlew.bat`）を使用する。CIも同じWrapperを使い、実行バージョンをログに出力する。
- Android StudioのGradle設定は「Wrapper」を選び、更新後にGradle同期を行う。ローカルに別途インストールした旧Gradleを指定しない。

```powershell
.\gradlew.bat --version
.\gradlew.bat :core:test :desktop:test :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

Gradle 9.8.2は、[Issue #79](https://github.com/kisschan/mogura-game/issues/79)で追跡する3件の脆弱性修正を含む[公式推奨版](https://docs.gradle.org/9.8.2/release-notes.html)（2026-10-25確認）。配布ZIPのSHA-256を `gradle/wrapper/gradle-wrapper.properties` に固定し、Wrapper JARも[公式チェックサム](https://gradle.org/release-checksums/)と照合している。

配布ZIPの取得で一時的なHTTP 504などが発生した場合、Wrapperは1秒・2秒・4秒の待機を挟んで最大3回再試行する（初回を含め最大4回）。接続・読み取りのタイムアウトは60秒。取得後は固定SHA-256で検証し、不一致や再試行後も続く取得失敗はエラーとして終了する。

[Kotlinの互換表](https://kotlinlang.org/docs/gradle-configure-project.html)ではKotlin 2.3.21の完全サポート範囲はGradle 9.3.0／AGP 9.0.0まで。現在の組み合わせはその範囲外のため、Wrapper更新時は上記の回帰テスト・Android debugビルド・IDE同期を確認する。

更新前から起動しているGradle daemonはWrapperの変更だけでは終了しない。開発端末では古いビルドが完了した後、旧バージョンのGradleから `--stop` を実行して停止し、新しいWrapperで再開する。新バージョンの `--stop` だけでは旧バージョンのdaemonを停止できない。

## 依存物の固定と検証

各モジュールの `gradle.lockfile` で解決した依存バージョンを固定し、`gradle/verification-metadata.xml` のSHA-256で外部依存のPOM・JAR・AAR等を検証する。通常のビルドではロックの不足・変更やハッシュ不一致をエラーにし、CIは `--dependency-verification strict` を明示する。DesktopのJLayerもMaven座標 `javazoom:jlayer:1.0.1` から取得し、検証対象に含める。

意図して依存物を更新するときだけ、次を実行する。

```powershell
.\gradlew.bat resolveAndLockAll --write-locks --write-verification-metadata sha256
.\gradlew.bat :core:test :desktop:test :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

生成したハッシュは、その時取得したファイルを記録したものにすぎない。差分と公式配布元のチェックサムを確認してから採用する。不明な検証エラーを消す目的でハッシュを再生成しない。OS別のAndroidビルドツールなど、別環境だけで解決される依存物も同様に照合して追加する。

音源作成スクリプト用のローカル `libs/jlayer-1.0.1.jar` は維持し、Javaを起動する前にスクリプト内の固定SHA-256と照合する。更新時はMaven側と同じ公式配布物であることを確認し、この値も併せて更新する。

## 開発コンテナとCI

2026-11-07の更新では、開発コンテナをNode.js 24.22.2 LTSのイメージdigestで固定し、Claude Code 2.1.319・Codex CLI 0.167.0を指定した。設定を既存コンテナへ反映するにはコンテナの再ビルドが必要。ホスト側に個別インストールされたCLIのバージョンはこの設定では変わらない。

CIのActionsは公式リリースの完全commit SHAで固定する。更新時はタグ名だけでなく、そのタグの実commitを照合する。Gradleのキャッシュは `basic` を明示し、JDK 17は最新パッチの取得とダウンロード時の署名検証を有効にする。

## モック画像の撮影

`mockups/android/render.mjs` のHTTPサーバーは、撮影に必要なHTML・CSS・画像20ファイルだけを起動時に読み込み、localhostへ配信する。公開ファイルを追加するときは `mockup-server.mjs` の一覧とHTML/CSSの参照を併せて更新する。リポジトリ全体を公開する設定には戻さない。

```powershell
& 'C:\Program Files\nodejs\node.exe' --test mockups/android/mockup-server.test.mjs
```
