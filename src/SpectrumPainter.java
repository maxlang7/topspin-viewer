import java.awt.*;

/** Uniform-spectrum drawing with the target renderer's stroke and compression. */
public final class SpectrumPainter {
    public static void paint(Graphics2D g,double[] values,int start,int x,int y,int width,int height,
            VisualNumerics.Mapper horizontal,VisualNumerics.Mapper vertical,double left,double right,int size,double gain) {
        paintClipped(g,values,start,x,y,width,height,horizontal,vertical,left,right,size,gain,height-1);
    }
    /** The complete data panel lays out the intensity mapper through y + height. */
    public static void paintPanel(Graphics2D g,double[] values,int start,int x,int y,int width,int height,
            VisualNumerics.Mapper horizontal,VisualNumerics.Mapper vertical,double left,double right,int size,double gain) {
        paintClipped(g,values,start,x,y,width,height,horizontal,vertical,left,right,size,gain,height);
    }
    private static void paintClipped(Graphics2D g,double[] values,int start,int x,int y,int width,int height,
            VisualNumerics.Mapper horizontal,VisualNumerics.Mapper vertical,double left,double right,int size,double gain,int clipHeight) {
        var compressed=VisualNumerics.compress(values,start,width);
        if(values.length<2*width)compressed=null;
        double[] data=compressed==null?values:compressed.values();int[] xs=new int[data.length],ys=new int[data.length];
        for(int i=0;i<data.length;i++) {
            xs[i]=compressed==null?horizontal.screen(left+((right-left)/size)*(start+i),1):x+i/2;
            ys[i]=vertical.screen(data[i],gain);
        }
        Graphics2D drawing=(Graphics2D)g.create();
        drawing.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
        drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        drawing.clipRect(x,y,width+1,clipHeight);
        // The original compressed buffer flushes at capacity minus one and
        // leaves its final vertex in a separate, one-point flush.
        drawing.drawPolyline(xs,ys,compressed==null?data.length:Math.max(0,data.length-1));drawing.dispose();
    }
    private SpectrumPainter() {}
}
