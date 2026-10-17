package oracle;
import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.Properties;
import javax.imageio.ImageIO;
import de.bruker.nmr.jview.data.PhysicalRange;
import de.bruker.nmr.jview.view.*;
import de.bruker.nmr.jview.renderer.ForegroundRenderer;

/** A pure axis renderer in the target Windows JVM; no dataset or license services. */
public class WindowsAxisCapture {
    public static void main(String[] args) throws Exception {
        int width=1033,height=27;double start=14.9974006758278,end=-4.9974006758207725;
        BufferedImage image=new BufferedImage((width+8)*2,(height+8)*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);g.setColor(new Color(245,245,245));g.fillRect(0,0,width+8,height+8);
        Axis axis=new Axis();axis.setViewport(new Rectangle(2,2,width,height));axis.setPosition(1);axis.setOffset(0);axis.setUnit("ppm");
        axis.setProperty("AXIS_COLOR","128 128 128");axis.setProperty("AXIS_FONT","FN=Dialog,FY=BOLD,FS=14");
        axis.setRange(new PhysicalRange(start,end,"ppm"));MapperX mapper=new MapperX();mapper.setPhysicalMapping(new PhysicalRange(start,end,"ppm"));mapper.setScreenMapping(2,width+1);axis.setMapper(mapper);
        axis.paint(new ForegroundRenderer(g,new Properties(),new javax.swing.JPanel()));
        System.out.println("Axis font="+g.getFont()+" height="+g.getFontMetrics().getHeight()+" stroke="+((BasicStroke)g.getStroke()).getLineWidth());
        System.out.println("user scale="+com.formdev.flatlaf.util.UIScale.getUserScaleFactor());
        g.dispose();Path destination=Path.of(args[0]);
        try(var output=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){ImageIO.write(image,"png",output);}
    }
}
