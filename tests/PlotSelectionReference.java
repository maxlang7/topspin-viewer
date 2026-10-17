import java.awt.*;
import java.lang.reflect.*;
import java.util.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.listener.AreaSelector;
import de.bruker.nmr.jview.view.SpecView1D;

public class PlotSelectionReference {
    static class Original extends AreaSelector {
        Original(Point start,SpecView1D view){super(start,view,10,"mousezoom");}
        // No worker thread is started; only desktop cleanup is suppressed.
        @Override public synchronized boolean exited(BMouseEventIfc event){return false;}
    }
    static BMouseEventIfc event(Point point) {
        return (BMouseEventIfc)Proxy.newProxyInstance(BMouseEventIfc.class.getClassLoader(),new Class<?>[]{BMouseEventIfc.class},(p,m,a)->{
            if(m.getName().equals("getPoint"))return point;
            throw new AssertionError("Unexpected event query "+m);
        });
    }
    public static void main(String[] args) {
        Random random=new Random(728182);int compared=0;
        for(int i=0;i<2000;i++) {
            Rectangle viewport=new Rectangle(2,2,20+random.nextInt(1800),20+random.nextInt(1000));
            Point from=new Point(random.nextInt(2000)-100,random.nextInt(1200)-100);
            Point to=i%7==0?from:new Point(random.nextInt(2000)-100,random.nextInt(1200)-100);
            SpecView1D view=new SpecView1D();view.setViewport(viewport);
            Original original=new Original(from,view);original.released(event(to));
            Rectangle actual=PlotSelection.horizontal(from,to,viewport);
            if(!actual.equals(original.getSelRan()))throw new AssertionError(original.getSelRan()+" != "+actual);
            view.setViewListener(null);compared++;
        }
        System.out.println("PASS "+compared+" original plot selection rectangles, including clicks and reversed drags");
    }
}
