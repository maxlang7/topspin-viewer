import java.lang.reflect.*;
import java.util.*;
import de.bruker.nmr.jview.view.MapperX;
import de.bruker.nmr.jview.data.IndexRange;
import de.bruker.nmr.jview.ifc.CompressedSpecDataIfc;

public class DisplayCompressionReference {
    public static void main(String[] args) throws Exception {
        var proxy=(CompressedSpecDataIfc)Proxy.newProxyInstance(CompressedSpecDataIfc.class.getClassLoader(),new Class<?>[]{CompressedSpecDataIfc.class},(object,method,values)->{
            if(method.getName().equals("isNonContinuousData"))return false;
            throw new AssertionError("Unexpected data request: "+method);
        });
        Random random=new Random(123456);long points=0;int cases=0;
        for(int count:new int[]{2,17,256,4096,65536})for(int width:new int[]{1,2,7,40,320,1040}) {
            if(width>=count)continue;
            double[] data=new double[count];for(int i=0;i<count;i++)data[i]=random.nextDouble()*1e8;
            for(int start:new int[]{0,12345}) {
                MapperX mapper=new MapperX();double[] expected=mapper.getCompression(proxy,data,new IndexRange(start,count),14,14+width);
                var actual=VisualNumerics.compress(data,start,width);
                VendorDifferential.equal(expected,actual.values(),"display compression");
                if(!Arrays.equals(mapper.getCompressionTable(),actual.boundaries()))throw new AssertionError("Compression boundaries differ");
                points+=expected.length;cases++;
            }
        }
        System.out.println("PASS "+cases+" display compression cases, "+points+" bitwise sample comparisons and exact boundary tables");
    }
}
