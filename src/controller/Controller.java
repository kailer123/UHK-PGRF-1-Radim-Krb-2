package controller;

import graphics.filler.ScanlineFiller;
import graphics.filler.RecursiveSeedFiller;
import graphics.filler.SeedFiller;
import graphics.clipping.CohenSutherlandClipper;
import graphics.clipping.LineClipper;
import graphics.clipping.PolygonClipper;
import graphics.clipping.SutherlandHodgmanClipper;
import graphics.rasterizer.PolygonOutlineRasterizer;
import graphics.rasterizer.PolygonRasterizer;
import graphics.rasterizer.XiaolinWuLineRasterizer;
import graphics.rasterizer.LineRasterizer;
import model.Line;
import model.Point;
import model.Polygon;
import model.ClippingPolygon;
import model.ClipWindow;
import view.Canvas;
import view.gui.Toolbar;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;

import static java.awt.event.KeyEvent.VK_C;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;

public class Controller {

    private final Canvas canvas;
    private final Toolbar toolbar;

    private Line previewLine;
    private boolean lineMode;
    private final Polygon polygon;
    private final List<Line> lines;
    private final PolygonClipper clipper;
    private final ClippingPolygon clippingPolygon;
    private final LineClipper lineClipper;
    private final ClipWindow clipWindow;
    private final LineRasterizer lineRasterizer;
    private final PolygonRasterizer polygonRasterizer;

    private int draggedVertex = NONE;
    private int draggedCorner = NONE;
    private Point seedFillPoint;
    private boolean seedFillMode;
    private final SeedFiller seedFiller;
    private final ScanlineFiller scanlineFiller;

    private static final int LINE_COLOR = Color.WHITE.getRGB();
    private static final int PREVIEW_COLOR = Color.RED.getRGB();
    private static final int ORIGINAL_COLOR = new Color(0x9099A3).getRGB();
    private static final int CLIPPED_COLOR = new Color(0xA4F2DF).getRGB();
    private static final int CLIPPING_POLYGON_COLOR = new Color(0xE6B85C).getRGB();
    private static final int CLIP_WINDOW_COLOR = new Color(0x6FA8DC).getRGB();
    private static final int FILL_COLOR = new Color(0x58BFA9).getRGB();

    private static final int NONE = -1;
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
        this.lines = new ArrayList<>();
        this.clipper = new SutherlandHodgmanClipper();
        this.clippingPolygon = new ClippingPolygon(DEFAULT_CLIPPING_VERTICES);
        this.lineClipper = new CohenSutherlandClipper();
        this.clipWindow = new ClipWindow(520, 100, 760, 400);
        this.scanlineFiller = new ScanlineFiller(canvas.getRaster());
        this.seedFiller = new RecursiveSeedFiller(canvas.getRaster());
        this.lineRasterizer = new XiaolinWuLineRasterizer(canvas.getRaster());
        this.polygonRasterizer = new PolygonOutlineRasterizer(lineRasterizer);
    }

    public void init() {
        toolbar.addClearListener(_ -> clearDrawing());
        toolbar.addModeListener(_ -> toggleLineMode());
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
                    startEditing(getPoint(event));
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON3) {
                    draggedVertex = NONE;
                    draggedCorner = NONE;
                    return;
                }
                if (event.getButton() != MouseEvent.BUTTON1 || previewLine == null) {
                    return;
                }
                finishLine(getPoint(event));
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (draggedVertex != NONE) {
                    dragVertex(getPoint(event));
                } else if (draggedCorner != NONE) {
                    dragCorner(getPoint(event));
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
        renderPolygon();
        renderLines();
        if (seedFillPoint != null) {
            seedFiller.fill(seedFillPoint.getX(), seedFillPoint.getY(), canvas.getBackgroundColor(), FILL_COLOR);
        }
        canvas.repaint();
    }

    private void renderPolygon() {
        Line polygonPreview = lineMode ? null : previewLine;
        Polygon boundary = clippingPolygon.toPolygon(CLIPPING_POLYGON_COLOR);
        Polygon clipped = clipper.clip(withPreviewLine(polygon, polygonPreview), boundary);
        scanlineFiller.fill(clipped, FILL_COLOR);
        polygonRasterizer.rasterize(withColor(polygon, ORIGINAL_COLOR), polygonPreview);
        polygonRasterizer.rasterize(withColor(clipped, CLIPPED_COLOR), null);
        polygonRasterizer.rasterize(boundary, null);
    }
    private void renderLines() {
        polygonRasterizer.rasterize(clipWindow.toPolygon(CLIP_WINDOW_COLOR), null);
        for (Line line : lines) {
            renderClippedLine(line, ORIGINAL_COLOR);
        }
        if (lineMode && previewLine != null) {
            renderClippedLine(previewLine, PREVIEW_COLOR);
        }
    }


    private void renderClippedLine(Line line, int color) {
        lineRasterizer.rasterize(new Line(line.getPoint1(), line.getPoint2(), color));
        Line clipped = lineClipper.clip(line, clipWindow);
        if (clipped != null) {
            lineRasterizer.rasterize(new Line(clipped.getPoint1(), clipped.getPoint2(), CLIPPED_COLOR));
        }
    }

    private void clearDrawing() {
        polygon.clear();
        lines.clear();
        previewLine = null;
        lineMode = false;
        seedFillPoint = null;
        seedFillMode = false;
        draggedVertex = NONE;
        draggedCorner = NONE;
        clippingPolygon.reset();
        clipWindow.reset();
        toolbar.setLineMode(false);
        toolbar.setSeedFillMode(false);
        toolbar.setClockwise(clippingPolygon.isClockwiseOnScreen());
        render();
    }

    private void toggleLineMode() {
        lineMode = !lineMode;
        previewLine = null;
        toolbar.setLineMode(lineMode);
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
    private void startLine(MouseEvent event) {
        seedFillPoint = null;
        Point startPoint = lineMode ? null : polygon.getLastPoint();
        if (startPoint == null) {
            startPoint = getPoint(event);
        }
        previewLine = new Line(startPoint, startPoint, PREVIEW_COLOR);
        render();
    }

    private void finishLine(Point endPoint) {
        Line line = new Line(previewLine.getPoint1(), endPoint, LINE_COLOR);
        if (lineMode) {
            lines.add(line);
        } else {
            polygon.addLine(line);
        }
        previewLine = null;
        render();
    }

    private void startEditing(Point point) {
        if (lineMode) {
            draggedCorner = clipWindow.findCornerNear(point, GRAB_RADIUS);
        } else {
            draggedVertex = clippingPolygon.findVertexNear(point, GRAB_RADIUS);
        }
    }

    private void dragVertex(Point position) {
        if (clippingPolygon.moveVertex(draggedVertex, position)) {
            render();
        }
    }

    private void dragCorner(Point position) {
        if (clipWindow.moveCorner(draggedCorner, position)) {
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


