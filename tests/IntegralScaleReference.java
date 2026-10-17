import java.lang.reflect.*;
import java.util.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.an.int1d.IntegralView1D;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

/** Runs the original final controller with in-memory data and no desktop or license service. */
public class IntegralScaleReference {
    static void field(Object target,String name,Object value) throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {
            Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static class Area extends VendorDifferential.ReferenceIntegral {
        final DataLayoutIfc layout;
        Area(double[] samples) throws Exception {
            super(samples,false,1);calculateIntegral(0,0);
            IntegralView1D view=new IntegralView1D();
            layout=(DataLayoutIfc)Proxy.newProxyInstance(DataLayoutIfc.class.getClassLoader(),new Class<?>[]{DataLayoutIfc.class},
                (proxy,method,args)->method.getName().equals("getBaseView")?view:null);
        }
        @Override public DataLayoutIfc getLayout(){return layout;}
    }
    static void initializeServices() {
        // Only audit dependencies are supplied. These arithmetic tests never request a license or open files.
        com.bruker.ioc.IOCBuilder.createInjector(new com.google.inject.AbstractModule(){
            protected void configure(){
                bind(bruker.bio.topspin.audit.IMasterAuditService.class)
                    .annotatedWith(com.google.inject.name.Names.named("NON_BLOCKING_SERVICE"))
                    .toInstance(noService(bruker.bio.topspin.audit.IMasterAuditService.class));
                bind(de.bruker.bio.auditlib.audit.service.IGxpModeService.class)
                    .toInstance(noService(de.bruker.bio.auditlib.audit.service.IGxpModeService.class));
            }
        });
    }
    public static void main(String[] args) throws Exception {
        initializeServices();
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)f.get(null);
        Random random=new Random(871992);int cases=0;
        for(int run=0;run<200;run++) {
            var original=(de.bruker.nmr.an.int1d.Module)unsafe.allocateInstance(de.bruker.nmr.an.int1d.Module.class);
            var source=new VendorDifferential.ReferenceData(new double[]{1,2,3,4});
            int nc=run%41-20;double stored=run%7==0?0:random.nextGaussian()*100;
            source.getProperties().setProperty("1s NC_proc",Integer.toString(nc));
            source.getProperties().setProperty("1s INTSCL",Double.toString(stored));
            List<AbstractViewableDataIfc> data=new ArrayList<>();data.add(source);
            List<Area> areas=new ArrayList<>();double[] values=new double[4];
            for(int i=0;i<values.length;i++) {
                double[] samples=new double[19];for(int j=0;j<samples.length;j++)samples[j]=run%11==0?0:random.nextGaussian()*1e6;
                Area area=new Area(samples);areas.add(area);data.add(area);values[i]=area.getIntegralValue();
            }
            field(original,"localData",data);field(original,"baseLineShift",.2);
            for(int reference:new int[]{-2,-1,0,1,3,8})for(double calibration:new double[]{0,1,-2.34,100,1e10}) {
                field(original,"calReference",reference);field(original,"calValue",calibration);
                original.normalizeIntegrals(reference,calibration);
                double actual=IntegralScale.factor(values,reference,calibration,stored,nc);
                for(Integral area:areas)VendorDifferential.equal(new double[]{area.getNormalizingFactor()},new double[]{actual},"controller normalization");
                cases++;
            }
        }
        IntegrationRegions zero=new IntegrationRegions();zero.add(11,10);
        for(double calibration:new double[]{0,1,-2.34,100}) {
            zero.calibrate(0,calibration,new double[128],12,-2);
            VendorDifferential.equal(new double[]{calibration},new double[]{zero.normalizingFactor},"zero-integral calibration");
        }
        System.out.println("PASS "+cases+" original normalization transitions, "+VendorDifferential.comparisons+" bitwise comparisons");
    }
    static <T> T noService(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(m.getDeclaringClass()==Object.class)return switch(m.getName()){
                case "toString"->"Unused test audit service";case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];default->null;};
            throw new AssertionError("Unexpected service call: "+m);
        }));
    }
}
