package controller;

import graphics.filler.ScanlineFiller;
import graphics.filler.RecursiveSeedFiller;
import graphics.filler.SeedFiller;
import graphics.clipping.PolygonClipper;
import graphics.clipping.SutherlandHodgmanClipper;
import graphics.rasterizer.PolygonOutlineRasterizer;
import graphics.rasterizer.PolygonRasterizer;
import graphics.rasterizer.XiaolinWuLineRasterizer;
import model.Line;
import model.Point;
import model.Polygon;
import model.ClippingPolygon;
import view.Canvas;
import view.gui.Toolbar;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;

import javax.swing.AbstractAction;

import static java.awt.event.KeyEvent.VK_C;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;

public class Controller {

    private final Canvas canvas;
    private final Toolbar toolbar;

    private Line previewLine;
    private final Polygon polygon;
    private final PolygonClipper clipper;
    private final ClippingPolygon clippingPolygon;
    private final PolygonRasterizer polygonRasterizer;

    private int draggedVertex = NO_VERTEX;
    private Point seedFillPoint;
    private boolean seedFillMode;
    private final SeedFiller seedFiller;
    private final ScanlineFiller scanlineFiller;

    private static final int LINE_COLOR = Color.WHITE.getRGB();
    private static final int PREVIEW_COLOR = Color.RED.getRGB();
    private static final int ORIGINAL_COLOR = new Color(0x9099A3).getRGB();
    private static final int CLIPPED_COLOR = new Color(0xA4F2DF).getRGB();
    private static final int CLIPPING_POLYGON_COLOR = new Color(0xE6B85C).getRGB();
    private static final int FILL_COLOR = new Color(0x58BFA9).getRGB();

    private static final int NO_VERTEX = -1;
    private static final int GRAB_RADIUS = 10;
    private static final List<Point> DEFAULT_CLIPPING_VERTICES = List.of(
            new Point(250, 90),
            new Point(450, 200),
            new Point(400, 450),
            new Point(120, 450),
            new Point(60, 220)
    );

    public Controller(Canvas canvas, Toolbar toolbar) {
        this.canvas = canvas;
        this.toolbar = toolbar;
        this.polygon = new Polygon();
        this.clipper = new SutherlandHodgmanClipper();
        this.scanlineFiller = new ScanlineFiller(canvas.getRaster());
        this.seedFiller = new RecursiveSeedFiller(canvas.getRaster());
        this.polygonRasterizer = new PolygonOutlineRasterizer(new XiaolinWuLineRasterizer(canvas.getRaster()));
        this.clippingPolygon = new ClippingPolygon(DEFAULT_CLIPPING_VERTICES);
    }

    public void init() {
        toolbar.addClearListener(_ -> clearDrawing());
        toolbar.addSeedFillListener(_ -> toggleSeedFillMode());
        toolbar.addOrientationListener(_ -> toggleOrientation());
        toolbar.setClockwise(clippingPolygon.isClockwiseOnScreen());
        canvas.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(getKeyStroke(VK_C, 0), "clearCanvas");
        canvas.getActionMap().put("clearCanvas", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                clearDrawing();
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    if (seedFillMode) {
                        fillAt(event);
                    } else {
                        startLine(event);
                    }
                } else if (event.getButton() == MouseEvent.BUTTON3) {
                    draggedVertex = clippingPolygon.findVertexNear(getPoint(event), GRAB_RADIUS);
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON3) {
                    draggedVertex = NO_VERTEX;
                    return;
                }
                if (event.getButton() != MouseEvent.BUTTON1 || previewLine == null) {
                    return;
                }
                polygon.addLine(new Line(previewLine.getPoint1(), getPoint(event), LINE_COLOR));
                previewLine = null;
                render();
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (draggedVertex != NO_VERTEX) {
                    dragVertex(getPoint(event));
                } else if (previewLine != null) {
                    previewLine = new Line(previewLine.getPoint1(), getPoint(event), PREVIEW_COLOR);
                    render();
                }
            }
        });

        render();
    }

    private void render() {
        canvas.clear();
        Polygon boundary = clippingPolygon.toPolygon(CLIPPING_POLYGON_COLOR);
        Polygon clipped = clipper.clip(withPreviewLine(polygon, previewLine), boundary);

        scanlineFiller.fill(clipped, FILL_COLOR);
        polygonRasterizer.rasterize(withColor(polygon, ORIGINAL_COLOR), previewLine);
        polygonRasterizer.rasterize(withColor(clipped, CLIPPED_COLOR), null);
        polygonRasterizer.rasterize(boundary, null);
        if (seedFillPoint != null) {
            seedFiller.fill(seedFillPoint.getX(), seedFillPoint.getY(), canvas.getBackgroundColor(), FILL_COLOR);
        }
        canvas.repaint();
    }

    private void clearDrawing() {
        polygon.clear();
        previewLine = null;
        seedFillPoint = null;
        seedFillMode = false;
        draggedVertex = NO_VERTEX;
        clippingPolygon.reset();
        toolbar.setSeedFillMode(false);
        toolbar.setClockwise(clippingPolygon.isClockwiseOnScreen());
        render();
    }

    private void toggleSeedFillMode() {
        seedFillMode = !seedFillMode;
        toolbar.setSeedFillMode(seedFillMode);
        render();
    }

    private void toggleOrientation() {
        clippingPolygon.reverseOrientation();
        toolbar.setClockwise(clippingPolygon.isClockwiseOnScreen());
        render();
    }

    private void dragVertex(Point position) {
        if (clippingPolygon.moveVertex(draggedVertex, position)) {
            render();
        }
    }

    private void fillAt(MouseEvent event) {
        Point seed = getPoint(event);
        Integer seedColor = canvas.getRaster().getPixel(seed.getX(), seed.getY());
        if (seedFillPoint != null && seedColor != null && seedColor == FILL_COLOR) {
            seedFillPoint = null;
            render();
            return;
        }

        boolean filled = seedFiller.fill(seed.getX(), seed.getY(), canvas.getBackgroundColor(), FILL_COLOR);
        if (filled) {
            seedFillPoint = seed;
            render();
        }
    }

    private Point getPoint(MouseEvent event) {
        return new Point(event.getX(), event.getY());
    }

    private void startLine(MouseEvent event) {
        seedFillPoint = null;
        Point startPoint = polygon.getLastPoint();
        if (startPoint == null) {
            startPoint = getPoint(event);
        }
        previewLine = new Line(startPoint, startPoint, PREVIEW_COLOR);
        render();
    }

    private Polygon withColor(Polygon source, int color) {
        Polygon result = new Polygon();
        for (Line line : source.getLines()) {
            result.addLine(new Line(line.getPoint1(), line.getPoint2(), color));
        }
        return result;
    }

    private Polygon withPreviewLine(Polygon source, Line preview) {
        Polygon result = new Polygon();
        for (Line line : source.getLines()) {
            result.addLine(line);
        }
        if (preview != null) {
            result.addLine(preview);
        }
        return result;
    }

}


