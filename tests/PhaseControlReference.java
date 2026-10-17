import java.util.*;
import java.lang.reflect.*;

public class PhaseControlReference {
    static class Phase extends VendorDifferential.ReferencePhase {
        Phase(int size,int ft,int strip) {
            super(new double[size],new double[size],ft,strip);
            setDimension(1);setSizes(new int[]{size});setFullSizes(new int[]{size});
        }
        @Override public void invalidateData(){}
        void sample(double real,double imaginary){dataRe[0]=real;dataIm[0]=imaginary;}
    }
    static class Original extends bruker.bio.topspin.procphase.phas1d.Module {
        int pivot;
        Phase phase;
        Original() throws Exception {super();}
        static Original fixture(int size,int ft,int strip,int pivot,double sensitivity) throws Exception {
            // Test-only allocation isolates the arithmetic from GUI and desktop initialization.
            Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);
            Original value=(Original)((sun.misc.Unsafe)singleton.get(null)).allocateInstance(Original.class);value.pivot=pivot;
            Class<?> owner=bruker.bio.topspin.procphase.phas1d.Module.class;
            Field data=owner.getDeclaredField("phasedData");data.setAccessible(true);
            Vector<bruker.bio.topspin.procphase.phas1d.PhasedData> phases=new Vector<>();
            value.phase=new Phase(size,ft,strip);
            phases.add(value.phase);data.set(value,phases);
            for(String name:new String[]{"mouseSens","phaseIncr"}) {
                Field field=owner.getDeclaredField(name);field.setAccessible(true);field.setDouble(value,name.equals("mouseSens")?sensitivity:1);
            }
            return value;
        }
        @Override public void fullRepaint() {}
        @Override public int getPivotPoint(){return pivot;}
        double value(String field) throws Exception {
            Field f=bruker.bio.topspin.procphase.phas1d.Module.class.getDeclaredField(field);f.setAccessible(true);return f.getDouble(this);
        }
    }
    public static void main(String[] args) throws Exception {
        int cases=0;
        for(int ft:new int[]{0,128,65536})for(int strip:new int[]{0,24})for(int pivot:new int[]{0,37,99})for(double sensitivity:new double[]{.00625,.2,1}) {
            Original original=Original.fixture(128,ft,strip,pivot,sensitivity);
            Numerics.PhaseAngles actual=new Numerics.PhaseAngles(0,0);
            for(double amount:new double[]{20,-10,.5,1.23456789,-2000})for(int order:new int[]{0,1}) {
                if(order==0)original.CMD__ph0(amount,null);else original.CMD__ph1(amount,null);
                actual=Numerics.dragPhase(actual.zero(),actual.first(),order,amount,1,sensitivity,pivot,strip,ft,128);
                actual=new Numerics.PhaseAngles(Numerics.wrapPhaseZero(actual.zero()),actual.first());
                VendorDifferential.equal(new double[]{original.value("ph0"),original.value("ph1")},new double[]{actual.zero(),actual.first()},"phase control");cases++;
            }
            for(int preset:new int[]{90,90,180,-90,-90,180,180}) {
                if(preset==90)original.CMD_ph90(null,null);else if(preset==-90)original.CMD_phm90(null,null);else original.CMD_ph180(null,null);
                actual=new Numerics.PhaseAngles(Numerics.wrapPhaseZero(actual.zero()+preset),actual.first());
                VendorDifferential.equal(new double[]{original.value("ph0"),original.value("ph1")},new double[]{actual.zero(),actual.first()},"phase preset");cases++;
            }
        }
        Original pivot=Original.fixture(128,128,0,0,1);Random random=new Random(281177);
        for(int i=0;i<1000;i++) {
            double real=i%7==0?0:random.nextGaussian()*1e8,imaginary=i%11==0?0:random.nextGaussian()*1e8;
            pivot.phase.sample(real,imaginary);
            VendorDifferential.equal(new double[]{pivot.getPH0(0)},new double[]{Numerics.phaseAtPivot(real,imaginary)},"calculate ph0 at pivot");cases++;
        }
        System.out.println("PASS "+cases+" exact phase-control transitions, "+VendorDifferential.comparisons+" bitwise comparisons");
    }
}
