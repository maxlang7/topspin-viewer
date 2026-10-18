import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

public final class ViewerToolsRasterReference {
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();SwingUtilities.invokeAndWait(()->{try {
            String[] tools={"Larger by factor 2","Smaller by factor 2","Smaller/larger","Smooth zoom","Shift baseline up/down","Shift spectrum left","Shift baseline to bottom","Zoom in","Zoom out","Show the last zoom","Show full spectrum","Synchronize visible regions","Retain expansion and scale","Start distance measurement","Multiple display","More icons"};
            BufferedImage source=ImageIO.read(Path.of("evidence/ui/topspin-phase.png").toFile());long pixels=0;
            {
                ViewerTools panel=new ViewerTools();
                int limit=11;
                for(int i=0;i<limit;i++){if(i==2 || i==4 || i==7 || i==13 || i==15)panel.separator();panel.addTool(tools[i],()->{});}
                panel.setSize(panel.getPreferredSize());panel.doLayout();
                BufferedImage actual=new BufferedImage(panel.getWidth()*2,panel.getHeight()*2,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();g.scale(2,2);panel.paint(g);g.dispose();
                long different=0;
                for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++) {
                    pixels++;if(actual.getRGB(x,y)!=source.getRGB(16+x,240+y)){if(different++<5)System.out.println("diff "+x+","+y+" "+Integer.toHexString(actual.getRGB(x,y))+" / "+Integer.toHexString(source.getRGB(16+x,240+y)));}
                }
                if(different!=0)throw new AssertionError(different+" retained viewer toolbar pixels differ");
            }
            System.out.println("PASS retained viewer toolbar: "+pixels+" exact captured Windows pixels");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
