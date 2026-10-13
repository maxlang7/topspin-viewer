package oracle;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

public class LiveIntegrationReopen {
    private static Object call(Object object,String name) throws Exception {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {Method m=c.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(object);}catch(NoSuchMethodException e){}
        throw new NoSuchMethodException(name);
    }
    public static void capture(Object module,String destination) throws Exception {
        SpecDataIfc data=(SpecDataIfc)call(module,"firstDataset");String id=data.getIdentifier().replace('\\','/');
        if(!id.contains("topspin-viewer") || !id.contains("artifacts"))throw new IllegalStateException("Standalone test output required");
        Path root=Files.createTempDirectory(Paths.get(destination).getParent(),"live-integration-reopen-");
        Files.write(root.getParent().resolve("latest-integration-reopen-root.txt"),root.toString().getBytes("UTF-8"));
        @SuppressWarnings("unchecked") List<Integral> areas=(List<Integral>)call(module,"getListOfIntegrals");
        StringBuilder rows=new StringBuilder();int index=0;
        for(Integral area:areas) {
            double[] curve=area.getDataDouble();ByteBuffer bytes=ByteBuffer.allocate(curve.length*8).order(ByteOrder.LITTLE_ENDIAN);for(double value:curve)bytes.putDouble(value);
            String file="region-"+(index++)+".f64";Files.write(root.resolve(file),bytes.array(),StandardOpenOption.CREATE_NEW);
            rows.append(file);for(double value:area.getIntegParams())rows.append('\t').append(value);
            rows.append('\t').append(area.getNormalizingFactor()).append('\t').append(area.getNormalizedValue()).append('\n');
        }
        Files.write(root.resolve("regions.tsv"),rows.toString().getBytes("UTF-8"));
        Files.write(root.resolve("identifier.txt"),id.getBytes("UTF-8"));
    }
}
