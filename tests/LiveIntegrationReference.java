import java.nio.*;
import java.nio.file.*;
import java.util.*;

public class LiveIntegrationReference {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);BrukerDataset spectrum=new BrukerDataset(Path.of(args[1]));
        IntegrationRegions model=new IntegrationRegions();long compared=0;int cases=0;
        List<String[]> rows=Files.readAllLines(root.resolve("regions.tsv")).stream().map(line->line.split("\t")).toList();
        for(String[] row:rows)model.add(Double.parseDouble(row[1]),Double.parseDouble(row[2]));
        model.calibrate(0,1,spectrum.real,spectrum.left,spectrum.right);
        for(int index=0;index<rows.size();index++) {
            String[] row=rows.get(index);var actual=model.calculate(model.regions().get(index),spectrum.real,spectrum.left,spectrum.right);
            ByteBuffer bytes=ByteBuffer.wrap(Files.readAllBytes(root.resolve(row[0]))).order(ByteOrder.LITTLE_ENDIAN);
            if(bytes.remaining()!=actual.curve().length*8)throw new AssertionError("Region size differs");
            for(double value:actual.curve()){if(Double.doubleToRawLongBits(value)!=Double.doubleToRawLongBits(bytes.getDouble()))throw new AssertionError("Live integration curve differs");compared++;}
            if(Double.doubleToRawLongBits(model.normalizingFactor)!=Double.doubleToRawLongBits(Double.parseDouble(row[5])))throw new AssertionError("Default normalization differs");
            if(Double.doubleToRawLongBits(actual.normalized(model.normalizingFactor))!=Double.doubleToRawLongBits(Double.parseDouble(row[6])))throw new AssertionError("Live normalized value differs");
            cases++;
        }
        if(!model.serialize(spectrum.integer("NC_proc")).equals(Files.readString(root.resolve("intrng"))))throw new AssertionError("Live saved region file differs");
        String scale=String.format(Locale.ROOT,"%.15g",model.normalizingFactor*Math.pow(2.0,spectrum.integer("NC_proc")));
        if(!scale.equals(Files.readString(root.resolve("INTSCL.txt"))))throw new AssertionError("Live saved normalization parameter differs");
        System.out.println("PASS "+cases+" live manual regions, "+compared+" bitwise curve comparisons, exact normalization and saved file");
    }
}
