package com.example.zayankovsky.homework4;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;

import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
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
                handleSendImage(intent); // Handle single image being sent
            }
        }
    }

    private void handleSendImage(Intent intent) {
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            CustomView customView = (CustomView) findViewById(R.id.custom);
            if (customView != null) {
                try {
                    customView.setBitmap(BitmapFactory.decodeStream(
                            getContentResolver().openInputStream(imageUri)
                    ));
                } catch (FileNotFoundException ignored) {}
            }
        }
    }

    /**
     * Custom view that is not necessary at all.
     */
    public static class CustomView extends View {
        private Bitmap bitmap = null;
        private RectF dst;

        /**
         * Class constructor taking a context and an attribute set.
         * This constructor is used by the layout engine
         * to construct a {@link CustomView} from a set of XML attributes.
         *
         * @param context
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

            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, dst, null);
            }
        }


        private void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
        }
    }
}

