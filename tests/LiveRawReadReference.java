import java.nio.*;
import java.nio.file.*;

/** Full raw samples and physical ranges from the licensed, running Windows TopSpin API. */
public final class LiveRawReadReference {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);long samples=0;int cases=0;
        for(String line:Files.readAllLines(root.resolve("raw.tsv"))) {
            String[] row=line.split("\t");Path experiment=root.resolve(row[1]);
            RawTrace actual=new RawTrace(new RawDataset(experiment,experiment.resolve("pdata/1/proc")));
            int count=Integer.parseInt(row[2]);
            if(count!=actual.samples.length || Integer.parseInt(row[3])!=0 || Integer.parseInt(row[4])!=count || !row[7].equals("s"))throw new AssertionError("Native raw dimensions or units differ: "+row[0]);
            if(Double.doubleToRawLongBits(Double.parseDouble(row[5]))!=Double.doubleToRawLongBits(actual.time.start())
                || Double.doubleToRawLongBits(Double.parseDouble(row[6]))!=Double.doubleToRawLongBits(actual.time.end()))throw new AssertionError("Native raw time endpoints differ: "+row[0]);
            byte[] encoded=Files.readAllBytes(root.resolve("captures/"+row[0]+".f64"));if(encoded.length!=count*8)throw new AssertionError("Truncated capture");
            ByteBuffer expected=ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<count;i++)if(expected.getLong()!=Double.doubleToRawLongBits(actual.samples[i]))throw new AssertionError("Native Windows raw samples differ: "+row[0]+", index "+i);
            samples+=count;cases++;
        }
        if(cases!=85)throw new AssertionError("Incomplete full-inventory raw API capture");
        System.out.println("PASS "+cases+" actual Windows TopSpin raw acquisitions; "+samples+" bitwise samples, "+cases*2+" bitwise time endpoints, exact index ranges and units");
    }
}
