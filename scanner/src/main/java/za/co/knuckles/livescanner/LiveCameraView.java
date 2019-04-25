package za.co.knuckles.livescanner;

import android.app.Activity;
import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.shapes.PathShape;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.List;

import static org.opencv.core.CvType.CV_8UC1;

public class LiveCameraView extends FrameLayout implements SurfaceHolder.Callback {
    private static final String TAG = LiveCameraView.class.getSimpleName();

    SurfaceView mSurfaceView;

    private final CameraViewCanvasOverlay cameraViewCanvasOverlay;

    private int vHeight = 0;
    private int vWidth = 0;

    private final Context context;
    private final IScanner scanner;

    private Camera camera;
    private CountDownTimer autoCaptureTimer;
    private int secondsLeft;
    private boolean isAutoCaptureScheduled;
    private Camera.Size previewSize;
    private boolean isCapturing = false;


    public LiveCameraView(Context context, IScanner scanner) {
        super(context);
        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView);
        this.context = context;
        this.cameraViewCanvasOverlay = new CameraViewCanvasOverlay(context);
        addView(cameraViewCanvasOverlay);
        SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.addCallback(this);
        this.scanner = scanner;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            requestLayout();
            openCamera();
            this.camera.setPreviewDisplay(holder);
            setPreviewCallback();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (vWidth == vHeight) {
            return;
        }
        if (previewSize == null) {
            previewSize = camera.getParameters().getPreviewSize();
        }

        Camera.Parameters parameters = camera.getParameters();
        camera.setDisplayOrientation(ImageScanUtils.configureCameraAngle((Activity) context));
        parameters.setPreviewSize(previewSize.width, previewSize.height);
        if (parameters.getSupportedFocusModes() != null
                && parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (parameters.getSupportedFocusModes() != null
                && parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        Camera.Size size = ImageScanUtils.determinePictureSize(camera, parameters.getPreviewSize());
        parameters.setPictureSize(size.width, size.height);
        parameters.setPictureFormat(ImageFormat.JPEG);

        camera.setParameters(parameters);
        requestLayout();
        setPreviewCallback();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopPreviewAndFreeCamera();
    }

    public void setPreviewCallback() {
        this.camera.startPreview();
        this.camera.setPreviewCallback(previewCallback);
    }

    public void immediateCapture() {
        isCapturing = true;
        scanner.displayHint(Hints.CAPTURING_IMAGE);

        camera.takePicture(mShutterCallBack, null, pictureCallback);
        camera.setPreviewCallback(null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        vWidth = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        vHeight = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(vWidth, vHeight);
        previewSize = ImageScanUtils.getOptimalPreviewSize(camera, vWidth, vHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if(getChildCount() <= 0){
            return;
        }
        int width = r - l;
        int height = b - t;

        int previewWidth = width;
        int previewHeight = height;

        if (previewSize != null) {
            previewWidth = previewSize.width;
            previewHeight = previewSize.height;

            int displayOrientation = ImageScanUtils.configureCameraAngle((Activity) context);
            if (displayOrientation == 90 || displayOrientation == 270) {
                previewWidth = previewSize.height;
                previewHeight = previewSize.width;
            }
        }

        int nW;
        int nH;
        int top;
        int left;

        float scale = 1.0f;

        if (width * previewHeight < height * previewWidth) {
            Log.d(TAG, "center horizontally");
            int scaledChildWidth = (int) ((previewWidth * height / previewHeight) * scale);
            nW = (width + scaledChildWidth) / 2;
            nH = (int) (height * scale);
            top = 0;
            left = (width - scaledChildWidth) / 2;
        } else {
            Log.d(TAG, "center vertically");
            int scaledChildHeight = (int) ((previewHeight * width / previewWidth) * scale);
            nW = (int) (width * scale);
            nH = (height + scaledChildHeight) / 2;
            top = (height - scaledChildHeight) / 2;
            left = 0;
        }
        mSurfaceView.layout(left, top, nW, nH);
        cameraViewCanvasOverlay.layout(left, top, nW, nH);
    }

    private void stopPreviewAndFreeCamera() {
        if (camera != null) {
            // Call stopPreview() to stop updating the preview surface.
            camera.stopPreview();
            camera.setPreviewCallback(null);
            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            camera.release();
            camera = null;
        }
    }

    private void openCamera() {
        if (camera != null) {
            return;
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        int defaultCameraId = 0;
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                defaultCameraId = i;
            }
        }
        camera = Camera.open(defaultCameraId);
        Camera.Parameters cameraParams = camera.getParameters();

        List<String> flashModes = cameraParams.getSupportedFlashModes();
        if (null != flashModes && flashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
            cameraParams.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        }

        camera.setParameters(cameraParams);
    }

    private final Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (camera == null) {
                return;
            }
            try {
                Camera.Size pictureSize = camera.getParameters().getPreviewSize();
                Log.d(TAG, "onPreviewFrame - received image " + pictureSize.width + "x" + pictureSize.height);

                Mat yuv = new Mat(new Size(pictureSize.width, pictureSize.height * 1.5), CV_8UC1);
                yuv.put(0, 0, data);

                Mat mat = new Mat(new Size(pictureSize.width, pictureSize.height), CvType.CV_8UC4);
                Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2BGR_NV21, 4);
                yuv.release();

                Size originalPreviewSize = mat.size();
                int originalPreviewArea = mat.rows() * mat.cols();

                Quadrilateral largestQuad = ImageScanUtils.detectLargestQuadrilateral(mat);
                clearAndInvalidateCanvas();

                mat.release();

                if (largestQuad != null) {
                    drawLargestRect(largestQuad.contour, largestQuad.points, originalPreviewSize, originalPreviewArea);
                } else {
                    showFindingReceiptHint();
                }
            } catch (Exception e) {
                showFindingReceiptHint();
            }
        }
    };

    private void showFindingReceiptHint() {
        scanner.displayHint(Hints.FIND_RECT);
        clearAndInvalidateCanvas();
    }

    private void clearAndInvalidateCanvas() {
        cameraViewCanvasOverlay.clear();
        invalidateCanvas();
    }

    private void invalidateCanvas() {
        cameraViewCanvasOverlay.invalidate();
    }

    private void drawLargestRect(MatOfPoint2f approx, Point[] points, Size stdSize, int previewArea) {
        Path path = new Path();
        // ATTENTION: axis are swapped
        float previewWidth = (float) stdSize.height;
        float previewHeight = (float) stdSize.width;

        Log.i(TAG, "previewWidth: " + String.valueOf(previewWidth));
        Log.i(TAG, "previewHeight: " + String.valueOf(previewHeight));

        //Points are drawn in anticlockwise direction
        path.moveTo(previewWidth - (float) points[0].y, (float) points[0].x);
        path.lineTo(previewWidth - (float) points[1].y, (float) points[1].x);
        path.lineTo(previewWidth - (float) points[2].y, (float) points[2].x);
        path.lineTo(previewWidth - (float) points[3].y, (float) points[3].x);
        path.close();

        double area = Math.abs(Imgproc.contourArea(approx));

        PathShape newBox = new PathShape(path, previewWidth, previewHeight);
        Paint paint = new Paint();
        Paint border = new Paint();

        //Height calculated on Y axis
        double resultHeight = points[1].x - points[0].x;
        double bottomHeight = points[2].x - points[3].x;
        if (bottomHeight > resultHeight)
            resultHeight = bottomHeight;

        //Width calculated on X axis
        double resultWidth = points[3].y - points[0].y;
        double bottomWidth = points[2].y - points[1].y;
        if (bottomWidth > resultWidth)
            resultWidth = bottomWidth;

        Log.i(TAG, "resultWidth: " + String.valueOf(resultWidth));
        Log.i(TAG, "resultHeight: " + String.valueOf(resultHeight));

        EdgeDetectionProperties imgDetectionPropsObj
                = new EdgeDetectionProperties(previewWidth, previewHeight, resultWidth, resultHeight,
                previewArea, area, points[0], points[1], points[2], points[3]);

        final Hints hint;

        if (imgDetectionPropsObj.isDetectedAreaBeyondLimits()) {
            hint = Hints.FIND_RECT;
            cancelAutoCapture();
        } else if (imgDetectionPropsObj.isDetectedAreaBelowLimits()) {
            cancelAutoCapture();
            if (imgDetectionPropsObj.isEdgeTouching()) {
                hint = Hints.MOVE_AWAY;
            } else {
                hint = Hints.MOVE_CLOSER;
            }
        }
//        else if (imgDetectionPropsObj.isDetectedHeightAboveLimit()) {
//            cancelAutoCapture();
//            hint = Hints.MOVE_AWAY;
//        }
        else if ( imgDetectionPropsObj.isDetectedAreaAboveLimit()) { //imgDetectionPropsObj.isDetectedWidthAboveLimit() ||
            cancelAutoCapture();
            hint = Hints.MOVE_AWAY;
        } else {
            if (imgDetectionPropsObj.isEdgeTouching()) {
                cancelAutoCapture();
                hint = Hints.MOVE_AWAY;
            } else if (imgDetectionPropsObj.isAngleNotCorrect(approx)) {
                cancelAutoCapture();
                hint = Hints.ADJUST_ANGLE;
            } else {
                hint = Hints.CAPTURING_IMAGE;
                clearAndInvalidateCanvas();

                if (!isAutoCaptureScheduled) {
                    scheduleAutoCapture(hint);
                }
            }
        }

        border.setStrokeWidth(5);
        scanner.displayHint(hint);
        setPaintAndBorder(hint, paint, border);
        cameraViewCanvasOverlay.clear();
        cameraViewCanvasOverlay.addShape(newBox, paint, border);
        invalidateCanvas();
    }

    private void scheduleAutoCapture(final Hints scanHint) {
        isAutoCaptureScheduled = true;
        secondsLeft = 0;
        autoCaptureTimer = new CountDownTimer(2000, 100) {
            public void onTick(long millisUntilFinished) {
                if (Math.round((float) millisUntilFinished / 1000.0f) != secondsLeft) {
                    secondsLeft = Math.round((float) millisUntilFinished / 1000.0f);
                }
                switch (secondsLeft) {
                    case 1:
                        autoCapture(scanHint);
                        break;
                    default:
                        break;
                }
            }

            public void onFinish() {
                isAutoCaptureScheduled = false;
            }
        };
        autoCaptureTimer.start();
    }

    private void autoCapture(Hints scanHint) {
        if (isCapturing) return;
        if (Hints.CAPTURING_IMAGE.equals(scanHint)) {
            try {
                isCapturing = true;
                scanner.displayHint(Hints.CAPTURING_IMAGE);

                camera.takePicture(mShutterCallBack, null, pictureCallback);
                camera.setPreviewCallback(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private final Camera.ShutterCallback mShutterCallBack = new Camera.ShutterCallback() {
        @Override
        public void onShutter() {
            if (context != null) {
                AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (mAudioManager != null)
                    mAudioManager.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
            }
        }
    };

    private final Camera.PictureCallback pictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            camera.stopPreview();
            scanner.displayHint(Hints.NO_MESSAGE);
            clearAndInvalidateCanvas();

            Bitmap bitmap = ImageScanUtils.decodeBitmapFromByteArray(data,
                    ScanConstants.HIGHER_SAMPLING_THRESHOLD, ScanConstants.HIGHER_SAMPLING_THRESHOLD);

            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            scanner.onPictureClicked(bitmap);
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    isCapturing = false;
                }
            }, 3000);

        }
    };

    private void cancelAutoCapture() {
        if (isAutoCaptureScheduled) {
            isAutoCaptureScheduled = false;
            if (null != autoCaptureTimer) {
                autoCaptureTimer.cancel();
            }
        }
    }

    private void setPaintAndBorder(Hints hint, Paint paint, Paint border) {
        int paintColor = 0;
        int borderColor = 0;

        switch (hint) {
            case MOVE_CLOSER:
            case MOVE_AWAY:
            case ADJUST_ANGLE:
                paintColor = Color.argb(30, 255, 38, 0);
                borderColor = Color.rgb(255, 38, 0);
                break;
            case FIND_RECT:
                paintColor = Color.argb(0, 0, 0, 0);
                borderColor = Color.argb(0, 0, 0, 0);
                break;
            case CAPTURING_IMAGE:
                paintColor = Color.argb(30, 38, 216, 76);
                borderColor = Color.rgb(38, 216, 76);
                break;
        }

        paint.setColor(paintColor);
        border.setColor(borderColor);
    }


}
