import java.lang.reflect.*;
import java.util.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.view.SpecView1D;
import de.bruker.nmr.an.int1d.IntegralView1D;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

/** Test-only UI adapters let the unmodified integration controller rebuild real integral objects. */
public class IntegrationUndoReference {
    static class Data extends NMRData {
        final double[] samples;
        Data(double[] samples) {
            this.samples=samples;setSizes(new int[]{samples.length});setFullSizes(new int[]{samples.length});
            PhysicalRange range=new PhysicalRange(12,-2,"ppm");range.setReference(400);
            setFullPhysicalRange(new PhysicalRangeIfc[]{range});setVisible(true);
        }
        @Override public int getDimension(){return 1;}
        @Override public double[] getDataDouble(){return samples;}
        @Override public double[] getDataDouble(IndexRangeIfc[] r){return Arrays.copyOfRange(samples,r[0].getStart(),r[0].getStart()+r[0].getNum());}
        @Override public double[] getDataDouble(PhysicalRangeIfc[] r){
            int a=Numerics.index(r[0].getStart(),12,-2,samples.length),b=Numerics.index(r[0].getEnd(),12,-2,samples.length);
            return Arrays.copyOfRange(samples,a,b+1);
        }
    }
    static class Panel extends de.bruker.nmr.jview.module.ViewPanel {
        @Override public void notifySelected(){}
    }
    static DataLayoutIfc layout(AbstractViewableDataIfc data) {
        SpecView1D view=data instanceof Integral?new IntegralView1D():new SpecView1D();
        DataLayoutIfc result=(DataLayoutIfc)Proxy.newProxyInstance(DataLayoutIfc.class.getClassLoader(),new Class<?>[]{DataLayoutIfc.class},(p,m,a)->switch(m.getName()){
            case "getBaseView","getCursorHolder"->view;case "getViewCount"->1;case "getViews"->new ViewIfc[]{view};default->null;
        });
        ((AbstractData)data).setLayout(result);view.setViewedData(data);return result;
    }
    static Object call(Object target,String name,Class<?>[] types,Object... args) throws Exception {
        for(Class<?> c=target.getClass();c!=null;c=c.getSuperclass())try {
            Method method=c.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);
        }catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
    static de.bruker.nmr.an.int1d.Module fixture(double[] samples,IntegrationRegions model,List<IntegrationRegions.Region> previous,int reference) throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        var original=(de.bruker.nmr.an.int1d.Module)((sun.misc.Unsafe)f.get(null)).allocateInstance(de.bruker.nmr.an.int1d.Module.class);
        Data data=new Data(samples);layout(data);
        data.getProperties().setProperty("1s NC_proc","0");
        data.getProperties().setProperty("1s INTSCL",Double.toString(model.normalizingFactor));
        List<AbstractViewableDataIfc> datasets=new ArrayList<>();datasets.add(data);
        for(var r:model.regions()) {
            Integral area=new Integral(data,new PhysicalRangeIfc[]{new PhysicalRange(r.left(),r.right(),"ppm")},r.bias(),r.slope());
            layout(area);area.setNormalizingFactor(model.normalizingFactor);datasets.add(area);
        }
        Panel panel=new Panel();
        PanelLayoutIfc panelLayout=(PanelLayoutIfc)Proxy.newProxyInstance(PanelLayoutIfc.class.getClassLoader(),new Class<?>[]{PanelLayoutIfc.class},(p,m,a)->{
            if(m.getName().equals("add"))layout((AbstractViewableDataIfc)a[0]);
            return null;
        });
        panel.setPanelLayout(panelLayout);
        IntegralScaleReference.field(original,"dataPanel",panel);IntegralScaleReference.field(original,"localData",datasets);
        IntegralScaleReference.field(original,"moduleProperties",new Properties());
        IntegralScaleReference.field(original,"actIntRngs",bounds(model.regions()));
        IntegralScaleReference.field(original,"prevIntRngs",bounds(previous));
        IntegralScaleReference.field(original,"auditList",new ArrayList<>());
        IntegralScaleReference.field(original,"calReference",reference);IntegralScaleReference.field(original,"calValue",1.0);
        return original;
    }
    static List<double[]> bounds(List<IntegrationRegions.Region> regions) {
        List<double[]> result=new ArrayList<>();for(var r:regions)result.add(new double[]{r.left(),r.right(),r.slope(),r.bias()});return result;
    }
    static void check(de.bruker.nmr.an.int1d.Module original,IntegrationRegions model,double[] samples) throws Exception {
        List<Integral> areas=new ArrayList<>();for(var item:original.getDatasetsVector())if(item instanceof Integral area)areas.add(area);
        if(areas.size()!=model.regions().size())throw new AssertionError("Undo region count differs");
        for(int i=0;i<areas.size();i++) {
            var region=model.regions().get(i);Integral area=areas.get(i);
            VendorDifferential.equal(area.getIntegParams(),new double[]{region.left(),region.right(),region.bias(),region.slope()},"undo parameters");
            VendorDifferential.equal(area.getDataDouble(),model.calculate(region,samples,12,-2).curve(),"undo curve");
            VendorDifferential.equal(new double[]{area.getNormalizingFactor()},new double[]{model.normalizingFactor},"undo normalization");
        }
    }
    public static void main(String[] args) throws Exception {
        IntegralScaleReference.initializeServices();Random random=new Random(829191);int transitions=0;
        for(int run=0;run<120;run++) {
            double[] samples=new double[128];for(int i=0;i<samples.length;i++)samples[i]=random.nextGaussian()*1e4;
            IntegrationRegions model=new IntegrationRegions();List<IntegrationRegions.Region> previous=List.of();
            int count=1+run%4;
            for(int i=0;i<count;i++) {
                previous=model.regions();model.add(11-i*2,10-i*2);
                model.replace(i,new IntegrationRegions.Region(11-i*2,10-i*2,random.nextGaussian()*10,random.nextGaussian()));
            }
            model.normalizingFactor=.123456789;boolean pinned=run%2==0;if(pinned)model.preserveScale();
            var original=fixture(samples,model,previous,pinned?-2:0);
            int selected=run%3==0?-1:run%count;model.select(selected);
            var selector=new de.bruker.nmr.jview.listener.ObjectSelector();
            selector.setObject(selected<0?null:(AbstractData)original.getDatasetsVector().get(selected+1));
            for(int step=0;step<5;step++) {
                original.CMD_int1d_undo(null,null);
                if(model.undo())model.normalize(samples,12,-2);
                check(original,model,samples);transitions++;
            }
            selector.setObject(null);
        }
        for(int count:new int[]{1,3}) {
            double[] samples=new double[128];for(int i=0;i<samples.length;i++)samples[i]=i*i-7*i+31;
            IntegrationRegions model=new IntegrationRegions();
            for(int i=0;i<count;i++)model.add(11-i*2,10-i*2);
            model.normalizingFactor=.125;model.preserveScale();
            var original=fixture(samples,model,List.of(),-2);
            for(var item:original.getDatasetsVector())item.setSelected(false);
            Integral chosen=(Integral)original.getDatasetsVector().get(1);chosen.setSelected(true);
            new de.bruker.nmr.jview.listener.ObjectSelector().setObject(chosen);model.select(0);
            original.CMD_int1d_del(null,null);model.remove(0);model.normalize(samples,12,-2);check(original,model,samples);transitions++;
            for(int step=0;step<3;step++) {
                original.CMD_int1d_undo(null,null);if(model.undo())model.normalize(samples,12,-2);
                check(original,model,samples);transitions++;
            }
            new de.bruker.nmr.jview.listener.ObjectSelector().setObject(null);
        }
        System.out.println("PASS "+transitions+" original integration undo transitions, "+VendorDifferential.comparisons+" bitwise comparisons");
    }
}
