package com.example.administrator.androidrecordscreendemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final int
            REQUEST_CODE = 1000, REQUEST2_CODE = 1001;
    private int screenDensity, DISPLAY_WIDTH, DISPLAY_HEIGHT;
    private String videoUri = "";
    private String[] requestPermissionStrings = {
            Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private MediaProjection mediaProjection;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjectionCallback mediaProjectionCallback;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;

    private VideoView videoView;
    private ToggleButton toggleButton;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();                       // 拍照方向

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,                          // 去除状态栏
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);
        toggleButton = findViewById(R.id.toggleButton);

        /** 確認如果沒有取得相關權限, 會跳出請求權限 */
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, requestPermissionStrings, REQUEST_CODE);
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenDensity = displayMetrics.densityDpi;
        DISPLAY_WIDTH = displayMetrics.widthPixels;
        DISPLAY_HEIGHT = displayMetrics.heightPixels;
        mediaRecorder = new MediaRecorder();
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toogleScreenShare(view);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /** 監聽使用者是否接受錄製視頻的權限, 以及對應的操作 */
        if (requestCode == REQUEST2_CODE) {
            if (resultCode == RESULT_OK) {
                mediaProjectionCallback = new MediaProjectionCallback();
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                mediaProjection.registerCallback(mediaProjectionCallback, null);
                virtualDisplay = createVirtualDisplay();
                mediaRecorder.start();

                /** 跳转至桌面首页 */
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        Intent intent = new Intent(Intent.ACTION_MAIN, null);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        startActivity(intent);
                    }
                }, 200);
            } else {
                toggleButton.setChecked(false);
                mediaRecorder.reset();
                stopRecordScreen();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE:

                /** 只要拒絕某一個權限, 就會強制結束APP */
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(this, "PERMISSION_DENIED", Toast.LENGTH_SHORT).show();
                        finish();
                    } else if (grantResults[1] == PackageManager.PERMISSION_DENIED) {
                        Toast.makeText(this, "PERMISSION_DENIED", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
                break;
        }
    }

    private void toogleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            initRecorder();
            recordScreen();
        } else {
            mediaRecorder.stop();
            mediaRecorder.reset();
            stopRecordScreen();

            /** Play in video view */
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(Uri.parse(videoUri));
            videoView.start();
        }
    }

    /** 初始化mediaRecorder相關設定 */
    private void initRecorder() {
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);                            // 创建mediarecorder对象
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            videoUri = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    + new StringBuilder("/ARSD_Record_")
                    .append(new SimpleDateFormat("yyyy-MM-dd_hhmmss")
                            .format(new Date())).append(".mp4").toString();
            mediaRecorder.setOutputFile(videoUri);
            mediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setVideoEncodingBitRate(512 * 1000);
            mediaRecorder.setVideoFrameRate(30);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int orientation = ORIENTATIONS.get(rotation + 90);
            mediaRecorder.setOrientationHint(orientation);
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 請求錄製視頻 */
    private void recordScreen() {
        if (mediaProjection == null) {
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST2_CODE);
            return;
        }
        virtualDisplay = createVirtualDisplay();
        mediaRecorder.start();
    }

    /** 結束錄製視頻 */
    private void stopRecordScreen() {
        if (virtualDisplay == null) {
            return;
        }
        virtualDisplay.release();
        destroyMediaProjection();
    }

    private void destroyMediaProjection() {
        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(mediaProjectionCallback);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private VirtualDisplay createVirtualDisplay() {
        return mediaProjection.createVirtualDisplay(
                "MainActivity", DISPLAY_WIDTH, DISPLAY_HEIGHT, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(), null, null);
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (toggleButton.isChecked()) {
                toggleButton.setChecked(false);
                mediaRecorder.stop();
                mediaRecorder.reset();
            }
            mediaProjection = null;
            stopRecordScreen();
            super.onStop();
        }
    }
}
