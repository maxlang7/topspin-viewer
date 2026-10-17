import java.awt.*;
import java.util.Arrays;

/** Integral trace rendering with a shared display range and each region's reference intensity. */
public final class IntegralCurvePainter {
    public static double reference(double[] curve) {
        double min=curve[0],max=curve[0];for(int i=1;i<curve.length;i++){if(curve[i]<min)min=curve[i];else if(curve[i]>max)max=curve[i];}
        return 0>max?min:0;
    }
    public static ViewRanges.Range commonRange(java.util.List<double[]> curves,double baseline) {
        ViewRanges.Range common=null;
        for(double[] curve:curves) {
            double min=curve[0],max=curve[0];for(int i=1;i<curve.length;i++){if(curve[i]<min)min=curve[i];else if(curve[i]>max)max=curve[i];}
            var range=ViewRanges.baseline(new ViewRanges.Range(min,max),baseline,0>max?min:0);
            if(common==null || range.end()>common.end())common=range;
        }
        return common;
    }
    public static ViewRanges.Range scaledRange(ViewRanges.Range common,double reference,double factor) {
        double extent=common.end()-common.start();
        double baseline=extent==0 || Math.abs(extent)<1e-10?0:(reference-common.start())/extent;
        return ViewRanges.baseline(ViewRanges.scale(common,factor,reference),baseline,reference);
    }
    public static int paint(Graphics2D graphics,double[] curve,double left,double right,
            double viewLeft,double viewRight,Rectangle viewport,VisualNumerics.Mapper horizontal,
            ViewRanges.Range common,double reference,double factor) {
        ViewRanges.Range range=scaledRange(common,reference,factor);
        if(range.start()==range.end()) {
            double min=curve[0],max=curve[0];for(int i=1;i<curve.length;i++){if(curve[i]<min)min=curve[i];else if(curve[i]>max)max=curve[i];}
            range=min!=max?new ViewRanges.Range(min,max):ViewRanges.baseline(new ViewRanges.Range(min-1,max),0,reference);
        }
        var vertical=VisualNumerics.vertical(range.start(),range.end(),viewport.y,viewport.y+viewport.height);
        int start=Numerics.index(viewLeft,left,right,curve.length),end=Numerics.index(viewRight,left,right,curve.length);
        start=Math.max(0,Math.min(curve.length-1,start));end=Math.max(start,Math.min(curve.length-1,end));
        Graphics2D g=(Graphics2D)graphics.create();g.setColor(new Color(237,28,36));
        SpectrumPainter.paintPanel(g,Arrays.copyOfRange(curve,start,end+1),start,viewport.x,viewport.y,viewport.width,viewport.height,horizontal,vertical,left,right,curve.length,1);g.dispose();
        return vertical.screen(curve[curve.length-1],1);
    }
    private IntegralCurvePainter(){}
}
