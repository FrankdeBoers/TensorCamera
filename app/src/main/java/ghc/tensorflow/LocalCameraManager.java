package ghc.tensorflow;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by guohongcheng on 2018/03/05.
 */
public class LocalCameraManager implements SurfaceHolder.Callback {
    private final static String TAG = "LocalCameraManager";

    private final int PREVIEW_SIZE_WIDTH = 1280;
    private final int PREVIEW_SIZE_HEIGHT = 720;

    private final int TAKE_PHOTO_SUCCESS = 103;   // 拍照成功

    // 照片路径
    private String IMG_FILE_PATH
            = Environment.getExternalStoragePublicDirectory("DCIM").getAbsolutePath() + "/Camera";

    private SurfaceView mSurfaceview;
    private SurfaceHolder mHolder;
    private Camera mCamera;

    // 默认前置或者后置相机 0:后置 1:前置
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera.Parameters mParameters;

    private IOrientationEventListener orienListener;
    private Lock mLock = new ReentrantLock();                          // 锁对象

    private Activity mActivity;

    Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int result = msg.what;
            switch (result) {
                case TAKE_PHOTO_SUCCESS:
                    startPreview(); //重新预览
                    // Toast.makeText(mActivity, "照片保存到: DCIM/Camera", Toast.LENGTH_SHORT).show();
                    break;

            }
        }
    };

    public LocalCameraManager(Activity activity, final SurfaceView surfaceView) {
        Log.d(TAG, "[LocalCameraManager()] >> create.");
        mSurfaceview = surfaceView;
        mActivity = activity;
        orienListener = new IOrientationEventListener(mActivity);
        mHolder = mSurfaceview.getHolder();
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated");
        startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e("TAG", "surfaceDestroyed");
        stop();
    }

    /**
     * 获取Camera实例
     *
     * @return
     */
    private Camera getCamera(int id) {
        Camera camera = null;
        try {
            camera = Camera.open(id);
        } catch (Exception e) {
        }
        return camera;
    }

    /**
     * 开始预览
     */
    public void startPreview() {
        Log.d(TAG, "[startPreview] >> begin.");
        if (mCamera == null) {
            mCamera = getCamera(mCameraId);
        }

        if (orienListener != null) {
            orienListener.enable();
        }

        try {
            Log.d(TAG, "[startPreview] >> setCameraParameters.");
            setCameraParameters();
            mCamera.setPreviewDisplay(mHolder);
            setCameraDisplayOrientation(mActivity, mCameraId, mCamera);
            mCamera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 可能是意外停止相机 释放资源
     */
    public void stop() {
        if (orienListener != null) {
            orienListener.disable();
        }
        releaseCamera();
    }

    public interface TakePhotoResultCallBack {
        void onSuccess(String picPath);

        void onError();
    }

    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 设置Camera参数
     */
    private void setCameraParameters() {
        if (mCamera != null) {
            Log.d(TAG, "[setCameraParameters] >> begin. mCamera != null");
            mParameters = mCamera.getParameters();
            mParameters.setPictureSize(PREVIEW_SIZE_WIDTH, PREVIEW_SIZE_HEIGHT);
            mParameters.setPreviewSize(PREVIEW_SIZE_WIDTH, PREVIEW_SIZE_HEIGHT);
            /*List<Size> videoSize = mParameters.getSupportedVideoSizes();
            for (int i = 0; i < videoSize.size(); i++) {
                Log.d(TAG, "getSupportedVideoSizes : width " + videoSize.get(i).width + " ;height " + videoSize.get(i).height);
            }

            List<Size> picSize = mParameters.getSupportedPictureSizes();
            for (int i = 0; i < picSize.size(); i++) {
                Log.d(TAG, "getSupportedPictureSizes : width " + picSize.get(i).width + " ;height " + picSize.get(i).height);
            }

            List<Size> preSize = mParameters.getSupportedPreviewSizes();
            for (int i = 0; i < preSize.size(); i++) {
                Log.d(TAG, "getSupportedPreviewSizes : width " + preSize.get(i).width + " ;height " + preSize.get(i).height);
            }*/

            List<String> focusModes = mParameters.getSupportedFocusModes();
            if (focusModes != null && focusModes.size() > 0) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    mParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);  //设置自动对焦
                }
            }
            mCamera.setParameters(mParameters);
        } else {
            Log.e(TAG, "[setCameraParameters] >> mCamera == null!!");
        }
    }

    private boolean isTakingPic = false;

    /**
     * 开始拍照
     */
    public void takePhoto(final TakePhotoResultCallBack mTakePhotoResultCallBack) {
        if (isTakingPic) {
            return;
        }
        isTakingPic = true;

        mCamera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera mCamera) {
                // 启动存储照片的线程
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mLock.lock();
                        byte[] dataMirror = rotateAndMirror(data, 0, false);
                        File dir = new File(IMG_FILE_PATH);
                        if (!dir.exists()) {
                            dir.mkdirs();      // 创建文件夹
                        }
                        String name = "IMG_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance()) + ".jpg";
                        File file = new File(dir, name);
                        FileOutputStream outputStream;
                        try {
                            outputStream = new FileOutputStream(file);
                            outputStream.write(dataMirror);             // 写入sd卡中
                            outputStream.close();                      // 关闭输出流
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        mHandler.sendEmptyMessage(TAKE_PHOTO_SUCCESS);
                        isTakingPic = false;
                        mTakePhotoResultCallBack.onSuccess(file.getAbsolutePath());
                        mLock.unlock();
                    }
                }).start();
            }
        });
    }

    /**
     * 保证预览方向正确
     *
     * @param activity
     * @param cameraId
     * @param camera
     */
    private void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG, "[setCameraDisplayOrientation] >> rotation = " + rotation + " 相机 " + info.orientation);
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public class IOrientationEventListener extends OrientationEventListener {
        public IOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (ORIENTATION_UNKNOWN == orientation) {
                return;
            }
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, info);
            orientation = (orientation + 45) / 90 * 90;
            int rotation = 0;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                rotation = (info.orientation - orientation + 360) % 360;
            } else {
                rotation = (info.orientation + orientation) % 360;
            }
            if (null != mCamera) {
                Camera.Parameters parameters = mCamera.getParameters();
                // 不随手表转动改变方向，要不然会导致图片旋转90°
                parameters.setRotation(rotation);
                Log.d(TAG, "rotation: " + rotation);
                mCamera.setParameters(parameters);
            }
        }
    }

    /**
     * 实现反转、镜像
     *
     * @param data    [源数据]
     * @param degrees [旋转角度]
     * @param mirror  [是否镜像]
     * @return [description]
     */
    public byte[] rotateAndMirror(byte[] data, int degrees, boolean mirror) {
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
        Log.d(TAG, "b.getHeight(): " + b.getHeight());
        if (b.getHeight() == 320) {
            degrees = 90;
        }
        if ((degrees != 0 || mirror) && b != null) {
            Matrix m = new Matrix();
            // Mirror first.
            // horizontal flip + rotation = -rotation + horizontal flip
            if (mirror) {
                m.postScale(-1, 1);
                degrees = (degrees + 360) % 360;
                if (degrees == 0 || degrees == 180) {
                    m.postTranslate(b.getWidth(), 0);
                } else if (degrees == 90 || degrees == 270) {
                    m.postTranslate(b.getHeight(), 0);
                } else {
                    throw new IllegalArgumentException("Invalid degrees=" + degrees);
                }
            }
            if (degrees != 0) {
                // clockwise
                m.postRotate(degrees,
                        (float) b.getWidth() / 2, (float) b.getHeight() / 2);
            }

            try {
                Bitmap b2 = Bitmap.createBitmap(
                        b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (b != b2) {
                    b.recycle();
                    b = b2;
                }

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 30, outputStream);
                byte[] scaleData = outputStream.toByteArray();
                b.recycle();
                return scaleData;
            } catch (OutOfMemoryError ex) {
                // We have no memory to rotate. Return the original bitmap.
                ex.printStackTrace();
            }
        } else {
            return data;
        }
        return null;
    }
}
