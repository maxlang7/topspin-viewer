import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Exercises the same buttons and mouse events used in the standalone UI. */
public class ViewerWorkflowReference {
    private static Object field(Object object,String name) throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    private static JButton button(Container parent,String tooltip) {
        for(Component child:parent.getComponents()) {
            if(child instanceof JButton b && b.getToolTipText()!=null && b.getToolTipText().startsWith(tooltip))return b;
            if(child instanceof Container container){JButton b=button(container,tooltip);if(b!=null)return b;}
        }
        return null;
    }
    private static void click(Viewer viewer,String tooltip) {
        JButton button=button(viewer.getContentPane(),tooltip);if(button==null)throw new AssertionError("Missing control "+tooltip);button.doClick();
    }
    private static void command(Viewer viewer,String value) throws Exception {
        JTextField command=(JTextField)field(viewer,"command");command.setText(value);command.postActionEvent();
    }
    private static void drag(Component plot,int a,int b) {
        long time=System.currentTimeMillis();
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_PRESSED,time,InputEvent.BUTTON1_DOWN_MASK,a,120,1,false,MouseEvent.BUTTON1));
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_DRAGGED,time+1,InputEvent.BUTTON1_DOWN_MASK,b,120,0,false,MouseEvent.NOBUTTON));
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_RELEASED,time+2,0,b,120,1,false,MouseEvent.BUTTON1));
    }
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]).toRealPath(),root=Files.createTempDirectory(Path.of("artifacts"),"viewer-workflow-");
        Path experiment=root.resolve("sample/10");
        try(var files=Files.walk(source)) {for(Path file:files.toList()) {
            Path target=experiment.resolve(source.relativize(file));
            if(Files.isDirectory(file))Files.createDirectories(target);else if(Files.isRegularFile(file))Files.copy(file,target);
        }}
        byte[] original=Files.readAllBytes(experiment.resolve("pdata/1/1r"));
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);JFrame.setDefaultLookAndFeelDecorated(true);
        Viewer[] reference=new Viewer[1];
        SwingUtilities.invokeAndWait(()->{reference[0]=new Viewer();reference[0].setVisible(true);reference[0].open(experiment);});
        try {
            SwingUtilities.invokeAndWait(()->{
                try {
                    Viewer viewer=reference[0];Spectrum spectrum=(Spectrum)field(viewer,"spectrum");
                    double[] before=spectrum.real.clone();int regions=spectrum.integrals.regions().size();
                    command(viewer,".ph");click(viewer,"Add 90 degrees");
                    if((double)field(viewer,"ph0")!=90)throw new AssertionError("Phase preset did not update preview");
                    command(viewer,".ret");if(!Arrays.equals(before,spectrum.real))throw new AssertionError("Phase cancel changed data");
                    command(viewer,".int");Component plot=(Component)field(viewer,"plot");
                    if(regions==0)click(viewer,"Define new region using cursor (toggle)");
                    drag(plot,Math.max(8,plot.getWidth()/5),Math.max(18,plot.getWidth()/4));
                    if(spectrum.integrals.regions().size()!=regions+1)throw new AssertionError("Mouse drag did not create a manual region");
                    click(viewer,"Undo last region operation");
                    if(spectrum.integrals.regions().size()!=regions)throw new AssertionError("Region undo failed");
                    drag(plot,Math.max(8,plot.getWidth()/5),Math.max(18,plot.getWidth()/4));
                    click(viewer,"Return, do NOT save regions");
                    if(spectrum.integrals.regions().size()!=regions)throw new AssertionError("Integration cancel failed");
                    command(viewer,".ph");click(viewer,"Add 90 degrees");click(viewer,"Return & save phased spectrum");
                    Path saved=experiment.resolve("pdata/2");
                    if(!Files.isRegularFile(saved.resolve("1r")))throw new AssertionError("Phase save produced no new processing result");
                    if(!Arrays.equals(original,Files.readAllBytes(experiment.resolve("pdata/1/1r"))))throw new AssertionError("Phase save overwrote input");
                    BrukerDataset reopened=new BrukerDataset(saved);
                    if(!Arrays.equals(reopened.real,spectrum.real) || !Arrays.equals(reopened.imaginary,spectrum.imaginary))throw new AssertionError("Saved phase differs from reopened view");
                    BufferedImage image=new BufferedImage(viewer.getContentPane().getWidth()*2,viewer.getContentPane().getHeight()*2,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics=image.createGraphics();graphics.scale(2,2);viewer.getContentPane().printAll(graphics);graphics.dispose();ImageIO.write(image,"png",root.resolve("viewer.png").toFile());
                }catch(Exception error){throw new RuntimeException(error);}
            });
        } finally {SwingUtilities.invokeAndWait(()->reference[0].dispose());}
        System.out.println("PASS viewer open, phase preset/cancel/save/reopen, manual region drag/undo/cancel; "+root);
    }
}
