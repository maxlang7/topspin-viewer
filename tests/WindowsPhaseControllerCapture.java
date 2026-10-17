package oracle;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import bruker.bio.topspin.procphase.phas1d.PhasedData;

/** Read-only numerical oracle in the bundled Windows JVM, with desktop repainting suppressed. */
public class WindowsPhaseControllerCapture {
    static class Data extends PhasedData {
        Data(int size,int ft,int strip) {
            dataRe=new double[size];dataIm=new double[size];dataOut=new double[size];ftSize=ft;stripStart=strip;
            setDimension(1);setSizes(new int[]{size});setFullSizes(new int[]{size});
        }
        @Override public void invalidateData(){}
        void sample(double real,double imaginary){dataRe[0]=real;dataIm[0]=imaginary;}
    }
    static class Controller extends bruker.bio.topspin.procphase.phas1d.Module {
        Controller() throws Exception {super();}
        @Override public void fullRepaint(){}
    }
    static void set(Object target,String name,Object value) throws Exception {
        Field f=bruker.bio.topspin.procphase.phas1d.Module.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static double get(Object target,String name) throws Exception {
        Field f=bruker.bio.topspin.procphase.phas1d.Module.class.getDeclaredField(name);f.setAccessible(true);return f.getDouble(target);
    }
    static Controller create(Data data,int pivot,double sensitivity) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        Controller result=(Controller)((sun.misc.Unsafe)f.get(null)).allocateInstance(Controller.class);
        Vector<PhasedData> phases=new Vector<>();phases.add(data);
        set(result,"phasedData",phases);set(result,"n0",pivot);set(result,"mouseSens",sensitivity);set(result,"phaseIncr",1.0);
        return result;
    }
    static void row(StringBuilder text,Object... fields) {
        for(int i=0;i<fields.length;i++){if(i>0)text.append('\t');Object value=fields[i];text.append(value instanceof Double?Double.toHexString((Double)value):value);}text.append('\n');
    }
    public static void main(String[] args) throws Exception {
        StringBuilder text=new StringBuilder("# Windows JVM "+System.getProperty("java.version")+"\n");
        for(int ft:new int[]{0,128,65536})for(int strip:new int[]{0,24})for(int pivot:new int[]{0,37,99})for(double sensitivity:new double[]{.00625,.2,1}) {
            Data data=new Data(128,ft,strip);Controller original=create(data,pivot,sensitivity);
            for(double amount:new double[]{20,-10,.5,1.23456789,-2000,40000,-80000})for(int order:new int[]{0,1}) {
                double zero=get(original,"ph0"),first=get(original,"ph1");
                if(order==0)original.CMD__ph0(amount,null);else original.CMD__ph1(amount,null);
                row(text,"drag",zero,first,order,amount,sensitivity,pivot,strip,ft,128,get(original,"ph0"),get(original,"ph1"));
            }
            for(int preset:new int[]{90,90,180,-90,-90,180,180}) {
                double zero=get(original,"ph0");
                if(preset==90)original.CMD_ph90(null,null);else if(preset==-90)original.CMD_phm90(null,null);else original.CMD_ph180(null,null);
                row(text,"preset",zero,preset,get(original,"ph0"));
            }
            var selector=new de.bruker.nmr.jview.listener.ObjectSelector();selector.setObject(data);
            selector.setLastCursorIndexPosition(new de.bruker.nmr.jview.ifc.IndexRangeIfc[]{new de.bruker.nmr.jview.data.IndexRange(0,1)});
            double zero=get(original,"ph0");original.CMD_phase1d_set_pivot(null,null);row(text,"move",zero,get(original,"ph0"));selector.setObject(null);
        }
        Data data=new Data(128,128,0);Controller original=create(data,0,1);Random random=new Random(992177);
        for(int i=0;i<1000;i++) {
            double real=i%7==0?0:random.nextGaussian()*1e8,imaginary=i%11==0?0:random.nextGaussian()*1e8;
            data.sample(real,imaginary);row(text,"pivot",real,imaginary,original.getPH0(0));
        }
        Files.writeString(Path.of(args[0]),text,StandardOpenOption.CREATE_NEW);
        System.out.println("Captured actual Windows phase controller and pivot calculations");
    }
}
