import java.awt.*;
import javax.swing.*;
import javax.swing.tree.*;
import com.formdev.flatlaf.ui.FlatTreeUI;

/** Dataset tree with native row text and the target's plus/minus expansion controls. */
public final class BrowserTree extends JTree {
    public BrowserTree(TreeNode root) {
        super(root);setFont(Assets.regular);setRootVisible(false);setShowsRootHandles(true);setRowHeight(16);setBackground(Color.WHITE);setBorder(BorderFactory.createEmptyBorder(0,8,0,0));
        setUI(new FlatTreeUI(){@Override protected void installDefaults(){super.installDefaults();setExpandedIcon(expansionIcon(false));setCollapsedIcon(expansionIcon(true));}});
        setCellRenderer(new DefaultTreeCellRenderer(){
            {setOpenIcon(null);setClosedIcon(null);}
            @Override protected void paintComponent(Graphics graphics) {
                Color background=selected?getBackgroundSelectionColor():Color.WHITE;
                Graphics2D g=(Graphics2D)graphics.create();g.setColor(background);g.fillRect(0,0,getWidth(),getHeight());
                int textX=2;
                if(getIcon()!=null){getIcon().paintIcon(this,g,2,(getHeight()-getIcon().getIconHeight())/2);textX+=getIcon().getIconWidth()+getIconTextGap();}
                NativeText.draw(g,Assets.regular,getText(),textX,13.5,new Color(51,51,51),background);g.dispose();
            }
        });
    }
    static Icon expansionIcon(boolean collapsed) {
        return new Icon(){
            public int getIconWidth(){return 16;}public int getIconHeight(){return 16;}
            public void paintIcon(Component component,Graphics graphics,int x,int y){graphics.setColor(new Color(51,51,51));graphics.fillRect(x+3,y+10,collapsed?10:9,2);if(collapsed)graphics.fillRect(x+7,y+6,2,10);}
        };
    }
}
