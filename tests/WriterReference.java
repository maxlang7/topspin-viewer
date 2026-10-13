import java.nio.file.*;
import java.util.*;

/** Stored int32 files must survive saving and reopening without a changed bit. */
public class WriterReference {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Path output=Files.createTempDirectory(Path.of("artifacts"),"writer-reference-");
        long bytes=0,points=0;int cases=0;
        try(var folders=Files.list(root)) {
            for(Path folder:folders.toList()) {
                Path reference=folder.resolve("pk-manual");Spectrum spectrum=new Spectrum(reference);
                spectrum.integrals.add(8.31,7.81);
                spectrum.integrals.calibrate(0,1,spectrum.real,spectrum.left,spectrum.right);
                Path target=output.resolve(folder.getFileName());Files.createDirectory(target);
                BrukerWriter.write(spectrum,target);
                for(String component:new String[]{"1r","1i"}) {
                    if(Files.mismatch(reference.resolve(component),target.resolve(component))!=-1)
                        throw new AssertionError("Saved bytes differ: "+folder+" "+component);
                    bytes+=Files.size(target.resolve(component));
                }
                Spectrum reread=new Spectrum(target);
                for(int i=0;i<spectrum.real.length;i++) {
                    if(Double.doubleToRawLongBits(spectrum.real[i])!=Double.doubleToRawLongBits(reread.real[i])
                        || Double.doubleToRawLongBits(spectrum.imaginary[i])!=Double.doubleToRawLongBits(reread.imaginary[i]))
                        throw new AssertionError("Reopened sample differs");
                    points+=2;
                }
                if(spectrum.integrals.regions().size()!=reread.integrals.regions().size())throw new AssertionError("Reopened integration regions differ");
                cases++;
            }
        }
        System.out.println("PASS "+cases+" save/reopen cases, "+bytes+" identical bytes, "+points+" bitwise comparisons; output "+output);
    }
}
