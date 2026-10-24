import java.awt.*;
import javax.swing.*;

/** Grouped icon controls used by manual phase correction and integration. */
public final class EditingTools extends JPanel {
    public EditingTools() {
        super(new FlowLayout(FlowLayout.LEFT,0,0));setBackground(Theme.color(Color.WHITE));
        setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
    }
    public JButton addTool(String mode,String tooltip,Runnable action) {
        JButton button=Assets.button(mode,tooltip,action,30,28);button.setBackground(Theme.color(Color.WHITE));add(button);return button;
    }
    public void separator() {
        JSeparator separator=new JSeparator(SwingConstants.VERTICAL);separator.setPreferredSize(new Dimension(3,28));separator.setForeground(Theme.color(new Color(179,179,179)));add(separator);
    }
}
