import java.nio.*;
import java.nio.file.*;

/** Checks the replacement against captures from TopSpin's actual Java 11 process. */
public class LiveNumericsReference {
    private static long compared;
    private static double[] read(Path file) throws Exception {
        ByteBuffer bytes=ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
        double[] values=new double[bytes.remaining()/8];for(int i=0;i<values.length;i++)values[i]=bytes.getDouble();return values;
    }
    private static void equal(double[] actual,double[] expected,String name) {
        if(actual.length!=expected.length)throw new AssertionError(name+" length");
        for(int i=0;i<actual.length;i++) {compared++;
            if(Double.doubleToRawLongBits(actual[i])!=Double.doubleToRawLongBits(expected[i]))
                throw new AssertionError(name+" sample "+i+": "+Double.toHexString(actual[i])+" != "+Double.toHexString(expected[i]));
        }
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);double[] real=read(root.resolve("real.f64")),imag=read(root.resolve("imag.f64"));int cases=0;
        for(String line:Files.readAllLines(root.resolve("phase.tsv"))) {
            String[] c=line.split("\t");int start=Integer.parseInt(c[3]);
            equal(Numerics.phasePreview(real,imag,Double.parseDouble(c[1]),Double.parseDouble(c[2]),real.length,0,start,real.length-start),read(root.resolve(c[0])),c[0]);cases++;
        }
        for(String line:Files.readAllLines(root.resolve("integral.tsv"))) {
            String[] c=line.split("\t");
            equal(Numerics.integrate(real,Double.parseDouble(c[3]),Double.parseDouble(c[4]),Boolean.parseBoolean(c[1]),Integer.parseInt(c[2])).curve(),read(root.resolve(c[0])),c[0]);cases++;
        }
        System.out.println("PASS "+cases+" actual-TopSpin-JVM cases, "+compared+" bit-for-bit double comparisons; "+Files.readString(root.resolve("jvm.txt")).replace('\n',' '));
    }
}
