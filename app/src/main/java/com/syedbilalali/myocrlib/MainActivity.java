package com.syedbilalali.myocrlib;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.syedbilalali.ocr.Main;


public class MainActivity extends Activity {
    Button button2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        button2 = (Button)findViewById(R.id.btn);

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent it= new Intent(MainActivity.this, Main.class);
                startActivity(it);
                finish();
            }
        });




    }
}