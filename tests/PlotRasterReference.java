import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
public class PlotRasterReference {
    public static void main(String[] args) throws Exception {
        BrukerDataset data=new BrukerDataset(Path.of(args[0]));int w=1033,h=636;
        BufferedImage image=new BufferedImage((w+4)*2,(h+4)*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);g.setColor(Color.WHITE);g.fillRect(0,0,w+4,h+4);g.setColor(Color.BLUE);
        double min=Arrays.stream(data.real).min().orElseThrow(),max=Arrays.stream(data.real).max().orElseThrow(),span=max-min,left=-span*.15,right=left+span;
        SpectrumPainter.paint(g,data.real,0,2,2,w,h,VisualNumerics.horizontal(12,-2,2,w+1),VisualNumerics.vertical(left,right,2,h+1),12,-2,data.real.length,1);g.dispose();
        ImageIO.write(image,"png",Path.of("artifacts/standalone-spectrum-raster.png").toFile());
        BufferedImage expected=ImageIO.read(Path.of(args.length>1?args[1]:"artifacts/original-spectrum-raster.png").toFile());long differences=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if(image.getRGB(x,y)!=expected.getRGB(x,y))differences++;
        System.out.println("Spectrum raster pixel differences: "+differences+" of "+image.getWidth()*image.getHeight());
        if(differences!=0)throw new AssertionError("Spectrum raster is not yet equivalent");
    }
}
