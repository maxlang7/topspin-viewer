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
    private static JButton textButton(Container parent,String text) {
        for(Component child:parent.getComponents()) {
            if(child instanceof JButton b && text.equals(b.getText()))return b;
            if(child instanceof Container c){JButton b=textButton(c,text);if(b!=null)return b;}
        }return null;
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
        SwingUtilities.invokeAndWait(()->{reference[0]=new Viewer(false);reference[0].setVisible(true);reference[0].open(experiment);});
        try {
            SwingUtilities.invokeAndWait(()->{
                try {
                    Viewer viewer=reference[0];Spectrum spectrum=(Spectrum)field(viewer,"spectrum");
                    double[] before=spectrum.real.clone();int regions=spectrum.integrals.regions().size();
                    textButton(viewer.getContentPane(),"Analyze").doClick();
                    if(field(viewer,"mode")!=Viewer.Mode.VIEW)throw new AssertionError("Analyze ribbon changed the active data tool");
                    textButton(viewer.getContentPane(),"Integrate").doClick();
                    if(field(viewer,"mode")!=Viewer.Mode.INTEGRATE)throw new AssertionError("Analyze ribbon did not open manual integration");
                    command(viewer,".ret");textButton(viewer.getContentPane(),"Process").doClick();
                    Object displayedPlot=field(viewer,"plot");ViewRanges.Range originalRange=(ViewRanges.Range)field(displayedPlot,"verticalRange");
                    click(viewer,"Larger by factor 2");
                    if(!field(displayedPlot,"verticalRange").equals(ViewRanges.scale(originalRange,2,0)))throw new AssertionError("Viewer scaling did not update the physical display range");
                    command(viewer,".all");command(viewer,".zi");
                    ViewRanges.Range zoomed=new ViewRanges.Range((double)field(displayedPlot,"viewLeft"),(double)field(displayedPlot,"viewRight"));
                    ViewRanges.Range expectedZoom=ViewRanges.zoom(new ViewRanges.Range(spectrum.left,spectrum.right),2,new ViewRanges.Range(spectrum.left,spectrum.right));
                    if(!zoomed.equals(expectedZoom))throw new AssertionError("Viewer zoom differs");
                    command(viewer,".all");
                    textButton(viewer.getContentPane(),"INTEGRALS").doClick();
                    IntegralTable table=(IntegralTable)field(viewer,"integralTable");
                    if(!table.isShowing() || field(viewer,"mode")!=Viewer.Mode.VIEW || table.table.getRowCount()!=regions)throw new AssertionError("Integral tab did not display the table");
                    textButton(viewer.getContentPane(),"SPECTRUM").doClick();
                    if(!((Component)field(viewer,"plot")).isShowing())throw new AssertionError("Spectrum tab did not restore plot");
                    command(viewer,".ph");JButton phase=button(viewer.getContentPane(),"0 order correction");long time=System.currentTimeMillis();
                    phase.dispatchEvent(new MouseEvent(phase,MouseEvent.MOUSE_PRESSED,time,InputEvent.BUTTON1_DOWN_MASK,10,5,1,false,MouseEvent.BUTTON1));
                    for(int y:new int[]{5,15,25})phase.dispatchEvent(new MouseEvent(phase,MouseEvent.MOUSE_DRAGGED,time,InputEvent.BUTTON1_DOWN_MASK,10,y,0,false,MouseEvent.NOBUTTON));
                    double sensitivity=(double)field(viewer,"sensitivity"),expectedPhase=10*sensitivity;
                    if(Double.doubleToRawLongBits((double)field(viewer,"ph0"))!=Double.doubleToRawLongBits(expectedPhase))throw new AssertionError("Toolbar phase drag differs from original pointer events");
                    phase.dispatchEvent(new MouseWheelEvent(phase,MouseEvent.MOUSE_WHEEL,time,0,10,15,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,1));expectedPhase+=4*sensitivity;
                    if(Double.doubleToRawLongBits((double)field(viewer,"ph0"))!=Double.doubleToRawLongBits(expectedPhase))throw new AssertionError("Toolbar phase wheel differs");
                    phase.dispatchEvent(new MouseEvent(phase,MouseEvent.MOUSE_RELEASED,time,0,10,15,1,false,MouseEvent.BUTTON1));
                    phase.dispatchEvent(new MouseEvent(phase,MouseEvent.MOUSE_EXITED,time,0,40,40,0,false,MouseEvent.NOBUTTON));
                    phase.dispatchEvent(new MouseWheelEvent(phase,MouseEvent.MOUSE_WHEEL,time,0,10,15,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,1));
                    if((double)field(viewer,"ph0")!=expectedPhase)throw new AssertionError("Inactive phase control kept consuming wheel events");
                    Component phasePlot=(Component)field(viewer,"plot");double oldLeft=(double)field(phasePlot,"viewLeft");
                    phasePlot.dispatchEvent(new MouseEvent(phasePlot,MouseEvent.MOUSE_MOVED,time,0,phasePlot.getWidth()/3,120,0,false,MouseEvent.NOBUTTON));
                    BufferedImage hoverImage=new BufferedImage(phasePlot.getWidth()*2,phasePlot.getHeight()*2,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D hoverGraphics=hoverImage.createGraphics();hoverGraphics.scale(2,2);phasePlot.paint(hoverGraphics);hoverGraphics.dispose();
                    Method hoverReadout=phasePlot.getClass().getDeclaredMethod("cursorReadout");hoverReadout.setAccessible(true);
                    String[] hoverLabels=(String[])hoverReadout.invoke(phasePlot);
                    if(hoverLabels==null || hoverLabels.length!=4 || !hoverLabels[0].contains(" ppm / ") || !hoverLabels[3].startsWith("Phase = "))throw new AssertionError("Phase hover did not expose coordinate, index, amplitude and phase readouts");
                    phasePlot.dispatchEvent(new MouseEvent(phasePlot,MouseEvent.MOUSE_EXITED,time,0,0,0,0,false,MouseEvent.NOBUTTON));
                    if(field(phasePlot,"cursorPoint")!=null || field(phasePlot,"cursorValue")!=null)throw new AssertionError("Phase cursor did not hide on exit");
                    drag(phasePlot,phasePlot.getWidth()/4,phasePlot.getWidth()/2);
                    if((double)field(viewer,"ph0")!=expectedPhase)throw new AssertionError("Plot drag changed phase instead of zooming");
                    if((double)field(phasePlot,"viewLeft")==oldLeft)throw new AssertionError("Plot drag did not zoom while phasing");
                    command(viewer,".all");
                    int oldPivot=(int)field(phasePlot,"pivot");
                    phasePlot.dispatchEvent(new MouseEvent(phasePlot,MouseEvent.MOUSE_PRESSED,time,InputEvent.BUTTON3_DOWN_MASK,8,120,1,true,MouseEvent.BUTTON3));
                    if((int)field(phasePlot,"pivot")!=oldPivot)throw new AssertionError("Right click changed the pivot before a menu command");
                    JPopupMenu popup=null;for(MenuElement item:MenuSelectionManager.defaultManager().getSelectedPath())if(item instanceof JPopupMenu p)popup=p;
                    if(popup==null || popup.getComponentCount()!=2 || !((JMenuItem)popup.getComponent(0)).getText().equals("Set Pivot Point"))throw new AssertionError("Phase pivot menu differs");
                    ((JMenuItem)popup.getComponent(0)).doClick();MenuSelectionManager.defaultManager().clearSelectedPath();
                    if((int)field(phasePlot,"pivot")==oldPivot)throw new AssertionError("Set Pivot Point did not use the clicked position");
                    command(viewer,".ret");
                    command(viewer,".ph");click(viewer,"Add 90 degrees");
                    if((double)field(viewer,"ph0")!=90)throw new AssertionError("Phase preset did not update preview");
                    click(viewer,"Add 90 degrees");if((double)field(viewer,"ph0")!=-180)throw new AssertionError("Phase preset did not wrap like the original controller");
                    command(viewer,".ret");if(!Arrays.equals(before,spectrum.real))throw new AssertionError("Phase cancel changed data");
                    command(viewer,".int");Component plot=(Component)field(viewer,"plot");
                    if(regions==0)click(viewer,"Define new region using cursor (toggle)");
                    drag(plot,Math.max(8,plot.getWidth()/5),Math.max(18,plot.getWidth()/4));
                    if(spectrum.integrals.regions().size()!=regions+1)throw new AssertionError("Mouse drag did not create a manual region");
                    click(viewer,"Undo last region operation");
                    if(spectrum.integrals.regions().size()!=(regions==0?1:regions))throw new AssertionError("Region undo differs from the original first-region guard");
                    if(regions==0){
                        drag(plot,Math.max(8,plot.getWidth()/3),Math.max(18,plot.getWidth()/2));
                        if(spectrum.integrals.regions().size()!=2)throw new AssertionError("Second manual region failed");
                        click(viewer,"Undo last region operation");
                        if(spectrum.integrals.regions().size()!=1)throw new AssertionError("Second-region undo failed");
                    }
                    drag(plot,Math.max(8,plot.getWidth()/5),Math.max(18,plot.getWidth()/4));
                    click(viewer,"Return, do NOT save regions");
                    if(spectrum.integrals.regions().size()!=regions)throw new AssertionError("Integration cancel failed");
                    command(viewer,".ph");click(viewer,"Add 90 degrees");click(viewer,"Return & save phased spectrum");
                    Path saved=experiment.resolve("pdata/1");spectrum=(Spectrum)field(viewer,"spectrum");
                    if(!spectrum.path.equals(saved.toRealPath()) || Files.exists(experiment.resolve("pdata/2")))throw new AssertionError("Phase save changed the processing number");
                    Path history=experiment.resolve("pdata/.topspin-viewer-history/1");
                    try(var snapshots=Files.list(history)) {
                        Path backup=snapshots.findFirst().orElseThrow().resolve("before/1r");
                        if(!Arrays.equals(original,Files.readAllBytes(backup)))throw new AssertionError("Phase save did not preserve original processed data");
                    }
                    BrukerDataset reopened=new BrukerDataset(saved);
                    if(!Arrays.equals(reopened.real,spectrum.real) || !Arrays.equals(reopened.imaginary,spectrum.imaginary))throw new AssertionError("Saved phase differs from reopened view");
                    BufferedImage image=new BufferedImage(viewer.getContentPane().getWidth()*2,viewer.getContentPane().getHeight()*2,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics=image.createGraphics();graphics.scale(2,2);viewer.getContentPane().printAll(graphics);graphics.dispose();ImageIO.write(image,"png",root.resolve("viewer.png").toFile());
                }catch(Exception error){throw new RuntimeException(error);}
            });
        } finally {SwingUtilities.invokeAndWait(()->reference[0].dispose());}
        System.out.println("PASS viewer open, integral table tabs, phase preset/cancel/save/reopen, manual region drag/undo/cancel; "+root);
    }
}
