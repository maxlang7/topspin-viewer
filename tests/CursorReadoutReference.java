import java.awt.*;
import java.util.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.listener.Cursor1D;
import de.bruker.nmr.jview.view.SpecView1D;

public final class CursorReadoutReference {
    static class Data extends VendorDifferential.ReferenceData {
        Data(double[] values){super(values);setType(AbstractDataIfc.DataType.NMR_DATA);}
    }
    static class Original extends Cursor1D {
        Original(ViewIfc view){super(view);}
        String[] labels(SpecView1D view,Point point,double value,double frequency,int start,int count) {
            PhysicalPoint physical=new PhysicalPoint();physical.setValue(value);physical.setUnit("ppm");physical.setReference(frequency);
            currentPhysical[0]=physical;currentIndex[0]=new IndexRange(start,count);return createLabels(view,point);
        }
    }
    public static void main(String[] args)throws Exception {
        Random random=new Random(655312);double[] values=new double[256];for(int i=0;i<values.length;i++)values[i]=random.nextGaussian()*1e8;
        Data data=new Data(values);SpecView1D view=new SpecView1D();view.setViewedData(data);view.setProperty("VERTICAL_AXIS_UNIT","rel");
        PhysicalRange vertical=new PhysicalRange(-1e7,1e8,"");vertical.setReference(2.03e-7);view.getValueMapper().setPhysicalMapping(vertical);Original original=new Original(view);
        for(int run=0;run<10000;run++) {
            double left=15,right=left-Math.pow(10,run%12-7),frequency=run%2==0?500.13:400.132211;
            Rectangle viewport=new Rectangle(2,2,20+random.nextInt(1800),636);view.setViewport(viewport);
            PhysicalRange horizontal=new PhysicalRange(left,right,"ppm");horizontal.setReference(frequency);
            view.getCoordinateMapper()[0].setPhysicalMapping(horizontal);view.getCoordinateMapper()[0].setScreenMapping(2,viewport.width+2);
            int x=2+random.nextInt(viewport.width),start=random.nextInt(200),count=1+random.nextInt(10);
            double coordinate=view.getCoordinateMapper()[0].mapToPhysical(x);
            String[] expected=original.labels(view,new Point(x,100),coordinate,frequency,start,count);
            String[] actual=CursorReadout.labels(VisualNumerics.horizontal(left,right,2,viewport.width+2),viewport,coordinate,frequency,start,count,values[start],vertical.getReference());
            if(!Arrays.equals(expected,actual))throw new AssertionError(Arrays.toString(expected)+" != "+Arrays.toString(actual));
        }
        System.out.println("PASS 10000 original cursor readouts, exact coordinate, index and amplitude text");
    }
}
