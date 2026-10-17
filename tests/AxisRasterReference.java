import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.Properties;
import javax.imageio.ImageIO;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.view.*;
import de.bruker.nmr.jview.renderer.ForegroundRenderer;

public class AxisRasterReference {
    static class OriginalRenderer extends ForegroundRenderer {
        final Graphics2D graphics;final Font font;
        OriginalRenderer(Graphics2D g,Font font){super(g,new Properties(),new javax.swing.JPanel());graphics=g;this.font=font;}
        // Use the pinned physical font on both sides instead of Linux's logical Dialog substitute.
        @Override public void setFont(Font ignored){graphics.setFont(font);}
    }
    static BufferedImage canvas(int width,int height,int scale) {
        BufferedImage image=new BufferedImage((width+8)*scale,(height+8)*scale,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(245,245,245));g.fillRect(0,0,image.getWidth(),image.getHeight());g.dispose();return image;
    }
    public static void main(String[] args) throws Exception {
        Assets.load();long compared=0;int cases=0;
        for(int width:new int[]{31,257,1033})for(int scale:new int[]{1,2})for(int position:new int[]{1,2})for(int offset:new int[]{0,2})
        for(double[] range:new double[][]{{12,-2},{7.321,7.019},{-23.001,102.345},{.000001,.000007},{3e8,9e8}}) {
            String unit=range[0]<.001 && range[0]>0?"s":range[0]>1e6?"Hz":"ppm";
            int height=29;Rectangle viewport=new Rectangle(2,2,width,height);Font font=Assets.bold.deriveFont(14f);
            Axis axis=new Axis();axis.setViewport(viewport);axis.setPosition(position);axis.setOffset(offset);axis.setUnit(unit);
            axis.setProperty("AXIS_COLOR","128 128 128");axis.setProperty("AXIS_FONT","FN=Arial,FY=BOLD,FS=14");
            axis.setRange(new PhysicalRange(range[0],range[1],unit));MapperX mapper=new MapperX();
            mapper.setPhysicalMapping(new PhysicalRange(range[0],range[1],unit));mapper.setScreenMapping(2,width+1);axis.setMapper(mapper);
            BufferedImage expected=canvas(width,height,scale),actual=canvas(width,height,scale);
            Graphics2D g=expected.createGraphics();g.scale(scale,scale);axis.paint(new OriginalRenderer(g,font));g.dispose();
            g=actual.createGraphics();g.scale(scale,scale);
            AxisPainter.paint(g,viewport,VisualNumerics.horizontal(range[0],range[1],2,width+1),AxisTicks.calculate(range[0],range[1],unit,null,true,true),font,new Color(128,128,128),position,offset);g.dispose();
            long different=0;for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++){compared++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))different++;}
            if(different!=0){ImageIO.write(expected,"png",Path.of("artifacts/original-axis-raster.png").toFile());ImageIO.write(actual,"png",Path.of("artifacts/standalone-axis-raster.png").toFile());throw new AssertionError(different+" axis pixels differ at width="+width+" scale="+scale+" position="+position+" offset="+offset+" start="+range[0]);}
            cases++;
        }
        System.out.println("PASS "+cases+" original axis raster cases, "+compared+" exact pixels");
    }
}
