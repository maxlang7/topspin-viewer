import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

public class RibbonRasterReference {
    public static void main(String[] args) throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);UIManager.put("Component.hideMnemonics",false);
        Ribbon ribbon=new Ribbon();ribbon.addCommand("Proc. Spectrum",Assets.icon("phase","text.Proc. Spectrum"),125,()->{});
        ribbon.addCommand("Adjust Phase",Assets.icon("phase","text.Adjust Phase"),111,()->{});
        JFrame[] frame=new JFrame[1];SwingUtilities.invokeAndWait(()->{
            frame[0]=new JFrame();frame[0].setUndecorated(true);frame[0].setContentPane(ribbon);frame[0].setSize(317,50);frame[0].addNotify();frame[0].validate();
        });
        BufferedImage actual=new BufferedImage(634,100,BufferedImage.TYPE_INT_ARGB);Graphics2D g=actual.createGraphics();
        g.scale(2,2);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);ribbon.printAll(g);g.dispose();
        BufferedImage original=ImageIO.read(Path.of("evidence/ui/topspin-phase.png").toFile());
        BufferedImage expected=original.getSubimage(8,138,634,100);
        long diff=0;int minX=634,minY=100,maxX=0,maxY=0;
        for(int y=0;y<100;y++)for(int x=0;x<634;x++)if(actual.getRGB(x,y)!=expected.getRGB(x,y)){diff++;minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        ImageIO.write(actual,"png",Path.of("artifacts/standalone-ribbon.png").toFile());
        ImageIO.write(expected,"png",Path.of("artifacts/original-ribbon.png").toFile());
        SwingUtilities.invokeAndWait(frame[0]::dispose);
        if(diff!=0)throw new AssertionError("Ribbon differs on "+diff+" pixels at "+minX+","+minY+".."+maxX+","+maxY);
        System.out.println("PASS processing ribbon: 63400 exact text, mnemonic, icon, arrow and separator pixels");
    }
}
