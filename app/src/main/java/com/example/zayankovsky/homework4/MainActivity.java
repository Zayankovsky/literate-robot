package com.example.zayankovsky.homework4;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;

import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    private Bitmap mBitmapIn;
    private Bitmap mBitmapOut;
    private CustomView mCustomView;

    private Allocation mInAllocation;
    private Allocation mOutAllocation;
    private ScriptC_saturation mScript;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                handleSendImage(intent); // Handle single image being sent. Initialize UI
                createScript(); // Create renderScript
            }
        }
    }

    private void handleSendImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri == null) {
            return;
        }

        try {
            mBitmapIn = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
        } catch (FileNotFoundException e) {
            return;
        }
        mBitmapOut = Bitmap.createBitmap(mBitmapIn.getWidth(), mBitmapIn.getHeight(), mBitmapIn.getConfig());

        mCustomView = (CustomView) findViewById(R.id.customView);
        if (mCustomView == null) {
            return;
        }
        mCustomView.setBitmap(mBitmapIn);
        mCustomView.invalidate();

        SeekBar seekbar = (SeekBar) findViewById(R.id.seekBar);
        if (seekbar == null) {
            return;
        }
        seekbar.setVisibility(View.VISIBLE);
        seekbar.setProgress(50);
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateImage(progress / 50f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    /*
     * Initialize RenderScript
     * It creates RenderScript kernel that performs saturation manipulation.
     */
    private void createScript() {
        //Initialize RS
        RenderScript mRS = RenderScript.create(this);

        //Allocate buffers
        mInAllocation = Allocation.createFromBitmap(mRS, mBitmapIn);
        mOutAllocation = Allocation.createFromBitmap(mRS, mBitmapOut);

        //Load script
        mScript = new ScriptC_saturation(mRS);
    }

    /*
     * In the AsyncTask, it invokes RenderScript intrinsics to do a filtering.
     * After the filtering is done, an operation blocks at Allocation.copyTo() in AsyncTask thread.
     * Once all operation is finished at onPostExecute() in UI thread, it can invalidate and update ImageView UI.
     */
    private class RenderScriptTask extends AsyncTask<Float, Void, Void> {
        Boolean issued = false;

        protected Void doInBackground(Float... values) {
            if (!isCancelled()) {
                issued = true;

                // Set global variable in RS
                mScript.set_saturationValue(values[0]);

                // Invoke saturation filter kernel
                mScript.forEach_saturation(mInAllocation, mOutAllocation);

                // Copy to bitmap and invalidate image view
                mOutAllocation.copyTo(mBitmapOut);
            }
            return null;
        }

        void updateView() {
            if (issued) {
                // Request UI update
                mCustomView.setBitmap(mBitmapOut);
                mCustomView.invalidate();
            }
        }

        protected void onPostExecute(Void result) {
            updateView();
        }

        protected void onCancelled(Void result) {
            updateView();
        }
    }

    private RenderScriptTask currentTask = null;

    /*
    Invoke AsyncTask and cancel previous task.
    When AsyncTasks are piled up (typically in slow device with heavy kernel),
    Only the latest (and already started) task invokes RenderScript operation.
     */
    private void updateImage(final float f) {
        if (currentTask != null)
            currentTask.cancel(false);
        currentTask = new RenderScriptTask();
        currentTask.execute(f);
    }

    /**
     * Custom view that is not necessary at all.
     */
    public static class CustomView extends View {
        private Bitmap bitmap = null;
        private RectF dst = null;

        /**
         * Class constructor taking a context and an attribute set.
         * This constructor is used by the layout engine
         * to construct a {@link CustomView} from a set of XML attributes.
         *
         * @param attrs   An attribute set which can contain
         *                attributes inherited from {@link View}.
         */
        public CustomView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            if (bitmap == null) {
                return;
            }

            int left = getPaddingLeft(), right = w - getPaddingRight();
            int top = getPaddingTop(), bottom = h - getPaddingBottom();

            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            int value_1 = (right - left) * bitmapHeight;
            int value_2 = (bottom - top) * bitmapWidth;

            if (value_1 > value_2) {
                float center = (left + right) / 2f;
                float halfWidth = value_2 / 2f / bitmapHeight;
                dst = new RectF(center - halfWidth, top, center + halfWidth, bottom);
            } else if (value_1 < value_2) {
                float center = (top + bottom) / 2f;
                float halfHeight = value_1 / 2f / bitmapWidth;
                dst = new RectF(left, center - halfHeight, right, center + halfHeight);
            } else {
                dst = new RectF(left, top, right, bottom);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (dst != null) {
                canvas.drawBitmap(bitmap, null, dst, null);
            }
        }

        private void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }
}