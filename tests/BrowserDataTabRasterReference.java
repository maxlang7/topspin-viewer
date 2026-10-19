import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;

public class BrowserDataTabRasterReference {
    public static void main(String[] args)throws Exception {
        Assets.load();SwingUtilities.invokeAndWait(()->{try {
            BrowserDataTab tab=new BrowserDataTab(()->{});tab.setSize(72,32);
            BufferedImage actual=new BufferedImage(144,64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);tab.paint(g);g.dispose();
            BufferedImage source=ImageIO.read(Path.of("evidence/ui/topspin-integration.png").toFile());
            long different=0;
            for(int y=0;y<64;y++)for(int x=0;x<144;x++)if(actual.getRGB(x,y)!=source.getRGB(36+x,336+y))different++;
            if(different!=0){ImageIO.write(actual,"png",Path.of("artifacts/browser-data-tab-mismatch.png").toFile());throw new AssertionError(different+" Data-tab pixels differ");}
            System.out.println("PASS retained browser Data tab: 9216 exact captured Windows pixels");
        }catch(Exception error){throw new RuntimeException(error);}});
    }
}
