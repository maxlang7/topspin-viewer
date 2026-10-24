import java.awt.*;

/** Foreground cursor text and its translucent background, outside the logical bitmap. */
public final class CursorMessagePainter {
    public static Rectangle paint(Graphics2D graphics,String[] labels,Rectangle viewport,int offset,Color foreground) {
        return paint(graphics,labels,viewport,offset,foreground,Assets.mono);
    }
    public static Rectangle paint(Graphics2D graphics,String[] labels,Rectangle viewport,int offset,Color foreground,Font font) {
        if(labels==null || viewport==null)return null;
        Graphics2D g=(Graphics2D)graphics.create();g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FontMetrics metrics=g.getFontMetrics();boolean windowsMono=font.equals(Assets.mono);
        int height=windowsMono?15:metrics.getHeight(),maxWidth=0,totalHeight=0,lastHeight=0;
        for(String label:labels)if(label!=null) {
            Rectangle bounds=metrics.getStringBounds(label,g).getBounds();if(windowsMono)bounds.height=15;totalHeight+=bounds.height;lastHeight=bounds.height;maxWidth=Math.max(maxWidth,bounds.width);
        }
        Rectangle bounds=new Rectangle(viewport.x+1,viewport.y+offset+1,maxWidth+height/2,totalHeight+lastHeight/3);
        g.setColor(Theme.color(new Color(255,255,255,128)));g.fill(bounds);g.setColor(foreground);
        int x=viewport.x+height/4,y=viewport.y+offset+height;
        for(String label:labels){if(label!=null)g.drawString(label,x,y);y+=height;}
        g.dispose();return bounds;
    }
    private CursorMessagePainter(){}
}
