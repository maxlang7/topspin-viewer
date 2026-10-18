/** Raw file axis arithmetic, reconstructed from the native scale routine. */
public final class RawTimeRange {
    public record Range(long points,double start,double end) {}
    public static Range calculate(int td,int loop,double sfo1,double sw) {
        long points=loop>1?(long)loop*td:td;
        return new Range(points,0,(double)points*(.5/(sfo1*sw))+0.0);
    }
    private RawTimeRange(){}
}
