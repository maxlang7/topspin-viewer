import java.io.IOException;
import java.util.Map;

/** Full interleaved raw trace, using the native raw handler's axis and amplitude conventions. */
public final class RawTrace {
    public final double[] samples;
    public final RawTimeRange.Range time;
    public final RawValueRange.Range values;
    public RawTrace(RawDataset raw) throws IOException {
        this(raw.fid,raw.acquisition);
    }
    RawTrace(double[] data,Map<String,String> acquisition)throws IOException {
        int type=integer(acquisition,"DTYPA"),td=integer(acquisition,"TD"),loop=integer(acquisition,"FnILOOP");
        if(type!=0 && type!=2)throw new IOException("Unsupported raw trace type");
        time=RawTimeRange.calculate(td,loop,number(acquisition,"SFO1"),number(acquisition,"SW"));
        if(data.length!=td || !(time.end()>0) || !Double.isFinite(time.end()))throw new IOException("Invalid raw trace dimensions");
        double scale=Math.pow(2,integer(acquisition,"NC"));
        var extrema=RawValueRange.calculate(data);
        values=new RawValueRange.Range(extrema.minimum()*scale,extrema.maximum()*scale);
        if(!Double.isFinite(values.minimum()) || !Double.isFinite(values.maximum()))throw new IOException("Raw trace amplitude overflow");
        samples=data.clone();
        // The original double-buffer branch copies samples; only int32 extraction applies NC.
        if(type==0)for(int i=0;i<samples.length;i++)samples[i]*=scale;
    }
    public int index(double coordinate){return Math.max(0,Math.min(samples.length-1,Numerics.index(coordinate,time.start(),time.end(),samples.length)));}
    public ViewRanges.Range fullRange(){return new ViewRanges.Range(time.start(),time.end());}
    public ViewRanges.Range verticalRange() {
        return ViewRanges.initial(new ViewRanges.Range(values.minimum(),values.maximum()));
    }
    private static double number(Map<String,String> values,String name)throws IOException {
        try{double value=Double.parseDouble(values.get(name));if(Double.isFinite(value))return value;}
        catch(RuntimeException ignored){}throw new IOException("Invalid raw trace parameter "+name);
    }
    private static int integer(Map<String,String> values,String name)throws IOException {
        double value=number(values,name);if(value!=(int)value)throw new IOException("Invalid raw trace integer "+name);return (int)value;
    }
}
