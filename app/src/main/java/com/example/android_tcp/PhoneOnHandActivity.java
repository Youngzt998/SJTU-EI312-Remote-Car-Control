package com.example.android_tcp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.android_tcp.util.NetTools;
import com.example.android_tcp.util.QRCodeUtil;
import com.google.zxing.qrcode.QRCodeWriter;

import org.opencv.android.OpenCVLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class PhoneOnHandActivity extends AppCompatActivity {
    static {
        if(!OpenCVLoader.initDebug())
        {
            Log.d("opencv","初始化失败");
        }
        else{
            Log.d("opencv","初始化成功");
        }
    }


    private String TAG = "PhoneOnHandActivity";

    //可能需要建立两条信道
    //作为服务端发送控制数据相关变量名后缀"_Control"
    //作为服务端接受图像数据的相关变量后缀"_Image"
    ServerSocket mServerSocket_Control;
    Socket mSocket_Control;

    private int mServerPort_Control;

    //暂时没啥用
    private final int STATE_CLOSED = 1;
    private final int STATE_ACCEPTING= 2;
    private final int STATE_CONNECTED = 3;
    private final int STATE_DISCONNECTED = 4;
    private int mSocketConnectState_Control = STATE_CLOSED;

    private OutputStream mOutStream_Control;
    private InputStream mInStream_Image;

    private SocketBuildThread mAcceptThread_Control;
    private SocketSendThread_Control mSendThread_Control;
    private sendMessageThread_Control mSendMessageThread_Control;

    Socket mSocket_Image;
    private SocketReceiveThread_Image mReceiveThread_Image;
    private ImageHandler mHandler_Image;    //子线程向主线程发送图片消息


    //测试。没用
    //Handler: 用于线程之间通信
    private Handler mHandler;           //子线程向主线程发送消息
    //private static int MSG_TEST = 1;    //测试，没用

    private Handler subThreadHandler;   //主线程向子线程发送消息

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_on_hand);
        Toast.makeText(this, "Phone On Hand", Toast.LENGTH_SHORT).show();

        //设置按钮事件
        setButton();
        //开启控制流的服务端连接
        startServer_Control();

        //测试：使用子线程循环向小车端发送数据
        mSendThread_Control = new SocketSendThread_Control();
        mSendThread_Control.start();

        //向小车端发送ip地址
        UdpSendThread udpSendThread = new UdpSendThread(this.getApplicationContext());
        udpSendThread.start();

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
    }

    public void startServer_Control()
    {
        try{
            //开启服务并指定端口
            mServerSocket_Control = new ServerSocket(8000);
        }
        catch (IOException e){
            Toast.makeText(this, "绑定端口失败...", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            //Log.d("Socket:", );
            return;
        }
        Toast.makeText(this, "绑定端口成功...", Toast.LENGTH_SHORT).show();

        mSocketConnectState_Control = STATE_ACCEPTING;

        mAcceptThread_Control = new SocketBuildThread();
        mAcceptThread_Control.start();

        Toast.makeText(this, "服务开启", Toast.LENGTH_SHORT).show();
    }
    /*************************************************************************************************************************/
    //点击开启图像接收的按钮
    //暂时只能在连接建立以后再按
    public void clickOnReceiveImage(View view)
    {
        //开始接受小车端发来的图像
        startReveiveImage();
    }

    //开启图像接收
    public void startReveiveImage()
    {
        mHandler_Image = new ImageHandler();
        mReceiveThread_Image = new SocketReceiveThread_Image();
        if(mReceiveThread_Image!=null)
            Log.d(TAG, "after mReceiveThread_Image = new SocketReceiveThread_Image(); ");
        mReceiveThread_Image.start();
    }


    //接受图像流使用的线程(作为服务端)
    class SocketReceiveThread_Image extends Thread
    {
        private static final String TAG = "SocketReceiveThread_Image";

        SocketReceiveThread_Image()
        {
            Log.d(TAG, "Image receiving thread created! ");
        }

        @Override
        public void run()
        {
            //mInStream_Image = mSocket_Control.getInputStream();
            try{
                byte[] header = new byte[4];
                int size;
                while (!isInterrupted())
                {
                    //read header
                    size = NetTools.readall(mInStream_Image, header, 0, 4);
                    if(size == -1){break;}
                    Log.d(TAG, "read image's header successfully ");

                    //read image
                    int length = NetTools.bytesToInt(header);
                    Log.d(TAG, "length: "+length);
                    byte [] buffer = new byte[length];
                    size = NetTools.readall(mInStream_Image, buffer, 0, length);
                    if (size == -1){break;}
                    Log.d(TAG, "read image successfully ");

                    //convert to bitmap
                    Bitmap bitmapImage = BitmapFactory.decodeByteArray(buffer, 0, buffer.length, null);
                    mHandler_Image.setImageBitmap(bitmapRotate(bitmapImage));
                }
            }catch (Exception e){
                e.printStackTrace();
            }

        }

        private Bitmap bitmapRotate(Bitmap bitmap) {
            // 旋转图片 动作
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            // 创建新的图片
            Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            return resizedBitmap;
        }
    }

    //从主线程接受子线程送来的图像的Handler子类
    class ImageHandler extends Handler
    {
        public static final int SET_IMAGEVIEW = 0;
        private ImageView mImageView;
        ImageHandler()
        {
            try {
                mImageView = (ImageView) findViewById(R.id.imageFromCar);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        //处理信息的函数
        @Override
        public void handleMessage(Message msg)
        {
            Log.d("ImageHandler", "handleMessage");
            switch (msg.what)
            {
                case SET_IMAGEVIEW:
                    mImageView.setImageBitmap((Bitmap)msg.obj);
                    break;
            }
        }

        //从子线程发送信息的函数
        public void setImageBitmap(Bitmap mBitmap)
        {
            Message msg = new Message();
            //信息类型
            msg.what = SET_IMAGEVIEW;
            //信息内容
            msg.obj = mBitmap;
            //发送信息
            this.sendMessage(msg);
        }

    }

    /*************************************************************************************************************************/

    /*************************************************************************************************************************/
    //开启服务端（传输控制流）使用的线程
    class SocketBuildThread extends Thread
    {
        @Override
        public void run()
        {
//            while(!interrupted())
//            {
                try {
                    //等待客户端连接，使用子线程运行（否则会阻塞）
                    mSocket_Control = mServerSocket_Control.accept();
                    //获取输入流、输出流
                    mInStream_Image = mSocket_Control.getInputStream();
                    mOutStream_Control = mSocket_Control.getOutputStream();

                } catch (IOException e) {
                    //Toast.makeText(PhoneOnCarActivity.this, "accept failed", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "accept fail");
                    return;
                }
                Log.d(TAG, "accept success");
//            }
        }
    }

    //向小车端发送控制指令使用的线程
    class SocketSendThread_Control extends Thread
    {
        @Override
        public void run()
        {
            Looper.prepare();//必须写
            subThreadHandler = new Handler()
            {
                @Override
                public void handleMessage(Message msg)
                {
                    if(msg.obj instanceof String)
                    {
                        Log.d("Subthread received message ", (String)msg.obj);
                        sendMsgToCar((String)msg.obj);
                    }
                }
            };
            Looper.loop();  //必须写   //应该是用来让子线程循环的
        }
    }

    //发送数据（控制流）
    public void sendMsgToCar(String msg)
    {
        if(msg.length()==0 || mOutStream_Control == null) return;
        try{
            mOutStream_Control.write(msg.getBytes());
            mOutStream_Control.flush();
            Log.d(TAG, "write success");
        }
        catch (Exception e){
            Log.d(TAG, "write fail");
            e.printStackTrace();
        }
    }



    /*************************************************************************************************************************/
    /*
    * 设置按钮点击事件
    */

    //生成包含IP地址的二维码的点击事件
    //借用之后的image
    public void clickOnBtnIpQr(View view)
    {

        String ip = "000.000.000.000";
        try {
            ip = NetTools.getIP(this.getApplicationContext());
        }catch (Exception e){
            e.printStackTrace();
        }

        ImageView imageView = (ImageView) findViewById(R.id.imageFromCar);
        imageView.setImageBitmap(QRCodeUtil.generateBitmap(ip, 600, 600));

    }


    //获取控制流信道的连接状态
    public void clickBtnGetStateControl(View view)
    {
        if(mSocket_Control == null)
        {
            Toast.makeText(this, "小车端未连接", Toast.LENGTH_SHORT).show();
            return;
        }
        else {
            Toast.makeText(this, "小车端已连接", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    //获取图像流信道连接状态
    //TBD...

    public void setButton()
    {
        setButtonUDLR();
    }

    //为了能够按下时持续发送指令，需要在按下时开启一个线程，松开时关闭这个线程（因为Button本身没有这个功能）
    //（有点丑...没找到更好的办法...
    public void setButtonUDLR()
    {
        Button button = (Button)findViewById(R.id.btnUp);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:{  //按下
                        mSendMessageThread_Control = new sendMessageThread_Control(0);  //开启发送"U"的线程
                        mSendMessageThread_Control.start();
                        Log.d("onTouch UP", "open thread");
                        break;
                    }

                    case MotionEvent.ACTION_UP:{    //松开
                        mSendMessageThread_Control.exitThread=true;         //关闭发送"U"的线程
                        Log.d("onTouch UP", "close thread");
                        break;
                    }
                }
                return true;    //不响应click事件
            }
        });


        button = (Button)findViewById(R.id.btnDown);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:{  //按下
                        mSendMessageThread_Control = new sendMessageThread_Control(1);  //开启发送"D"的线程
                        mSendMessageThread_Control.start();
                        Log.d("onTouch UP", "open thread");
                        break;
                    }

                    case MotionEvent.ACTION_UP:{    //松开
                        mSendMessageThread_Control.exitThread=true;         //关闭发送"U"的线程
                        Log.d("onTouch UP", "close thread");
                        break;
                    }
                }
                return true;    //不响应click事件
            }
        });

        button = (Button)findViewById(R.id.btnLeft);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:{  //按下
                        mSendMessageThread_Control = new sendMessageThread_Control(2);  //开启发送"L"的线程
                        mSendMessageThread_Control.start();
                        Log.d("onTouch UP", "open thread");
                        break;
                    }

                    case MotionEvent.ACTION_UP:{    //松开
                        mSendMessageThread_Control.exitThread=true;         //关闭发送"U"的线程
                        Log.d("onTouch UP", "close thread");
                        break;
                    }
                }
                return true;    //不响应click事件
            }
        });

        button = (Button)findViewById(R.id.btnRight);
        button.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:{  //按下
                        mSendMessageThread_Control = new sendMessageThread_Control(3);  //开启发送"D"的线程
                        mSendMessageThread_Control.start();
                        Log.d("onTouch UP", "open thread");
                        break;
                    }

                    case MotionEvent.ACTION_UP:{    //松开
                        mSendMessageThread_Control.exitThread=true;         //关闭发送"U"的线程
                        Log.d("onTouch UP", "close thread");
                        break;
                    }
                }
                return true;    //不响应click事件
            }
        });
    }

    //子线程，用于按下按钮未松开时持续发送控制指令
    class sendMessageThread_Control extends Thread
    {
        // 0:up     1:down      2: left     3:right
        private String direction;
        public boolean exitThread = false;
        public sendMessageThread_Control(int dir)   //注意构造函数不能有返回类型
        {
            switch (dir)
            {
                case 0: direction = "U"; break;
                case 1: direction = "D"; break;
                case 2: direction = "L"; break;
                case 3: direction = "R"; break;
                default:direction = "U"; break;
            }
        }
        @Override
        public void run()
        {
            Log.d("sendMessageThread", "started");
            int counter = 0;
            while (!exitThread)
            {
                sendMsgToCar(direction);
                Log.d("sendMessageThread", direction);

                //强制退出
                counter++;
                if(counter>=10000)
                    exitThread = true;
            }
        }
    }

    //控制上下左右，这种方式只能点击一下发送一个字符串
//    public void clickBtnUp(View view)
//    {
//        //不能在主线程进行，否则报错(Android 4 以下)
//        // writeMsgToCar("u");
//
//        //向子线程发送消息
//        Message message = new Message();
//        message.obj = "U";
//        try {
//            subThreadHandler.sendMessage(message);
//        }
//        catch (Exception e){
//            Log.d(TAG, "send to subthread fail");
//            e.printStackTrace();
//        }
//    }

}
