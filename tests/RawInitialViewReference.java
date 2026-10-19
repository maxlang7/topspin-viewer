import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import de.bruker.nmr.jview.data.PhysicalRange;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.panel.*;
import de.bruker.nmr.jview.view.*;
import de.bruker.nmr.jview.renderer.ForegroundRenderer;

/** Original FID1DLayout default ranges, including the layout's separate empty-range fallback. */
public final class RawInitialViewReference {
    static final class Data extends VendorDifferential.ReferenceData {
        Data(RawTrace trace) {
            super(trace.samples);setType(AbstractDataIfc.DataType.NMR_DATA);
            setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(trace.time.start(),trace.time.end(),"s")});
            setValueRange(new PhysicalRange(trace.values.minimum(),trace.values.maximum(),""));
            setProperty("$localDATA_REF_INTENSITY","0");
        }
    }
    static {AbstractDataLayout.registerDataView(Data.class,SpecView1D.class,SpecView1DOverview.class);}
    static PhysicalRangeIfc sourceRange(RawTrace trace)throws Exception {
        Data data=new Data(trace);FID1DLayout layout=new FID1DLayout();JPanel panel=new JPanel();panel.setSize(406,272);
        Properties properties=new Properties();properties.setProperty("AXIS_Y_MODE","4");
        data.setLayout(layout);layout.setData(data,panel,properties);ViewIfc[] views=layout.getViews();
        // The dataset-name annotation alone asks for the global TopSpin desktop. It is outside this range check.
        views[11]=new EmptyView();SpecView1D view=(SpecView1D)views[0];view.getValueMapper().setPhysicalMapping(ModuleRangesReference.padded(trace.values.minimum(),trace.values.maximum()));
        BufferedImage image=new BufferedImage(406,272,BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();
        try {
            layout.calculateLayout(new ForegroundRenderer(graphics,properties,panel),new Rectangle(0,0,406,272),views,null,
                data.getFullPhysicalRange(),data.getFullPhysicalRange(),new String[]{"s"},0);
            return view.getValueMapper().getPhysicalMapping();
        }finally{graphics.dispose();}
    }
    private static void check(RawTrace trace)throws Exception {
        PhysicalRangeIfc expected=sourceRange(trace);var actual=trace.verticalRange();
        VendorDifferential.equal(new double[]{expected.getStart(),expected.getEnd()},new double[]{actual.start(),actual.end()},"original FID layout initial range");
    }
    public static void main(String[] args)throws Exception {
        Random random=new Random(0x46494452414e4745L);int cases=0,inventory=0;
        for(int c=0;c<300;c++) {
            int type=c%2==0?0:2;double[] samples=new double[512];
            for(int i=0;i<samples.length;i++)samples[i]=c%5==0?0:c%5==1?123:c%5==2?-123:type==0?random.nextInt():random.nextGaussian()*1e6;
            Map<String,String> acquisition=Map.of("TD","512","DTYPA",Integer.toString(type),"FnILOOP","0","NC",Integer.toString(c%25-12),"SFO1","500.13","SW","20");
            check(new RawTrace(samples,acquisition));cases++;
        }
        try(var paths=Files.walk(Path.of("artifacts/full-lab-references/datasets"))) {
            for(Path acqus:paths.filter(p->p.getFileName().toString().equals("acqus")).sorted().toList()) {
                check(new RawTrace(new RawDataset(acqus.getParent(),acqus.getParent().resolve("pdata/1/proc"))));cases++;inventory++;
            }
        }
        System.out.println("PASS "+cases+" original FID1DLayout initial ranges, including "+inventory+" acquisitions, zero/constant buffers and both numeric types; "+cases*2+" bitwise endpoints; dataset-name annotation excluded");
    }
}
