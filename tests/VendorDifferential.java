import java.util.Arrays;
import java.util.Random;
import java.nio.file.*;
import java.nio.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.prsc.datacomps.integral.Integral;
import bruker.bio.topspin.procphase.phas1d.PhasedData;

/** Test-only vendor oracle. Does not open datasets, initialize instruments or write vendor files. */
public class VendorDifferential {
    static class ReferencePhase extends PhasedData {
        ReferencePhase(double[] re, double[] im, int ft, int strip) {
            super(); dataRe = re; dataIm = im; dataOut = new double[re.length];
            ftSize = ft; stripStart = strip;
        }
        @Override public IndexRangeIfc[] getCachedIndexRange() { return null; }
        @Override public synchronized double[] getDataDouble(IndexRangeIfc[] r) {
            return Arrays.copyOfRange(dataOut, r[0].getStart(), r[0].getStart() + r[0].getNum());
        }
        double[] run(int start, int count, double ph0, double ph1) throws Exception {
            return super.getDataDouble(new IndexRangeIfc[]{new IndexRange(start, count)}, ph0, ph1);
        }
    }
    static class ReferenceData extends VirtualSpecData {
        ReferenceData(double[] data) {
            super(); setDimension(1); setSizes(new int[]{data.length});
            setFullSizes(new int[]{data.length}); setDataDouble(data);
            setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(12.0, -2.0)});
        }
    }
    static class ReferenceIntegral extends Integral {
        ReferenceIntegral(double[] data, boolean correction, int points) {
            super(); integratedData = new ReferenceData(data);
            integratedIndexRange = new IndexRange(0, data.length);
            setDimension(1); setSizes(new int[]{data.length}); setFullSizes(new int[]{data.length});
            setDataDouble(new double[data.length]); setBaselinCorr(correction); setBaselinPoints(points);
        }
    }
    static long comparisons;
    static void equal(double[] a, double[] b, String label) {
        if (a.length != b.length) throw new AssertionError(label + ": length mismatch");
        for (int i = 0; i < a.length; i++) {
            comparisons++;
            if (Double.doubleToRawLongBits(a[i]) != Double.doubleToRawLongBits(b[i]))
                throw new AssertionError(label + " sample " + i + ": " + Double.toHexString(a[i])
                    + " vs " + Double.toHexString(b[i]));
        }
    }
    public static void main(String[] args) throws Exception {
        Random random = new Random(0x544f505350494eL);
        int cases = 0;
        for (int n : new int[]{1,2,3,4,17,256,4096,65536}) {
            double[] re = new double[n], im = new double[n];
            for (int i=0;i<n;i++) { re[i] = random.nextGaussian()*1e8; im[i] = random.nextGaussian()*1e8; }
            for (double ph0 : new double[]{0,-41.5206061238053,90,180,359.99999}) {
                for (double ph1 : new double[]{0,-11.9209746984819,720,-180}) {
                    for (int start : new int[]{0,n/3}) {
                        for (int strip : new int[]{0,128}) {
                            int ft = n*2;
                            equal(new ReferencePhase(re,im,ft,strip).run(start,n-start,ph0,ph1),
                                Numerics.phasePreview(re,im,ph0,ph1,ft,strip,start,n-start), "phase");
                            cases++;
                        }
                    }
                }
            }
            for (boolean corrected : new boolean[]{false,true}) {
                for (int points : new int[]{1,Math.min(4,n)}) {
                    for (double bias : new double[]{0,1234.567,-112345.6}) {
                        for (double slope : new double[]{0,12.3,-1.234}) {
                            ReferenceIntegral original = new ReferenceIntegral(re,corrected,points);
                            original.calculateIntegral(bias,slope);
                            Numerics.Integral rebuilt = Numerics.integrate(re,bias,slope,corrected,points);
                            equal(original.getDataDouble(),rebuilt.curve(),"integral");
                            equal(new double[]{original.getBias(),original.getSlope(),original.getIntegralValue()},
                                new double[]{rebuilt.bias(),rebuilt.slope(),rebuilt.value()},"integral parameters");
                            original.setNormalizingFactor(0.123456);
                            equal(new double[]{original.getNormalizedValue()},new double[]{rebuilt.normalized(0.123456)},"normalization");
                            cases++;
                        }
                    }
                }
            }
        }
        System.out.println("PASS " + cases + " cases, " + comparisons + " bit-for-bit double comparisons");
    }
}
