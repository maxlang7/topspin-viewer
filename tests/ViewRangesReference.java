import java.util.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.view.*;

public class ViewRangesReference {
    static class Original extends SpecView1D {
        @Override protected void saveVerticalScale(){}
    }
    private static void equal(PhysicalRangeIfc expected,ViewRanges.Range actual,String action) {
        if(expected==null || actual==null){if(expected!=null || actual!=null)throw new AssertionError(action+" empty range differs");return;}
        VendorDifferential.equal(new double[]{expected.getStart(),expected.getEnd()},new double[]{actual.start(),actual.end()},action);
    }
    public static void main(String[] args) throws Exception {
        Random random=new Random(401822);int cases=0;
        for(int i=0;i<2000;i++) {
            double start=12-random.nextDouble()*5,end=-2+random.nextDouble()*5;
            var data=new VendorDifferential.ReferenceData(new double[]{1,2,3,4});data.setSelected(true);
            boolean ascending=i%2!=0;if(ascending){double swap=start;start=end;end=swap;data.setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(-2,12)});}
            Original view=new Original();view.setViewedData(data);int width=31+random.nextInt(1500);view.setViewport(new java.awt.Rectangle(2,2,width,600));
            ViewRanges.Range range=new ViewRanges.Range(start,end),full=ascending?new ViewRanges.Range(-2,12):new ViewRanges.Range(12,-2);
            for(String action:new String[]{"zoomin","zoomout","smooth_zoom","sleft_right"}) {
                view.setRange(new PhysicalRangeIfc[]{new PhysicalRange(start,end)});
                double amount=action.equals("smooth_zoom")?(i%2==0?.97:1.03):random.nextDouble()*2000-1000;
                view.performAction(action,amount,view);
                var actual=switch(action){case "zoomin"->ViewRanges.zoom(range,2,full);case "zoomout"->ViewRanges.zoom(range,.5,full);case "smooth_zoom"->ViewRanges.zoom(range,amount<1?1.025:.9756097560975611,full);default->ViewRanges.pan(range,amount,width,full);};
                equal(view.getRange()[0],actual,action);cases++;
            }
            MapperY mapper=new MapperY();double low=-random.nextDouble()*1e8,high=random.nextDouble()*1e9,reference=i%3==0?17:0;
            mapper.setPhysicalMapping(low,high);mapper.setReferenceIntensity(reference);
            var vertical=new ViewRanges.Range(low,high);
            for(double factor:new double[]{2,.5,Math.pow(1.05,random.nextDouble()*20),0}){mapper.doScale(factor);vertical=ViewRanges.scale(vertical,factor,reference);equal(mapper.getPhysicalMapping(),vertical,"vertical scale");cases++;}
            for(double baseline:new double[]{.05,.15,.5,-.123,1.3}){mapper.setBaselineShift(baseline);vertical=ViewRanges.baseline(vertical,baseline,reference);equal(mapper.getPhysicalMapping(),vertical,"baseline");cases++;}
        }
        System.out.println("PASS "+cases+" original viewer range transitions, "+VendorDifferential.comparisons+" bitwise comparisons");
    }
}
