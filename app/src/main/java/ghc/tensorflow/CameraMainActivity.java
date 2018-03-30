package ghc.tensorflow;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Created by guohongcheng on 2018/03/05.
 * 相机应用的主Activity
 */

public class CameraMainActivity extends Activity implements View.OnClickListener {
    private final static String TAG = "CameraMainActivity";

    // SurfaceView用于展示Camera获得的数据
    private SurfaceView surfaceView;
    // 点击拍照按钮
    private Button mShutterBtn;

    private LocalCameraManager mLocalCamManager;

    // 设置拍照键是否可以点击，防止多次点击拍照按钮造成资源浪费
    private static final int ENABLE_PIC_BTN = 1;

    // 展示识别结果界面
    private static final int MSG_2_SHOW_RESULT = 2;

    private Classifier classifier;
    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";
    private static String MODEL_FILE = "file:///android_asset/model/tensorflow_inception_graph.pb";
    private static String LABEL_FILE = "file:///android_asset/model/imagenet_comp_graph_label_strings.txt";

    private android.app.FragmentManager fragmentManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 设置为全屏显示，没有标题栏，没有状态栏
        Log.d(TAG, "onCreate >> begin.");
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        initView();
        initClassifier();
        fragmentManager = getFragmentManager();
    }

    private void initClassifier() {
        classifier = TensorFlowImageClassifier.create(
                getAssets(),
                MODEL_FILE,
                LABEL_FILE,
                INPUT_SIZE,
                IMAGE_MEAN,
                IMAGE_STD,
                INPUT_NAME,
                OUTPUT_NAME
        );
    }

    private void initView() {
        Log.d(TAG, "initView >> begin.");
        mShutterBtn = (Button) findViewById(R.id.btn_takePic);
        mShutterBtn.setOnClickListener(this);

        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);

        // 创建新的Camera对象
        if (surfaceView != null) {
            mLocalCamManager = new LocalCameraManager(this, surfaceView);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume ..");
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mBatInfoReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause ..");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop ..");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy ..");
        if (mLocalCamManager != null) {
            mLocalCamManager.stop();
        }
        if (mBatInfoReceiver != null) {
            try {
                unregisterReceiver(mBatInfoReceiver);
            } catch (Exception e) {
                Log.e(TAG, "unregisterReceiver mBatInfoReceiver failure :" + e.getCause());
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            // 点击的是拍照键
            case R.id.btn_takePic:
                // add by guohongcheng_20180109
                // 空指针保护，压力测试中此处发生了空指针crash
                long takePicTime = System.currentTimeMillis();
                if (mLocalCamManager != null) {
                    mLocalCamManager.takePhoto(new LocalCameraManager.TakePhotoResultCallBack() {
                        @Override
                        public void onSuccess(String picPath) {
                            Log.d(TAG, "picPath: " + picPath);
                            // 拍照完成后扫描，添加到系统数据库
                            scanFile(picPath);
//                            finish();
                            Log.d(TAG, picPath + " 图片保存成功 ");
//                            Intent intent = new Intent(CameraMainActivity.this, RecActivity.class);
//                            intent.putExtra("picPath", picPath);
//                            startActivity(intent);
                            handleMsg(picPath);
//                            showResultFragment();
                        }

                        @Override
                        public void onError() {

                        }
                    });
                    // 防止拍照按钮频繁多次点击，添加拍照按钮点击时间间隔，800ms后恢复可点击
                    mShutterBtn.setEnabled(false);
                    mHandler.sendEmptyMessageDelayed(ENABLE_PIC_BTN, 800);// 800ms
                }
                break;

            default:
                break;
        }

    }

    private void toShowResult(String result, String path) {
        Message msg = new Message();
        msg.what = MSG_2_SHOW_RESULT;
        Bundle bundle = new Bundle();
        bundle.putString("result", result);
        bundle.putString("path", path);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    /*
     * 防止拍照按钮多次点击耗费资源
    */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int result = msg.what;
            switch (result) {
                case ENABLE_PIC_BTN:
                    Log.d(TAG, "mHandler]>> ENABLE_PIC_BTN ..");
                    mShutterBtn.setEnabled(true);
                    break;

                case MSG_2_SHOW_RESULT:
//                    mShutterBtn.setVisibility(View.GONE);
                    Bundle bundle = msg.getData();
                    String results = bundle.getString("result");
                    String path = bundle.getString("path");
                    showResultFragment(path, results);
                    break;


                default:
                    break;

            }
        }
    };

    /**
     * 在相机界面，按电源键，直接退出应用，避免相机SurfaceView冻屏
     */
    private final BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                //退出程序...
                if (mLocalCamManager != null) {
                    mLocalCamManager.stop();
                }
                finish();
            }
        }
    };


    /**
     * 扫描文件
     *
     * @param path
     */
    private void scanFile(String path) {
        MediaScannerConnection.scanFile(CameraMainActivity.this, new String[]{path},
                null, new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.e("TAG", "onScanCompleted");
                    }
                });
    }

    private void handleMsg(final String path) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Bitmap bitmap = getBitmap(path, INPUT_SIZE);
                    List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);
                    String res = "";
                    DecimalFormat df = new DecimalFormat("0.00%");
                    for (Classifier.Recognition recognition : results) {

                        res += recognition.getTitle() + " " + df.format(recognition.getConfidence()) + "\n";
                    }
                    toShowResult(res, path);
                } catch (Exception e) {
                    e.printStackTrace();
                }


            }
        }).start();
    }


    private Bitmap getBitmap(String path, int size) {
        Bitmap bitmap = getLocalBitmap(path);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = ((float) size) / width;
        float scaleHeight = ((float) size) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    private Bitmap getLocalBitmap(String path) {
        Bitmap bitmap = null;
        try {
            FileInputStream fis = new FileInputStream(path);
            bitmap = BitmapFactory.decodeStream(fis);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
            /* try catch  可以解决OOM后出现的崩溃，然后采取相应的解决措施，如缩小图片，较少内存使用
            * 但这不是解决OOM的根本方法，因为这个地方是压缩骆驼的最后一颗稻草，
            * 解决方法是dump内存，找到内存异常原因。*/
        } catch (OutOfMemoryError error) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                bitmap = null;
            }
            System.gc();
        }
        return bitmap;
    }

    private Drawable getDrawableFromPath(String path) {
        return new BitmapDrawable(getResources(), getLocalBitmap(path));
    }

    private void showResultFragment(String path, String result) {
        ResultFragment resultFragment = ResultFragment.newInstance(path, result);
        resultFragment.show(fragmentManager, "resultFragment");
    }

}
