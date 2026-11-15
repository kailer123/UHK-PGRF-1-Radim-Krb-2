package controller;

import graphics.filler.ScanlineFiller;
import graphics.filler.SeedFiller;
import graphics.clipping.CohenSutherlandClipper;
import graphics.clipping.LineClipper;
import graphics.clipping.PolygonClipper;
import graphics.clipping.SutherlandHodgmanClipper;
import graphics.rasterizer.PolygonOutlineRasterizer;
import graphics.rasterizer.PolygonRasterizer;
import graphics.rasterizer.XiaolinWuLineRasterizer;
import graphics.rasterizer.LineRasterizer;
import graphics.filler.FillCondition;
import graphics.filler.QueueSeedFiller;
import graphics.rasterizer.TrivialLineRasterizer;
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
import javax.swing.KeyStroke;

import static java.awt.event.KeyEvent.VK_C;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_SHIFT;

public class Controller {

    private final Canvas canvas;
    private final Toolbar toolbar;

    private Line previewLine;
    private Point lastMousePoint;
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
    private boolean borderCondition;
    private final LineRasterizer solidLineRasterizer;
    private final PolygonRasterizer solidPolygonRasterizer;
    private final SeedFiller seedFiller;
    private final ScanlineFiller scanlineFiller;

    private static final int LINE_COLOR = Color.WHITE.getRGB();
    private static final int PREVIEW_COLOR = Color.RED.getRGB();
    private static final int ORIGINAL_COLOR = new Color(0x9099A3).getRGB();
    private static final int CLIPPED_COLOR = new Color(0xA4F2DF).getRGB();
    private static final int CLIPPING_POLYGON_COLOR = new Color(0xE6B85C).getRGB();
    private static final int CLIP_WINDOW_COLOR = new Color(0x6FA8DC).getRGB();
    private static final int FILL_COLOR = new Color(0x58BFA9).getRGB();
    private static final int BORDER_COLOR = Color.WHITE.getRGB();

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
        this.seedFiller = new QueueSeedFiller(canvas.getRaster());
        this.solidLineRasterizer = new TrivialLineRasterizer(canvas.getRaster());
        this.solidPolygonRasterizer = new PolygonOutlineRasterizer(solidLineRasterizer);
        this.lineRasterizer = new XiaolinWuLineRasterizer(canvas.getRaster());
        this.polygonRasterizer = new PolygonOutlineRasterizer(lineRasterizer);
    }

    public void init() {
        toolbar.addClearListener(_ -> clearDrawing());
        toolbar.addModeListener(_ -> toggleLineMode());
        toolbar.addSeedFillListener(_ -> toggleSeedFillMode());
        toolbar.addOrientationListener(_ -> toggleOrientation());
        toolbar.setClockwise(clippingPolygon.isClockwiseOnScreen());
        toolbar.addFillConditionListener(_ -> toggleFillCondition());
        bindKey(getKeyStroke(VK_C, 0), "clearCanvas", this::clearDrawing);
        bindKey(getKeyStroke(VK_SHIFT, SHIFT_DOWN_MASK, false), "shiftPressed", () -> updatePreview(true));
        bindKey(getKeyStroke(VK_SHIFT, 0, true), "shiftReleased", () -> updatePreview(false));

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
                finishLine(endPoint(getPoint(event), event.isShiftDown()));
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
                    lastMousePoint = getPoint(event);
                    previewLine = new Line(previewLine.getPoint1(), endPoint(lastMousePoint, event.isShiftDown()), PREVIEW_COLOR);
                    render();
                }
            }
        });

        render();
    }

    private void render() {
        canvas.clear();
        boolean rasterBoundary = seedFillMode || seedFillPoint != null;
        renderPolygon(!rasterBoundary);
        renderLines(!rasterBoundary);
        if (rasterBoundary) {
            renderSeedFillBoundary();
        }
        if (seedFillPoint != null) {
            seedFiller.fill(seedFillPoint.getX(), seedFillPoint.getY(), fillCondition(), FILL_COLOR);
        }
        canvas.repaint();
    }

    private void renderPolygon(boolean drawOutline) {
        Line polygonPreview = lineMode ? null : previewLine;
        Polygon boundary = clippingPolygon.toPolygon(CLIPPING_POLYGON_COLOR);
        Polygon clipped = clipper.clip(withPreviewLine(polygon, polygonPreview), boundary);
        scanlineFiller.fill(clipped, FILL_COLOR);
        if (drawOutline) {
            polygonRasterizer.rasterize(withColor(polygon, ORIGINAL_COLOR), polygonPreview);
        }
        polygonRasterizer.rasterize(withColor(clipped, CLIPPED_COLOR), null);
        polygonRasterizer.rasterize(boundary, null);
    }

    private void renderLines(boolean drawOutlines) {
        polygonRasterizer.rasterize(clipWindow.toPolygon(CLIP_WINDOW_COLOR), null);
        for (Line line : lines) {
            if (drawOutlines) {
                lineRasterizer.rasterize(withColor(line, ORIGINAL_COLOR));
            }
            renderClippedPart(line);
        }
        if (lineMode && previewLine != null) {
            lineRasterizer.rasterize(previewLine);
            renderClippedPart(previewLine);
        }
    }

    private void renderClippedPart(Line line) {
        Line clipped = lineClipper.clip(line, clipWindow);
        if (clipped != null) {
            lineRasterizer.rasterize(withColor(clipped, CLIPPED_COLOR));
        }
    }

    private void renderSeedFillBoundary() {
        solidPolygonRasterizer.rasterize(withColor(polygon, BORDER_COLOR), null);
        for (Line line : lines) {
            solidLineRasterizer.rasterize(withColor(line, BORDER_COLOR));
        }
    }

    private void clearDrawing() {
        polygon.clear();
        lines.clear();
        borderCondition = false;
        toolbar.setBorderCondition(false);
        previewLine = null;
        lineMode = false;
        seedFillPoint = null;
        seedFillMode = false;
        lastMousePoint = null;
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
        lastMousePoint = null;
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

        boolean filled = seedFiller.fill(seed.getX(), seed.getY(), fillCondition(), FILL_COLOR);
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
    private Point endPoint(Point mouse, boolean aligned) {
        return aligned ? alignEndPoint(previewLine.getPoint1(), mouse) : mouse;
    }
    private void updatePreview(boolean aligned) {
        if (previewLine == null || lastMousePoint == null) {
            return;
        }
        previewLine = new Line(previewLine.getPoint1(), endPoint(lastMousePoint, aligned), PREVIEW_COLOR);
        render();
    }
    private Point alignEndPoint(Point start, Point mouse) {
        int dx = mouse.getX() - start.getX();
        int dy = mouse.getY() - start.getY();
        // Projection of the mouse onto the diagonal: |dx| = |dy| = average of both
        int diagonal = (Math.abs(dx) + Math.abs(dy)) / 2;

        Point[] candidates = {
                new Point(mouse.getX(), start.getY()),
                new Point(start.getX(), mouse.getY()),
                new Point(start.getX() + Integer.signum(dx) * diagonal, start.getY() + Integer.signum(dy) * diagonal)
        };

        Point best = candidates[0];
        for (Point candidate : candidates) {
            if (distance(candidate, mouse) < distance(best, mouse)) {
                best = candidate;
            }
        }
        return best;
    }

    private double distance(Point a, Point b) {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }
    private void bindKey(KeyStroke keyStroke, String name, Runnable action) {
        canvas.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        canvas.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.run();
            }
        });
    }
    private void toggleFillCondition() {
        borderCondition = !borderCondition;
        toolbar.setBorderCondition(borderCondition);
        render();
    }
    private FillCondition fillCondition() {
        return borderCondition
                ? FillCondition.border(BORDER_COLOR, FILL_COLOR)
                : FillCondition.background(canvas.getBackgroundColor());
    }
    private Line withColor(Line line, int color) {
        return new Line(line.getPoint1(), line.getPoint2(), color);
    }
}


