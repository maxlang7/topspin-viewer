import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;

/** The three retained ordinary dataset tabs. */
public final class DatasetTabs extends JPanel {
    private static final Color BACKGROUND=new Color(204,204,204),SELECTED=new Color(174,200,217),INK=new Color(51,51,51);
    private static final String[] NAMES={"SPECTRUM","INTEGRALS","FID"};
    private String selected="SPECTRUM";
    public DatasetTabs(Consumer<String> action) {
        super(new FlowLayout(FlowLayout.LEFT,0,0));setBackground(BACKGROUND);
        int[] widths={90,91,43};
        for(int i=0;i<NAMES.length;i++) {
            String name=NAMES[i];int width=widths[i],gap=i==NAMES.length-1?0:5;
            JButton button=new JButton(name){@Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();boolean chosen=selected.equals(name);Color background=chosen?SELECTED:Color.WHITE;
                g.setColor(BACKGROUND);g.fillRect(0,0,getWidth(),getHeight());g.setColor(background);g.fillRect(0,0,getWidth()-gap,getHeight());
                if(chosen || getModel().isRollover()){g.setColor(INK);g.fillRect(0,0,getWidth()-gap,1);g.fillRect(0,getHeight()-1,getWidth()-gap,1);g.fillRect(0,0,1,getHeight());g.fillRect(getWidth()-gap-1,0,1,getHeight());}
                g.setFont(Assets.regular);g.setColor(INK);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.drawString(name,12,19.5f);g.dispose();
            }};
            button.setPreferredSize(new Dimension(width,32));button.setFocusable(false);button.setBorderPainted(false);button.setContentAreaFilled(false);
            button.addActionListener(event->action.accept(name));add(button);
        }
    }
    public void select(String name){for(String candidate:NAMES)if(candidate.equals(name)){selected=name;repaint();return;}throw new IllegalArgumentException("Unknown dataset tab "+name);}
    public String selected(){return selected;}
    @Override public Dimension getPreferredSize(){return new Dimension(224,32);}
}
