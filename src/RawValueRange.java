/** Native 1D buffer extrema include zero, even when every sample has the same sign. */
public final class RawValueRange {
    public record Range(double minimum,double maximum) {}
    public static Range calculate(double[] values) {
        double minimum=0,maximum=0;
        for(double value:values){if(value<minimum)minimum=value;if(value>maximum)maximum=value;}
        return new Range(minimum,maximum);
    }
    private RawValueRange(){}
}
