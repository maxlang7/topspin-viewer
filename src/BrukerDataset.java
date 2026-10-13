import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Read-only processed 1D Bruker data, in TopSpin API amplitude units. */
public final class BrukerDataset {
    public final Path path;
    public final Map<String,String> parameters;
    public final double[] real, imaginary;
    public final double left, right;
    public final int ftSize, stripStart;

    public BrukerDataset(Path pdata) throws IOException {
        path = pdata.toRealPath();
        if (Files.exists(path.resolve("proc2s")))
            throw new IOException("Only one-dimensional spectra are supported");
        parameters = readParameters(path.resolve("procs"));
        int size = integer("SI");
        if (size <= 0 || size > 16_777_216) throw new IOException("Invalid processed spectrum size");
        if (integerOr("PPARMOD", 0) != 0) throw new IOException("Multidimensional processing is unsupported");
        if (integerOr("DTYPP", 0) != 0)
            throw new IOException("This processed numeric format has no validated TopSpin reference yet");
        int byteOrder = integer("BYTORDP");
        if (byteOrder != 0 && byteOrder != 1) throw new IOException("Invalid byte order");
        ByteOrder order = byteOrder == 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        int exponent = integer("NC_proc");
        if (exponent < -1022 || exponent > 1023) throw new IOException("Invalid scaling exponent");
        double scale = Math.scalb(1.0, exponent);
        real = readComponent(path.resolve("1r"), size, order, scale);
        imaginary = Files.isRegularFile(path.resolve("1i"))
            ? readComponent(path.resolve("1i"), size, order, scale) : null;
        double frequency = number("SF"), width = number("SW_p");
        if (frequency <= 0 || width <= 0) throw new IOException("Invalid frequency or spectral width");
        double axisLeft = numberOr("AXLEFT",0), axisRight = numberOr("AXRIGHT",0);
        if (axisLeft != axisRight) throw new IOException("Custom axes need an equivalence reference before use");
        left = number("OFFSET");
        right = left - width / frequency;
        ftSize = integerOr("FTSIZE", size);
        stripStart = integerOr("STSR", 0);
        int stripSize = integerOr("STSI", 0);
        if (stripStart != 0 || (stripSize != 0 && stripSize != size))
            throw new IOException("Stripped spectra need a file-opening equivalence reference before use");
    }

    private static double[] readComponent(Path file, int size, ByteOrder order, double scale) throws IOException {
        byte[] raw = Files.readAllBytes(file);
        if (raw.length < (long)size * 4) throw new IOException("Truncated spectrum component: " + file);
        ByteBuffer bytes = ByteBuffer.wrap(raw).order(order);
        double[] data = new double[size];
        for (int i=0;i<size;i++) data[i] = bytes.getInt() * scale;
        return data;
    }

    public double ppm(int i) { return left + ((right-left) / real.length) * i; }
    public int index(double ppm) { return Math.max(0,Math.min(real.length-1,Numerics.index(ppm,left,right,real.length))); }
    public double number(String key) throws IOException {
        String value = parameters.get(key);
        if (value == null) throw new IOException("Missing processing parameter " + key);
        try {
            double n = Double.parseDouble(value);
            if (!Double.isFinite(n)) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) { throw new IOException("Invalid " + key + ": " + value); }
    }
    public double numberOr(String key,double fallback) throws IOException { return parameters.containsKey(key)?number(key):fallback; }
    public int integer(String key) throws IOException {
        double n = number(key);
        if (n != Math.rint(n) || n<Integer.MIN_VALUE || n>Integer.MAX_VALUE) throw new IOException("Invalid integer " + key);
        return (int)n;
    }
    public int integerOr(String key,int fallback) throws IOException { return parameters.containsKey(key)?integer(key):fallback; }
    public static Map<String,String> readParameters(Path file) throws IOException {
        Map<String,String> result = new LinkedHashMap<>();
        Pattern scalar = Pattern.compile("^##\\$(\\w+)=\\s*(.*)$");
        for (String line:Files.readAllLines(file,java.nio.charset.StandardCharsets.ISO_8859_1)) {
            Matcher match = scalar.matcher(line);
            if (match.matches()) result.put(match.group(1),match.group(2).trim());
        }
        return Collections.unmodifiableMap(result);
    }
}
