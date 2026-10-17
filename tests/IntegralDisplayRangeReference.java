import java.awt.*;
import java.lang.reflect.Proxy;
import java.util.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.an.int1d.Integral1DLayout;

public final class IntegralDisplayRangeReference {
    public static void main(String[] args)throws Exception {
        Random random=new Random(861110);long compared=0;
        for(int run=0;run<2000;run++) {
            int count=1+run%5;ArrayList<double[]> curves=new ArrayList<>();ViewIfc[] views=new ViewIfc[count];double baseline=(run%11-2)*.1;
            for(int i=0;i<count;i++) {
                double[] curve=new double[11+i*7];for(int j=0;j<curve.length;j++)curve[j]=(random.nextGaussian()+run%5-2)*1e8;
                if(run%13==0)Arrays.fill(curve,run%3-1);
                curves.add(curve);var data=new IntegralCurveRasterReference.Data(curve);var view=new IntegralCurveRasterReference.View();view.setViewedData(data);
                view.getValueMapper().setBaselineShift(baseline);view.setProperty("DATA_COLOR_2","237 28 36");views[i]=view;
                data.setLayout((DataLayoutIfc)Proxy.newProxyInstance(DataLayoutIfc.class.getClassLoader(),new Class<?>[]{DataLayoutIfc.class},(p,m,a)->m.getName().equals("getViewCount")?1:null));
            }
            new Integral1DLayout().commonLayoutFunction(count,views);
            var actual=IntegralCurvePainter.commonRange(curves,baseline);
            for(int i=0;i<count;i++) {
                var mapper=((SpecViewIfc)views[i]).getValueMapper();var range=mapper.getPhysicalMapping();
                VendorDifferential.equal(new double[]{range.getStart(),range.getEnd(),mapper.getReferenceIntensity()},new double[]{actual.start(),actual.end(),IntegralCurvePainter.reference(curves.get(i))},"shared integral display range");compared+=3;
            }
        }
        System.out.println("PASS 2000 original shared integral display ranges, "+compared+" bitwise comparisons");
    }
}
