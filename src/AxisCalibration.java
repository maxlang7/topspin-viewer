/** One-point frequency referencing, in the operation order of the original 1D controller. */
public final class AxisCalibration {
    public record Result(double frequency,double offset,double shift){}
    public static boolean changed(double clicked,double target){return Math.abs(target-clicked)>1.0E-5;}
    public static Result calculate(double frequency,double offset,double clicked,double target,boolean hertz){
        double nextFrequency,nextOffset;
        if(hertz){nextFrequency=frequency+(clicked-target)*1.0E-6;nextOffset=(offset*frequency+(target-clicked))/nextFrequency;}
        else {nextFrequency=frequency*(1+clicked*1.0E-6)/(1+target*1.0E-6);nextOffset=(offset-clicked)*frequency/nextFrequency+target;}
        return new Result(nextFrequency,nextOffset,nextOffset-offset);
    }
    public static String scalar(double value){return String.format(java.util.Locale.ROOT,"%.15g",value);}
    private AxisCalibration(){}
}
