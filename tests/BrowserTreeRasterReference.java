import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.*;
import com.formdev.flatlaf.FlatLightLaf;
public class BrowserTreeRasterReference {
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        SwingUtilities.invokeAndWait(()->{try {
            DefaultMutableTreeNode root=new DefaultMutableTreeNode("Data");
            for(String path:new String[]{"C:\\Bruker\\TopSpinProcessing5.0.0\\examdata","C:\\topspin-license-test-mymoasab","C:\\users\\maxlang\\Documents\\IGB Lab\\NMRs","Z:\\home\\maxlang\\Projects\\topspin-viewer\\artifacts\\reference-datasets\\datasets"}){DefaultMutableTreeNode node=new DefaultMutableTreeNode(path);node.add(new DefaultMutableTreeNode("child"));root.add(node);}
            BrowserTree tree=new BrowserTree(root);tree.setSize(304,198);for(int i=0;i<4;i++)tree.collapseRow(i);
            BufferedImage actual=new BufferedImage(608,396,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);tree.paint(g);g.dispose();
            BufferedImage source=ImageIO.read(Path.of("evidence/ui/topspin-integration.png").toFile());
            long pixels=0,different=0;
            for(int y=0;y<396;y++)for(int x=0;x<608;x++) {pixels++;if(actual.getRGB(x,y)!=source.getRGB(36+x,514+y))different++;}
            if(different!=0)throw new AssertionError(different+" browser root-row pixels differ");
            System.out.println("PASS browser root rows and viewport: "+pixels+" exact captured Windows pixels");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
