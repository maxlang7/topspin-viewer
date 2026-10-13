import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import java.nio.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import de.bruker.nmr.jview.view.SpecView1D;
import de.bruker.nmr.jview.renderer.ForegroundRenderer;

/** Test-only original spectrum renderer on a controlled viewport. */
public class PlotRasterProbe {
    public static void main(String[] args) throws Exception {
        Path folder=Path.of(args[0]);Map<String,String> params=new HashMap<>();
        for(String line:Files.readAllLines(folder.resolve("procs")))if(line.startsWith("##$")){int equals=line.indexOf('=');if(equals>0)params.put(line.substring(3,equals),line.substring(equals+1).trim());}
        int size=Integer.parseInt(params.get("SI")),nc=Integer.parseInt(params.get("NC_proc"));
        ByteBuffer bytes=ByteBuffer.wrap(Files.readAllBytes(folder.resolve("1r"))).order(Integer.parseInt(params.get("BYTORDP"))==0?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN);
        double[] real=new double[size];for(int i=0;i<size;i++)real[i]=bytes.getInt()*Math.scalb(1.0,nc);
        var original=new oracle.LiveNumerics.Data(real);
        original.getProperties().setProperty("$localDATA_REF_INTENSITY","0");
        SpecView1D view=new SpecView1D();view.setViewedData(original);
        view.setProperty("DATA_COLOR","0 0 255");
        int width=1033,height=636;view.setViewport(new Rectangle(2,2,width,height));
        view.getCoordinateMapper()[0].setPhysicalMapping(12,-2);
        view.getCoordinateMapper()[0].setScreenMapping(2,width+1);
        double min=Arrays.stream(real).min().orElseThrow(),max=Arrays.stream(real).max().orElseThrow();
        view.getValueMapper().setPhysicalMapping(min,max);view.getValueMapper().setScreenMapping(2,height+1);view.getValueMapper().setBaselineShift(.15);
        BufferedImage image=new BufferedImage((width+4)*2,(height+4)*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();graphics.scale(2,2);graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width+4,height+4);
        Properties properties=new Properties();properties.setProperty("HIGH_QUALITY_RENDERING","yes");
        view.paint(new ForegroundRenderer(graphics,properties,new JPanel()));graphics.dispose();
        Path output=Path.of(args.length>1?args[1]:"artifacts/original-spectrum-raster.png");ImageIO.write(image,"png",output.toFile());
        System.out.println("Captured original renderer: "+output);
    }
}
