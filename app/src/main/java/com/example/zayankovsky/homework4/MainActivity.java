package com.example.zayankovsky.homework4;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.RenderScript;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;

import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 200;

    private Bitmap mBitmapIn;
    private Bitmap mBitmapOut;
    private CustomView mCustomView;

    private Allocation mInAllocation;
    private Allocation mOutAllocation;
    private ScriptC_saturation mScript;

    private RenderScriptTask currentTask;

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
                Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                handleSendImage(imageUri); // Handle single image being sent. Initialize UI
                createScript(); // Create renderScript
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.save).setEnabled(mCustomView != null && mCustomView.getBitmap() != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.open:
                Intent requestFileIntent = new Intent(Intent.ACTION_PICK);
                requestFileIntent.setType("image/*");
                startActivityForResult(requestFileIntent, 0);
                return true;
            case R.id.save:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                            this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE
                    );
                } else {
                    MediaStore.Images.Media.insertImage(getContentResolver(), mCustomView.getBitmap(), null, null);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        // If the selection worked
        if (resultCode == RESULT_OK) {
            // Get the file's content URI from the incoming Intent
            Uri returnUri = returnIntent.getData();
            handleSendImage(returnUri);
            createScript();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay!
                MediaStore.Images.Media.insertImage(getContentResolver(), mCustomView.getBitmap(), null, null);
            }
        }
    }

    private void handleSendImage(Uri imageUri) {
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
        seekbar.setOnSeekBarChangeListener(null);
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
    Invoke AsyncTask and cancel previous task.
    When AsyncTasks are piled up (typically in slow device with heavy kernel),
    Only the latest (and already started) task invokes RenderScript operation.
     */
    private void updateImage(final float f) {
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        currentTask = new RenderScriptTask();
        currentTask.execute(f);
    }

    /*
     * In the AsyncTask, it invokes RenderScript intrinsics to do a filtering.
     * After the filtering is done, an operation blocks at Allocation.copyTo() in AsyncTask thread.
     * Once all operation is finished at onPostExecute() in UI thread, it can invalidate and update ImageView UI.
     */
    private class RenderScriptTask extends AsyncTask<Float, Void, Void> {
        boolean issued;

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

    /**
     * Custom view that is not necessary at all.
     */
    private static class CustomView extends View {
        private Bitmap bitmap;
        private Rect fullRect;
        private RectF rect;

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

            fullRect = new Rect(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), h - getPaddingBottom());
            if (bitmap != null) {
                updateRect();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (rect != null) {
                canvas.drawBitmap(bitmap, null, rect, null);
            }
        }

        private Bitmap getBitmap() {
            return bitmap;
        }

        private void setBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            if (fullRect != null) {
                updateRect();
            }
        }

        private void updateRect() {
            int fullWidth = fullRect.width();
            int fullHeight = fullRect.height();

            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();

            int value_1 = fullWidth * bitmapHeight;
            int value_2 = fullHeight * bitmapWidth;

            rect = new RectF(fullRect);
            if (value_1 > value_2) {
                rect.inset((fullWidth - (float) value_2 / bitmapHeight) / 2, 0);
            } else if (value_1 < value_2) {
                rect.inset(0, (fullHeight - (float) value_1 / bitmapWidth) / 2);
            }
        }
    }
}