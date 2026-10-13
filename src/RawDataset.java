import java.io.IOException;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** Raw 1D acquisition and the processing inputs, without changing the dataset. */
public final class RawDataset {
    public final Path path;
    public final Map<String,String> acquisition, processing;
    public final double[] fid;
    public final int size, groupDelay, nc;
    public final double lineBroadening, spectralWidth, ph0, ph1, left, right;

    public RawDataset(Path experiment, Path proc) throws IOException {
        path=experiment.toRealPath();
        acquisition=BrukerDataset.readParameters(path.resolve("acqus"));
        processing=BrukerDataset.readParameters(proc);
        if(Files.exists(path.resolve("ser")) || integer(acquisition,"AQ_mod")!=3)
            throw new IOException("This acquisition mode has no validated TopSpin reference yet");
        int type=integer(acquisition,"DTYPA"), order=integer(acquisition,"BYTORDA");
        if((type!=0 && type!=2) || (order!=0 && order!=1))throw new IOException("Unsupported raw numeric format");
        int count=integer(acquisition,"TD");
        if(count<2 || (count&1)!=0 || count>33_554_432)throw new IOException("Invalid raw acquisition size");
        double delay=number(acquisition,"GRPDLY");
        if(delay!=Math.rint(delay))throw new IOException("Fractional digital-filter delay needs an equivalence reference");
        groupDelay=(int)delay;size=integer(processing,"SI");nc=integer(acquisition,"NC");
        if(integer(processing,"ME_mod")!=0 || integer(processing,"WDW")!=1)
            throw new IOException("This window or prediction mode has no validated TopSpin reference yet");
        if(!"yes".equals(processing.get("PKNL")))throw new IOException("Disabled digital-filter correction needs a reference");
        lineBroadening=number(processing,"LB");spectralWidth=number(acquisition,"SW_h");
        ph0=number(processing,"PHC0");ph1=number(processing,"PHC1");
        byte[] bytes=Files.readAllBytes(path.resolve("fid"));
        if(bytes.length<(long)count*(type==2?8:4))throw new IOException("Truncated raw acquisition");
        ByteBuffer buffer=ByteBuffer.wrap(bytes).order(order==0?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN);
        fid=new double[count];
        for(int i=0;i<count;i++) {
            fid[i]=type==2?buffer.getDouble():buffer.getInt();
            if(!Double.isFinite(fid[i]))throw new IOException("Non-finite raw acquisition sample");
        }
        double sf=number(processing,"SF"), carrier=number(acquisition,"SFO1");
        if(sf<=0 || spectralWidth<=0)throw new IOException("Invalid acquisition frequency or width");
        // TopSpin writes scalar processing parameters with fifteen significant digits.
        double offset=(carrier-sf)*1_000_000.0/sf+spectralWidth/(2.0*sf);
        left=Double.parseDouble(String.format(Locale.ROOT,"%.15g",offset));
        right=left-spectralWidth/sf;
    }

    public NativeKernel.PhaseResult process() throws IOException {
        int cache=cacheBytes("index0"), second=cacheBytes("index2");
        if(second<size*8L && second>128)
            throw new IOException("This CPU's second-level FFT split needs an equivalence reference");
        return NativeKernel.efp(fid,size,groupDelay,lineBroadening,spectralWidth,ph0,ph1,nc,cache);
    }

    private static int cacheBytes(String index) throws IOException {
        Path file=Path.of("/sys/devices/system/cpu/cpu0/cache",index,"size");
        if(!Files.isRegularFile(file))throw new IOException("Cannot determine native FFT cache geometry");
        String value=Files.readString(file).trim();
        return Integer.parseInt(value.substring(0,value.length()-1))*(value.endsWith("K")?1024:1);
    }
    private static double number(Map<String,String> params,String key) throws IOException {
        try {double value=Double.parseDouble(params.get(key));if(!Double.isFinite(value))throw new NumberFormatException();return value;}
        catch(RuntimeException e) {throw new IOException("Invalid or missing parameter "+key,e);}
    }
    private static int integer(Map<String,String> params,String key) throws IOException {
        double value=number(params,key);
        if(value!=Math.rint(value) || value<Integer.MIN_VALUE || value>Integer.MAX_VALUE)throw new IOException("Invalid integer "+key);
        return (int)value;
    }
}
