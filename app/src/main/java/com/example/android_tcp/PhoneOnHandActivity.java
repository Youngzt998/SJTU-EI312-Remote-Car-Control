package com.example.android_tcp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.pm.ActivityInfo;
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
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android_tcp.util.NetTools;
import com.example.android_tcp.util.QRCodeUtil;
import com.google.zxing.qrcode.QRCodeWriter;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

public class PhoneOnHandActivity extends AppCompatActivity implements
        CameraBridgeViewBase.CvCameraViewListener2{
    static {
        if(!OpenCVLoader.initDebug())
        {
            Log.d("opencv","初始化失败");
        }
        else{
            Log.d("opencv","初始化成功");
        }

        System.loadLibrary("opencv_java3");
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

    private CameraBridgeViewBase cameraView;
    private CascadeClassifier classifier;
    private Mat mGray;
    private Mat mRgba;
    private int mAbsoluteFaceSize = 0;
    private boolean isFrontCamera;

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
//        UdpSendThread udpSendThread = new UdpSendThread(this.getApplicationContext());
//        udpSendThread.start();

//        QRCodeWriter qrCodeWriter = new QRCodeWriter();


//        initWindowSettings();
//        cameraView = findViewById(R.id.FaceView);
//        cameraView.setVisibility(SurfaceView.VISIBLE);
//        cameraView.setCvCameraViewListener(this); // 设置相机监听
//        initClassifier();
//        cameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
//        cameraView.enableView();


    }



    public void clickOnStartServer(View view)
    {
//        try {
//            mSocket_Control.close();
//        }catch (Exception e){
//            e.printStackTrace();
//        }
//
//        mAcceptThread_Control = new SocketBuildThread();
//        mAcceptThread_Control.start();
//        Toast.makeText(this, "服务重启成功", Toast.LENGTH_SHORT).show();
    }

    public void startServer_Control()
    {
        try{
            //开启服务并指定端口
            mServerSocket_Control = new ServerSocket(60000);
        }
        catch (IOException e){
            Toast.makeText(this, "绑定端口失败...", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "端口失败");
            e.printStackTrace();
            Log.d(TAG, "端口失败");
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

    /***************************************************************************************************/
    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    // 初始化窗口设置, 包括全屏、横屏、常亮
    private void initWindowSettings() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    // 初始化人脸级联分类器，必须先初始化
    private void initClassifier() {
        try {
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();
            classifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    // 这里执行人脸检测的逻辑, 根据OpenCV提供的例子实现(face-detection)
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        // 翻转矩阵以适配前后置摄像头
        if (isFrontCamera) {
            Core.flip(mRgba, mRgba, 0);
            Core.flip(mGray, mGray, 0);
            Core.rotate(mRgba, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);
            Core.rotate(mGray, mGray, Core.ROTATE_90_COUNTERCLOCKWISE);
        } //else {
        //Core.flip(mRgba, mRgba, 1);
        //Core.flip(mGray, mGray, 1);
        //}



        float mRelativeFaceSize = 0.2f;
        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }
        MatOfRect faces = new MatOfRect();
        if (classifier != null)
            classifier.detectMultiScale(mGray, faces, 1.1, 2, 2,
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        Rect[] facesArray = faces.toArray();
        Scalar faceRectColor = new Scalar(0, 255, 0, 255);
        //TextView textview = findViewById(R.id.textView2);
        for (Rect faceRect : facesArray) {
            Imgproc.rectangle(mRgba, faceRect.tl(), faceRect.br(), faceRectColor, 3);
        }

        //判断脸在右侧还是左侧
        /*if(facesArray[0].tl().x*2>mRgba.cols())
            textview.setText("left");
        else if(facesArray[0].br().x*2<mRgba.cols())
            textview.setText("right");
        else
            textview.setText("midle");*/

        return mRgba;
    }


    /*************************************************************************************************************************/
    /*
    * 设置按钮点击事件
    */



    //生成包含IP地址的二维码的点击事件
    //借用一下之后的image对象
    public void clickOnBtnIpQr(View view)
    {
        String ip = "000.000.000.000";
        try {
            ip = NetTools.getIP(this.getApplicationContext());
            ImageView imageView = (ImageView) findViewById(R.id.imageFromCar);
            imageView.setImageBitmap(QRCodeUtil.generateBitmap(ip, 600, 600));
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(this, "生成ip二维码时出错", Toast.LENGTH_SHORT).show();
        }

    }


    //获取控制流信道的连接状态
    public void clickBtnGetStateControl(View view)
    {
        if(mSocket_Control != null && mSocket_Control.isConnected() && !mSocket_Control.isClosed() )
        {
            Toast.makeText(this, "小车端已连接", Toast.LENGTH_SHORT).show();
            return;
        }
        else {
            Toast.makeText(this, "小车端未连接", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    //获取图像流信道连接状态
    //TBD...

    public void setButton()
    {
        //setButtonUDLR();
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
                        mSendMessageThread_Control.interrupt();         //关闭发送"U"的线程
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
                        mSendMessageThread_Control.interrupt();         //关闭发送"U"的线程
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
                        mSendMessageThread_Control.interrupt();         //关闭发送"U"的线程
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
                        mSendMessageThread_Control.interrupt();         //关闭发送"U"的线程
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
            while (!interrupted())
            {
                sendMsgToCar(direction);
                Log.d("sendMessageThread", direction);

                //强制退出
                counter++;
                if(counter>=10000)
                    interrupt();
            }
        }
    }

    //控制上下左右，这种方式只能点击一下发送一个字符串
    public void clickBtnUp(View view)
    {
        //不能在主线程进行，否则报错(Android 4 以下)
        // writeMsgToCar("u");

        //向子线程发送消息
        Message message = new Message();
        message.obj = "3";
        try {
            subThreadHandler.sendMessage(message);
        }
        catch (Exception e){
            Log.d(TAG, "send to subthread fail");
            e.printStackTrace();
        }
    }

    public void clickBtnDown(View view)
    {
        //不能在主线程进行，否则报错(Android 4 以下)
        // writeMsgToCar("u");

        //向子线程发送消息
        Message message = new Message();
        message.obj = "4";
        try {
            subThreadHandler.sendMessage(message);
        }
        catch (Exception e){
            Log.d(TAG, "send to subthread fail");
            e.printStackTrace();
        }
    }

    public void clickBtnLeft(View view)
    {
        //不能在主线程进行，否则报错(Android 4 以下)
        // writeMsgToCar("u");

        //向子线程发送消息
        Message message = new Message();
        message.obj = "1";
        try {
            subThreadHandler.sendMessage(message);
        }
        catch (Exception e){
            Log.d(TAG, "send to subthread fail");
            e.printStackTrace();
        }
    }

    public void clickBtnRight(View view)
    {
        //不能在主线程进行，否则报错(Android 4 以下)
        // writeMsgToCar("u");

        //向子线程发送消息
        Message message = new Message();
        message.obj = "2";
        try {
            subThreadHandler.sendMessage(message);
        }
        catch (Exception e){
            Log.d(TAG, "send to subthread fail");
            e.printStackTrace();
        }
    }


    public void setBottonGesture()
    {
        ImageButton imageButton= (ImageButton)findViewById(R.id.btnGesture);
        imageButton.setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event)
                    {
                        double startX, startY, endX, endY, offsetX, offsetY, finalX, finalY;
                        switch (event.getAction())
                        {
                            case MotionEvent.ACTION_DOWN:{  //按下
                                startX = event.getRawX();
                                endY = event.getRawY();
                                break;
                            }

                            case MotionEvent.ACTION_MOVE:{


                            }

                            case MotionEvent.ACTION_UP:{    //松开

                                break;
                            }
                            default:break;
                        }
                        return true;    //不响应click事件
                    }
                }
        );

    }


    public void onClickBtnFace(View view)
    {

    }


}
