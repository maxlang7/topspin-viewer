import java.util.Arrays;

/** Reconstructed operations. No Bruker classes are needed at runtime. */
public final class Numerics {
    private static final double DEGREES = 0.017453292519943295;
    public record PhaseAngles(double zero,double first) {}
    public static double wrapPhaseZero(double zero) {
        if(zero>=180)zero-=360;
        else if(zero< -180)zero+=360;
        return zero;
    }
    public static double phaseAtPivot(double real,double imaginary) {
        if(real==0)return imaginary<0?-90:90;
        return Math.atan2(-imaginary,real)*90.0/1.5707963267948966;
    }
    public static PhaseAngles dragPhase(double zero,double first,int order,double amount,
            double increment,double sensitivity,int pivot,int stripStart,int ftSize,int size) {
        if(order==0)zero+=amount*increment*sensitivity;
        else {
            double delta=4.0*amount*increment*sensitivity;
            zero-=(-delta*(stripStart+(double)pivot)/(ftSize>0?ftSize:size));
            first-=delta;
        }
        return new PhaseAngles(zero,first);
    }

    public static double[] phasePreview(double[] re, double[] im, double ph0,
            double ph1, int ftSize, int stripStart, int start, int count) {
        if (re.length != im.length || start < 0 || count < 0 || start + count > re.length)
            throw new IllegalArgumentException("Invalid complex data or sample range");
        double step = ph1 / (double) (ftSize > 0 ? ftSize : re.length);
        double angle = ph0 + stripStart * step;
        angle *= DEGREES;
        step *= DEGREES;
        angle += start * step;
        double c = Math.cos(angle), s = Math.sin(angle);
        double dc = Math.cos(step), ds = Math.sin(step);
        double[] result = new double[count];
        for (int i = start; i < start + count; i++) {
            result[i - start] = re[i] * c - im[i] * s;
            double nc = c * dc - s * ds;
            double ns = s * dc + c * ds;
            c = nc;
            s = ns;
        }
        return result;
    }

    public record Integral(double[] curve, double bias, double slope) {
        public double value() { return curve.length == 0 ? 0 : curve[curve.length - 1]; }
        public double normalized(double factor) { return value() * factor; }
    }

    public static Integral integrate(double[] samples, double bias, double slope,
            boolean baselineCorrection, int baselinePoints) {
        double[] result = new double[samples.length];
        if (result.length <= 1) return new Integral(result, bias, slope);
        if (baselineCorrection && baselinePoints > 1) {
            if (baselinePoints > samples.length)
                throw new IllegalArgumentException("Baseline sample count exceeds region size");
            double first = 0, last = 0;
            for (int i = 0; i < baselinePoints; i++) {
                first += samples[i];
                last += samples[result.length - (i + 1)];
            }
            first /= baselinePoints;
            last /= baselinePoints;
            bias = -first;
            slope += -(last - first) / result.length;
        }
        double savedBias = bias;
        result[0] = samples[0] + bias;
        bias += slope;
        // The interactive TopSpin routine excludes the last selected sample.
        for (int i = 1; i < result.length - 1; i++) {
            result[i] = result[i - 1] + samples[i] + bias;
            bias += slope;
        }
        result[result.length - 1] = result[result.length - 2];
        return new Integral(result, savedBias, slope);
    }

    public static int index(double physical, double fullStart, double fullEnd, int size) {
        return (int) (((physical - fullStart) / (fullEnd - fullStart)) * size + 0.5);
    }
    public static int pivot(double[] real,double[] imaginary,int start,int count) {
        if(imaginary==null || imaginary.length!=real.length || start<0 || count<0 || start+count>real.length)
            throw new IllegalArgumentException("Invalid phase-pivot range");
        int best=start;double maximum=0;
        for(int i=start;i<start+count;i++) {
            double magnitude=real[i]*real[i]+imaginary[i]*imaginary[i];
            if(magnitude>maximum){maximum=magnitude;best=i;}
        }
        return best;
    }

    private Numerics() {}
}
