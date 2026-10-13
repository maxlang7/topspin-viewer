import java.nio.file.*;
import java.util.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.prsc.datacomps.integral.*;

/** Checks complete manual regions and saved-file units against the vendor Java implementation. */
public class IntegrationFileReference {
    public static void main(String[] args) throws Exception {
        long compared=0;int cases=0;
        Random random=new Random(1234567);
        for(int nc:new int[]{-12,-2,0,8}) {
            double[] samples=new double[1024];for(int i=0;i<samples.length;i++)samples[i]=random.nextGaussian()*1e6;
            var data=new VendorDifferential.ReferenceData(samples);
            data.getProperties().setProperty("1s NC_proc",Integer.toString(nc));
            data.getProperties().setProperty("1s INTSCL","0.123456789");
            IntegrationRegions model=new IntegrationRegions();List<Integral> original=new ArrayList<>();
            for(double[] bounds:new double[][]{{10.3,9.6},{6.01,5.011},{1.342,0.134},{11.98,11.978}}) {
                double bias=random.nextDouble()*150,slope=random.nextDouble()*0.002;
                model.add(bounds[0],bounds[1]);int index=0;
                while(model.regions().get(index).left()!=bounds[0])index++;
                model.replace(index,new IntegrationRegions.Region(bounds[0],bounds[1],bias,slope));
                Integral integral=new Integral(data,new PhysicalRangeIfc[]{new PhysicalRange(bounds[0],bounds[1])},bias,slope);
                original.add(integral);
                var rebuilt=model.calculate(model.regions().get(index),samples,12,-2);
                VendorDifferential.equal(integral.getDataDouble(),rebuilt.curve(),"bounded integral");
                VendorDifferential.equal(new double[]{integral.getIntegralValue()},new double[]{rebuilt.value()},"bounded value");
            }
            IntegralRange.save(data,original);
            String expected=data.getProperties().getProperty("DF_PROCFILE intrng");
            String actual=model.serialize(nc);
            if(!expected.equals(actual))throw new AssertionError("intrng text mismatch\nEXPECTED:\n"+expected+"ACTUAL:\n"+actual);
            cases++;compared+=expected.length();
        }
        System.out.println("PASS "+cases+" integration-file cases, "+compared+" exact characters, "+VendorDifferential.comparisons+" bitwise sample comparisons");
    }
}
