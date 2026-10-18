import java.lang.reflect.Field;
import java.util.*;
import de.bruker.nmr.an.int1d.IntegralView1D;
import de.bruker.nmr.jview.listener.ObjectSelector;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

public final class IntegrationDisplayScaleReference {
    public static void main(String[] args)throws Exception {
        IntegralScaleReference.initializeServices();Random random=new Random(417612);long checked=0;
        Field hidden=IntegralView1D.class.getDeclaredField("noFactorDisplay");hidden.setAccessible(true);
        for(int run=0;run<60;run++) {
            IntegrationRegions model=new IntegrationRegions();for(int i=0;i<1+run%7;i++)model.add(11-i,10.5-i);
            var original=IntegrationUndoReference.fixture(new double[128],model,List.of(),0);
            List<Integral> areas=new ArrayList<>();for(var item:original.getDatasetsVector())if(item instanceof Integral area){area.setSelected(false);areas.add(area);}
            for(int step=0;step<100;step++) {
                if(step%7==0){model.toggleSelectAll();for(int i=0;i<areas.size();i++)areas.get(i).setSelected(model.isSelected(i));}
                else if(step%3==0) {
                    int index=random.nextInt(areas.size());var region=model.regions().get(index);
                    model.selectRegion(index,(region.left()+region.right())/2,14,false,random.nextBoolean());
                    for(int i=0;i<areas.size();i++)areas.get(i).setSelected(model.isSelected(i));
                }
                double factor=new double[]{2,.5,1e-9,4,.25,1.234567}[random.nextInt(6)];
                IntegrationUndoReference.call(original,"scaleIntegrals",new Class<?>[]{double.class},factor);model.scaleSelected(factor);
                for(int i=0;i<areas.size();i++) {
                    var view=(IntegralView1D)areas.get(i).getLayout().getBaseView();var actual=model.displayScale(i);
                    VendorDifferential.equal(new double[]{view.getScalingFactor(),view.getDefaultScalingFactor(),view.getRelScalingFactor()},new double[]{actual.factor(),actual.factor(),actual.relative()},"selected integral display scaling");
                    if(hidden.getBoolean(view)!=actual.hideFactor())throw new AssertionError("Scale-factor visibility differs");checked++;
                }
            }
            new ObjectSelector().setObject(null);
        }
        System.out.println("PASS 6000 original integral scaling operations, "+checked+" display states and "+VendorDifferential.comparisons+" bitwise comparisons");
    }
}
