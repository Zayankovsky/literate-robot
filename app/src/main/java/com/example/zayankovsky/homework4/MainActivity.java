package com.example.zayankovsky.homework4;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.Matrix3f;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.ScriptIntrinsicBlur;
import android.support.v8.renderscript.ScriptIntrinsicColorMatrix;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;

import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 200;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 210;

    private Bitmap mBitmapIn;
    private Bitmap mBitmapOut;
    private CustomView mCustomView;
    private SeekBar mSeekBar;
    private RadioGroup mRadioGroupChannel;

    private Allocation mInAllocation;
    private Allocation mOutAllocation;

    private ScriptIntrinsicBlur mScriptBlur;
    private ScriptIntrinsicColorMatrix mScriptColorMatrix;

    private final int MODE_BLUR = 0;
    private final int MODE_BRIGHTNESS = 1;
    private final int MODE_CHANNEL = 2;

    private final int MODE_CHANNEL_RED = 3;
    private final int MODE_CHANNEL_GREEN = 4;
    private final int MODE_CHANNEL_BLUE = 5;

    private int mFilterMode = MODE_BLUR;
    private int mFilterChannelMode = MODE_CHANNEL_RED;

    private RenderScriptTask mLatestTask;

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
                handleImage(imageUri); // Handle single image being sent
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
            handleImage(returnUri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // permission was granted, yay!
            switch (requestCode) {
                case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                    MediaStore.Images.Media.insertImage(getContentResolver(), mCustomView.getBitmap(), null, null);
                    break;
                case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION:
                    // Register the listener with the Location Manager to receive location updates
                    requestLocationUpdate();
                    break;
            }
        }
    }

    private void handleImage(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        /*
         * Initialize UI
         */

        //Set up custom view
        try {
            mBitmapIn = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
        } catch (FileNotFoundException e) {
            return;
        }
        mBitmapOut = mBitmapIn.copy(mBitmapIn.getConfig(), true);

        mCustomView = (CustomView) findViewById(R.id.customView);
        if (mCustomView == null) {
            return;
        }
        mCustomView.setBitmap(mBitmapOut);
        mCustomView.invalidate();

        //Set up seekBar
        mSeekBar = (SeekBar) findViewById(R.id.seekBar);
        if (mSeekBar == null) {
            return;
        }
        mSeekBar.setVisibility(View.VISIBLE);
        mSeekBar.setProgress(mFilterMode == MODE_BLUR ? 0 : 50);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateImage(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        //Setup effect selector
        mRadioGroupChannel = (RadioGroup) findViewById(R.id.radioGroupChannel);
        if (mRadioGroupChannel == null) {
            return;
        }

        initRadioButton(R.id.radioBlur, MODE_BLUR);
        initRadioButton(R.id.radioBrightness, MODE_BRIGHTNESS);
        initRadioButton(R.id.radioChannel, MODE_CHANNEL);
        initRadioButton(R.id.radioChannelRed, MODE_CHANNEL_RED);
        initRadioButton(R.id.radioChannelGreen, MODE_CHANNEL_GREEN);
        initRadioButton(R.id.radioChannelBlue, MODE_CHANNEL_BLUE);

        RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioGroupFilter);
        if (radioGroup == null) {
            return;
        }
        radioGroup.setVisibility(View.VISIBLE);

        /*
         * Create renderScript
         */
        createScript();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            );
        } else {
            requestLocationUpdate();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void requestLocationUpdate() {
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the location provider.
                mCustomView.setLocation(location);
                mCustomView.invalidate();
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        // Register the listener with the Location Manager to receive location update
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null);
        locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null);
    }

    /*
    Invoke AsyncTask and cancel previous task.
    When AsyncTasks are piled up (typically in slow device with heavy kernel),
    Only the latest (and already started) task invokes RenderScript operation.
     */
    private void updateImage(int progress) {
        float value = getFilterParameter(progress);

        if (mLatestTask != null) {
            mLatestTask.cancel(false);
        }
        mLatestTask = new RenderScriptTask();

        mLatestTask.execute(value);
    }

    private void initRadioButton(int id, final int filterMode) {
        RadioButton radioButton = (RadioButton) findViewById(id);
        if (radioButton == null) {
            return;
        }
        radioButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    switch (filterMode) {
                        case MODE_BLUR:case MODE_BRIGHTNESS:
                            mRadioGroupChannel.setVisibility(View.GONE);
                            mFilterMode = filterMode;
                            break;
                        case MODE_CHANNEL:
                            mRadioGroupChannel.setVisibility(View.VISIBLE);
                            mFilterMode = filterMode;
                            break;
                        case MODE_CHANNEL_RED:case MODE_CHANNEL_GREEN:case MODE_CHANNEL_BLUE:
                            mFilterChannelMode = filterMode;
                            break;
                    }
                    mBitmapIn = mBitmapOut.copy(mBitmapOut.getConfig(), true);
                    mSeekBar.setProgress(filterMode == MODE_BLUR ? 0 : 50);
                    createScript();
                }
            }
        });
    }

    /*
     * Initialize RenderScript
     * It creates RenderScript kernels that perform blur, brightness or color manipulation.
     */
    private void createScript() {
        //Initialize RS
        RenderScript mRS = RenderScript.create(this);

        //Allocate buffers
        mInAllocation = Allocation.createFromBitmap(mRS, mBitmapIn);
        mOutAllocation = Allocation.createFromBitmap(mRS, mBitmapOut);

        //Load scripts
        mScriptBlur = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));
        mScriptColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));
    }

    /*
    Convert seekBar progress parameter (0-100 in range) to parameter for each intrinsic filter.
     */
    private float getFilterParameter(int progress) {
        float f = 0.f;
        switch (mFilterMode) {
            case MODE_BLUR:
                return progress / 4f;
            case MODE_BRIGHTNESS:
                return (progress - 50) / 255f;
            case MODE_CHANNEL:
                return progress / 50f;
        }
        return f;

    }

    private void performFilter(float value) {
        switch (mFilterMode) {
            case MODE_BLUR:
                if (value > 0) {
                    //Set blur kernel size
                    mScriptBlur.setRadius(value);

                    // Invoke filter kernel
                    mScriptBlur.setInput(mInAllocation);
                    mScriptBlur.forEach(mOutAllocation);
                } else {
                    mOutAllocation.copyFrom(mInAllocation);
                }
                break;
            case MODE_BRIGHTNESS:
                mScriptColorMatrix.setColorMatrix(new Matrix3f(new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1}));
                mScriptColorMatrix.setAdd(value, value, value, 0);

                // Invoke filter kernel
                mScriptColorMatrix.forEach(mInAllocation, mOutAllocation);
                break;
            case MODE_CHANNEL:
                if (value != 1) {
                    switch (mFilterChannelMode) {
                        case MODE_CHANNEL_RED:
                            mScriptColorMatrix.setColorMatrix(new Matrix3f(new float[]{value, 0, 0, 0, 1, 0, 0, 0, 1}));
                            break;
                        case MODE_CHANNEL_GREEN:
                            mScriptColorMatrix.setColorMatrix(new Matrix3f(new float[]{1, 0, 0, 0, value, 0, 0, 0, 1}));
                            break;
                        case MODE_CHANNEL_BLUE:
                            mScriptColorMatrix.setColorMatrix(new Matrix3f(new float[]{1, 0, 0, 0, 1, 0, 0, 0, value}));
                            break;
                    }

                    // Invoke filter kernel
                    mScriptColorMatrix.forEach(mInAllocation, mOutAllocation);
                } else {
                    mOutAllocation.copyFrom(mInAllocation);
                }
                break;
        }

        // Copy to bitmap and invalidate custom view
        mOutAllocation.copyTo(mBitmapOut);
    }

    /**
     * Custom view that is not necessary at all.
     */
    private static class CustomView extends View {
        private Bitmap mBitmap;
        private Rect mFullRect;
        private RectF mRect;

        private String mTextLat, mTextLong;
        private final Paint mTextPaint;
        private final int mTextHeight;

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

            mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mTextPaint.setTextAlign(Paint.Align.RIGHT);

            TypedArray typedArray = getContext().obtainStyledAttributes(
                    android.R.style.TextAppearance_Small,
                    new int[] {android.R.attr.textSize}
            );
            mTextPaint.setTextSize(typedArray.getDimensionPixelSize(0, 42));
            typedArray.recycle();

            String text = " -.0123456789:EILNadefginotuy";
            Rect bounds = new Rect();
            mTextPaint.getTextBounds(text, 0, text.length(), bounds);
            mTextHeight = bounds.height();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            mFullRect = new Rect(getPaddingLeft(), getPaddingTop(),
                    w - getPaddingRight(), h - getPaddingBottom() - 2 * mTextHeight);
            if (mBitmap != null) {
                updateRect();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (mRect != null) {
                canvas.drawBitmap(mBitmap, null, mRect, null);

                if (mTextLat != null) {
                    canvas.drawText(mTextLat, mRect.right, mRect.bottom + mTextHeight, mTextPaint);
                    canvas.drawText(mTextLong, mRect.right, mRect.bottom + 2 * mTextHeight, mTextPaint);
                }
            }
        }

        private Bitmap getBitmap() {
            return mBitmap;
        }

        private void setBitmap(Bitmap bitmap) {
            this.mBitmap = bitmap;
            if (mFullRect != null) {
                updateRect();
            }
        }

        private void setLocation(Location location) {
            mTextLat = "Latitude: " + location.getLatitude();
            mTextLong = "Longitude: " + location.getLongitude();
        }

        private void updateRect() {
            int fullWidth = mFullRect.width();
            int fullHeight = mFullRect.height();

            int bitmapWidth = mBitmap.getWidth();
            int bitmapHeight = mBitmap.getHeight();

            int value_1 = fullWidth * bitmapHeight;
            int value_2 = fullHeight * bitmapWidth;

            mRect = new RectF(mFullRect);
            if (value_1 > value_2) {
                mRect.inset((fullWidth - (float) value_2 / bitmapHeight) / 2, 0);
            } else if (value_1 < value_2) {
                mRect.inset(0, (fullHeight - (float) value_1 / bitmapWidth) / 2);
            }
        }
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

                performFilter(values[0]);
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
}