import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;

public class NavigationTabsRasterReference {
    public static void main(String[] args)throws Exception {
        Assets.load();SwingUtilities.invokeAndWait(()->{try {
            NavigationTabs tabs=new NavigationTabs();tabs.addTab("Process",75,0,()->{});tabs.addTab("Analyze",74,1,()->{});tabs.setSize(tabs.getPreferredSize());
            BufferedImage actual=new BufferedImage(tabs.getWidth()*2,100,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);tabs.paint(g);g.dispose();
            BufferedImage source=ImageIO.read(Path.of("evidence/ui/topspin-integration.png").toFile());
            long pixels=0,different=0;
            for(int y=0;y<100;y++)for(int x=0;x<actual.getWidth();x++) {pixels++;if(actual.getRGB(x,y)!=source.getRGB(132+x,34+y)){if(different++<12)System.out.println("diff "+x+","+y+" "+Integer.toHexString(actual.getRGB(x,y))+" / "+Integer.toHexString(source.getRGB(132+x,34+y)));}}
            if(different!=0)throw new AssertionError(different+" navigation pixels differ");
            System.out.println("PASS retained navigation tabs: "+pixels+" exact captured Windows pixels");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
