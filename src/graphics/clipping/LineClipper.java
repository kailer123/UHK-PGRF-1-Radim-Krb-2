package graphics.clipping;

import model.ClipWindow;
import model.Line;

public interface LineClipper {
    Line clip(Line line, ClipWindow window);
}