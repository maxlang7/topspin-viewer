import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;
public class BrowserSearchRasterReference {
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        SwingUtilities.invokeAndWait(()->{try {
            BrowserSearchField field=new BrowserSearchField();field.setSize(149,34);field.doLayout();for(Component child:field.getComponents())if(child instanceof Container c)c.doLayout();
            BufferedImage actual=new BufferedImage(298,68,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);field.paint(g);g.dispose();
            BufferedImage source=ImageIO.read(Path.of("evidence/ui/topspin-integration.png").toFile());
            long pixels=0,different=0;
            for(int y=0;y<68;y++)for(int x=0;x<298;x++){pixels++;if(actual.getRGB(x,y)!=source.getRGB(46+x,434+y))different++;}
            if(different!=0)throw new AssertionError(different+" empty search field pixels differ");
            int[] actions={0};field.addActionListener(event->actions[0]++);field.setText("MLN1P121");
            Container reset=(Container)field.getClientProperty("JTextField.trailingComponent");
            for(Component child:reset.getComponents())if(child instanceof JButton button)button.doClick();
            if(!field.getText().isEmpty() || actions[0]!=1)throw new AssertionError("Reset did not clear and submit search");
            System.out.println("PASS browser search placeholder: "+pixels+" exact captured Windows pixels; reset clears and submits");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
