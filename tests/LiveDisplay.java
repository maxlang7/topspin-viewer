package oracle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import de.bruker.nmr.jview.ifc.*;

/** Read-only display-state and renderer capture inside the target JVM. */
public class LiveDisplay {
    private static Object call(Object object,String name) throws Exception {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {
            Method method=c.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);
        }catch(NoSuchMethodException e){}
        throw new NoSuchMethodException(name);
    }
    public static void capture(Object module,String destination) throws Exception {
        Path root=Files.createTempDirectory(Paths.get(destination).getParent(),"live-display-integration-");
        Files.write(root.getParent().resolve("latest-display-root.txt"),root.toString().getBytes("UTF-8"));
        try {
        ViewPanelIfc panel=(ViewPanelIfc)call(module,"getPanel");
        StringBuilder text=new StringBuilder("module="+module.getClass().getName()+"\nlayout="+panel.getPanelLayout().getClass().getName()+"\n");
        int index=0;
        for(ViewIfc view:panel.getPanelLayout().getViewsVector()) {
            text.append("\nview=").append(index).append(' ').append(view.getClass().getName()).append('\n');
            text.append("viewport=").append(view.getViewport()).append('\n');
            Properties properties=view.getProperties();
            for(String key:new TreeSet<String>(properties.stringPropertyNames()))text.append("property ").append(key).append('=').append(properties.getProperty(key)).append('\n');
            if(view instanceof SpecViewIfc) {
                SpecViewIfc spec=(SpecViewIfc)view;ScaledMapperIfc y=spec.getValueMapper();
                if(y!=null)text.append("yPhysical=").append(y.getPhysicalMapping()).append("\nyScreen=").append(y.getScreenMapping()).append("\nyBaseline=").append(y.getBaselineShift()).append("\nyScale=").append(y.getIndividualScale()).append("\nyReference=").append(y.getReferenceIntensity()).append('\n');
                if(spec.getCoordinateMapper()!=null)for(DiscFuncCoordinateMapperIfc x:spec.getCoordinateMapper())if(x!=null)text.append("xPhysical=").append(x.getPhysicalMapping()).append("\nxScreen=").append(x.getScreenMapping()).append('\n');
            }
            index++;
        }
        Files.write(root.resolve("views.txt"),text.toString().getBytes("UTF-8"));
        Component component=(Component)panel;
        BufferedImage image=new BufferedImage(component.getWidth()*2,component.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();graphics.scale(2,2);component.paint(graphics);graphics.dispose();
        ImageIO.write(image,"png",root.resolve("panel.png").toFile());
        } catch(Throwable error) {
            java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
            Files.write(root.resolve("error.txt"),trace.toString().getBytes("UTF-8"));throw new RuntimeException(error);
        }
    }
}
