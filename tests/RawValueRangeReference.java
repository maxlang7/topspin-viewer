import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Executes the complete original buffer-extrema routine against controlled memory. */
public class RawValueRangeReference {
    private static BufferedWriter input;
    private static BufferedReader output;
    private static long samples;
    private static int cases;
    private static void check(double[] values,int type)throws Exception {
        input.write(values.length+" "+type+"\n");
        for(double value:values){input.write(Long.toUnsignedString(Double.doubleToRawLongBits(value),16));input.write(' ');}input.write('\n');input.flush();
        String line=output.readLine();if(line==null)throw new AssertionError("Original extrema fixture exited early");String[] cells=line.split(" ");
        RawValueRange.Range actual=RawValueRange.calculate(values);
        if(Double.doubleToRawLongBits(actual.minimum())!=Long.parseUnsignedLong(cells[0],16) || Double.doubleToRawLongBits(actual.maximum())!=Long.parseUnsignedLong(cells[1],16))throw new AssertionError("Raw extrema differ: "+Arrays.toString(values));
        cases++;samples+=values.length;
    }
    public static void main(String[] args)throws Exception {
        Path binary=Path.of(args.length>0?args[0]:"/home/maxlang/.local/share/topspin/opt/topspin5.0.0/prog/mod/dataserver");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(binary)));
        if(!hash.equals("2e742041be3eca47fdf5fb88df273f2ca419660b0590309f33769483669529a6"))throw new AssertionError("Native reference version changed");
        Process process=new ProcessBuilder("build/test/native-raw-range-oracle",binary.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));output=new BufferedReader(new InputStreamReader(process.getInputStream()));Random random=new Random(0x4649444d494e4dL);
            for(int i=0;i<6000;i++) {
                int type=i%2==0?0:2;double[] values=new double[i%9==0?4096:random.nextInt(128)];
                for(int j=0;j<values.length;j++){double value=type==0?random.nextInt():random.nextGaussian()*Math.pow(10,random.nextInt(21)-10);double magnitude=type==0?Math.min(Math.abs(value),Integer.MAX_VALUE):Math.abs(value);values[j]=i%3==0?magnitude:i%3==1?-magnitude:value;}
                check(values,type);
            }
            check(new double[]{Integer.MIN_VALUE,Integer.MAX_VALUE},0);
            for(double[] values:new double[][]{{},{0},{-0.0},{0,-0.0},{Double.NaN},{Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY},{Double.NaN,1,-2},{1,2,3},{-1,-2,-3}})check(values,2);
            int inventory=0;
            try(var paths=Files.walk(Path.of("artifacts/full-lab-references/datasets"))) {
                for(Path acqus:paths.filter(path->path.getFileName().toString().equals("acqus")).sorted().toList()) {
                    RawDataset raw=new RawDataset(acqus.getParent(),acqus.getParent().resolve("pdata/1/proc"));check(raw.fid,Integer.parseInt(raw.acquisition.get("DTYPA")));inventory++;
                }
            }
            input.close();if(process.waitFor()!=0)throw new AssertionError("Native extrema fixture failed");
            System.out.println("PASS "+cases+" complete native raw-extrema cases, including "+inventory+" acquisitions; "+samples+" samples; "+cases*2+" bitwise extrema; no native stubs");
        }finally{if(process.isAlive())process.destroy();}
    }
}
