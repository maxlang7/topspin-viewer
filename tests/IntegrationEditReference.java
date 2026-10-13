import java.nio.*;
import java.nio.file.*;
import java.util.*;

public class IntegrationEditReference {
    private static long comparisons;
    private static double[] read(Path file) throws Exception {
        ByteBuffer bytes=ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        double[] values=new double[bytes.remaining()/8];for(int i=0;i<values.length;i++)values[i]=bytes.getDouble();return values;
    }
    private static void equal(double actual,double expected) {comparisons++;if(Double.doubleToRawLongBits(actual)!=Double.doubleToRawLongBits(expected))throw new AssertionError(Double.toHexString(actual)+" != "+Double.toHexString(expected));}
    private static void check(IntegrationRegions model,Path root,String stage,double[] data,double left,double right) throws Exception {
        List<String> rows=Files.readAllLines(root.resolve(stage+"/regions.tsv"));
        if(rows.size()!=model.regions().size())throw new AssertionError(stage+" region count");
        for(int i=0;i<rows.size();i++) {
            String[] row=rows.get(i).split("\t");var region=model.regions().get(i);
            equal(region.left(),Double.parseDouble(row[1]));equal(region.right(),Double.parseDouble(row[2]));
            equal(region.bias(),Double.parseDouble(row[3]));equal(region.slope(),Double.parseDouble(row[4]));
            equal(model.normalizingFactor,Double.parseDouble(row[5]));
            var area=model.calculate(region,data,left,right);double[] expected=read(root.resolve(stage+"/"+row[0]));
            if(expected.length!=area.curve().length)throw new AssertionError(stage+" curve size");
            for(int j=0;j<expected.length;j++)equal(area.curve()[j],expected[j]);
            equal(area.normalized(model.normalizingFactor),Double.parseDouble(row[6]));
        }
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);double[] data=read(root.resolve("spectrum.f64"));String[] axis=Files.readString(root.resolve("axis.tsv")).trim().split("\t");
        double left=Double.parseDouble(axis[0]),right=Double.parseDouble(axis[1]);IntegrationRegions model=new IntegrationRegions();
        for(String line:Files.readAllLines(root.resolve("before/regions.tsv"))) {
            String[] row=line.split("\t");model.add(Double.parseDouble(row[1]),Double.parseDouble(row[2]));
            model.normalizingFactor=Double.parseDouble(row[5]);
        }
        check(model,root,"before",data,left,right);
        model.adjustBias(0,1.234,1);check(model,root,"bias",data,left,right);
        model.adjustSlope(0,-2.345,1,data,left,right);check(model,root,"slope",data,left,right);
        model.split(0,8,left,right,data.length);check(model,root,"split",data,left,right);
        System.out.println("PASS live manual bias, slope and split controller operations; "+comparisons+" bit-for-bit comparisons");
    }
}
