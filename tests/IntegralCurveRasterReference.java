import java.awt.*;
import java.util.*;
import de.bruker.nmr.an.int1d.IntegralView1D;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.data.*;

public final class IntegralCurveRasterReference {
    static class Data extends VendorDifferential.ReferenceIntegral {
        Data(double[] curve){super(curve,false,1);setDataDouble(curve);setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(12,-2)});updateMinMax();}
    }
    static class View extends IntegralView1D {
        @Override protected void setBaseline(AbstractViewableDataIfc data){}
        @Override public void updateEreticView(){}
    }
    public static void main(String[] args)throws Exception {
        Assets.load();Random random=new Random(97112);long pixels=0;int cases=0;
        for(int kind=0;kind<5;kind++)for(int size:new int[]{4,71,4096})for(int width:new int[]{31,257})for(double reference:new double[]{0,-1e8})for(double factor:new double[]{.8,2,.125})
        for(double[] visible:new double[][]{{12,-2},{10.371,3.789}}) {
            double[] curve=new double[size];for(int i=0;i<size;i++)curve[i]=random.nextGaussian()*1e8;
            if(kind>0 && kind<4)Arrays.fill(curve,new double[]{0,0,-17,17}[kind]);
            var common=kind==0?new ViewRanges.Range(-1.4e8,5.6e8):kind==4?new ViewRanges.Range(0,0):IntegralCurvePainter.commonRange(java.util.List.of(curve),.2);
            Data data=new Data(curve);View view=new View();view.setViewedData(data);view.setScalingFactor(factor);
            Rectangle viewport=new Rectangle(2,2,width,173);view.setViewport(viewport);view.setRange(new PhysicalRangeIfc[]{new PhysicalRange(visible[0],visible[1])});
            view.setProperty("DATA_COLOR","237 28 36");view.setProperty("SHOW_INTEGRALS","yes");view.setProperty("SHOW_INTEGRAL_LABEL","no");
            view.getValueMapper().setReferenceIntensity(reference);view.getValueMapper().setPhysicalMapping(new PhysicalRange(common.start(),common.end()));view.getValueMapper().setScreenMapping(2,175);
            view.getCoordinateMapper()[0].setPhysicalMapping(visible[0],visible[1]);view.getCoordinateMapper()[0].setScreenMapping(2,width+2);
            var expected=AxisRasterReference.canvas(width,173,1);var actual=AxisRasterReference.canvas(width,173,1);
            Graphics2D g=expected.createGraphics();view.paint(new AxisRasterReference.OriginalRenderer(g,Assets.bold.deriveFont(14f)));g.dispose();
            g=actual.createGraphics();IntegralCurvePainter.paint(g,curve,12,-2,visible[0],visible[1],viewport,VisualNumerics.horizontal(visible[0],visible[1],2,width+2),common,reference,factor);g.dispose();
            long different=0;for(int y=0;y<expected.getHeight();y++)for(int x=0;x<expected.getWidth();x++){pixels++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))different++;}
            if(different!=0)throw new AssertionError(different+" integral curve pixels differ: n="+size+" width="+width+" reference="+reference+" factor="+factor+" start="+visible[0]);cases++;
        }
        System.out.println("PASS "+cases+" original integral curve cases, "+pixels+" exact pixels");
    }
}
