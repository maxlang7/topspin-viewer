import java.awt.*;
import javax.swing.*;

/** Viewer icon groups, with the original padding and inset separators. */
public final class ViewerTools extends JPanel {
    public ViewerTools() {
        super(new FlowLayout(FlowLayout.LEFT,0,0));setBackground(Theme.color(new Color(245,245,245)));
        setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
    }
    public JButton addTool(String tooltip,Runnable action) {
        JButton button=Assets.button("phase",tooltip,action,34,34);add(button);return button;
    }
    public void separator() {
        JComponent separator=new JComponent(){@Override protected void paintComponent(Graphics graphics){graphics.setColor(Theme.color(new Color(128,128,128)));graphics.fillRect(4,5,1,24);}};
        separator.setPreferredSize(new Dimension(9,34));add(separator);
    }
}
