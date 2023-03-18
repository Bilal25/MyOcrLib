package com.syedbilalali.ocr;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewStub;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.zxing.Resultview;
import com.syedbilalali.ocr.camera.CameraManager;
import com.syedbilalali.ocr.decode.CaptureActivityHandler;
import com.syedbilalali.ocr.decode.DecodeManager;
import com.syedbilalali.ocr.decode.InactivityTimer;
import com.syedbilalali.ocr.tess.TesseractCallback;
import com.syedbilalali.ocr.tess.TesseractThread;
import com.syedbilalali.ocr.utils.Tools;
import com.syedbilalali.ocr.view.ImageDialog;
import com.syedbilalali.ocr.view.ScannerFinderView;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 二维码扫描类。
 */
public class ScannerActivity extends AppCompatActivity implements Callback, Camera.PictureCallback, Camera.ShutterCallback{
    public static final String mypreference = "mypref";
    private CaptureActivityHandler mCaptureActivityHandler;
    private boolean mHasSurface;
    private InactivityTimer mInactivityTimer;
    private ScannerFinderView mQrCodeFinderView;
    private SurfaceView mSurfaceView;
    private ViewStub mSurfaceViewStub;
    private DecodeManager mDecodeManager = new DecodeManager();
    private Switch switch1;
    private Button bt;
    SharedPreferences sharedpreferences;
    private ProgressDialog progressDialog;
    private Bitmap bmp;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        if (ContextCompat.checkSelfPermission(this, CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA, READ_EXTERNAL_STORAGE}, 100);
        } else {
            initView();
            initData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100){
            boolean permissionGranted = true;
            for (int i : grantResults) {
                if (i != PackageManager.PERMISSION_GRANTED) {
                    permissionGranted = false;
                }
            }
            if (permissionGranted){
                initView();
                initData();
            }else {
                // 无权限退出
                finish();
            }
        }
    }

    private void inspectFromBitmap(Bitmap bitmap) {
        TextRecognizer textRecognizer = new TextRecognizer.Builder(this).build();
        try {
            if (!textRecognizer.isOperational()) {
                new AlertDialog.
                        Builder(this).
                        setMessage("Text recognizer could not be set up on your device").show();
                return;
            }

            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray<TextBlock> origTextBlocks = textRecognizer.detect(frame);
            List<TextBlock> textBlocks = new ArrayList<>();
            for (int i = 0; i < origTextBlocks.size(); i++) {
                TextBlock textBlock = origTextBlocks.valueAt(i);
                textBlocks.add(textBlock);
            }
            Collections.sort(textBlocks, new Comparator<TextBlock>() {
                @Override
                public int compare(TextBlock o1, TextBlock o2) {
                    int diffOfTops = o1.getBoundingBox().top - o2.getBoundingBox().top;
                    int diffOfLefts = o1.getBoundingBox().left - o2.getBoundingBox().left;
                    if (diffOfTops != 0) {
                        return diffOfTops;
                    }
                    return diffOfLefts;
                }
            });

            StringBuilder detectedText = new StringBuilder();
            for (TextBlock textBlock : textBlocks) {
                if (textBlock != null && textBlock.getValue() != null) {
                    detectedText.append(textBlock.getValue());
                    detectedText.append("\n");
                }
            }
            phoneSucceed(String.valueOf(detectedText), bmp);
            //textreading.setText(detectedText);
        } finally {
            textRecognizer.release();
        }
    }

    private void initView() {
        mQrCodeFinderView = (ScannerFinderView) findViewById(R.id.qr_code_view_finder);
        mSurfaceViewStub = (ViewStub) findViewById(R.id.qr_code_view_stub);
        switch1 = (Switch) findViewById(R.id.switch1);
        mHasSurface = false;

        bt = (Button) findViewById(R.id.bt);

        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bt.setEnabled(false);
                buildProgressDialog();
                CameraManager.get().takeShot(ScannerActivity.this, ScannerActivity.this, ScannerActivity.this);
            }
        });

        Switch switch2 = (Switch) findViewById(R.id.switch2);
        switch2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                CameraManager.get().setFlashLight(isChecked);
            }
        });
    }

    public Rect getCropRect() {
        return mQrCodeFinderView.getRect();
    }

    public boolean isQRCode() {
        return switch1.isChecked();
    }

    private void initData() {
        mInactivityTimer = new InactivityTimer(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mInactivityTimer != null){
            CameraManager.init();
            initCamera();
        }
    }

    private void initCamera() {
        if (null == mSurfaceView) {
            mSurfaceViewStub.setLayoutResource(R.layout.layout_surface_view);
            mSurfaceView = (SurfaceView) mSurfaceViewStub.inflate();
        }
        SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
        if (mHasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);

            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    @Override
    protected void onPause() {
        if (mCaptureActivityHandler != null) {
            try {
                mCaptureActivityHandler.quitSynchronously();
                mCaptureActivityHandler = null;
                if (null != mSurfaceView && !mHasSurface) {
                    mSurfaceView.getHolder().removeCallback(this);
                }
                CameraManager.get().closeDriver();
            } catch (Exception e) {

                finish();
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (null != mInactivityTimer) {
            mInactivityTimer.shutdown();
        }
        super.onDestroy();
    }

    /**
     * Handler scan result
     *
     * @param result
     */
    public void handleDecode(Resultview result) {
        mInactivityTimer.onActivity();
        if (null == result) {
            mDecodeManager.showCouldNotReadQrCodeFromScanner(this, new DecodeManager.OnRefreshCameraListener() {
                @Override
                public void refresh() {
                    restartPreview();
                }
            });
        } else {
            handleResult(result);
        }
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            if (!CameraManager.get().openDriver(surfaceHolder)) {
                return;
            }
        } catch (IOException e) {
            // 基本不会出现相机不存在的情况
            Toast.makeText(this, getString(R.string.camera_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        } catch (RuntimeException re) {
            re.printStackTrace();
            return;
        }
        mQrCodeFinderView.setVisibility(View.VISIBLE);
        findViewById(R.id.qr_code_view_background).setVisibility(View.GONE);
        if (mCaptureActivityHandler == null) {
            mCaptureActivityHandler = new CaptureActivityHandler(this);
        }
    }

    public void restartPreview() {
        if (null != mCaptureActivityHandler) {
            try {
                mCaptureActivityHandler.restartPreviewAndDecode();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!mHasSurface) {
            mHasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;
    }

    public Handler getCaptureActivityHandler() {
        return mCaptureActivityHandler;
    }

    private void handleResult(Resultview result) {
        if (TextUtils.isEmpty(result.getText())) {
            mDecodeManager.showCouldNotReadQrCodeFromScanner(this, new DecodeManager.OnRefreshCameraListener() {
                @Override
                public void refresh() {
                    restartPreview();
                }
            });
        } else {
            Vibrator vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
            vibrator.vibrate(200L);
            if (switch1.isChecked()) {
                qrSucceed(result.getText());
            } else {
                phoneSucceed(result.getText(), result.getBitmap());
            }
        }
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        if (data == null) {
            return;
        }
        mCaptureActivityHandler.onPause();
        bmp = null;
        bmp = Tools.getFocusedBitmap(this, camera, data, getCropRect());

        TesseractThread mTesseractThread = new TesseractThread(bmp, new TesseractCallback() {

            @Override
            public void succeed(String result) {
                Message message = Message.obtain();
                message.what = 0;
                message.obj = result;
                mHandler.sendMessage(message);
            }

            @Override
            public void fail() {
                Message message = Message.obtain();
                message.what = 1;
                mHandler.sendMessage(message);
            }
        });

        Thread thread = new Thread(mTesseractThread);
        thread.start();
    }

    @Override
    public void onShutter() {}

    private void qrSucceed(String result){
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.notification)
                .setMessage(result)
                .setPositiveButton(R.string.positive_button_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        restartPreview();
                    }
                })
                .show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                restartPreview();
            }
        });
    }

    private void phoneSucceed(String result, Bitmap bitmap){
        ImageDialog dialog = new ImageDialog(this);
        dialog.addBitmap(bitmap);
        dialog.addTitle(result);
        dialog.show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                SharedPreferences.Editor editor = getSharedPreferences(mypreference, MODE_PRIVATE).edit();
                editor.putString("name", result);
                editor.apply();
                finish();

                startActivity(Main.getCallingIntent(ScannerActivity.this));
                restartPreview();
            }

        });

//        dialog.setPositiveButton(
//                "Yes",
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        dialog.cancel();
//                    }
//                });
//
//        dialog.setNegativeButton(
//                "No",
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int id) {
//                        dialog.cancel();
//                    }
//                });

    }

    private Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            bt.setEnabled(true);
            cancelProgressDialog();
            switch (msg.what){
                case 0:
                    SharedPreferences.Editor editor = getSharedPreferences(mypreference, MODE_PRIVATE).edit();
                    editor.putString("name", null);
                    editor.apply();
                    inspectFromBitmap(bmp);
                    // phoneSucceed((String) msg.obj, bmp);
                    break;
                case 1:
                    Toast.makeText(ScannerActivity.this, "Error", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };

    public void buildProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }
        progressDialog.setMessage("Wait...");
        progressDialog.setCancelable(true);
        progressDialog.show();
    }

    public void cancelProgressDialog() {
        if (progressDialog != null){
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }
}