import java.lang.reflect.*;
import java.util.*;
import javax.swing.JPanel;
import de.bruker.nmr.jutil.ifc.SelectionIfc;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.module.BasicModule;

/** Execute the top-level module's reset and zoom commands, rather than the view-only handlers. */
public final class ModuleRangesReference {
    private static final class Sink extends JPanel implements SelectionIfc {
        PhysicalRangeIfc[] full,viewed;
        PhysicalRangeIfc reset;
        public Object getData(){return null;}
        public void redisplay(int reason){}
        public void notifySelected(){}
        public void notifyDeselected(){}
        public void cleanUp(){}
        public boolean doCloseAction(){return true;}
        public Object performAction(String command,Object first,Object second) {
            switch(command) {
                case "showfullrange"->viewed=RangeUtil.duplicate(full);
                case "vreset"->reset=(PhysicalRangeIfc)first;
                case "exactzoom"->viewed=RangeUtil.commonPart((PhysicalRangeIfc[])first,full);
                default->throw new AssertionError("Unexpected controller action "+command);
            }
            return this;
        }
    }
    private static void field(Object target,String name,Object value)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {
            Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;
        }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
    }
    private static BasicModule controller(VendorDifferential.ReferenceData data,boolean time,Sink sink)throws Exception {
        NMRDataIfc wrapped=(NMRDataIfc)Proxy.newProxyInstance(NMRDataIfc.class.getClassLoader(),new Class<?>[]{NMRDataIfc.class},(proxy,method,args)->{
            if(method.getName().equals("isTimeDomain") || method.getName().equals("isRawData"))return time;
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {case "toString"->"Copied in-memory NMR fixture";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
            try{return data.getClass().getMethod(method.getName(),method.getParameterTypes()).invoke(data,args);}
            catch(InvocationTargetException error){throw error.getCause();}
        });
        BasicModule module=new BasicModule();
        field(module,"localData",new java.util.concurrent.CopyOnWriteArrayList<>(List.of(wrapped)));
        field(module,"moduleContainer",sink);
        PanelLayoutIfc layout=(PanelLayoutIfc)Proxy.newProxyInstance(PanelLayoutIfc.class.getClassLoader(),new Class<?>[]{PanelLayoutIfc.class},(proxy,method,args)->switch(method.getName()) {
            case "usesSynchronizedViews"->true;
            case "getViewedRegion"->sink.viewed;
            case "getReferenceRegion"->sink.full;
            default->throw new AssertionError("Unexpected panel-layout call "+method);
        });
        ViewPanelIfc panel=(ViewPanelIfc)Proxy.newProxyInstance(ViewPanelIfc.class.getClassLoader(),new Class<?>[]{ViewPanelIfc.class},(proxy,method,args)->{
            if(method.getName().equals("getPanelLayout"))return layout;throw new AssertionError("Unexpected panel call "+method);
        });
        field(module,"dataPanel",panel);return module;
    }
    static PhysicalRangeIfc padded(double low,double high)throws Exception {
        var data=new VendorDifferential.ReferenceData(new double[]{low,high});data.setValueRange(new PhysicalRange(low,high,""));
        Sink sink=new Sink();sink.full=data.getFullPhysicalRange();sink.viewed=sink.full;
        controller(data,false,sink).CMD_resetall(null,null);
        if(sink.reset==null)throw new AssertionError("Original reset did not provide an intensity range");return sink.reset;
    }
    private static void equal(PhysicalRangeIfc expected,ViewRanges.Range actual,String context) {
        if(expected==null || actual==null){if(expected!=null || actual!=null)throw new AssertionError(context+" null range differs");return;}
        VendorDifferential.equal(new double[]{expected.getStart(),expected.getEnd()},new double[]{actual.start(),actual.end()},context);
    }
    public static void main(String[] args)throws Exception {
        Random random=new Random(0x4d4f44554c45524cL);int resets=0,zooms=0;
        for(int i=0;i<4000;i++) {
            double low=i%19==0?0:-random.nextDouble()*Math.pow(10,i%24-12),high=i%19==0?0:random.nextDouble()*Math.pow(10,i%24-12);
            equal(padded(low,high),ViewRanges.padded(new ViewRanges.Range(low,high)),"module reset padding");resets++;
            boolean time=i%2==0;double start=time?0:15,end=time?1+random.nextDouble()*10:-5;
            var full=new ViewRanges.Range(start,end);double a=start+(end-start)*.1,b=start+(end-start)*.8;
            var data=new VendorDifferential.ReferenceData(new double[]{1,-2,3,4});data.setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(start,end,time?"s":"ppm")});
            Sink sink=new Sink();sink.full=data.getFullPhysicalRange();BasicModule module=controller(data,time,sink);
            for(double factor:new double[]{2,.5,1.025,.9756097560975611,0,3.7}) {
                sink.viewed=new PhysicalRangeIfc[]{new PhysicalRange(a,b,time?"s":"ppm")};
                module.CMD_zoomin(factor,null);
                equal(sink.viewed==null?null:sink.viewed[0],ViewRanges.zoom(new ViewRanges.Range(a,b),factor,full,time),"module zoom policy");zooms++;
            }
        }
        System.out.println("PASS "+resets+" original module resets and "+zooms+" original module zoom commands, both time-domain/left-bound and spectrum/centered policies; "+(resets+zooms)*2+" bitwise endpoints");
    }
}
