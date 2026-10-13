import java.nio.*;
import java.nio.file.*;
import java.util.*;
public class IntegrationReopenReference {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);Spectrum data=new Spectrum(Path.of(args[1]));long points=0;List<String> rows=Files.readAllLines(root.resolve("regions.tsv"));
        if(rows.isEmpty() || rows.size()!=data.integrals.regions().size())throw new AssertionError("TopSpin did not reconstruct every saved region");
        for(int i=0;i<rows.size();i++) {
            String[] row=rows.get(i).split("\t");var region=data.integrals.regions().get(i);
            double[] actualParams={region.left(),region.right(),region.bias(),region.slope(),data.integrals.normalizingFactor};
            for(int j=0;j<actualParams.length;j++)if(Double.doubleToRawLongBits(actualParams[j])!=Double.doubleToRawLongBits(Double.parseDouble(row[j+1])))throw new AssertionError("Reopened region parameter differs");
            var area=data.integrals.calculate(region,data.real,data.left,data.right);
            ByteBuffer expected=ByteBuffer.wrap(Files.readAllBytes(root.resolve(row[0]))).order(ByteOrder.LITTLE_ENDIAN);
            if(expected.remaining()!=area.curve().length*8)throw new AssertionError("Reopened curve size differs");
            for(double value:area.curve()){if(Double.doubleToRawLongBits(value)!=Double.doubleToRawLongBits(expected.getDouble()))throw new AssertionError("Reopened curve differs");points++;}
            if(Double.doubleToRawLongBits(area.normalized(data.integrals.normalizingFactor))!=Double.doubleToRawLongBits(Double.parseDouble(row[6])))throw new AssertionError("Reopened normalized value differs");
        }
        System.out.println("PASS actual TopSpin reopened "+rows.size()+" standalone integration regions; "+points+" bitwise curve comparisons and exact normalization");
    }
}
