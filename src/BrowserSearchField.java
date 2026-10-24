import java.awt.*;
import javax.swing.*;
import com.formdev.flatlaf.ui.FlatTextFieldUI;

/** Quick dataset search with the original magnifier and reset-filter control. */
public final class BrowserSearchField extends JTextField {
    private java.util.function.Supplier<JPopupMenu> filterMenu;
    private JButton filterButton;
    public void setFilterMenu(java.util.function.Supplier<JPopupMenu> menu){filterMenu=menu;filterButton.setToolTipText("Filters and sorting");filterButton.setIcon(new Icon(){public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(Theme.color(new Color(51,51,51)));g.drawPolyline(new int[]{x+2,x+16,x+10,x+10,x+7,x+7,x+2},new int[]{y+3,y+3,y+9,y+15,y+13,y+9,y+3},7);}});}
    public BrowserSearchField() {
        setFont(Assets.regular.deriveFont(14f));
        putClientProperty("JTextField.placeholderText","Search...");putClientProperty("JTextField.leadingIcon",Assets.image("search-magnifier.png"));
        putClientProperty("JTextField.showClearButton",true);
        putClientProperty("FlatLaf.style",Theme.style("borderColor: #333333; borderWidth: 1; innerFocusWidth: 0; focusWidth: 0; arc: 0"));
        JPanel reset=new JPanel(new BorderLayout());reset.setBackground(Theme.color(Color.WHITE));reset.setPreferredSize(new Dimension(31,32));
        JComponent separator=new JComponent(){@Override protected void paintComponent(Graphics graphics){graphics.setColor(Theme.color(new Color(179,179,179)));graphics.fillRect(1,2,1,getHeight()-4);}};
        separator.setPreferredSize(new Dimension(3,32));reset.add(separator,BorderLayout.WEST);
        JButton clear=Assets.button("phase","Clear all filters",()->{if(filterMenu!=null)filterMenu.get().show(filterButton,0,filterButton.getHeight());else{setText("");postActionEvent();}},28,28);filterButton=clear;clear.setBackground(Theme.color(Color.WHITE));reset.add(clear);
        putClientProperty("JTextField.trailingComponent",reset);
        setUI(new FlatTextFieldUI(){@Override protected void paintPlaceholder(Graphics graphics){
            if(!BrowserSearchField.this.getText().isEmpty())return;
            Rectangle bounds=getVisibleEditorRect();if(bounds==null)return;
            Graphics2D g=(Graphics2D)graphics.create();FontMetrics metrics=g.getFontMetrics(getFont());
            NativeText.draw(g,getFont(),"Search...",bounds.x,bounds.y+(bounds.height-metrics.getHeight())/2+metrics.getAscent()-.5,Theme.placeholder(),Theme.color(Color.WHITE));g.dispose();
        }});
    }
}
