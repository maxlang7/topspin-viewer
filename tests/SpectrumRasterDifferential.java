import java.awt.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import de.bruker.nmr.jview.view.SpecView1D;
import de.bruker.nmr.jview.renderer.ForegroundRenderer;

/** Original renderer is used only as a test oracle. Every pixel must match. */
public class SpectrumRasterDifferential {
    private static BufferedImage canvas(int width,int height,int scale) {
        BufferedImage image=new BufferedImage((width+8)*scale,(height+8)*scale,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.dispose();return image;
    }
    static long pixels;static int cases;
    private static void compare(double[] data,int width,int height,int scale,double baseline,double gain,double viewLeft,double viewRight) throws Exception {
        compare(data,width,height,scale,baseline,gain,12,-2,viewLeft,viewRight);
    }
    private static void compare(double[] data,int width,int height,int scale,double baseline,double gain,double fullLeft,double fullRight,double viewLeft,double viewRight) throws Exception {
        var original=new VendorDifferential.ReferenceData(data);
        String unit=fullLeft<fullRight?"s":"";
        original.setFullPhysicalRange(new de.bruker.nmr.jview.ifc.PhysicalRangeIfc[]{new de.bruker.nmr.jview.data.PhysicalRange(fullLeft,fullRight,unit)});
        original.getProperties().setProperty("$localDATA_REF_INTENSITY","0");
        SpecView1D view=new SpecView1D();view.setViewedData(original);view.setProperty("DATA_COLOR","0 0 255");
        view.setViewport(new Rectangle(2,2,width,height));
        view.getCoordinateMapper()[0].setPhysicalMapping(new de.bruker.nmr.jview.data.PhysicalRange(viewLeft,viewRight,unit));view.getCoordinateMapper()[0].setScreenMapping(2,width+1);
        double min=Arrays.stream(data).min().orElseThrow(),max=Arrays.stream(data).max().orElseThrow(),span=max-min;
        if(span==0){min-=1;span=max-min;}
        view.getValueMapper().setPhysicalMapping(min,max);view.getValueMapper().setScreenMapping(2,height+1);
        view.getValueMapper().setBaselineShift(baseline);view.getValueMapper().setIndividualScale(gain);
        BufferedImage expected=canvas(width,height,scale),actual=canvas(width,height,scale);
        Graphics2D g=expected.createGraphics();g.scale(scale,scale);
        view.paint(new ForegroundRenderer(g,new Properties(),new javax.swing.JPanel()));g.dispose();
        g=actual.createGraphics();g.scale(scale,scale);g.setColor(Color.BLUE);
        double left=-span*baseline;
        var range=view.getRange();double[] visible=original.getDataDouble(range);int start=original.getIndexRange(range)[0].getStart();
        SpectrumPainter.paint(g,visible,start,2,2,width,height,VisualNumerics.horizontal(viewLeft,viewRight,2,width+1),
            VisualNumerics.vertical(left,left+span,2,height+1),fullLeft,fullRight,data.length,gain);g.dispose();
        long diff=0;for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++) {
            pixels++;if(actual.getRGB(x,y)!=expected.getRGB(x,y))diff++;
        }
        if(diff!=0) {
            for(int y=0;y<actual.getHeight();y++)for(int x=0;x<actual.getWidth();x++)if(actual.getRGB(x,y)!=expected.getRGB(x,y) && diff<20)System.out.println("pixel "+x+","+y+" expected="+Integer.toHexString(expected.getRGB(x,y))+" actual="+Integer.toHexString(actual.getRGB(x,y)));
            if(data.length<20)for(int i=0;i<data.length;i++)System.out.println("vertex "+i+" physical="+original.getPhysicalFromIndex(i,0)+" x="+view.getCoordinateMapper()[0].mapToScreen(original.getPhysicalFromIndex(i,0))+" y="+view.getValueMapper().mapToScreen(data[i]));
            ImageIO.write(expected,"png",Path.of("artifacts/raster-differential-expected.png").toFile());
            ImageIO.write(actual,"png",Path.of("artifacts/raster-differential-actual.png").toFile());
            throw new AssertionError(diff+" pixels differ: n="+data.length+" w="+width+" h="+height+" scale="+scale+" baseline="+baseline+" gain="+gain);
        }cases++;
    }
    public static void main(String[] args) throws Exception {
        Random random=new Random(752614L);
        for(int n:new int[]{4,17,71,256,4096,65536}) {
            double[] data=new double[n];for(int i=0;i<n;i++)data[i]=random.nextGaussian()*1e7;
            for(int width:new int[]{31,257,1033})for(int scale:new int[]{1,2})
            for(double gain:new double[]{.5,1,4})for(double baseline:new double[]{.05,.15,.4})
                compare(data,width,Math.max(43,width/2),scale,baseline,gain,12,-2);
            for(double[] range:new double[][]{{11.713,3.219},{7.321,7.019},{1.03,-1.7}})
            for(int width:new int[]{31,257})compare(data,width,173,2,.15,1,range[0],range[1]);
        }
        for(double constant:new double[]{0,17,-123}){double[] data=new double[256];Arrays.fill(data,constant);compare(data,257,173,2,.15,1,12,-2);}
        int spectrumCases=cases;long spectrumPixels=pixels;
        for(int n:new int[]{4,17,71,256,4096,32768})for(double duration:new double[]{1e-6,.128,2.730666666666667}) {
            double[] data=new double[n];
            for(int i=0;i<n;i++)data[i]=Math.exp(-i/(n*.15))*Math.cos(i*.37)*1e8;
            for(int width:new int[]{31,1033})for(int scale:new int[]{1,2}) {
                compare(data,width,173,scale,.5,1,0,duration,0,duration);
                compare(data,width,173,scale,.5,.5,0,duration,duration*.125,duration*.625);
            }
        }
        System.out.println("PASS "+spectrumCases+" spectrum raster cases, "+spectrumPixels+" exact pixel comparisons; "+(cases-spectrumCases)+" ascending-time FID-like trace cases, "+(pixels-spectrumPixels)+" exact pixels");
    }
}
