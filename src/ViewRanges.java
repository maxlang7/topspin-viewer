/** Display-range arithmetic in the same operation order as the target viewer. */
public final class ViewRanges {
    public record Range(double start,double end) {}
    public static Range intersect(Range range,Range full) {
        double start,end;
        if(range.start()-range.end()>0){start=Math.min(range.start(),full.start());end=Math.max(range.end(),full.end());if(start<=end)return null;}
        else {start=Math.max(range.start(),full.start());end=Math.min(range.end(),full.end());if(start>=end)return null;}
        return new Range(start,end);
    }
    public static Range zoom(Range range,double factor,Range full) {
        if(factor==0)return intersect(range,full);
        double half=(range.start()-range.end())/factor/2.0,center=(range.end()+range.start())/2.0;
        return intersect(new Range(center+half,center-half),full);
    }
    public static Range pan(Range range,double pixels,int width,Range full) {
        double relative=pixels/(double)width;
        double shift=-(-Math.abs(range.start()-range.end())*relative);
        double start=range.start()+shift/2.0,end=range.end()+shift/2.0,extent=start-end;
        if(extent>0) {
            if(start>full.start() && end<full.end())return full;
            if(start>full.start()){start=full.start();end=start-extent;}
            else if(end<full.end()){end=full.end();start=end+extent;}
        } else {
            if(start<full.start() && end>full.end())return full;
            if(start<full.start()){start=full.start();end=start-extent;}
            else if(end>full.end()){end=full.end();start=end+extent;}
        }
        return new Range(start,end);
    }
    public static Range scale(Range range,double factor,double reference) {
        if(factor==0)return range;
        double shift=-reference/factor+reference;
        return new Range(range.start()/factor+shift,range.end()/factor+shift);
    }
    public static Range baseline(Range range,double baseline,double reference) {
        double extent=range.end()-range.start(),start=reference-extent*baseline;
        return new Range(start,start+extent);
    }
    private ViewRanges(){}
}
