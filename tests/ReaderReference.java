import java.nio.*;
import java.nio.file.*;
import java.util.*;

public final class ReaderReference {
    public static void main(String[] args) throws Exception {
        Path captures=Path.of(args[0]);
        long comparisons=0;int spectra=0;
        try (var paths=Files.walk(captures)) {
            for (Path p:paths.filter(p->p.getFileName().toString().equals("snapshot.json")).toList()) {
                BrukerDataset dataset=new BrukerDataset(p.getParent());
                for (String component:List.of("1r","1i")) {
                    double[] actual=component.equals("1r")?dataset.real:dataset.imaginary;
                    ByteBuffer expected=ByteBuffer.wrap(Files.readAllBytes(p.getParent().resolve(component+".api.f64"))).order(ByteOrder.LITTLE_ENDIAN);
                    if(expected.remaining()!=actual.length*8)throw new AssertionError("Length mismatch");
                    for(int i=0;i<actual.length;i++) {
                        if(Double.doubleToRawLongBits(expected.getDouble())!=Double.doubleToRawLongBits(actual[i]))
                            throw new AssertionError(p+" "+component+" sample "+i);
                        comparisons++;
                    }
                }
                spectra++;
            }
        }
        if(spectra==0)throw new AssertionError("No captured references found");
        System.out.println("PASS "+spectra+" captured spectra, "+comparisons+" bit-for-bit reader comparisons");
    }
}
