package ghc.tensorflow;

import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * Created by guohongcheng on 2018/3/24.
 */

public class ResultFragment extends DialogFragment {

    private String picPath, result;

    private View mFragment;
    private RelativeLayout mRLResult;
    private TextView tvResult;

    public static ResultFragment newInstance(String path, String result) {
        ResultFragment resultFragment = new ResultFragment();
        resultFragment.picPath = path;
        resultFragment.result = result;
        Log.d("ResultFragment", "result " + result + " path " + path);
        return resultFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        mFragment = inflater.inflate(R.layout.result_fragment, container, false);
        initView();
        return mFragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    private void initView() {
        mRLResult = (RelativeLayout) mFragment.findViewById(R.id.rl_rec);
        tvResult = (TextView) mFragment.findViewById(R.id.result);
        mRLResult.setBackgroundDrawable(getDrawableFromPath(picPath));
        tvResult.setText(result);
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

}
