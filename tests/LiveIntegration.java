package oracle;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.prsc.datacomps.integral.Integral;

/** Test-only capture of the original interactive module, restricted to copied data. */
public class LiveIntegration {
    private static Object call(Object object,String name) throws Exception {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {
            Method method=c.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);
        }catch(NoSuchMethodException e){}
        throw new NoSuchMethodException(name);
    }
    private static void write(Path file,double[] values) throws Exception {
        ByteBuffer buffer=ByteBuffer.allocate(values.length*8).order(ByteOrder.LITTLE_ENDIAN);for(double value:values)buffer.putDouble(value);Files.write(file,buffer.array(),StandardOpenOption.CREATE_NEW);
    }
    public static void capture(Object module,String destination) throws Exception {
        SpecDataIfc data=(SpecDataIfc)call(module,"firstDataset");
        String identifier=data.getIdentifier().replace('\\','/');
        if(!identifier.contains("topspin-viewer") || !identifier.contains("artifacts"))
            throw new IllegalStateException("Refusing integration test on original acquisition: "+identifier);
        Path root=Paths.get(destination);Files.createDirectory(root);
        SpecViewIfc view=(SpecViewIfc)call(module,"getSpecView");
        ScaledMapperIfc mapper=view.getValueMapper();
        PhysicalRangeIfc range=mapper.getPhysicalMapping();
        String state="identifier="+identifier+"\nbaseline="+mapper.getBaselineShift()+"\nscale="+mapper.getIndividualScale()+"\nreference="+mapper.getReferenceIntensity()+"\nvalueLeft="+range.getStart()+"\nvalueRight="+range.getEnd()+"\nviewport="+view.getViewport()+"\n";
        Files.write(root.resolve("display.txt"),state.getBytes("UTF-8"));
        @SuppressWarnings("unchecked") List<Integral> before=(List<Integral>)call(module,"getListOfIntegrals");
        if(!before.isEmpty())throw new IllegalStateException("Expected empty integration list on the selected copied acquisition");
        Method add=module.getClass().getMethod("CMD_int1d_add",Object.class,Object.class);
        add.invoke(module,"8.31 7.81",null);add.invoke(module,"7.50 7.05",null);add.invoke(module,"2.60 2.40",null);
        @SuppressWarnings("unchecked") List<Integral> integrals=(List<Integral>)call(module,"getListOfIntegrals");
        StringBuilder manifest=new StringBuilder();int index=0;
        for(Integral area:integrals) {
            double[] params=area.getIntegParams();String name="region-"+(index++)+".f64";write(root.resolve(name),area.getDataDouble());
            manifest.append(name);for(double value:params)manifest.append('\t').append(value);
            manifest.append('\t').append(area.getNormalizingFactor()).append('\t').append(area.getNormalizedValue()).append('\n');
        }
        Files.write(root.resolve("regions.tsv"),manifest.toString().getBytes("UTF-8"));
        call(module,"saveChanges");
        Files.write(root.resolve("intrng"),data.getProperties().getProperty("DF_PROCFILE intrng").getBytes("UTF-8"));
        Files.write(root.resolve("INTSCL.txt"),data.getProperties().getProperty("1s INTSCL").getBytes("UTF-8"));
    }
}
