import java.awt.*;
import javax.swing.*;

/** Retained top-level tabs with the target's native text and mnemonic rendering. */
public final class NavigationTabs extends JPanel {
    static final Color BACKGROUND=Theme.color(new Color(5,65,105)),SELECTED=Theme.color(new Color(4,48,77)),FOREGROUND=Theme.color(new Color(195,208,217));
    private JButton selected;
    private int next;
    public NavigationTabs(){setLayout(null);setBackground(BACKGROUND);}
    public void addTab(String text,int width,int mnemonic,Runnable action) {
        JButton tab=new JButton(text){
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();Color background=this==selected?SELECTED:BACKGROUND;
                g.setColor(background);g.fillRect(0,0,getWidth(),getHeight());
                g.setColor(Theme.border());g.fillRect(getWidth()-1,0,1,getHeight());if(getX()==0)g.fillRect(0,0,1,getHeight());
                NativeText.draw(g,Assets.regular.deriveFont(14f),text,12.5,29.5,FOREGROUND,background,-.5);
                var context=new java.awt.font.FontRenderContext(java.awt.geom.AffineTransform.getScaleInstance(2,2),RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                var glyphs=Assets.regular.deriveFont(14f).createGlyphVector(context,text);
                double x=glyphs.getGlyphPosition(mnemonic).getX(),end=glyphs.getGlyphPosition(mnemonic+1).getX();
                g.setColor(FOREGROUND);g.fill(new java.awt.geom.Rectangle2D.Double(12+x,30.5,end-x+1,1));g.dispose();
            }
        };
        tab.setFocusable(false);tab.setBorderPainted(false);tab.setBounds(next,0,width,50);
        tab.addActionListener(e->{selected=tab;repaint();action.run();});add(tab);if(selected==null)selected=tab;
        next+=width;setPreferredSize(new Dimension(next,50));
    }
}
