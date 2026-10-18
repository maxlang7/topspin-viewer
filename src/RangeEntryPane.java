import java.awt.*;
import javax.swing.*;
import com.formdev.flatlaf.ui.FlatButtonUI;

/** Content area of the retained exact-coordinate integration dialog. */
public final class RangeEntryPane extends JPanel {
    final JTextField from=new JTextField(8),to=new JTextField(8);
    final JButton ok,cancel;
    public RangeEntryPane(String left,String right,Runnable accept,Runnable reject) {
        setLayout(null);setBackground(new Color(245,245,245));setPreferredSize(new Dimension(215,151));
        from.setText(left);to.setText(right);
        for(JTextField field:new JTextField[]{from,to}) {
            field.setFont(Assets.regular);field.setUI(new DialogFieldUI());field.setForeground(new Color(51,51,51));field.setBackground(Color.WHITE);
            field.setSelectionColor(new Color(174,200,217));field.setSelectedTextColor(new Color(51,51,51));
            field.putClientProperty("FlatLaf.style","arc: 0; focusWidth: 0; innerFocusWidth: 0.5; borderWidth: 1; borderColor: #c4c4c4; focusedBorderColor: #b3b3b3");add(field);
        }
        from.setBounds(109,48,102,26);to.setBounds(109,74,102,26);
        ok=button("OK",accept);cancel=button("Cancel",reject);ok.setBounds(15,116,83,30);cancel.setBounds(103,116,107,30);add(ok);add(cancel);
    }
    @Override public void doLayout() {
        // The original BoxLayout rounds down one pixel when its prompt grows.
        int width=getWidth(),extra=Math.max(0,getHeight()-152);
        from.setBounds(width-106,48+extra,102,26);to.setBounds(width-106,74+extra,102,26);
        ok.setBounds(width-200,getHeight()-35,83,30);cancel.setBounds(width-112,getHeight()-35,107,30);
    }
    private JButton button(String text,Runnable action) {
        Icon graphic=Assets.image("dialog-"+text.toLowerCase()+".png");
        JButton button=new JButton(text,graphic);button.setFont(Assets.regular.deriveFont(14f));
        button.setForeground(new Color(51,51,51));button.setBackground(Color.WHITE);button.setMnemonic(text.charAt(0));
        button.putClientProperty("FlatLaf.style","arc: 6; focusWidth: 0; innerFocusWidth: 0.5; borderWidth: 1; borderColor: #b3b3b3; focusedBorderColor: #b3b3b3");
        button.setUI(new FlatButtonUI(false){@Override protected void paintText(Graphics graphics,AbstractButton b,Rectangle bounds,String label){
            Graphics2D g=(Graphics2D)graphics.create();FontMetrics metrics=b.getFontMetrics(b.getFont());double baseline=bounds.y+metrics.getAscent()-.5;
            g.setFont(b.getFont());g.setColor(b.getForeground());g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.drawString(label,bounds.x,(float)baseline);
            g.setColor(b.getForeground());g.fill(new java.awt.geom.Rectangle2D.Double(bounds.x,baseline+.5,metrics.charWidth(label.charAt(0)),1));g.dispose();
        }});
        button.addActionListener(event->action.run());return button;
    }
    private void text(Graphics2D g,String text,float x,float y,Color color){g.setFont(Assets.regular);g.setColor(color);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.drawString(text,x,y);}
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();Color ink=new Color(51,51,51);
        text(g,"Exact coordinates of integrated area",10,16.5f,new Color(5,65,105));
        int extra=Math.max(0,getHeight()-152);
        text(g,"F1",getWidth()-56,41.5f+extra,ink);text(g," [ppm] ",getWidth()-42,41.5f+extra,ink);
        text(g,"  From ",4,64.5f+extra,ink);text(g,"  To   ",4,90.5f+extra,ink);
        g.setColor(new Color(179,179,179));g.fillRect(0,getHeight()-42,getWidth(),1);g.dispose();
    }
}
