import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Compares only the native routine's arithmetic with cached parameter fixtures. */
public class RawTimeRangeReference {
    private static BufferedWriter input;
    private static BufferedReader output;
    private static int cases;
    private static void check(int td,int loop,double sfo1,double sw)throws Exception {
        input.write(Double.toHexString(sfo1)+" "+Double.toHexString(sw)+" "+td+" "+loop+"\n");input.flush();
        String line=output.readLine();if(line==null)throw new AssertionError("Original axis fixture exited early");String[] cells=line.split(" ");
        RawTimeRange.Range actual=RawTimeRange.calculate(td,loop,sfo1,sw);
        if(actual.points()!=Long.parseLong(cells[0]) || Double.doubleToRawLongBits(actual.start())!=Long.parseUnsignedLong(cells[1],16) || Double.doubleToRawLongBits(actual.end())!=Long.parseUnsignedLong(cells[2],16))throw new AssertionError("Raw axis arithmetic differs for "+td+","+loop+","+sfo1+","+sw);
        cases++;
    }
    public static void main(String[] args)throws Exception {
        Path binary=Path.of(args.length>0?args[0]:"/home/maxlang/.local/share/topspin/opt/topspin5.0.0/prog/mod/dataserver");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(binary)));
        if(!hash.equals("2e742041be3eca47fdf5fb88df273f2ca419660b0590309f33769483669529a6"))throw new AssertionError("Native reference version changed");
        Process process=new ProcessBuilder("build/test/native-raw-axis-oracle",binary.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));output=new BufferedReader(new InputStreamReader(process.getInputStream()));
            Random random=new Random(0x46494454494d45L);
            for(int i=0;i<12000;i++)check(2+2*random.nextInt(524288),i%9,20+random.nextDouble()*1000,Math.pow(10,random.nextDouble()*7-3));
            int inventory=0;
            try(var paths=Files.walk(Path.of("artifacts/full-lab-references/datasets"))) {
                for(Path acqus:paths.filter(path->path.getFileName().toString().equals("acqus")).sorted().toList()) {
                    var values=BrukerDataset.readParameters(acqus);
                    check(Integer.parseInt(values.get("TD")),Integer.parseInt(values.get("FnILOOP")),Double.parseDouble(values.get("SFO1")),Double.parseDouble(values.get("SW")));inventory++;
                }
            }
            input.close();if(process.waitFor()!=0)throw new AssertionError("Native axis fixture failed");
            System.out.println("PASS "+cases+" native raw-axis arithmetic cases, including "+inventory+" current acquisition parameter sets; "+cases*2+" bitwise endpoints; IO/cache callbacks stubbed");
        }finally{if(process.isAlive())process.destroy();}
    }
}
