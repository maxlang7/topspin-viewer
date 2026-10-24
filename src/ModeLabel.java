import java.awt.*;
import javax.swing.*;

/** Selected editing-mode tab, using the target's native text rendering. */
public final class ModeLabel extends JLabel {
    private static final Color BACKGROUND=Theme.color(new Color(174,200,217)),FOREGROUND=Theme.color(new Color(51,51,51));
    public ModeLabel(String text,Icon icon) {
        super(text,icon,SwingConstants.LEFT);setFont(Assets.regular);setForeground(FOREGROUND);
    }
    @Override public Dimension getPreferredSize(){var context=new java.awt.font.FontRenderContext(java.awt.geom.AffineTransform.getScaleInstance(2,2),RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        var glyphs=getFont().createGlyphVector(context,getText());
        return new Dimension(52+(int)Math.round(glyphs.getGlyphPosition(glyphs.getNumGlyphs()).getX()),32);}
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();g.setColor(FOREGROUND);g.fillRect(0,0,getWidth(),getHeight());
        g.setColor(BACKGROUND);g.fillRect(1,1,getWidth()-2,getHeight()-2);
        if(getIcon()!=null)getIcon().paintIcon(this,g,12,4);
        NativeText.draw(g,getFont(),getText(),40,19.5,FOREGROUND,BACKGROUND);g.dispose();
    }
}
