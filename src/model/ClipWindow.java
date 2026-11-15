package model;


public final class ClipWindow {

    private static final int MIN_SIZE = 20;
    private final int defaultMinX;
    private final int defaultMinY;
    private final int defaultMaxX;
    private final int defaultMaxY;
    private int minX;
    private int minY;
    private int maxX;
    private int maxY;

    public ClipWindow(int minX, int minY, int maxX, int maxY) {
        if (maxX - minX < MIN_SIZE || maxY - minY < MIN_SIZE) {
            throw new IllegalArgumentException("Window too small");
        }
        this.defaultMinX = minX;
        this.defaultMinY = minY;
        this.defaultMaxX = maxX;
        this.defaultMaxY = maxY;
        reset();
    }
    public int getMinX() {
        return minX;
    }
    public int getMinY() {
        return minY;
    }
    public int getMaxX() {
        return maxX;
    }
    public int getMaxY() {
        return maxY;
    }

    public int findCornerNear(Point point, double radius) {
        int nearest = -1;
        double nearestDistance = radius;
        for (int i = 0; i < 4; i++) {
            Point corner = getCorner(i);
            double distance = Math.hypot(corner.getX() - point.getX(), corner.getY() - point.getY());
            if (distance <= nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
    public boolean moveCorner(int index, Point position) {
        int newMinX = minX;
        int newMinY = minY;
        int newMaxX = maxX;
        int newMaxY = maxY;
        if (index == 0 || index == 3) {
            newMinX = position.getX();
        } else {
            newMaxX = position.getX();
        }
        if (index == 0 || index == 1) {
            newMinY = position.getY();
        } else {
            newMaxY = position.getY();
        }

        if (newMaxX - newMinX < MIN_SIZE || newMaxY - newMinY < MIN_SIZE) {
            return false;
        }
        minX = newMinX;
        minY = newMinY;
        maxX = newMaxX;
        maxY = newMaxY;
        return true;
    }


    public void reset() {
        minX = defaultMinX;
        minY = defaultMinY;
        maxX = defaultMaxX;
        maxY = defaultMaxY;
    }

    public Polygon toPolygon(int color) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 3; i++) {
            polygon.addLine(new Line(getCorner(i), getCorner(i + 1), color));
        }
        return polygon;
    }
    private Point getCorner(int index) {
        return switch (index) {
            case 0 -> new Point(minX, minY);
            case 1 -> new Point(maxX, minY);
            case 2 -> new Point(maxX, maxY);
            default -> new Point(minX, maxY);
        };
    }

}