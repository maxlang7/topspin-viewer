import java.awt.*;
import javax.swing.*;
import com.formdev.flatlaf.ui.FlatArrowButton;
import com.formdev.flatlaf.ui.FlatButtonUI;

/** The retained commands in the processing and analysis ribbons. */
public final class Ribbon extends JPanel {
    static final Color BACKGROUND=Theme.color(new Color(4,48,77)),FOREGROUND=Theme.color(new Color(195,208,217));
    private int next;
    public Ribbon(){setLayout(null);setBackground(BACKGROUND);setPreferredSize(new Dimension(1,50));}
    public void addCommand(String text,Icon icon,int width,Runnable action) {
        JButton button=new JButton(text,icon);button.setFont(Assets.regular.deriveFont(14f));
        button.setUI(new FlatButtonUI(false){
            @Override protected void paintText(Graphics graphics,AbstractButton b,Rectangle bounds,String label) {
                Graphics2D copy=(Graphics2D)graphics.create();copy.translate(0,-.5);
                FontMetrics metrics=b.getFontMetrics(b.getFont());int baseline=bounds.y+metrics.getAscent();
                NativeText.draw(copy,b.getFont(),label,bounds.x,baseline,b.getForeground(),BACKGROUND);
                int mnemonic=b.getDisplayedMnemonicIndex();
                if(mnemonic>=0 && mnemonic<label.length()) {
                    copy.setColor(b.getForeground());copy.fill(new java.awt.geom.Rectangle2D.Double(bounds.x+metrics.stringWidth(label.substring(0,mnemonic)),baseline+.5,metrics.charWidth(label.charAt(mnemonic)),1));
                }
                copy.dispose();
            }
        });
        if(text.equals("Proc. Spectrum"))button.setMnemonic('c');
        if(text.equals("Adjust Phase"))button.setMnemonic('j');
        button.setForeground(FOREGROUND);button.setBackground(BACKGROUND);
        button.setFocusable(false);button.setBorder(BorderFactory.createEmptyBorder());button.setMargin(new Insets(0,0,0,0));
        button.setContentAreaFilled(false);button.setOpaque(false);button.setHorizontalAlignment(SwingConstants.CENTER);
        button.addActionListener(e->action.run());button.setBounds(next+13,13,width,24);add(button);
        Color clear=Theme.color(new Color(0,0,0,0));
        FlatArrowButton arrow=new FlatArrowButton(SwingConstants.SOUTH,"triangle",FOREGROUND,Theme.foreground(Color.WHITE),Theme.foreground(Color.WHITE),clear,FOREGROUND,clear);
        arrow.setOpaque(false);arrow.setBounds(next+13+width,13,16,24);arrow.setFocusable(false);
        arrow.addActionListener(e->{JPopupMenu menu=new JPopupMenu();JMenuItem item=new JMenuItem(text,icon);item.addActionListener(event->action.run());menu.add(item);menu.show(arrow,0,arrow.getHeight());});
        add(arrow);next+=width+40;
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);graphics.setColor(Theme.border());
        for(Component c:getComponents())if(c instanceof FlatArrowButton)graphics.fillRect(c.getX()+c.getWidth()+11,0,1,getHeight());
    }
}
