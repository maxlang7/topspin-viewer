import java.text.*;
import java.util.*;

/** Tick values and labels for the retained linear spectrum axes. */
public final class AxisTicks {
    public record Tick(double value,boolean large,String label) {}
    public record Layout(List<Tick> ticks,String unitLabel,double multiplier,int subdivisions,String pattern,boolean exponents) {}
    public static Layout calculate(double start,double end,String unit,String label,boolean exponents,boolean engineering) {
        if(!Double.isFinite(start) || !Double.isFinite(end))throw new IllegalArgumentException("A finite axis range is required");
        if(unit==null)throw new IllegalArgumentException("Axis unit is required");
        double desired=(end-start)/5.0;
        double logarithm=Math.log(Math.abs(desired))/2.302585092994046;
        int power=(int)Math.floor(logarithm);
        double mantissa=Math.pow(10.0,logarithm-power);
        int subdivisions=5;
        if(Math.abs(mantissa)>7.5)mantissa=10;
        else if(Math.abs(mantissa)>3.5)mantissa=5;
        else if(Math.abs(mantissa)>1.5){mantissa=2;subdivisions=4;}
        else mantissa=1;
        double step=mantissa*Math.pow(10.0,power);
        double first=start-Math.IEEEremainder(start,step);
        if(desired<0)step=-step;
        double last=first+5.0*step,multiplier=1,extent=Math.max(Math.abs(first),Math.abs(last));int exponent=0;
        if(unit.equals("ppm"))exponents=false;
        if(exponents) {
            if(extent>10000 && extent!=Double.POSITIVE_INFINITY) {
                while(extent>10){multiplier*=10;exponent++;first*=.1;last*=.1;step*=.1;extent*=.1;}
            }else if(extent<.01 && extent!=Double.NEGATIVE_INFINITY) {
                while(extent<1){multiplier*=.1;exponent--;first*=10;last*=10;step*=10;extent*=10;}
            }
            if(engineering)while(exponent%3!=0){multiplier*=.1;exponent--;first*=10;last*=10;step*=10;extent*=10;}
        }
        String pattern=start==end?" 0E0 ":!exponents || Math.abs(first)<100000 && Math.abs(last)<100000?" 0 ":" 0E0 ";
        DecimalFormat format=new DecimalFormat(pattern,DecimalFormatSymbols.getInstance(Locale.ROOT));
        power=(int)Math.floor(Math.log(Math.abs(step))/2.302585092994046);
        if(power<0)format.setMinimumFractionDigits(-power);
        String drawnUnit=unit;
        if(unit.equals("Hz") && exponent==9){drawnUnit="GHz";exponent-=9;}
        else if(unit.equals("Hz") && exponent==6){drawnUnit="MHz";exponent-=6;}
        else if(unit.equals("Hz") && exponent==3){drawnUnit="kHz";exponent-=3;}
        else if(unit.equals("s") && exponent==-3){drawnUnit="ms";exponent+=3;}
        else if(unit.equals("s") && exponent==-6){drawnUnit="us";exponent+=6;}
        if(drawnUnit.equals("us"))drawnUnit="\u03bcs";
        if(exponent!=0)drawnUnit+=" *1e"+exponent;
        drawnUnit=drawnUnit.isEmpty()?null:"["+drawnUnit+"]";
        if(label!=null)drawnUnit=drawnUnit==null?label:label+" "+drawnUnit;
        List<Tick> ticks=new ArrayList<>();
        if(step!=0) {
            double minor=step/subdivisions,tick=first-step,value=tick*multiplier;int i=0;
            while(start>end?value>end:value<end) {
                if(start>end?value<start:value>start)ticks.add(new Tick(value,i%subdivisions==0,format.format(value/multiplier)));
                double old=tick;tick+=minor;if(old==tick || ++i>1000)break;
                value+=minor*multiplier;
            }
        }
        return new Layout(List.copyOf(ticks),drawnUnit,multiplier,subdivisions,format.toPattern(),exponents);
    }
    private AxisTicks(){}
}
