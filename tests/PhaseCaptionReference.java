import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.text.*;
import java.util.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.view.SpecView1D;
import bruker.bio.topspin.procphase.phas1d.*;

public final class PhaseCaptionReference {
    static class Controller extends bruker.bio.topspin.procphase.phas1d.Module {
        double point;
        Controller()throws Exception{super();}
        @Override public double getPivotPointPPM(){return point;}
        void format(){format=new DecimalFormat("0.00",DecimalFormatSymbols.getInstance(Locale.ROOT));incrementString="Phase increment = ";}
    }
    static class Layout extends Phase1DPanelLayout {
        Layout(Controller module){super(module);}
        Rectangle prepare(JRendererIfc renderer,Rectangle viewport,ViewIfc[] views){return prepareLayoutCalculation(renderer,viewport,views);}
    }
    static void set(Controller target,String name,double value)throws Exception {
        Field field=bruker.bio.topspin.procphase.phas1d.Module.class.getDeclaredField(name);field.setAccessible(true);field.setDouble(target,value);
    }
    public static void main(String[] args)throws Exception {
        Assets.load();Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        Controller original=(Controller)((sun.misc.Unsafe)singleton.get(null)).allocateInstance(Controller.class);original.format();
        Random random=new Random(203319);int cases=0;long pixels=0;
        for(int run=0;run<1000;run++) {
            double point=random.nextGaussian()*10,step=run%7==0?.00625:random.nextDouble()*20,zero=random.nextGaussian()*360,first=random.nextGaussian()*1e6;
            original.point=point;set(original,"mouseSens",step);set(original,"phaseIncr",1);set(original,"ph0",zero);set(original,"ph1",first);
            StringBuilder expected=new StringBuilder();for(String part:original.getInfoText(null))if(part!=null)expected.append(part).append("  ");
            String text=PhaseCaption.text(point,step,zero,first);if(!text.contentEquals(expected))throw new AssertionError(expected+" != "+text);
            if(run<40) {
                SpecView1D view=new SpecView1D();view.setViewedData(new VendorDifferential.ReferenceData(new double[]{1,2}));
                view.setProperty("PARAM_FONT","FN=Monospaced,FY=PLAIN,FS=12");view.setProperty("DATA_COLOR_2","237 28 36");view.setProperty("AXIS_BACKGROUND_COLOR","255 255 255");
                var a=AxisRasterReference.canvas(1000,25,1);var b=AxisRasterReference.canvas(1000,25,1);
                Graphics2D g=a.createGraphics();var renderer=new AxisRasterReference.OriginalRenderer(g,Assets.mono);Layout layout=new Layout(original);
                Rectangle viewport=layout.prepare(renderer,new Rectangle(0,0,1000,667),new ViewIfc[]{view});
                if(viewport.y!=PhaseCaption.height())throw new AssertionError("Phase header height differs");
                layout.paint(renderer,new Rectangle(0,0,1000,667),new ViewIfc[]{view});g.dispose();
                g=b.createGraphics();PhaseCaption.paint(g,text);g.dispose();
                for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){pixels++;if(a.getRGB(x,y)!=b.getRGB(x,y))throw new AssertionError("Phase caption pixel differs at "+x+","+y);}
            }
            cases++;
        }
        var capture=javax.imageio.ImageIO.read(java.nio.file.Path.of("evidence/ui/topspin-phase.png").toFile());
        var logical=AxisRasterReference.canvas(1066,14,1);Graphics2D g=logical.createGraphics();
        PhaseCaption.paint(g,PhaseCaption.text(3.78,.00625,0,0));g.dispose();
        BufferedImage actual=new BufferedImage(2132,44,BufferedImage.TYPE_INT_ARGB);
        g=actual.createGraphics();g.drawImage(logical,0,0,logical.getWidth()*2,logical.getHeight()*2,null);g.dispose();
        for(int y=0;y<28;y++)for(int x=0;x<1100;x++)if(capture.getRGB(704+x,422+y)!=actual.getRGB(x,y))throw new AssertionError("Windows phase-caption pixel "+x+","+y);
        System.out.println("PASS "+cases+" original phase-caption strings, "+pixels+" oracle raster pixels and 30800 captured Windows pixels");
    }
}
