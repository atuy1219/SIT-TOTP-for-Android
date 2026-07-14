package com.atuy.sittotp;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.GeneralSecurityException;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 1001;

    private SeedStore seedStore;
    private EditText seedInput;
    private TextView statusText;
    private boolean requestTileAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        seedStore = new SeedStore(this);
        setContentView(createContentView());
        refreshStatus();
    }

    private View createContentView() {
        int horizontalPadding = dp(24);
        int verticalPadding = dp(28);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        TextView title = new TextView(this);
        title.setText("SIT TOTP");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView description = new TextView(this);
        description.setText("SIT用のBase32シードを暗号化して端末内に保存します。保存後、クイック設定タイルを追加してください。タイルを押すと、現在の6桁コードが通知に30秒間表示されます。");
        description.setTextSize(16);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(12);
        content.addView(description, descriptionParams);

        seedInput = new EditText(this);
        seedInput.setHint("Base32シード または otpauth:// URI");
        seedInput.setSingleLine(false);
        seedInput.setMinLines(2);
        seedInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(24);
        content.addView(seedInput, inputParams);

        CheckBox showSeed = new CheckBox(this);
        showSeed.setText("入力中のシードを表示");
        showSeed.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int selection = seedInput.getSelectionStart();
            seedInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | (isChecked ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            seedInput.setSelection(Math.max(0, selection));
        });
        content.addView(showSeed, matchWrap());

        Button saveButton = new Button(this);
        saveButton.setText("保存してタイルを追加");
        saveButton.setOnClickListener(view -> saveSeed());
        LinearLayout.LayoutParams saveParams = matchWrap();
        saveParams.topMargin = dp(12);
        content.addView(saveButton, saveParams);

        Button deleteButton = new Button(this);
        deleteButton.setText("保存済みシードを削除");
        deleteButton.setOnClickListener(view -> clearSeed());
        LinearLayout.LayoutParams deleteParams = matchWrap();
        deleteParams.topMargin = dp(8);
        content.addView(deleteButton, deleteParams);

        statusText = new TextView(this);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.START);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(24);
        content.addView(statusText, statusParams);

        TextView warning = new TextView(this);
        warning.setText("注意: TOTPシードはパスワードと同等の秘密情報です。Issue、ログ、スクリーンショットなどに記載しないでください。通知内容はロック画面にも表示される場合があります。");
        warning.setTextSize(14);
        LinearLayout.LayoutParams warningParams = matchWrap();
        warningParams.topMargin = dp(24);
        content.addView(warning, warningParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        return scrollView;
    }

    private void saveSeed() {
        try {
            String normalized = Totp.normalizeSeed(seedInput.getText().toString());
            Totp.generate(normalized, System.currentTimeMillis() / 1000L);
            seedStore.save(normalized);
            seedInput.setText("");
            refreshStatus();
            Toast.makeText(this, "シードを暗号化して保存しました", Toast.LENGTH_SHORT).show();
            requestNotificationPermissionThenTile();
        } catch (IllegalArgumentException exception) {
            seedInput.setError(exception.getMessage());
        } catch (GeneralSecurityException | IllegalStateException exception) {
            statusText.setText("保存エラー: " + safeMessage(exception));
        }
    }

    private void clearSeed() {
        try {
            seedStore.clear();
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.cancel(OtpNotificationService.NOTIFICATION_ID);
            TileService.requestListeningState(
                    this,
                    new ComponentName(this, TotpTileService.class)
            );
            refreshStatus();
            Toast.makeText(this, "保存済みシードを削除しました", Toast.LENGTH_SHORT).show();
        } catch (GeneralSecurityException exception) {
            statusText.setText("削除エラー: " + safeMessage(exception));
        }
    }

    private void requestNotificationPermissionThenTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestTileAfterPermission = true;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS
            );
            return;
        }
        requestTileAddition();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS && requestTileAfterPermission) {
            requestTileAfterPermission = false;
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "通知が許可されていないため、タイルを押してもコードを表示できません",
                        Toast.LENGTH_LONG
                ).show();
            }
            requestTileAddition();
        }
    }

    private void requestTileAddition() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            statusText.append("\nクイック設定の編集画面から「SIT TOTP」を手動で追加してください。");
            return;
        }

        StatusBarManager manager = getSystemService(StatusBarManager.class);
        if (manager == null) {
            statusText.append("\nタイル追加APIを利用できません。クイック設定から手動で追加してください。");
            return;
        }

        manager.requestAddTileService(
                new ComponentName(this, TotpTileService.class),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_totp),
                getMainExecutor(),
                result -> {
                    String message;
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        message = "クイック設定タイルを追加しました";
                    } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        message = "クイック設定タイルは追加済みです";
                    } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
                        message = "タイルは追加されませんでした。クイック設定の編集画面から追加できます";
                    } else {
                        message = "タイル追加要求を完了できませんでした (結果: " + result + ")";
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    refreshStatus();
                }
        );
    }

    private void refreshStatus() {
        if (seedStore.hasSeed()) {
            statusText.setText("状態: シード保存済み\n方式: HMAC-SHA-256 / 6桁 / 30秒");
        } else {
            statusText.setText("状態: シード未設定");
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
