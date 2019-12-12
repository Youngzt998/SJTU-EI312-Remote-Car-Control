package com.example.android_tcp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.view.View;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
