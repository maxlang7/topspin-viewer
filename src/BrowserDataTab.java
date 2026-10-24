import java.awt.*;
import javax.swing.*;

/** The retained Data tab, with the source list icon and selected border. */
public final class BrowserDataTab extends JButton {
    private final Icon icon=Assets.image("browser-data.png");
    public BrowserDataTab(Runnable action) {
        super("Data");setPreferredSize(new Dimension(72,32));setFocusable(false);setBorderPainted(false);setContentAreaFilled(false);
        addActionListener(event->action.run());setToolTipText("Data browser");
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();g.setColor(Theme.color(new Color(174,200,217)));g.fillRect(0,0,getWidth(),getHeight());
        Color ink=Theme.color(new Color(51,51,51)),background=Theme.color(new Color(174,200,217));g.setColor(ink);
        g.fillRect(0,0,getWidth(),1);g.fillRect(0,getHeight()-1,getWidth(),1);g.fillRect(0,0,1,getHeight());g.fillRect(getWidth()-1,0,1,getHeight());
        icon.paintIcon(this,g,12,4);
        NativeText.draw(g,Assets.regular,"Data",40,19.5,ink,background);g.dispose();
    }
}
