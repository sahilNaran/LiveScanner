package za.co.knuckles.livescanner;

import android.util.Log;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

public class EdgeDetectionProperties {
    private final double previewWidth;
    private final double previewHeight;
    private final double resultWidth;
    private final double resultHeight;
    private final Point topLeftPoint;
    private final Point bottomLeftPoint;
    private final Point bottomRightPoint;
    private final Point topRightPoint;
    private final double previewArea;
    private final double resultArea;

    public EdgeDetectionProperties(double previewWidth, double previewHeight, double resultWidth,
                                    double resultHeight, double previewArea, double resultArea,
                                    Point topLeftPoint, Point bottomLeftPoint, Point bottomRightPoint,
                                    Point topRightPoint) {
        this.previewWidth   = previewWidth;
        this.previewHeight  = previewHeight;
        this.previewArea    = previewArea;
        this.resultWidth    = resultWidth;
        this.resultHeight   = resultHeight;
        this.resultArea     = resultArea;
        this.bottomLeftPoint    = bottomLeftPoint;
        this.bottomRightPoint   = bottomRightPoint;
        this.topLeftPoint       = topLeftPoint;
        this.topRightPoint      = topRightPoint;
    }

    public boolean isDetectedAreaBeyondLimits() {
        return resultArea > previewArea * 0.95  || resultArea < previewArea * 0.10;
    }

    public boolean isDetectedAreaBelowLimits() {
        return resultArea < previewArea * 0.10;
    }

    public boolean isDetectedWidthAboveLimit() {
        return resultWidth / previewWidth > 0.9;
    }

    public boolean isDetectedHeightAboveLimit() {
        return resultHeight / previewHeight > 0.9;
    }

    public boolean isDetectedAreaAboveLimit() {
        return resultArea > previewArea * 0.75;
    }


    public boolean isAngleNotCorrect(MatOfPoint2f approx) {
        return getMaxCosine(approx) || isLeftEdgeDistorted() || isRightEdgeDistorted();
    }

    private boolean isRightEdgeDistorted() {
        return Math.abs(topRightPoint.y - bottomRightPoint.y) > 300;
    }

    private boolean isLeftEdgeDistorted() {
        return Math.abs(topLeftPoint.y - bottomLeftPoint.y) > 300;
    }

    private boolean getMaxCosine(MatOfPoint2f approx) {
        double maxCosine = 0;
        Point[] approxPoints = approx.toArray();
        maxCosine = ImageScanUtils.getMaxCosine(maxCosine, approxPoints);
        Log.d("Max cosine", "getMaxCosine: " + maxCosine);
        return maxCosine >= 0.170; //(smallest angle is below 170 deg)
    }

    public boolean isEdgeTouching() {
        return isTopEdgeTouching() || isBottomEdgeTouching() || isLeftEdgeTouching() || isRightEdgeTouching();
    }

    public boolean isReceiptToughingSides() {
        return isLeftEdgeTouching() || isRightEdgeTouching();
    }

    public boolean isReceiptTouchingTopOrBottom() {
        return isTopEdgeTouching() || isBottomEdgeTouching();
    }

    public boolean isReceiptTouchingTopAndBottom() {
        return isTopEdgeTouchingProper() && isBottomEdgeTouchingProper();
    }

    private boolean isBottomEdgeTouchingProper() {
        return (bottomLeftPoint.x >= previewHeight - 10 || bottomRightPoint.x >= previewHeight - 10);
    }

    private boolean isTopEdgeTouchingProper() {
        return (topLeftPoint.x <= 10 || topRightPoint.x <= 10);
    }

    private boolean isBottomEdgeTouching() {
        return (bottomLeftPoint.x >= previewHeight - 50 || bottomRightPoint.x >= previewHeight - 50);
    }

    private boolean isTopEdgeTouching() {
        return (topLeftPoint.x <= 50 || topRightPoint.x <= 50);
    }

    private boolean isRightEdgeTouching() {
        return (topRightPoint.y >= previewWidth - 50 || bottomRightPoint.y >= previewWidth - 50);
    }

    private boolean isLeftEdgeTouching() {
        return (topLeftPoint.y <= 50 || bottomLeftPoint.y <= 50);
    }
}
