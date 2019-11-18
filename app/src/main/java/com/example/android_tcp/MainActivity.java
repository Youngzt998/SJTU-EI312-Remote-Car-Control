package com.example.android_tcp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //主界面两个按钮
//        Button button_hand = (Button) findViewById(R.id.phoneOnHand);
//        Button button_car = (Button) findViewById(R.id.phoneOnCar);
    }

    public void clickBtnHand(View view)
    {
        Intent intent = new Intent(this, PhoneOnHandActivity.class);
        startActivity(intent);
    }
    public void clickBtnCar(View view)
    {
        Intent intent = new Intent(this, PhoneOnCarActivity.class);
        startActivity(intent);
    }
}
