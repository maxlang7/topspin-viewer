import java.nio.file.*;
import java.util.*;

public class SavedPhaseReference {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);long comparisons=0;int cases=0;
        try(var folders=Files.list(root)) {
            for(Path folder:folders.toList()) {
                BrukerDataset before=new BrukerDataset(folder.resolve("efp"));
                BrukerDataset expected=new BrukerDataset(folder.resolve("pk-manual"));
                Map<String,String> input=BrukerDataset.readParameters(folder.resolve("pk-manual/proc"));
                var actual=NativeKernel.savePhase(before.real,before.imaginary,
                    Double.parseDouble(input.get("PHC0")),Double.parseDouble(input.get("PHC1")),before.integer("NC_proc"));
                if(actual.nc()!=expected.integer("NC_proc"))throw new AssertionError("NC_proc mismatch");
                for(int i=0;i<before.real.length;i++) {
                    if(Double.doubleToRawLongBits(actual.real()[i])!=Double.doubleToRawLongBits(expected.real[i]))
                        throw new AssertionError(folder+" real sample "+i);
                    if(Double.doubleToRawLongBits(actual.imaginary()[i])!=Double.doubleToRawLongBits(expected.imaginary[i]))
                        throw new AssertionError(folder+" imaginary sample "+i);
                    comparisons+=2;
                }
                cases++;
            }
        }
        if(cases==0)throw new AssertionError("No phase references found");
        System.out.println("PASS "+cases+" saved-phase cases, "+comparisons+" bit-for-bit double comparisons");
    }
}
