import java.util.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.view.*;

public class AxisTicksReference {
    static class Original extends Axis {
        void run(double start,double end,String unit,String label,boolean exponent,boolean engineering) {
            useExponents=exponent;useEngeneeringNotation=engineering;setUnit(unit);setLabel(label);
            MapperX mapper=new MapperX();mapper.setPhysicalMapping(start,end);setMapper(mapper);
            calculateTicks(new PhysicalRange(start,end,unit));
        }
        void check(AxisTicks.Layout actual) {
            if(tickVector.size()!=actual.ticks().size())throw new AssertionError("Tick counts differ: "+tickVector.size()+" != "+actual.ticks().size());
            if(!Objects.equals(drawnAxisLabel,actual.unitLabel()) || nSmallTicks!=actual.subdivisions() || !tickFormat.toPattern().equals(actual.pattern()) || useExponents!=actual.exponents())
                throw new AssertionError("Axis label or format differs: "+drawnAxisLabel+" / "+actual.unitLabel()+" / "+tickFormat.toPattern()+" / "+actual.pattern());
            VendorDifferential.equal(new double[]{tickMultFactor},new double[]{actual.multiplier()},"axis multiplier");
            for(int i=0;i<tickVector.size();i++) {
                var tick=tickVector.get(i);var rebuilt=actual.ticks().get(i);
                VendorDifferential.equal(new double[]{tick.value},new double[]{rebuilt.value()},"axis tick");
                if(tick.large!=rebuilt.large() || !tickFormat.format(tick.value/tickMultFactor).equals(rebuilt.label()))throw new AssertionError("Axis tick label differs");
            }
        }
    }
    public static void main(String[] args) {
        Random random=new Random(881921);int cases=0;
        for(int run=0;run<10000;run++) {
            double scale=Math.pow(10,random.nextInt(21)-10),start=(random.nextDouble()*30-15)*scale,end=(random.nextDouble()*30-15)*scale;
            String unit=new String[]{"ppm","Hz","s","rel","","us"}[run%6],label=run%9==0?"F1":null;
            boolean exponents=run%4!=0,engineering=run%5!=0;
            Original original=new Original();original.run(start,end,unit,label,exponents,engineering);
            original.check(AxisTicks.calculate(start,end,unit,label,exponents,engineering));cases++;
        }
        for(double value:new double[]{0,1,-2,1e-30,1e30})for(String unit:new String[]{"s","rel","ppm"})for(boolean exponents:new boolean[]{false,true}) {
            Original original=new Original();original.run(value,value,unit,null,exponents,true);
            original.check(AxisTicks.calculate(value,value,unit,null,exponents,true));cases++;
        }
        System.out.println("PASS "+cases+" original linear-axis layouts, "+VendorDifferential.comparisons+" bitwise tick comparisons and exact labels");
    }
}
