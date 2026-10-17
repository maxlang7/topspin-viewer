import java.nio.file.*;

public class WindowsPhaseControllerReference {
    static long comparisons;
    static double number(String value){return Double.parseDouble(value);}
    static void equal(double actual,String expected) {
        comparisons++;if(Double.doubleToRawLongBits(actual)!=Double.doubleToRawLongBits(number(expected)))
            throw new AssertionError(Double.toHexString(actual)+" != "+expected);
    }
    public static void main(String[] args) throws Exception {
        int cases=0;
        for(String line:Files.readAllLines(Path.of(args[0]))) {
            if(line.startsWith("#"))continue;String[] row=line.split("\t");
            switch(row[0]) {
                case "drag" -> {
                    var phase=Numerics.dragPhase(number(row[1]),number(row[2]),Integer.parseInt(row[3]),number(row[4]),1,number(row[5]),Integer.parseInt(row[6]),Integer.parseInt(row[7]),Integer.parseInt(row[8]),Integer.parseInt(row[9]));
                    equal(Numerics.wrapPhaseZero(phase.zero()),row[10]);equal(phase.first(),row[11]);
                }
                case "preset" -> equal(Numerics.wrapPhaseZero(number(row[1])+Integer.parseInt(row[2])),row[3]);
                case "move" -> equal(Numerics.wrapPhaseZero(Numerics.wrapPhaseZero(number(row[1]))),row[2]);
                case "pivot" -> equal(Numerics.phaseAtPivot(number(row[1]),number(row[2])),row[3]);
                default -> throw new AssertionError("Unknown reference row "+row[0]);
            }
            cases++;
        }
        System.out.println("PASS "+cases+" actual Windows phase-controller cases, "+comparisons+" bitwise comparisons");
    }
}
