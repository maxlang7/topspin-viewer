package oracle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import de.bruker.nmr.jview.data.NMRData;
import de.bruker.nmr.jview.ifc.*;

/** Read-only raw-panel capture inside the licensed Windows TopSpin JVM. */
public final class LiveFidDisplay {
    private static Object call(Object object,String name)throws Exception {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {
            Method method=c.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);
        }catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException(name);
    }
    public static void capture(Object module,String destination)throws Exception {
        capture(module,destination,true);
    }
    public static void capture(Object module,String destination,boolean requireRaw)throws Exception {
        final Throwable[] error={null};Runnable action=()->{try{captureOnEdt(module,Paths.get(destination),requireRaw);}catch(Throwable failure){error[0]=failure;}};
        if(SwingUtilities.isEventDispatchThread())action.run();else SwingUtilities.invokeAndWait(action);
        if(error[0]!=null)throw new RuntimeException(error[0]);
    }
    private static void captureOnEdt(Object module,Path root,boolean requireRaw)throws Exception {
        @SuppressWarnings("unchecked") java.util.List<AbstractViewableDataIfc> datasets=(java.util.List<AbstractViewableDataIfc>)call(module,"getDatasetsVector");
        if(datasets.isEmpty() || !(datasets.get(0) instanceof SpecDataIfc) || requireRaw && (!(datasets.get(0) instanceof NMRData) || !((NMRData)datasets.get(0)).isRawData()))throw new IllegalStateException("The active module has no requested NMR view: "+module.getClass().getName()+", first="+(datasets.isEmpty()?"empty":datasets.get(0).getClass().getName()+" "+datasets.get(0).getIdentifier()));
        SpecDataIfc data=(SpecDataIfc)datasets.get(0);AbstractViewableDataIfc sourceData=datasets.get(0);
        if(sourceData instanceof DataPipeIfc)sourceData=((DataPipeIfc)sourceData).getRootPipedData();
        String id=sourceData.getIdentifier().replace('\\','/');
        if(!id.contains("topspin-viewer/artifacts/live-fid-reference-20261019/"))throw new IllegalStateException("Raw panel is outside the copied reference root: "+id);
        Files.createDirectory(root);
        ViewPanelIfc panel=(ViewPanelIfc)call(module,"getPanel");Component component=(Component)panel;
        StringBuilder text=new StringBuilder("module="+module.getClass().getName()+"\nlayout="+panel.getPanelLayout().getClass().getName()+"\nidentifier="+id+"\n");
        PhysicalRangeIfc physical=data.getFullPhysicalRange()[0],values=data.getValueRange();
        Files.write(root.resolve("data.tsv"),(((int[])call(data,"getFullSizes"))[0]+"\t"+Double.toHexString(physical.getStart())+"\t"+Double.toHexString(physical.getEnd())+"\t"+physical.getUnit()+"\t"+Double.toHexString(values.getStart())+"\t"+Double.toHexString(values.getEnd())+"\n").getBytes("UTF-8"));
        int index=0;
        for(ViewIfc view:panel.getPanelLayout().getViewsVector()) {
            text.append("\nview=").append(index++).append(' ').append(view.getClass().getName()).append("\nviewport=").append(view.getViewport()).append('\n');
            for(String key:new String[]{"DATA_COLOR","VERTICAL_AXIS_UNIT","AXIS_COLOR","AXIS_FONT","TEXT_OFFSET"})text.append("property ").append(key).append('=').append(view.getProperty(key)).append('\n');
            if(view instanceof SpecViewIfc) {
                SpecViewIfc spec=(SpecViewIfc)view;ScaledMapperIfc y=spec.getValueMapper();
                if(y!=null)text.append("yPhysical=").append(y.getPhysicalMapping()).append("\nyScreen=").append(y.getScreenMapping()).append("\nyBaseline=").append(y.getBaselineShift()).append("\nyScale=").append(y.getIndividualScale()).append("\nyReference=").append(y.getReferenceIntensity()).append('\n');
                if(spec.getCoordinateMapper()!=null)for(DiscFuncCoordinateMapperIfc x:spec.getCoordinateMapper())if(x!=null)text.append("xPhysical=").append(x.getPhysicalMapping()).append("\nxScreen=").append(x.getScreenMapping()).append('\n');
            }
        }
        Files.write(root.resolve("views.txt"),text.toString().getBytes("UTF-8"));
        Files.write(root.resolve("jvm.txt"),(System.getProperty("java.version")+"\n"+System.getProperty("os.name")+"\n").getBytes("UTF-8"));
        BufferedImage image=new BufferedImage(component.getWidth()*2,component.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.scale(2,2);component.paint(g);g.dispose();ImageIO.write(image,"png",root.resolve("panel.png").toFile());
    }
}
