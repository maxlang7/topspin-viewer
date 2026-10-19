import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Execute original raw file read and byte swapping, with a real temporary file per buffer. */
public class RawReadReference {
    private static BufferedWriter input;
    private static BufferedReader output;
    private static int cases;
    private static long samples;
    private static void check(byte[] encoded,int count,int type,int order,double[] expected)throws Exception {
        input.write(count+" "+type+" "+order+"\n");
        ByteBuffer words=ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<count;i++){input.write(Long.toUnsignedString(type==2?words.getLong():Integer.toUnsignedLong(words.getInt()),16));input.write(' ');}
        input.write('\n');input.flush();
        String line=output.readLine();if(line==null)throw new AssertionError("Native raw-read fixture exited");String[] cells=line.strip().split(" ");
        if(cells.length!=expected.length)throw new AssertionError("Native sample count differs");
        for(int i=0;i<count;i++)if(Long.parseUnsignedLong(cells[i],16)!=Double.doubleToRawLongBits(expected[i]))throw new AssertionError("Raw decoding differs at "+i+", type="+type+", order="+order);
        cases++;samples+=count;
    }
    public static void main(String[] args)throws Exception {
        Path binary=Path.of(args[0]);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(binary)));
        if(!hash.equals("2e742041be3eca47fdf5fb88df273f2ca419660b0590309f33769483669529a6"))throw new AssertionError("Reference binary changed");
        Process process=new ProcessBuilder("build/test/native-raw-read-oracle",binary.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));output=new BufferedReader(new InputStreamReader(process.getInputStream()));
            Random random=new Random(0x52415742595445L);
            for(int c=0;c<1200;c++) {
                int type=c%2==0?0:2,order=c%4<2?0:1,count=1+random.nextInt(128);
                ByteBuffer bytes=ByteBuffer.allocate(count*(type==2?8:4)).order(order==0?ByteOrder.LITTLE_ENDIAN:ByteOrder.BIG_ENDIAN);
                double[] expected=new double[count];
                for(int i=0;i<count;i++) {
                    if(type==2){double value=c%7==0?-0.0:random.nextGaussian()*Math.pow(10,random.nextInt(31)-15);bytes.putDouble(value);expected[i]=value;}
                    else {int value=random.nextInt();bytes.putInt(value);expected[i]=value;}
                }
                check(bytes.array(),count,type,order,expected);
            }
            int inventory=0;
            try(var paths=Files.walk(Path.of("artifacts/full-lab-references/datasets"))) {
                for(Path acqus:paths.filter(p->p.getFileName().toString().equals("acqus")).sorted().toList()) {
                    RawDataset raw=new RawDataset(acqus.getParent(),acqus.getParent().resolve("pdata/1/proc"));
                    check(Files.readAllBytes(acqus.getParent().resolve("fid")),raw.fid.length,Integer.parseInt(raw.acquisition.get("DTYPA")),Integer.parseInt(raw.acquisition.get("BYTORDA")),raw.fid);inventory++;
                    RawTrace trace=new RawTrace(raw);
                    if(trace.samples.length!=raw.fid.length)throw new AssertionError("Trace discarded raw samples");
                    double factor=Integer.parseInt(raw.acquisition.get("DTYPA"))==2?1:Math.pow(2,raw.nc);
                    for(int i=0;i<trace.samples.length;i++)if(Double.doubleToRawLongBits(trace.samples[i])!=Double.doubleToRawLongBits(raw.fid[i]*factor))throw new AssertionError("Trace reordered or rescaled raw samples");
                }
            }
            input.close();if(process.waitFor()!=0)throw new AssertionError("Native read fixture failed");
            System.out.println("PASS "+cases+" original raw-file read cases, including "+inventory+" acquisitions; "+samples+" bitwise samples; actual file reads and both byte orders; only read@plt relocated");
        }finally{if(process.isAlive())process.destroy();}
    }
}
