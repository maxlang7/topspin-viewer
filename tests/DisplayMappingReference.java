import java.util.*;
import de.bruker.nmr.jview.view.*;

public class DisplayMappingReference {
    public static void main(String[] args) throws Exception {
        Random random=new Random(654321);long count=0;
        for(double[] physical:new double[][]{{12,-2},{-1e7,7.2e7},{-.123,.567},{-1e-30,1e-29}})
        for(int[] screen:new int[][]{{2,1035},{10,210},{210,10},{-7,919}}) {
            MapperX x=new MapperX();MapperY y=new MapperY();
            x.setPhysicalMapping(physical[0],physical[1]);x.setScreenMapping(screen[0],screen[1]);
            y.setPhysicalMapping(physical[0],physical[1]);y.setScreenMapping(screen[0],screen[1]);
            var rebuiltX=VisualNumerics.horizontal(physical[0],physical[1],screen[0],screen[1]);
            var rebuiltY=VisualNumerics.vertical(physical[0],physical[1],screen[0],screen[1]);
            for(double gain:new double[]{.5,1,2,1e5}) {
                y.setIndividualScale(gain);
                for(int i=0;i<1000;i++) {
                    double value=physical[0]+(physical[1]-physical[0])*(random.nextDouble()*4-2);
                    if(x.mapToScreen(value)!=rebuiltX.screen(value,1))throw new AssertionError("X mapping");
                    if(y.mapToScreen(value)!=rebuiltY.screen(value,gain))throw new AssertionError("Y mapping "+Arrays.toString(physical)+" "+Arrays.toString(screen)+" gain="+gain+" value="+Double.toHexString(value)+" original="+y.mapToScreen(value)+" rebuilt="+rebuiltY.screen(value,gain)+" mapping="+rebuiltY+" range="+y.getPhysicalMapping());
                    count+=2;
                }
            }
        }
        System.out.println("PASS "+count+" exact display-coordinate comparisons");
    }
}
