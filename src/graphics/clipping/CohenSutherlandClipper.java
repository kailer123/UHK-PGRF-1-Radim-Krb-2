package graphics.clipping;

import model.ClipWindow;
import model.Line;
import model.Point;
public final class CohenSutherlandClipper implements LineClipper {

    private static final int INSIDE = 0; // 0000
    private static final int LEFT = 1;   // 0001
    private static final int RIGHT = 2;  // 0010
    private static final int TOP = 4;    // 0100
    private static final int BOTTOM = 8; // 1000

    @Override
    public Line clip(Line line, ClipWindow window) {
        double x0 = line.getPoint1().getX();
        double y0 = line.getPoint1().getY();
        double x1 = line.getPoint2().getX();
        double y1 = line.getPoint2().getY();
        int code0 = regionCode(x0, y0, window);
        int code1 = regionCode(x1, y1, window);

        while (true) {
            if ((code0 | code1) == INSIDE) {
                return new Line(toPoint(x0, y0), toPoint(x1, y1), line.getColor());
            }
            if ((code0 & code1) != 0) {
                return null;
            }


            int outside = code0 != INSIDE ? code0 : code1;
            double x;
            double y;

            if ((outside & TOP) != 0) {
                x = x0 + (x1 - x0) * (window.getMinY() - y0) / (y1 - y0);
                y = window.getMinY();
            } else if ((outside & BOTTOM) != 0) {
                x = x0 + (x1 - x0) * (window.getMaxY() - y0) / (y1 - y0);
                y = window.getMaxY();
            } else if ((outside & RIGHT) != 0) {
                y = y0 + (y1 - y0) * (window.getMaxX() - x0) / (x1 - x0);
                x = window.getMaxX();
            } else {
                y = y0 + (y1 - y0) * (window.getMinX() - x0) / (x1 - x0);
                x = window.getMinX();
            }

            if (outside == code0) {
                x0 = x;
                y0 = y;
                code0 = regionCode(x0, y0, window);
            } else {
                x1 = x;
                y1 = y;
                code1 = regionCode(x1, y1, window);
            }
        }
    }
    private int regionCode(double x, double y, ClipWindow window) {
        int code = INSIDE;
        if (x < window.getMinX()) {
            code |= LEFT;
        } else if (x > window.getMaxX()) {
            code |= RIGHT;
        }
        if (y < window.getMinY()) {
            code |= TOP;
        } else if (y > window.getMaxY()) {
            code |= BOTTOM;
        }
        return code;
    }

    private Point toPoint(double x, double y) {
        return new Point((int) Math.round(x), (int) Math.round(y));
    }

}