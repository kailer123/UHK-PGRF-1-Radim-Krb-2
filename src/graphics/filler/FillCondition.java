package graphics.filler;

public interface FillCondition {
    boolean canFill(int pixelColor);
    static FillCondition background(int backgroundColor) {
        return pixelColor -> pixelColor == backgroundColor;
    }
    static FillCondition border(int borderColor, int fillColor) {
        return pixelColor -> pixelColor != borderColor && pixelColor != fillColor;
    }
}

