import java.nio.*;
import java.nio.file.*;
import java.util.*;

/** End-to-end raw FID comparison with actual Windows TopSpin efp captures. */
public class RawReference {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);long comparisons=0;int cases=0;
        try(var folders=Files.list(root.resolve("captures"))) {
            for(Path folder:folders.toList()) {
                Path experiment=root.resolve("datasets").resolve(folder.getFileName()).resolve("10");
                RawDataset raw=new RawDataset(experiment,folder.resolve("efp/proc"));
                BrukerDataset expected=new BrukerDataset(folder.resolve("efp"));
                var actual=raw.process();
                if(Double.doubleToRawLongBits(raw.left)!=Double.doubleToRawLongBits(expected.left)
                    || Double.doubleToRawLongBits(raw.right)!=Double.doubleToRawLongBits(expected.right))
                    throw new AssertionError(folder+" raw ppm axis "+raw.left+" != "+expected.left);
                if(actual.nc()!=expected.integer("NC_proc"))throw new AssertionError(folder+" NC_proc");
                for(int i=0;i<actual.real().length;i++) {
                    if(Double.doubleToRawLongBits(actual.real()[i])!=Double.doubleToRawLongBits(expected.real[i]))
                        throw new AssertionError(folder+" real sample "+i+": "+actual.real()[i]+" != "+expected.real[i]);
                    if(Double.doubleToRawLongBits(actual.imaginary()[i])!=Double.doubleToRawLongBits(expected.imaginary[i]))
                        throw new AssertionError(folder+" imaginary sample "+i);
                    comparisons+=2;
                }
                cases++;
            }
        }
        if(cases==0)throw new AssertionError("No raw references found");
        System.out.println("PASS "+cases+" raw-FID efp cases, "+comparisons+" bit-for-bit double comparisons");
    }
}
