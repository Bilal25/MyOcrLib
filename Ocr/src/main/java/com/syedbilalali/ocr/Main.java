package com.syedbilalali.ocr;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class Main extends AppCompatActivity {


    public static Intent getCallingIntent(Context _context) {
        return new Intent(_context, Main.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);

        Intent it= new Intent(Main.this, ScannerActivity.class);
        startActivity(it);
        finish();
    }
}