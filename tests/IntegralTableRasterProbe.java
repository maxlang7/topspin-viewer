import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Whole-table acceptance comparison against a captured Windows TopSpin table. */
public class IntegralTableRasterProbe {
    public static void main(String[] args) throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        IntegralTable table=new IntegralTable();table.load(new Spectrum(Path.of(args[0])));
        JFrame[] frame=new JFrame[1];
        if(!GraphicsEnvironment.isHeadless())SwingUtilities.invokeAndWait(()->{
            frame[0]=new JFrame();frame[0].setUndecorated(true);frame[0].setContentPane(table);frame[0].setSize(1066,667);frame[0].addNotify();frame[0].validate();
        });
        table.setSize(1066,667);table.doLayout();table.getViewport().doLayout();
        table.getColumnHeader().doLayout();table.table.doLayout();
        BufferedImage actual=new BufferedImage(2132,1334,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=actual.createGraphics();g.scale(2,2);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);table.printAll(g);g.dispose();
        ImageIO.write(actual,"png",Path.of("artifacts/standalone-integral-table.png").toFile());
        BufferedImage expected=ImageIO.read(Path.of("artifacts/original-integral-table.png").toFile());long difference=0;
        for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++)
            if(actual.getRGB(x,y)!=expected.getRGB(x,y))difference++;
        if(frame[0]!=null)SwingUtilities.invokeAndWait(frame[0]::dispose);
        if(difference!=0)throw new AssertionError("Integral table differs on "+difference+" pixels");
        System.out.println("PASS integral table raster: "+actual.getWidth()*actual.getHeight()+" exact pixel comparisons");
    }
}
