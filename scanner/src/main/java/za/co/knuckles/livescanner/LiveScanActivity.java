package za.co.knuckles.livescanner;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import static android.view.View.GONE;

public class LiveScanActivity extends AppCompatActivity implements IScanner, View.OnClickListener {

    private static final String TAG = LiveScanActivity.class.getSimpleName();
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
    private boolean isPermissionNotGranted;

    private ViewGroup containerScan;
    private FrameLayout cameraPreviewLayout;
    private LiveCameraView mLiveCameraView;

    private TextView captureHintText;
    private LinearLayout captureHintLayout;

    public final static Stack<PolygonPoints> allDraggedPointsStack = new Stack<>();
    private PolygonView polygonView;
    private ImageView cropImageView;
    private View cropAcceptBtn;
    private View cropRejectBtn;
    private Bitmap copyBitmap;
    private FrameLayout cropLayout;
    private ImageView immediateCapture;
    private ImageView placementHint;
    private TextView backgroundHintText;

    private boolean isImmediateCapture = false;

    static {
        System.loadLibrary("opencv_java3");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_livecamera);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        init();
    }

    private void init() {
        containerScan = findViewById(R.id.container_scan);
        cameraPreviewLayout = findViewById(R.id.camera_preview);
        captureHintLayout = findViewById(R.id.capture_hint_layout);
        captureHintText = findViewById(R.id.capture_hint_text);
        polygonView = findViewById(R.id.polygon_view);
        cropImageView = findViewById(R.id.crop_image_view);
        cropAcceptBtn = findViewById(R.id.crop_accept_btn);
        cropRejectBtn = findViewById(R.id.crop_reject_btn);
        cropLayout = findViewById(R.id.crop_layout);
        immediateCapture = findViewById(R.id.immediateCapture);
        placementHint = findViewById(R.id.image_placement_hint);
        backgroundHintText = findViewById(R.id.dark_background_hint);

        cropAcceptBtn.setOnClickListener(this);
        cropRejectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TransitionManager.beginDelayedTransition(containerScan);
                cropLayout.setVisibility(GONE);
                mLiveCameraView.setPreviewCallback();
                isImmediateCapture = false;
                immediateCapture.setVisibility(View.VISIBLE);
                placementHint.setVisibility(View.VISIBLE);
                backgroundHintText.setVisibility(View.VISIBLE);
            }
        });
        immediateCapture.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                mLiveCameraView.immediateCapture();
                isImmediateCapture = true;
                immediateCapture.setVisibility(GONE);
            }
        });

        checkCameraPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void displayHint(Hints scanHint) {
        captureHintLayout.setVisibility(View.VISIBLE);
        switch (scanHint) {
            case FIND_RECT:
                captureHintText.setText(getResources().getString(R.string.finding_rect));
                captureHintLayout.setBackground(getResources().getDrawable(R.drawable.hint_white));
                break;
            case CAPTURING_IMAGE:
                captureHintText.setText(getResources().getString(R.string.hold_still));
                captureHintLayout.setBackground(getResources().getDrawable(R.drawable.hint_green));
                break;
            case MOVE_CLOSER:
            case MOVE_AWAY:
            case ADJUST_ANGLE:
            case NO_MESSAGE:
                captureHintLayout.setVisibility(GONE);
                break;
            default:
                break;
        }
        /*
                        captureHintText.setText(getResources().getString(R.string.move_closer));
                captureHintLayout.setBackground(getResources().getDrawable(R.drawable.hint_white));
                        captureHintText.setText(getResources().getString(R.string.move_away));
                captureHintLayout.setBackground(getResources().getDrawable(R.drawable.hint_white));
                        captureHintText.setText(getResources().getString(R.string.adjust_angle));
                captureHintLayout.setBackground(getResources().getDrawable(R.drawable.hint_white));
        * */
    }

    @Override
    public void onPictureClicked(Bitmap bitmap) {
        immediateCapture.setVisibility(GONE);
        placementHint.setVisibility(GONE);
        backgroundHintText.setVisibility(GONE);
        try {
            copyBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            int height = getWindow().findViewById(Window.ID_ANDROID_CONTENT).getHeight();
            int width = getWindow().findViewById(Window.ID_ANDROID_CONTENT).getWidth();

            copyBitmap = ImageScanUtils.resizeToScreenContentSize(copyBitmap, width, height);
            Mat originalMat = new Mat(copyBitmap.getHeight(), copyBitmap.getWidth(), CvType.CV_8UC1);
            Utils.bitmapToMat(copyBitmap, originalMat);
            ArrayList<PointF> points;
            Map<Integer, PointF> pointFs = new HashMap<>();
            try {
                Quadrilateral quad = ImageScanUtils.detectLargestQuadrilateral(originalMat);
                if (quad != null || !isImmediateCapture) {
                    double resultArea = Math.abs(Imgproc.contourArea(quad.contour));
                    double previewArea = originalMat.rows() * originalMat.cols();
                    if (resultArea > previewArea * 0.08) {
                        points = new ArrayList<>();
                        points.add(new PointF((float) quad.points[0].x, (float) quad.points[0].y));
                        points.add(new PointF((float) quad.points[1].x, (float) quad.points[1].y));
                        points.add(new PointF((float) quad.points[3].x, (float) quad.points[3].y));
                        points.add(new PointF((float) quad.points[2].x, (float) quad.points[2].y));
                    } else {
                        points = ImageScanUtils.getPolygonDefaultPoints(copyBitmap);
                    }

                } else {
                    points = ImageScanUtils.getPolygonDefaultPoints(copyBitmap);
                }

                int index = -1;
                for (PointF pointF : points) {
                    pointFs.put(++index, pointF);
                }

                polygonView.setPoints(pointFs);
                int padding = (int) getResources().getDimension(R.dimen.scan_padding);
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(copyBitmap.getWidth() + 2 * padding, copyBitmap.getHeight() + 2 * padding);
                layoutParams.gravity = Gravity.CENTER;
                polygonView.setLayoutParams(layoutParams);
                TransitionManager.beginDelayedTransition(containerScan);
                cropLayout.setVisibility(View.VISIBLE);

                cropImageView.setImageBitmap(copyBitmap);
                cropImageView.setScaleType(ImageView.ScaleType.FIT_XY);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA:
                onRequestCamera(grantResults);
                break;
            default:
                break;
        }
    }

    @Override
    public void onClick(View v) {
        Map<Integer, PointF> points = polygonView.getPoints();

        Bitmap croppedBitmap;

        if (ImageScanUtils.isScanPointsValid(points)) {
            Point point1 = new Point(points.get(0).x, points.get(0).y);
            Point point2 = new Point(points.get(1).x, points.get(1).y);
            Point point3 = new Point(points.get(2).x, points.get(2).y);
            Point point4 = new Point(points.get(3).x, points.get(3).y);
            croppedBitmap = ImageScanUtils.enhanceScan(copyBitmap, point1, point2, point3, point4);
        } else {
            croppedBitmap = copyBitmap;
        }

        String path = ImageScanUtils.saveToInternalMemory(croppedBitmap, ScanConstants.IMAGE_DIR,
                ScanConstants.IMAGE_NAME, LiveScanActivity.this, 90)[0];
        setResult(Activity.RESULT_OK, new Intent().putExtra(ScanConstants.SCANNED_RESULT, path));
        System.gc();

        finish();
    }

    private void checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            isPermissionNotGranted = true;
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {
                Toast.makeText(this, "Enable camera permission from settings", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        MY_PERMISSIONS_REQUEST_CAMERA);
            }
        } else {
            if (!isPermissionNotGranted) {
                mLiveCameraView = new LiveCameraView(LiveScanActivity.this, this);
                cameraPreviewLayout.addView(mLiveCameraView);
            } else {
                isPermissionNotGranted = false;
            }
        }
    }

    private void onRequestCamera(int[] grantResults) {
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLiveCameraView = new LiveCameraView(LiveScanActivity.this, LiveScanActivity.this);
                            cameraPreviewLayout.addView(mLiveCameraView);
                        }
                    });
                }
            }, 500);

        } else {
            Toast.makeText(this, "No permissions", Toast.LENGTH_SHORT).show();
            this.finish();
        }
    }


}
