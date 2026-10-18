import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.Locale;
import javax.swing.*;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLabelUI;
import com.formdev.flatlaf.ui.FlatTitlePane;

/** Modal coordinate entry; returns only confirmed values. */
public final class RangeEntryDialog extends JDialog {
    private final RangeEntryPane pane;
    private double[] result;
    private RangeEntryDialog(JFrame owner,double left,double right) {
        super(owner,"add",Dialog.ModalityType.APPLICATION_MODAL);setUndecorated(true);
        Object unified=UIManager.get("TitlePane.unifiedBackground"),inactive=UIManager.get("TitlePane.inactiveForeground");
        try {
            UIManager.put("TitlePane.unifiedBackground",false);
            UIManager.put("TitlePane.inactiveForeground",new Color(115,115,115));
            getRootPane().setWindowDecorationStyle(JRootPane.PLAIN_DIALOG);
        }finally {
            UIManager.put("TitlePane.unifiedBackground",unified);UIManager.put("TitlePane.inactiveForeground",inactive);
        }
        getRootPane().putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_ICON,false);
        getRootPane().putClientProperty(FlatClientProperties.TITLE_BAR_BACKGROUND,Color.WHITE);
        for(Component component:getLayeredPane().getComponents())if(component instanceof FlatTitlePane title)styleCaption(title);
        getRootPane().setBorder(BorderFactory.createMatteBorder(0,2,2,2,new Color(179,179,179)));
        DecimalFormat format=new DecimalFormat("0.0000",DecimalFormatSymbols.getInstance(Locale.ROOT));
        pane=new RangeEntryPane(format.format(left),format.format(right),this::accept,this::dispose);setContentPane(pane);
        getRootPane().registerKeyboardAction(event->dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        pane.from.addActionListener(event->accept());pane.to.addActionListener(event->accept());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);pack();setMinimumSize(getSize());setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter(){@Override public void windowOpened(WindowEvent event){pane.from.requestFocusInWindow();pane.from.selectAll();}});
    }
    private static void styleCaption(FlatTitlePane title) {
        for(Component child:title.getComponents())if(child instanceof JLabel label)label.setUI(new FlatLabelUI(false){
            @Override public void paint(Graphics graphics,JComponent component) {
                Graphics2D g=(Graphics2D)graphics.create();g.translate(0,-.5);super.paint(g,component);g.dispose();
            }
        });
        // The Windows reference reserves one logical pixel but paints a one-device-pixel line at 2x.
        title.setBorder(new javax.swing.border.EmptyBorder(1,0,0,0){
            @Override public void paintBorder(Component component,Graphics graphics,int x,int y,int width,int height) {
                Graphics2D g=(Graphics2D)graphics.create();g.setColor(new Color(170,170,170));
                g.fill(new java.awt.geom.Rectangle2D.Double(x,y,width,.5));g.dispose();
            }
        });
    }
    private void accept() {
        try {result=new double[]{Double.parseDouble(pane.from.getText()),Double.parseDouble(pane.to.getText())};dispose();}
        catch(NumberFormatException error){JOptionPane.showMessageDialog(this,error.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);}
    }
    public static double[] show(JFrame owner,double left,double right) {
        RangeEntryDialog dialog=new RangeEntryDialog(owner,left,right);
        try{dialog.setVisible(true);return dialog.result;}finally{dialog.dispose();}
    }
}
