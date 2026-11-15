package graphics.filler;

import graphics.Raster;
import java.util.ArrayDeque;
import java.util.Deque;

public final class QueueSeedFiller implements SeedFiller {

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private final Raster raster;
    public QueueSeedFiller(Raster raster) {
        this.raster = raster;
    }

    @Override
    public boolean fill(int x, int y, FillCondition condition, int fillColor) {
        // If the fill colour itself satisfied the condition, filled pixels would be filled again forever
        if (condition.canFill(fillColor) || !canFill(x, y, condition)) {
            return false;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        raster.setPixel(x, y, fillColor);
        queue.add(new int[]{x, y});

        while (!queue.isEmpty()) {
            int[] pixel = queue.poll();
            for (int[] offset : NEIGHBOURS) {
                int nx = pixel[0] + offset[0];
                int ny = pixel[1] + offset[1];
                if (canFill(nx, ny, condition)) {
                    // Colour the pixel when adding it, so it is never added to the queue twice
                    raster.setPixel(nx, ny, fillColor);
                    queue.add(new int[]{nx, ny});
                }
            }
        }
        return true;
    }

    private boolean canFill(int x, int y, FillCondition condition) {
        Integer color = raster.getPixel(x, y);
        return color != null && condition.canFill(color);
    }

}