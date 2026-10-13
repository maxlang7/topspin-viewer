package oracle;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

/** Captures manual controller operations in the licensed GUI, on copied data only. */
public class LiveIntegrationEdits {
    private static Object call(Object object,String name) throws Exception {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {
            Method m=c.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(object);
        }catch(NoSuchMethodException e){}
        throw new NoSuchMethodException(name);
    }
    @SuppressWarnings("unchecked")
    private static List<Integral> areas(Object module) throws Exception {return (List<Integral>)call(module,"getListOfIntegrals");}
    private static void write(Path file,double[] values) throws Exception {
        ByteBuffer bytes=ByteBuffer.allocate(values.length*8).order(ByteOrder.LITTLE_ENDIAN);for(double v:values)bytes.putDouble(v);Files.write(file,bytes.array(),StandardOpenOption.CREATE_NEW);
    }
    private static void snapshot(Object module,Path root,String name) throws Exception {
        Path directory=root.resolve(name);Files.createDirectory(directory);StringBuilder rows=new StringBuilder();int index=0;
        for(Integral area:areas(module)) {
            String file="region-"+index+++".f64";write(directory.resolve(file),area.getDataDouble());
            rows.append(file);for(double value:area.getIntegParams())rows.append('\t').append(value);
            rows.append('\t').append(area.getNormalizingFactor()).append('\t').append(area.getNormalizedValue()).append('\n');
        }
        Files.write(directory.resolve("regions.tsv"),rows.toString().getBytes("UTF-8"));
    }
    public static void capture(Object module,String destination) throws Exception {
        SpecDataIfc data=(SpecDataIfc)call(module,"firstDataset");String id=data.getIdentifier().replace('\\','/');
        if(!id.contains("topspin-viewer") || !id.contains("artifacts"))throw new IllegalStateException("Copied acquisition required");
        Path root=Files.createTempDirectory(Paths.get(destination).getParent(),"live-integration-edits-");
        Files.write(root.getParent().resolve("latest-integration-edits-root.txt"),root.toString().getBytes("UTF-8"));
        if(areas(module).isEmpty())throw new IllegalStateException("Manual regions are required");
        write(root.resolve("spectrum.f64"),data.getDataDouble());
        PhysicalRangeIfc range=data.getFullPhysicalRange()[0];
        Files.write(root.resolve("axis.tsv"),(range.getStart()+"\t"+range.getEnd()+"\n").getBytes("UTF-8"));
        for(Integral area:areas(module))area.setSelected(false);areas(module).get(0).setSelected(true);
        snapshot(module,root,"before");
        module.getClass().getMethod("CMD__bias",Object.class,Object.class).invoke(module,1.234,null);
        snapshot(module,root,"bias");
        module.getClass().getMethod("CMD__slope",Object.class,Object.class).invoke(module,-2.345,null);
        snapshot(module,root,"slope");
        Method cut=module.getClass().getDeclaredMethod("cutIntegral",double.class);cut.setAccessible(true);cut.invoke(module,8.0);
        snapshot(module,root,"split");
    }
}
