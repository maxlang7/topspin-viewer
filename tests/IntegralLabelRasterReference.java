import java.awt.*;
import de.bruker.nmr.an.int1d.IntegralView1D;
import de.bruker.nmr.jview.ifc.AbstractViewableDataIfc;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

public final class IntegralLabelRasterReference {
    static class View extends IntegralView1D {
        @Override protected void setBaseline(AbstractViewableDataIfc data){}
        @Override public void updateEreticView(){}
    }
    static class Data extends VendorDifferential.ReferenceIntegral {
        double value;
        Data(double value){super(new double[]{1,2,3},false,1);this.value=value;setFullPhysicalRange(new de.bruker.nmr.jview.ifc.PhysicalRangeIfc[]{new de.bruker.nmr.jview.data.PhysicalRange(12,-2)});}
        @Override public double getNormalizedValue(){return value;}
    }
    public static void main(String[] args)throws Exception {
        Assets.load();long pixels=0;int cases=0;
        for(int width:new int[]{3,17,50,151})for(int scale:new int[]{1,2})for(int digits:new int[]{0,2,4,6})for(boolean selected:new boolean[]{false,true})
        for(double value:new double[]{0,-.1234567,1,12345.6789}) {
            Rectangle viewport=new Rectangle(2,2,width,260);Data data=new Data(value);data.setSelected(selected);
            View view=new View();view.setViewedData(data);view.setViewport(viewport);view.setProperty("SHOW_INTEGRALS","no");view.setProperty("SHOW_INTEGRAL_LABEL","yes");
            view.setProperty("AXIS_FONT","FN=Arial,FY=BOLD,FS=14");view.setProperty("DATA_COLOR_2","237 28 36");view.setProperty("DATA_COLOR_HIGHLIGHT","200 255 0");view.setProperty("INTEG_DIGITS",Integer.toString(digits));
            var expected=AxisRasterReference.canvas(width,260,scale);var actual=AxisRasterReference.canvas(width,260,scale);
            Graphics2D g=expected.createGraphics();g.scale(scale,scale);view.paint(new AxisRasterReference.OriginalRenderer(g,Assets.bold.deriveFont(14f)));g.dispose();
            g=actual.createGraphics();g.scale(scale,scale);IntegralLabelPainter.paint(g,viewport,value,digits,selected);g.dispose();
            long different=0;for(int y=0;y<expected.getHeight();y++)for(int x=0;x<expected.getWidth();x++){pixels++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))different++;}
            if(different!=0)throw new AssertionError(different+" integral label pixels differ: width="+width+" value="+value+" digits="+digits+" selected="+selected);cases++;
        }
        for(int width:new int[]{17,151})for(int scale:new int[]{1,2})
        for(double factor:new double[]{.000001,.5,.9989,.999,1,1.001,1.0011,2,12.34567,1000000}) {
            Rectangle viewport=new Rectangle(2,2,width,260);Data data=new Data(1);
            View view=new View();view.setViewedData(data);view.setViewport(viewport);view.setProperty("SHOW_INTEGRALS","no");view.setProperty("SHOW_INTEGRAL_LABEL","yes");
            view.setProperty("AXIS_FONT","FN=Arial,FY=BOLD,FS=14");view.setProperty("DATA_COLOR_2","237 28 36");view.setFactorDisplay(false);view.setRelScalingFactor(factor);
            var expected=AxisRasterReference.canvas(width,260,scale);var actual=AxisRasterReference.canvas(width,260,scale);
            Graphics2D g=expected.createGraphics();g.scale(scale,scale);view.paint(new AxisRasterReference.OriginalRenderer(g,Assets.bold.deriveFont(14f)));g.dispose();
            g=actual.createGraphics();g.scale(scale,scale);IntegralLabelPainter.paint(g,viewport,1,4,false);IntegralLabelPainter.paintFactor(g,viewport,factor);g.dispose();
            long different=0;for(int y=0;y<expected.getHeight();y++)for(int x=0;x<expected.getWidth();x++){pixels++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))different++;}
            if(different!=0)throw new AssertionError(different+" factor label pixels differ: factor="+factor+" width="+width+" scale="+scale);cases++;
        }
        System.out.println("PASS "+cases+" original integral label cases, "+pixels+" exact pixels");
    }
}
