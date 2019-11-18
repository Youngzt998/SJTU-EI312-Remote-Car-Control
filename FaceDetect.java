package com.ytchen.facedetect;


import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;


public class FaceDetect extends Activity {


        private Boolean checkCameraHardware(Context context){
            if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
                return true;
            }
            else
                return false;
        }
        public static class CameraInfo {
            public static final int CAMERA_FACING_BACK = 0;

            public static final int CAMERA_FACING_FRONT = 1;

            /**
            * 这个值就是标明相机是前置还是后置
            * CAMERA_FACING_BACK or CAMERA_FACING_FRONT.
            */
            public int facing;

            public int orientation;
        };

        @Override
        protected void onCreate(Bundle savedInstanceState){
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

        }

}
