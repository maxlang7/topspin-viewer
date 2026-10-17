/** Normalization arithmetic used by the manual integration controller. */
public final class IntegralScale {
    private IntegralScale() {}
    public static double factor(double[] values,int reference,double calibration,double storedScale,int nc) {
        double denominator=0;
        for(int i=0;i<values.length;i++) {
            if(reference==-1)denominator+=values[i];
            else if(reference==-2)denominator=storedScale/Math.pow(2.0,nc);
            else if(i==reference)denominator=values[i];
        }
        if(denominator==0)denominator=1;
        return reference==-2?denominator:calibration/denominator;
    }
}
