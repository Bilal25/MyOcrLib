package com.syedbilalali.ocr;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;


public class splash_Activity extends FragmentActivity {

    private static final long SPLASH_DURATION = 4000;
    public static final String mypreference = "mypref";
    LinearLayout imgAppLogo;
    TextView txtAppVersion;

    private Handler splashHandler;
    //private SharedPreferenceManager preferenceManager;
     private Typeface face;
    @Override
      protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(R.style.TransparentStatusBarTheme);
        setContentView(R.layout.activity_splashs);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        splashHandler = new Handler();
        imgAppLogo = (LinearLayout)findViewById(R.id.lio);
        txtAppVersion = (TextView)findViewById(R.id.textView2);

//        face = Typeface.createFromAsset(getAssets(),
//        "camptonbolddemo.ttf");
//        txtAppVersion.setTypeface(face);
//        preferenceManager = new SharedPreferenceManager(this);
        initiateAnimation();
        setVersionNumber();
    }

    //initiate animation fade in
    private void initiateAnimation() {
        SharedPreferences.Editor editor = getSharedPreferences(mypreference, MODE_PRIVATE).edit();
        editor.putString("name", null);
        editor.apply();
        Animation animation = AnimationUtils.loadAnimation(splash_Activity.this, R.anim.fade_in);
        txtAppVersion.startAnimation(animation);
        imgAppLogo.startAnimation(animation);
        splashHandler.postDelayed(() -> {
                    finish();
                  //  Commit
                   // startActivity(MainActivity.getCallingIntent(splash_Activity.this));
                }
                , SPLASH_DURATION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (splashHandler != null) {
            splashHandler.removeCallbacksAndMessages(null);
        }
    }

    private void setVersionNumber() {
        try {
           // txtAppVersion.setText(String.format("Build Version : %s", BuildConfig.VERSION_NAME));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}