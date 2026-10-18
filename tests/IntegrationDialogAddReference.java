import java.util.*;
import de.bruker.nmr.jview.ifc.SpecViewIfc;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

/** Command arguments enter the same add-region path as the original modal dialog. */
public final class IntegrationDialogAddReference {
    public static void main(String[] args)throws Exception {
        IntegralScaleReference.initializeServices();Random random=new Random(91872);int commands=0;
        for(int run=0;run<120;run++) {
            IntegrationRegions model=new IntegrationRegions();double[] samples=new double[128];
            for(int i=0;i<samples.length;i++)samples[i]=random.nextGaussian()*10000;
            if(run%2==0){model.normalizingFactor=.125;model.preserveScale();}
            var original=IntegrationUndoReference.fixture(samples,model,List.of(),run%2==0?-2:0);
            IntegralScaleReference.field(original,"mouseSens",1.0);
            IntegralScaleReference.field(original,"biasAndSlopeChanged",new IdentityHashMap<>());
            var mapper=((SpecViewIfc)original.getDatasetsVector().get(0).getLayout().getBaseView()).getCoordinateMapper()[0];mapper.setPhysicalMapping(12,-2);
            for(double[] bounds:new double[][]{{8.5,7.5},{5.5,6.5},{14,-5},{1.5,3.5},{8.5,7.5}}) {
                original.CMD_int1d_add(bounds[0]+" "+bounds[1],null);
                model.addFromDialog(bounds[0],bounds[1],samples,12,-2);
                IntegrationUndoReference.check(original,model,samples);commands++;
                for(var data:original.getDatasetsVector())data.setSelected(false);
                ((Integral)original.getDatasetsVector().get(1)).setSelected(true);
                if(!model.isSelected(0))model.selectRegion(0,8,14,false,false);
                double amount=random.nextDouble()-.5;
                original.CMD__bias(amount,null);model.adjustBias(0,amount,1);
                original.CMD__slope(amount,null);model.adjustSlope(0,amount,1,samples,12,-2);
                IntegrationUndoReference.check(original,model,samples);
            }
        }
        System.out.println("PASS "+commands+" original dialog/command region additions with inherited bias/slope, reversed/clamped bounds and frozen/live normalization; "+VendorDifferential.comparisons+" bitwise comparisons");
    }
}
