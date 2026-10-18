import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Retained tabs against original Windows selected, hover and on-screen references. */
public class DatasetTabsRasterReference {
    private static final Path ROOT=Path.of("artifacts/windows-dataset-tabs-reference-20261018-5");
    private static long compare(BufferedImage actual,BufferedImage source,String state)throws Exception {
        long different=0;
        for(int y=0;y<64;y++)for(int x=0;x<448;x++)if(actual.getRGB(x,y)!=source.getRGB(x+18,y+18))different++;
        if(different!=0){ImageIO.write(actual,"png",Path.of("artifacts/dataset-tabs-mismatch.png").toFile());throw new AssertionError(different+" dataset tab pixels differ: "+state);}
        return 448*64;
    }
    public static void main(String[] args)throws Exception {
        Assets.load();SwingUtilities.invokeAndWait(()->{try {
            DatasetTabs tabs=new DatasetTabs(name->{});tabs.setSize(224,32);tabs.doLayout();String[] names={"SPECTRUM","INTEGRALS","FID"};long pixels=0;
            for(int selected=0;selected<3;selected++)for(int hover=-1;hover<3;hover++) {
                tabs.select(names[selected]);for(int i=0;i<3;i++)((JButton)tabs.getComponent(i)).getModel().setRollover(i==hover);
                BufferedImage actual=new BufferedImage(448,64,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);tabs.paint(g);g.dispose();
                String state=selected+"-"+hover;pixels+=compare(actual,ImageIO.read(ROOT.resolve("tabs-"+state+".png").toFile()),state);
                if(selected==2 && hover==-1)pixels+=compare(actual,ImageIO.read(ROOT.resolve("screen.png").toFile()),"on-screen");
            }
            System.out.println("PASS 12 selected/hover dataset-tab states and on-screen Windows reference: "+pixels+" exact pixels");
        }catch(Exception error){throw new RuntimeException(error);}});
    }
}
