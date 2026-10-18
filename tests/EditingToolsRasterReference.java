import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

public final class EditingToolsRasterReference {
    static BufferedImage raster(JComponent component) {
        component.setSize(component.getPreferredSize());if(component instanceof Container container)container.doLayout();
        BufferedImage image=new BufferedImage(component.getWidth()*2,component.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);component.paint(g);g.dispose();return image;
    }
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        SwingUtilities.invokeAndWait(()->{try {
            BufferedImage source=ImageIO.read(Path.of("evidence/ui/topspin-phase.png").toFile());long compared=0;
            String[] controls={"0 order correction","1st order correction","Reset phase angles","Add 90 degrees","Subtract 90 degrees","Add 180 degrees","Increase mouse sensitivity","Decrease mouse sensitivity","Reset mouse sensitivity","Save for nD spectrum","Return & save phased spectrum","Return, do NOT save"};
            for(boolean retainNd:new boolean[]{true,false}) {
                EditingTools panel=new EditingTools();
                for(int i=0;i<controls.length;i++){if(i!=9 || retainNd)panel.addTool("phase",controls[i],()->{});if(i==2 || i==5 || i==8)panel.separator();}
                BufferedImage actual=raster(panel);
                for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++) {
                    int originalX=!retainNd && x>=562?x+60:x;compared++;
                    if(actual.getRGB(x,y)!=source.getRGB(1118+originalX,338+y))throw new AssertionError("Phase toolbar pixel "+x+","+y+" differs");
                }
            }
            for(String mode:new String[]{"phase","integration"}) {
                String text=mode.equals("phase")?"Interactive phase correction":"Integration";
                ModeLabel label=new ModeLabel(text,Assets.icon(mode,"text."+text));BufferedImage actual=raster(label);
                source=ImageIO.read(Path.of("evidence/ui/topspin-"+mode+".png").toFile());
                for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++){compared++;if(actual.getRGB(x,y)!=source.getRGB(704+x,338+y))throw new AssertionError(mode+" mode label pixel "+x+","+y+" differs");}
            }
            System.out.println("PASS phase toolbar, retained controls and both mode labels: "+compared+" exact captured Windows pixels");
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
