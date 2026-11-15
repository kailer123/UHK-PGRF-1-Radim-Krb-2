package model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClippingPolygon {

    private final List<Point> defaultVertices;
    private final List<Point> vertices;

    public ClippingPolygon(List<Point> defaultVertices) {
        if (defaultVertices.size() < 3 || !isConvex(defaultVertices)) {
            throw new IllegalArgumentException("Clipping polygon has to be more than 3 polygon and be convex");
        }
        this.defaultVertices = List.copyOf(defaultVertices);
        this.vertices = new ArrayList<>(defaultVertices);
    }
    public int findVertexNear(Point point, double radius) {
        int nearest = -1;
        double nearestDistance = radius;
        for (int i = 0; i < vertices.size(); i++) {
            Point vertex = vertices.get(i);
            double distance = Math.hypot(vertex.getX() - point.getX(), vertex.getY() - point.getY());
            if (distance <= nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public boolean moveVertex(int index, Point position) {
        Point original = vertices.get(index);
        vertices.set(index, position);
        if (isConvex(vertices)) {
            return true;
        }
        vertices.set(index, original);
        return false;
    }

    public void reverseOrientation() {
        Collections.reverse(vertices);
    }
    public boolean isClockwiseOnScreen() {
        return signedArea() > 0;
    }
    public void reset() {
        vertices.clear();
        vertices.addAll(defaultVertices);
    }

    public Polygon toPolygon(int color) {
        Polygon polygon = new Polygon();
        for (int i = 0; i + 1 < vertices.size(); i++) {
            polygon.addLine(new Line(vertices.get(i), vertices.get(i + 1), color));
        }
        return polygon;
    }

    private double signedArea() {
        double sum = 0;
        for (int i = 0; i < vertices.size(); i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % vertices.size());
            sum += (double) a.getX() * b.getY() - (double) b.getX() * a.getY();
        }
        return sum / 2;
    }

    private static boolean isConvex(List<Point> vertices) {
        int n = vertices.size();
        int sign = 0;
        for (int i = 0; i < n; i++) {
            Point a = vertices.get(i);
            Point b = vertices.get((i + 1) % n);
            for (int j = 0; j < n; j++) {
                if (j == i || j == (i + 1) % n) {
                    continue;
                }
                Point p = vertices.get(j);
                long cross = (long) (b.getX() - a.getX()) * (p.getY() - a.getY())
                        - (long) (b.getY() - a.getY()) * (p.getX() - a.getX());
                if (cross == 0) {
                    return false;
                }
                int side = cross > 0 ? 1 : -1;
                if (sign == 0) {
                    sign = side;
                } else if (side != sign) {
                    return false;
                }
            }
        }
        return true;
    }

}