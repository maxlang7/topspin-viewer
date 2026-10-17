import java.util.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.view.MapperX;

public final class CursorBoundariesReference {
    public static void main(String[] args)throws Exception {
        Random random=new Random(391617);long compared=0;
        for(int run=0;run<2000;run++) {
            int size=new int[]{4,17,71,256,4096}[run%5],width=1+random.nextInt(1800);
            double[] values=new double[size];for(int i=0;i<size;i++)values[i]=random.nextGaussian();
            var data=new VendorDifferential.ReferenceData(values);
            double left=run%3==0?12:11.713,right=run%3==0?-2:3.219;
            PhysicalRangeIfc[] range=new PhysicalRangeIfc[]{new PhysicalRange(left,right)};double[] visible=data.getDataDouble(range);int start=data.getIndexRange(range)[0].getStart();
            MapperX original=new MapperX();original.setPhysicalMapping(left,right);original.setScreenMapping(2,width+2);
            original.getCompression(data,visible,new IndexRange(start,visible.length),2,width+2);
            int[] actual=VisualNumerics.cursorBoundaries(visible,start,width,2,VisualNumerics.horizontal(left,right,2,width+2),12,-2,size),expected=original.getCompressionTable();
            if(!Arrays.equals(expected,actual))throw new AssertionError("Cursor sample boundaries differ at size="+size+" width="+width);compared+=actual.length;
        }
        System.out.println("PASS 2000 original cursor index tables, "+compared+" exact boundaries");
    }
}
