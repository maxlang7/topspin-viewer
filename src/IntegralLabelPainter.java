import java.awt.*;
import java.text.*;
import java.util.Locale;

/** Integral brackets, rotated values and the selected-region fill. */
public final class IntegralLabelPainter {
    public static Rectangle paint(Graphics2D graphics,Rectangle viewport,double value,int digits,boolean selected) {
        if(digits==0)digits=4;
        NumberFormat format=NumberFormat.getNumberInstance(Locale.ROOT);format.setMaximumFractionDigits(digits);format.setMinimumFractionDigits(digits);
        String label=format.format(value);Graphics2D g=(Graphics2D)graphics.create();g.setClip(null);
        g.setFont(Assets.bold.deriveFont(14f));g.setColor(new Color(237,28,36));
        g.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle bounds=g.getFontMetrics().getStringBounds(label,g).getBounds();
        int x=viewport.x,y=viewport.y,w=viewport.width,h=viewport.height,middle=x+w/2,bottom=y+h;
        int near=bottom-bounds.height/2,shoulder=bottom-3*bounds.height/4,upper=near-bounds.width;
        int left=middle-bounds.height/2,right=middle+bounds.height/2-3;
        g.drawPolyline(new int[]{x,x,left,left},new int[]{bottom,near,shoulder,upper},4);
        g.drawPolyline(new int[]{right,right,x+w,x+w},new int[]{upper,shoulder,near,bottom},4);
        Graphics2D text=(Graphics2D)g.create();text.translate(middle+bounds.height/4,near);text.rotate(-Math.PI/2);text.drawString(label,0,0);text.dispose();
        if(selected) {
            int selection=bottom-bounds.height/2+2;
            g.setColor(new Color(200,255,0));
            g.fillPolygon(new int[]{x+2,x+2,middle,x+w-2,x+w-2},new int[]{bottom-2,selection,selection-bounds.height/4,selection,bottom-2},5);
        }
        g.dispose();return new Rectangle(x,bottom-2*bounds.width,w,2*bounds.width);
    }
    public static void paintFactor(Graphics2D graphics,Rectangle viewport,double relative) {
        NumberFormat format=NumberFormat.getNumberInstance(Locale.ROOT);format.setMaximumFractionDigits(2);format.setMinimumFractionDigits(0);
        String label="0.1";
        if(relative>1.001)label+="* "+format.format(relative);
        else if(relative<.999)label+="/ "+format.format(1.0/relative);
        Graphics2D g=(Graphics2D)graphics.create();g.setClip(null);g.setFont(Assets.bold.deriveFont(14f));g.setColor(new Color(237,28,36));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle bounds=g.getFontMetrics().getStringBounds(label,g).getBounds();
        int x=viewport.x+viewport.width/2-bounds.width/2,y=viewport.y+bounds.height;
        g.drawRect(x-2,y+15,bounds.width+4,bounds.height+4);g.drawString(label,x,y+12+bounds.height);g.dispose();
    }
    private IntegralLabelPainter(){}
}
