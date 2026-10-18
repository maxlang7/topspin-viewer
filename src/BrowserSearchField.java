import java.awt.*;
import javax.swing.*;
import com.formdev.flatlaf.ui.FlatTextFieldUI;

/** Quick dataset search with the original magnifier and reset-filter control. */
public final class BrowserSearchField extends JTextField {
    public BrowserSearchField() {
        setFont(Assets.regular.deriveFont(14f));
        putClientProperty("JTextField.placeholderText","Search...");putClientProperty("JTextField.leadingIcon",Assets.image("search-magnifier.png"));
        putClientProperty("JTextField.showClearButton",true);
        putClientProperty("FlatLaf.style","borderColor: #333333; borderWidth: 1; innerFocusWidth: 0; focusWidth: 0; arc: 0");
        JPanel reset=new JPanel(new BorderLayout());reset.setBackground(Color.WHITE);reset.setPreferredSize(new Dimension(31,32));
        JComponent separator=new JComponent(){@Override protected void paintComponent(Graphics graphics){graphics.setColor(new Color(179,179,179));graphics.fillRect(1,2,1,getHeight()-4);}};
        separator.setPreferredSize(new Dimension(3,32));reset.add(separator,BorderLayout.WEST);
        JButton clear=Assets.button("phase","Clear all filters",()->{setText("");postActionEvent();},28,28);clear.setBackground(Color.WHITE);reset.add(clear);
        putClientProperty("JTextField.trailingComponent",reset);
        setUI(new FlatTextFieldUI(){@Override protected void paintPlaceholder(Graphics graphics){
            if(!BrowserSearchField.this.getText().isEmpty())return;
            Rectangle bounds=getVisibleEditorRect();if(bounds==null)return;
            Graphics2D g=(Graphics2D)graphics.create();FontMetrics metrics=g.getFontMetrics(getFont());
            NativeText.draw(g,getFont(),"Search...",bounds.x,bounds.y+(bounds.height-metrics.getHeight())/2+metrics.getAscent()-.5,new Color(204,204,204),Color.WHITE);g.dispose();
        }});
    }
}
