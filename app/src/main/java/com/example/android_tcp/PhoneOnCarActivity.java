package com.example.android_tcp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

//import com.google.zxing.client.*;

//外部库
import com.example.android_tcp.util.*;
import com.google.zxing.client.android.CaptureActivity;

public class PhoneOnCarActivity extends AppCompatActivity {
    private String TAG = "PhoneOnCarActivity";
    //可能需要建立两条信道
    //作为客户端接受控制数据相关变量名后缀"_Control"
    //作为客户端发送图像数据的相关变量后缀"_Image"

    //用于接受控制指令
    Socket mSocket_Control;
    Socket mSocket_Image;
    //OnePlus: 10.189.108.244
    //OnePlus in wifi 414: 192.168.1.*
    //vivo in wifi 414: 192.168.1.*
    String mPhoneOnCarIpAddress_Control = "192.168.43.237";
    int mPhoneOnCarPort_Control = 8000;
    private OutputStream mOutStream_Control;
    private InputStream mInStream_Control;
    private SocketConnectThread_Control mConnectThread_Control;
    private SocketReceiveThread_Control mReceiveThread_Control;



    //用于操作后置摄像头, 变量名后缀 _back
    CameraManager cameraManager_Back;
    String cameraId_Back;
    CameraDevice.StateCallback cameraCallBack_Back;
    CameraDevice cameraDevice_Back;
    HandlerThread cameraHandlerThread_Back;
    Handler cameraHandler_Back;
    ImageReader imageReader_Back;
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 1;
    CaptureRequest.Builder mPreviewRequestBuilder_Back;
    CaptureRequest mPreviewRequest_Back;
    CameraCaptureSession mCaptureSession_Back;
    CameraCaptureSession.CaptureCallback mCaptureCallback_Back;
    TextureView textureView_back;
    ImageReader.OnImageAvailableListener mOnImageAvailableListener;


    //蓝牙模块
    private Button buttonConnect;
    private BluetoothAdapter mbluetoothAdapter;
    private String bluetoothDeviceMacAddress = "00:15:FF:F3:23:89"; //Bluetooth module physical address
    private BluetoothDevice bluetoothDevice = null; // Connected Bluetooth device
    private BluetoothSocket btSocket = null; // Bluetooth communication socket
    private final static String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"; // SPP service UUID number
    private final static int RESULT_CODE = 1001;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_on_car);
        Toast.makeText(PhoneOnCarActivity.this, "Phone on car", Toast.LENGTH_SHORT).show();
        textureView_back = (TextureView)findViewById(R.id.textureView_Back);

        setOnImageAvailableListener();


        //蓝牙模块
        mbluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); //get the default bluetooth adapter
//        buttonConnect.setOnClickListener(PhoneOnCarActivity.this);
//        buttonLed.setOnClickListener(this);

        buttonConnect = (Button) findViewById(R.id.btnBlueTooth);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);  //Bluetooth search
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(mReceiver, filter);
    }

    /*************************************************************************************************************************/
    // 从某个acivity返回
    static int REQUEST_CODE_QR_STRING = 100;
    public String newActivityResultString;
    public int test = -1;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        test = 0;
        super.onActivityResult(requestCode, resultCode, data);
        //IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_CODE_QR_STRING)
        {
            Log.d(TAG, "REQUEST_CODE_QR_STRING");
            if(data!=null)
            {
                newActivityResultString= data.getStringExtra("resultString");
                Log.d(TAG, "intent data is: " + data.getDataString());
                Log.d(TAG, "intent data is: " + data.getStringExtra("resultString"));
            }
            else {
                Log.d(TAG, "intent data is null");
            }
        }else if (requestCode == RESULT_CODE) {
            connectBtSocket();
        }
    }


    /*************************************************************************************************************************/
    //按下开启摄像头的按钮
    public void clickBtnStartBackCamera(View view)
    {
        //设置获取到图像时时触发的函数，每获取到一些图像就发送一张
        //setOnImageAvailableListener();
        //开启摄像头
        startBackCamera();

    }
    //开启摄像头
    public void startBackCamera()
    {
        Toast.makeText(PhoneOnCarActivity.this, "正在开启后置摄像头", Toast.LENGTH_SHORT).show();

        //???
        cameraHandlerThread_Back= new HandlerThread("CameraThread");
        cameraHandlerThread_Back.start();
        cameraHandler_Back = new Handler(cameraHandlerThread_Back.getLooper());
        cameraManager_Back = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        imageReader_Back = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2);
        //设置OnImageAvailableListener的对象，需要另外重写此对象在有可用图片时的函数，向socket发送图片
        imageReader_Back.setOnImageAvailableListener(mOnImageAvailableListener, cameraHandler_Back);
        //获取后置摄像头ID
        cameraId_Back = Integer.toString(CameraCharacteristics.LENS_FACING_FRONT);  //Vivo X9s L: front is back, back is front...
        //设置后置摄像服务的回调函数
        cameraCallBack_Back = new CameraDevice.StateCallback()
        {
            @Override
            public void onOpened(@NonNull CameraDevice camera)
            {
                Log.d("CameraCallBack", "onOpened");
                cameraDevice_Back = camera;
                //打开摄像头后，开始预览图像
                createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera)
            {
                Log.d("CameraCallBack", "onDisconnected");
                camera.close();
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error)
            {
                Log.d("CameraCallBack", "onError");
                Toast.makeText(PhoneOnCarActivity.this, "开启后置摄像头失败", Toast.LENGTH_SHORT).show();
                camera.close();
            }
        };

        //设置？？？（我也不知道这啥...）的回调函数
        mCaptureCallback_Back = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {}
        };

        //开启摄像头之前，必须检测摄像头权限是否被用户开启，未开启则向用户申请权限
        if(cameraId_Back!=null && ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA)== PackageManager.PERMISSION_GRANTED)
        {
            try {
                cameraManager_Back.openCamera(cameraId_Back, cameraCallBack_Back, cameraHandler_Back);
            } catch (Exception e) {
                Toast.makeText(PhoneOnCarActivity.this, "开启后置摄像头错误", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return;
            }
        }
        else {//？？？
            ActivityCompat.requestPermissions(PhoneOnCarActivity.this, new String[]{Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_CAMERA);
            Toast.makeText(PhoneOnCarActivity.this, "没有摄像头权限", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(PhoneOnCarActivity.this, "开启后置摄像头成功", Toast.LENGTH_SHORT).show();
    }

    //摄像头实时画面,摄像头开启后直接调用
    public void createCameraPreviewSession()
    {
        try{
            SurfaceTexture texture = textureView_back.getSurfaceTexture();
            //texture.setDefaultBufferSize();
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder_Back = cameraDevice_Back.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            //测一下参数是用surface还是imageReader_Back.getSurface()
            //测试发现，使用addTarget(surface)则只能预览图像.
            //使用addTarget(imageReader_Back.getSurface())能激活mOnImageAvailableListener
            //尝试一起用...都可以用了...
            //解释待添加...参考资料：https://blog.csdn.net/lb377463323/article/details/52740411
            //mPreviewRequestBuilder_Back.addTarget(surface);
            mPreviewRequestBuilder_Back.addTarget(imageReader_Back.getSurface());
            //这一段写得有一点丑，因为还没有完全搞懂...
            cameraDevice_Back.createCaptureSession( //Arrays.asList(surface, imageReader_Back.getSurface()),
                    Arrays.asList(imageReader_Back.getSurface()),   //不需要预览
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session)
                        {
                            //摄像头已关闭
                            if(cameraDevice_Back==null)
                            {
                                Log.d("createCameraPreviewSession", "camera is closed");
                                return;
                            }
                            Log.d("createCameraPreviewSession", "CameraCaptureSession.StateCallback().onConfigured() " );
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession_Back = session;
                            try{
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder_Back.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                //setAutoFlash(mPreviewRequestBuilder);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest_Back = mPreviewRequestBuilder_Back.build();
                                mCaptureSession_Back.setRepeatingRequest(mPreviewRequest_Back,  mCaptureCallback_Back, cameraHandler_Back);

                                //
                                //mCaptureSession_Back.setRepeatingRequest(mPreviewRequestBuilder_Back.build(), mCaptureCallback_Back, cameraHandler_Back);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session)
                        {
                            Log.d("createCameraPreviewSession", "onConfigureFailed");
                        }
                    }, null);

        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //实时传输摄像头画面，每次onImageAvailableListener检测到有可用图片时都会调用
    //需要在上面的语句中和相关变量绑定才有效
    private int count = 0;
    public void setOnImageAvailableListener()
    {
        mOnImageAvailableListener = new ImageReader.OnImageAvailableListener()
        {
            private static final int SHOW_PER_IMG = 4;          //每多少张图片发送一次（不要发得太快）
            @Override
            public void onImageAvailable(ImageReader reader)
            {
                if(count++ > SHOW_PER_IMG)
                {
                    count = 0;
                }
               // Log.d("ImageReader.OnImageAvailableListener.onImageAvailable", "count: "+ count );
                Image img = reader.acquireNextImage();  //必须有这个语句和结尾的close(),否则画面会卡住
                if(count == SHOW_PER_IMG)
                {
                    //Log.d("ImageReader.OnImageAvailableListener.onImageAvailable", "try packge an image ");
                    //打包图片并发送
                    ByteBuffer buffer = img.getPlanes()[0].getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    Bitmap bitmapImage = BitmapFactory.decodeByteArray(data, 0, data.length, null);
                    //TBD
                    //Log.d("ImageReader.OnImageAvailableListener.onImageAvailable", "try send byte image ");
                    byte[] compressData = getBitmapData(bitmapImage);
                    sendByteArray(compressData);
                    //Log.d("ImageReader.OnImageAvailableListener.onImageAvailable", "after sending an image ");
                }
                img.close();
            }
        };
    }

    //处理图像数据相关函数
    private byte[] getBitmapData(Bitmap photo)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        return baos.toByteArray();
    }

    private void sendByteArray(byte[] data)
    {
        mSocket_Image = mSocket_Control;
        if(mSocket_Image == null)
        {
            Log.d("send image: ", "socket not connected " );
            return;
        }

        try{
            OutputStream out = mSocket_Image.getOutputStream();
            out.write(NetTools.intToBytes(data.length));
            out.write(data);
            // Log.d("send image: ", "after out.write()" );
        }catch (Exception e){
            Log.d("send image: ", "sen message failed " );
            e.printStackTrace();
        }
    }


    /*************************************************************************************************************************/

    //按下连接控制端的按钮
    public void clickBtnWifi(View view)
    {
        //Toast.makeText(PhoneOnCarActivity.this, "手持端 IP： ", Toast.LENGTH_SHORT).show();
        startConnect_Control();
    }


    //开启一个子线程用于建立连接，一个子线程用于接受控制数据
    public void startConnect_Control()
    {
        mConnectThread_Control = new SocketConnectThread_Control();
        mConnectThread_Control.start();

        try {
            mConnectThread_Control.join();
        }catch (Exception e){
            e.printStackTrace();
        }

        mReceiveThread_Control = new SocketReceiveThread_Control();
        mReceiveThread_Control.start();
    }

    //建立连接的子线程
    class SocketConnectThread_Control extends Thread
    {

        public SocketConnectThread_Control()
        {
            Intent intent = new Intent(PhoneOnCarActivity.this, CaptureActivity.class);
            startActivityForResult(intent,REQUEST_CODE_QR_STRING);
            // String content = intent.getStringExtra("codedContent");
            Log.d(TAG, "code content is " + newActivityResultString);
            Log.d(TAG, "test is: " + test);
        }

        @Override
        public void run()
        {

            try{
                Log.d(TAG, "try connecting: " + newActivityResultString);
                mPhoneOnCarIpAddress_Control = newActivityResultString;
                //mPhoneOnCarIpAddress_Control = "192.168.1.108";
                mPhoneOnCarPort_Control = 60000;

                mSocket_Control = new Socket(mPhoneOnCarIpAddress_Control, mPhoneOnCarPort_Control);
                if(mSocket_Control != null)
                {
                    mOutStream_Control = mSocket_Control.getOutputStream();
                    mInStream_Control = mSocket_Control.getInputStream();
                }
                else {
                    Log.d(TAG, " connect fail");
                }
            }
            catch (Exception e){
                Log.d(TAG, "failed");
                e.printStackTrace();
                return;
            }
            Log.d(TAG, "connect success");
        }
    }

    //接受控制指令的子线程
    //可能同时用于控制小车移动
    class SocketReceiveThread_Control extends Thread
    {

        public SocketReceiveThread_Control()
        {

        }

        public void run()
        {
            byte[] buffer = new byte[4];
            while (!interrupted())
            {
                try{
                    if(buffer == null)
                    {
                        Log.d(TAG, "buffer is null");
                    }
                    int count = mInStream_Control.read(buffer);
                    if(count == -1){
                        Log.d(TAG, "read -1");
                        break;
                    }
                    else {
                        String receiveData = new String(buffer, 0, count);
                        Log.d(TAG, "read buffer:"+receiveData+",count="+count);
                        //Toast.makeText(this, "Phone On Hand", Toast.LENGTH_SHORT).show();
                        if(receiveData.length()!=0)
                            send(receiveData.charAt(0));
                    }
                }
                catch (Exception e){
                    //Log.d(TAG, "read control msg error");
                }
            }
        }

    }



    public void onClickBtnBlueTooth(View view)
    {
        if ("连接蓝牙".equals(buttonConnect.getText().toString())) {
            if (!mbluetoothAdapter.isEnabled()){
                startActivityForResult(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), RESULT_CODE);
            } else {
                connectBtSocket();
            }

        } else {
            disconnect();
        }
    }

    public void onClickBtnTest(View view)
    {
        send('3');
    }

    public void send(char command) {
        try {
            if (btSocket != null) {
                OutputStream os = btSocket.getOutputStream();   //Bluetooth connection output stream
                os.write(command);
                Toast.makeText(this, "DELIVERED SUCCESS", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请先连接蓝牙", Toast.LENGTH_SHORT).show();
            }
            Log.d(TAG, "向小车发送" + command);

        } catch (IOException e) {
        }
    }


    // Help us find the physical address of the Bluetooth module that needs to be connected
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, "Searched Bluetooth device;  device name: " + device.getName() + "  device address: " + device.getAddress());
            }
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Log.i(TAG, "ACTION_ACL_CONNECTED");
                if (btSocket.isConnected()) {
                    buttonConnect.setText("蓝牙已连接");
                }
            }
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Log.i(TAG, "ACTION_ACL_CONNECTED");
                if (btSocket.isConnected()) {
                    buttonConnect.setText("连接蓝牙");
                }
            }
        }
    };

    private void connectBtSocket() {
        // Get the handle of the Bluetooth device
        bluetoothDevice = mbluetoothAdapter.getRemoteDevice(bluetoothDeviceMacAddress);
        //Turn off scanning before pairing
        if (mbluetoothAdapter.isDiscovering()) {
            mbluetoothAdapter.cancelDiscovery();
        }
        // Get the connected socket
        try {
            btSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID
                    .fromString(MY_UUID));
            btSocket.connect();  //Connection socket
        } catch (IOException e) {
            Toast.makeText(this, "Connection failed, can't get Socket！" + e, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        if (btSocket.isConnected()) {
            Log.i(TAG, "socket connected");
            Toast.makeText(this, "connect success", Toast.LENGTH_SHORT).show();
            buttonConnect.setText("蓝牙已连接");
        } else {
            Log.i(TAG, "socket didn't connected");
            Toast.makeText(this, "connect failed", Toast.LENGTH_SHORT).show();
        }
    }


    private void disconnect() {
        try {
            if (btSocket != null) {
                btSocket.close();
            }
        } catch (IOException e) {
        }
        buttonConnect.setText("连接蓝牙");
    }

}
