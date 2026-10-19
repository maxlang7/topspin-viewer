import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;
import de.bruker.nmr.jview.data.PhysicalRange;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.jview.view.SpecView1D;
import de.bruker.nmr.jview.renderer.ForegroundRenderer;

/** Product FID trace versus the original renderer, with native raw metadata supplied explicitly. */
public final class ViewerFidTraceReference {
    private static Field field(Object object,String name)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
    static final class Data extends VendorDifferential.ReferenceData {
        Data(RawTrace trace) {
            super(trace.samples);setType(AbstractDataIfc.DataType.NMR_DATA);
            setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(trace.time.start(),trace.time.end(),"s")});
            setValueRange(new PhysicalRange(trace.values.minimum(),trace.values.maximum(),""));
        }
    }
    public static void main(String[] args)throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        Path work=Files.createTempDirectory(Path.of("artifacts"),"viewer-fid-traces-");
        java.util.List<Path> experiments;
        try(var paths=Files.walk(Path.of("artifacts/full-lab-references/datasets"))) {experiments=paths.filter(p->p.getFileName().toString().equals("acqus")).sorted().map(Path::getParent).toList();}
        SwingUtilities.invokeAndWait(()->{Viewer viewer=new Viewer(false,new BrowserLocations(work.resolve("browser.properties"),Path.of("artifacts/full-lab-references/datasets").toAbsolutePath()));try {
            long pixels=0;int cases=0;
            for(Path experiment:experiments) {
                viewer.open(experiment);Spectrum spectrum=(Spectrum)field(viewer,"spectrum").get(viewer);
                spectrum.raw=new RawDataset(spectrum.experiment,spectrum.path.resolve("proc"));RawTrace initialTrace=new RawTrace(spectrum.raw);
                Component plot=(Component)field(viewer,"plot").get(viewer);int w=373,h=241;plot.setSize(w+33,h+31);
                Method showRaw=plot.getClass().getDeclaredMethod("showRaw",boolean.class);showRaw.setAccessible(true);showRaw.invoke(plot,true);
                Method full=plot.getClass().getDeclaredMethod("full");full.setAccessible(true);
                Method scale=plot.getClass().getDeclaredMethod("scale",double.class);scale.setAccessible(true);
                java.util.List<RawTrace> traces=new java.util.ArrayList<>();traces.add(initialTrace);
                if(experiment.equals(experiments.get(0)))for(int type:new int[]{0,2})for(int value:new int[]{0,123,-123}) {
                    var params=new java.util.LinkedHashMap<>(spectrum.raw.acquisition);params.put("TD","512");params.put("DTYPA",Integer.toString(type));params.put("NC","-6");
                    double[] flat=new double[512];java.util.Arrays.fill(flat,value);traces.add(new RawTrace(flat,params));
                }
                for(RawTrace trace:traces) {
                field(plot,"rawTrace").set(plot,trace);
                PhysicalRangeIfc initialRange=RawInitialViewReference.sourceRange(trace);
                Data data=new Data(trace);SpecView1D original=new SpecView1D();original.setViewedData(data);original.setProperty("DATA_COLOR","0 0 255");original.setViewport(new Rectangle(2,2,w,h));
                for(boolean sparse:new boolean[]{false,true})for(double factor:new double[]{1,2}) {
                    double left=sparse?trace.time.end()*.25:trace.time.start(),right=sparse?left+trace.time.end()*128/trace.samples.length:trace.time.end();
                    original.setRange(new PhysicalRangeIfc[]{new PhysicalRange(left,right,"s")});
                    original.getCoordinateMapper()[0].setPhysicalMapping(new PhysicalRange(left,right,"s"));original.getCoordinateMapper()[0].setScreenMapping(2,w+2);
                    original.getValueMapper().setReferenceIntensity(0);original.getValueMapper().setPhysicalMapping(initialRange);
                    original.getValueMapper().setScreenMapping(2,h+2);
                    original.getValueMapper().doScale(factor);
                    full.invoke(plot);scale.invoke(plot,factor);field(plot,"viewLeft").setDouble(plot,left);field(plot,"viewRight").setDouble(plot,right);
                    PhysicalRangeIfc vertical=original.getValueMapper().getPhysicalMapping();
                    var actualRange=(ViewRanges.Range)field(plot,"verticalRange").get(plot);
                    VendorDifferential.equal(new double[]{vertical.getStart(),vertical.getEnd()},new double[]{actualRange.start(),actualRange.end()},"product FID initial/scale range");
                    BufferedImage expected=new BufferedImage(w+33,h+31,BufferedImage.TYPE_INT_ARGB),actual=new BufferedImage(w+33,h+31,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g=expected.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,expected.getWidth(),expected.getHeight());
                    original.paint(new ForegroundRenderer(g,new java.util.Properties(),new JPanel()));g.dispose();
                    g=actual.createGraphics();plot.paint(g);g.dispose();long different=0;
                    // Exclude module annotations and the different direct/buffered panel clipping border.
                    for(int y=26;y<h+1;y++)for(int x=3;x<w+1;x++){pixels++;if(expected.getRGB(x,y)!=actual.getRGB(x,y))different++;}
                    if(different!=0) {
                        ImageIO.write(expected,"png",work.resolve("expected.png").toFile());ImageIO.write(actual,"png",work.resolve("actual.png").toFile());
                        throw new AssertionError(different+" product FID trace pixels differ: "+experiment+", sparse="+sparse+", scale="+factor+"; "+work);
                    }
                    cases++;
                }
                }
            }
            System.out.println("PASS actual Viewer FID trace against original renderer: "+cases+" cases across "+experiments.size()+" acquisitions, full/sparse views and two scales; "+pixels+" exact pixels; annotations and panel border excluded");
        }catch(Exception error){throw new RuntimeException(error);}finally{viewer.dispose();}});
    }
}
