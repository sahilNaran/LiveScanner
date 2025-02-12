package za.co.knuckles.livescanner;

import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

public class Quadrilateral {
    public final MatOfPoint2f contour;
    public final Point[] points;

    public Quadrilateral(MatOfPoint2f contour, Point[] points) {
        this.contour = contour;
        this.points = points;
    }
}
