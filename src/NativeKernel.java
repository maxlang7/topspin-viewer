public final class NativeKernel {
    static { System.loadLibrary("topspin_reconstructed"); }
    private static native double[] phaseSaved(double[] real, double[] imaginary,
        double ph0, double ph1, int nc);
    private static native double[] processRaw(double[] fid, int size, int groupDelay,
        double lineBroadening, double spectralWidth, double ph0, double ph1, int nc, int cacheBytes);

    /** Combined em/ft/phase, with a single final integer conversion as in efp. */
    public static PhaseResult efp(double[] fid, int size, int groupDelay, double lb,
            double sw, double ph0, double ph1, int nc, int cacheBytes) {
        if (size < 16 || size > 16_777_216 || (size & (size-1)) != 0
                || (Integer.numberOfTrailingZeros(size) & 1) != 0)
            throw new IllegalArgumentException("This Fourier size has no validated raw-data reference yet");
        if ((fid.length & 1) != 0 || fid.length > size*2 || groupDelay < 0
                || groupDelay >= fid.length/2 || cacheBytes < 0 || cacheBytes > 16_777_216
                || !Double.isFinite(lb) || lb < 0 || !Double.isFinite(sw) || sw <= 0
                || !Double.isFinite(ph0) || !Double.isFinite(ph1) || nc < -1022 || nc > 1023)
            throw new IllegalArgumentException("Invalid raw processing parameters");
        for(double value:fid)if(!Double.isFinite(value))throw new IllegalArgumentException("Raw samples must be finite");
        return unpack(processRaw(fid,size,groupDelay,lb,sw,ph0,ph1,nc,cacheBytes),size);
    }

    public static PhaseResult efp(int[] fid, int size, int groupDelay, double lb,
            double sw, double ph0, double ph1, int nc, int cacheBytes) {
        double[] converted=new double[fid.length];
        for(int i=0;i<fid.length;i++)converted[i]=fid[i];
        return efp(converted,size,groupDelay,lb,sw,ph0,ph1,nc,cacheBytes);
    }

    private static PhaseResult unpack(double[] result, int n) {
        return new PhaseResult(java.util.Arrays.copyOfRange(result,1,n+1),
            java.util.Arrays.copyOfRange(result,n+1,2*n+1),(int)result[0]);
    }

    public record PhaseResult(double[] real, double[] imaginary, int nc) {}
    public static PhaseResult savePhase(double[] real, double[] imaginary,
            double ph0, double ph1, int nc) {
        if (imaginary == null || real.length != imaginary.length || real.length < 2)
            throw new IllegalArgumentException("A complex spectrum is required for phase correction");
        if (!Double.isFinite(ph0) || !Double.isFinite(ph1))
            throw new IllegalArgumentException("Phase angles must be finite");
        if(nc < -1022 || nc > 1023)throw new IllegalArgumentException("Invalid phase scaling exponent");
        for(int i=0;i<real.length;i++)if(!Double.isFinite(real[i]) || !Double.isFinite(imaginary[i]))
            throw new IllegalArgumentException("Spectrum samples must be finite");
        double[] result=phaseSaved(real,imaginary,ph0,ph1,nc);
        return unpack(result,real.length);
    }
    private NativeKernel() {}
}
