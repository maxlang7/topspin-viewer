import de.bruker.nmr.jview.data.IndexRange;
public class PivotProbe {
    public static void main(String[] args) throws Exception {
        java.util.Random random=new java.util.Random(998877);int cases=0;
        for(int n:new int[]{3,17,256,65536}) {
            double[] data=new double[n],imag=new double[n];
            for(int i=0;i<n;i++){data[i]=random.nextGaussian()*1e8;imag[i]=random.nextGaussian()*1e8;}
            var original=new VendorDifferential.ReferencePhase(data,imag,n,0);
            for(int start:new int[]{0,n/3})for(int count:new int[]{1,n-start}) {
                if(original.getMaxAmplitudeIndex(new IndexRange(start,count))!=Numerics.pivot(data,imag,start,count))
                    throw new AssertionError("Complex phase pivot differs");
                cases++;
            }
        }
        System.out.println("PASS "+cases+" exact complex phase-pivot cases");
    }
}
