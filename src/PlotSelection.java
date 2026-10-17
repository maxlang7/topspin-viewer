import java.awt.*;

/** Horizontal plot selection in viewport coordinates. */
public final class PlotSelection {
    public static Rectangle horizontal(Point from,Point to,Rectangle viewport) {
        return new Rectangle(Math.min(from.x,to.x),viewport.y,Math.abs(to.x-from.x),viewport.height);
    }
    private PlotSelection(){}
}
