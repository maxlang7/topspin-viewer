import java.awt.*;
import java.text.*;
import java.util.Locale;

/** The phase module's status line, rendered in the logical data-panel bitmap. */
public final class PhaseCaption {
    public static String text(double pivot,double increment,double zero,double first) {
        DecimalFormat format=new DecimalFormat("0.00",DecimalFormatSymbols.getInstance(Locale.ROOT));
        String point=format.format(pivot);format.setMaximumFractionDigits(6);String step=format.format(increment);
        format.setMaximumFractionDigits(2);
        return "pivot = "+point+" ppm  Phase increment = "+step+"  ph0 = "+format.format(zero)+"  ph1 = "+format.format(first)+"  ";
    }
    public static int height() {
        return bounds(text(0,0,0,0)).height;
    }
    private static Rectangle bounds(String text) {
        var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setFont(Assets.mono);
        Rectangle bounds=g.getFontMetrics().getStringBounds(text,g).getBounds();g.dispose();return bounds;
    }
    public static void paint(Graphics2D graphics,String text) {
        Graphics2D g=(Graphics2D)graphics.create();g.setFont(Assets.mono);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle bounds=g.getFontMetrics().getStringBounds(text,g).getBounds();
        g.setColor(Color.WHITE);g.fillRect(bounds.x+10,0,bounds.width,bounds.height);
        g.setColor(new Color(237,28,36));g.drawString(text,10,-bounds.y);g.dispose();
    }
    private PhaseCaption(){}
}
