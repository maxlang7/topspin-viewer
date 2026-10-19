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
    private static void collectFields(Container parent,java.util.List<JTextField> fields) {
        for(Component child:parent.getComponents())if(child instanceof JTextField field)fields.add(field);else if(child instanceof Container container)collectFields(container,fields);
    }
    private static void regionDialogWorkflow(Viewer viewer,Spectrum spectrum,Object plot) throws Exception {
        spectrum.integrals.adjustBias(0,.003,1);spectrum.integrals.adjustSlope(0,.002,1,spectrum.real,spectrum.left,spectrum.right);
        var inherited=spectrum.integrals.regions().get(0);int count=spectrum.integrals.regions().size();Throwable[] failure={null};
        javax.swing.Timer timer=new javax.swing.Timer(100,null);
        timer.addActionListener(event->{
            for(Window window:viewer.getOwnedWindows())if(window instanceof JDialog dialog && dialog.isShowing()) {
                timer.stop();try {
                    if(!dialog.getTitle().equals("add"))throw new AssertionError("Unexpected region dialog title");
                    java.util.List<JTextField> fields=new ArrayList<>();collectFields(dialog,fields);
                    var format=new java.text.DecimalFormat("0.0000",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
                    if(fields.size()!=2 || !fields.get(0).getText().equals(format.format((double)field(plot,"viewLeft"))) || !fields.get(1).getText().equals(format.format((double)field(plot,"viewRight"))))throw new AssertionError("Region dialog did not default to the displayed range at four decimals");
                    fields.get(0).setText("8.5");fields.get(1).setText("7.5");textButton(dialog,"OK").doClick();
                }catch(Throwable error){failure[0]=error;dialog.dispose();}
            }
        });
        timer.start();try{click(viewer,"Define new region via dialog");}finally{timer.stop();}
        if(failure[0]!=null)throw new AssertionError("Region dialog workflow",failure[0]);
        if(spectrum.integrals.regions().size()!=count+1)throw new AssertionError("Dialog did not create a region");
        var added=spectrum.integrals.regions().get(count);
        if(added.left()!=8.5 || added.right()!=7.5 || added.bias()!=inherited.bias() || added.slope()!=inherited.slope())throw new AssertionError("Dialog did not inherit the last correction values");
        for(String action:new String[]{"Cancel","Escape","Close","Enter"}) {
            int before=spectrum.integrals.regions().size();
            javax.swing.Timer dismiss=new javax.swing.Timer(100,null);dismiss.addActionListener(event->{
                for(Window window:viewer.getOwnedWindows())if(window instanceof RangeEntryDialog dialog && dialog.isShowing()) {
                    dismiss.stop();try {
                        java.util.List<JTextField> fields=new ArrayList<>();collectFields(dialog,fields);fields.get(0).setText("6.25");fields.get(1).setText("5.75");
                        switch(action) {
                            case "Cancel"->textButton(dialog,"Cancel").doClick();
                            case "Escape"->{var root=dialog.getRootPane();Object key=root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0));root.getActionMap().get(key).actionPerformed(new ActionEvent(root,ActionEvent.ACTION_PERFORMED,""));}
                            case "Close"->dialog.dispatchEvent(new WindowEvent(dialog,WindowEvent.WINDOW_CLOSING));
                            case "Enter"->fields.get(0).postActionEvent();
                        }
                    }catch(Throwable error){failure[0]=error;dialog.dispose();}
                }
            });dismiss.start();try{click(viewer,"Define new region via dialog");}finally{dismiss.stop();}
            if(failure[0]!=null)throw new AssertionError("Region dialog "+action,failure[0]);
            int expected=before+(action.equals("Enter")?1:0);
            if(spectrum.integrals.regions().size()!=expected)throw new AssertionError("Region dialog "+action+" changed the wrong number of regions");
            if(action.equals("Enter")){var region=spectrum.integrals.regions().get(before);if(region.left()!=6.25 || region.right()!=5.75)throw new AssertionError("Enter did not submit edited coordinates");}
        }
    }
    private static void selectionWorkflow(Viewer viewer,Spectrum spectrum,Component plot) throws Exception {
        IntegrationRegions saved=spectrum.integrals;
        try {
            spectrum.integrals=new IntegrationRegions();double span=spectrum.left-spectrum.right;
            for(int i=0;i<3;i++)spectrum.integrals.add(spectrum.left-span*(.15+i*.2),spectrum.left-span*(.24+i*.2));
            BufferedImage image=new BufferedImage(plot.getWidth(),plot.getHeight(),BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=image.createGraphics();plot.paint(graphics);graphics.dispose();
            @SuppressWarnings("unchecked") Map<Integer,Rectangle> hits=(Map<Integer,Rectangle>)field(plot,"integralHitAreas");
            if(hits.size()!=3)throw new AssertionError("Missing integral label hit areas");
            long time=System.currentTimeMillis();
            for(int i:new int[]{0,2}) {
                Rectangle hit=hits.get(i);int x=hit.x+hit.width/2,y=hit.y+hit.height/2;
                int modifiers=InputEvent.BUTTON1_DOWN_MASK|(i==2?InputEvent.CTRL_DOWN_MASK:0);
                plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_PRESSED,time,modifiers,x,y,1,false,MouseEvent.BUTTON1));
                plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_RELEASED,time+1,modifiers,x,y,1,false,MouseEvent.BUTTON1));
            }
            if(!spectrum.integrals.selectedIndices().equals(java.util.List.of(0,2)))throw new AssertionError("Ctrl-click did not select two labels");
            click(viewer,"Integrals larger by factor 2");
            if(spectrum.integrals.displayScale(0).factor()!=1.6 || spectrum.integrals.displayScale(1).factor()!=.8 || spectrum.integrals.displayScale(2).factor()!=1.6 || spectrum.integrals.displayScale(0).hideFactor())throw new AssertionError("Only selected integral displays should scale");
            click(viewer,"Delete selected regions");
            if(spectrum.integrals.regions().size()!=1)throw new AssertionError("Delete did not remove both selected regions");
            click(viewer,"Undo last region operation");
            if(spectrum.integrals.regions().size()!=3 || !spectrum.integrals.selectedIndices().isEmpty())throw new AssertionError("Bulk delete did not undo in one step");
            click(viewer,"Select/Deselect all regions");
            if(spectrum.integrals.selectedIndices().size()!=3)throw new AssertionError("Select all failed");
            click(viewer,"Select/Deselect all regions");
            if(!spectrum.integrals.selectedIndices().isEmpty())throw new AssertionError("Deselect all failed");
            regionDialogWorkflow(viewer,spectrum,plot);
        } finally {spectrum.integrals=saved;}
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
        SwingUtilities.invokeAndWait(()->{reference[0]=new Viewer(false,new BrowserLocations(root.resolve("browser.properties"),root));reference[0].setVisible(true);reference[0].open(experiment);});
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
                    DatasetTabs tabs=(DatasetTabs)field(viewer,"datasetTabs");
                    if(!tabs.selected().equals("SPECTRUM"))throw new AssertionError("Spectrum tab was not initially selected");
                    textButton(viewer.getContentPane(),"FID").doClick();
                    if(!tabs.selected().equals("FID") || !(boolean)field(displayedPlot,"showFid") || spectrum.raw==null)throw new AssertionError("FID tab did not open raw data");
                    if(!Arrays.equals(before,spectrum.real))throw new AssertionError("FID viewing changed the processed spectrum");
                    RawTrace trace=(RawTrace)field(displayedPlot,"rawTrace");
                    if(trace.samples.length!=spectrum.raw.fid.length || (double)field(displayedPlot,"viewLeft")!=0 || (double)field(displayedPlot,"viewRight")!=trace.time.end() || (double)field(displayedPlot,"baseline")!=.5)throw new AssertionError("FID view did not use the full interleaved trace, time axis and centered baseline");
                    command(viewer,".zi");
                    double fidLeft=(double)field(displayedPlot,"viewLeft"),fidRight=(double)field(displayedPlot,"viewRight");
                    if(!(fidLeft>0 && fidRight<trace.time.end()))throw new AssertionError("FID zoom used spectrum coordinates");
                    click(viewer,"Larger by factor 2");ViewRanges.Range fidVertical=(ViewRanges.Range)field(displayedPlot,"verticalRange");
                    Component fidPlot=(Component)displayedPlot;long fidTime=System.currentTimeMillis();
                    fidPlot.dispatchEvent(new MouseEvent(fidPlot,MouseEvent.MOUSE_MOVED,fidTime,0,fidPlot.getWidth()/3,120,0,false,MouseEvent.NOBUTTON));
                    BufferedImage fidImage=new BufferedImage(fidPlot.getWidth(),fidPlot.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D fidGraphics=fidImage.createGraphics();fidPlot.paint(fidGraphics);fidGraphics.dispose();
                    Method fidReadout=fidPlot.getClass().getDeclaredMethod("cursorReadout");fidReadout.setAccessible(true);String[] fidLabels=(String[])fidReadout.invoke(fidPlot);
                    if(fidLabels==null || !fidLabels[0].endsWith(" s") || fidLabels[0].contains("ppm"))throw new AssertionError("FID cursor did not use time-domain labels");
                    ImageIO.write(fidImage,"png",root.resolve("fid.png").toFile());
                    textButton(viewer.getContentPane(),"INTEGRALS").doClick();
                    if(!tabs.selected().equals("INTEGRALS"))throw new AssertionError("Integral tab was not selected");
                    IntegralTable table=(IntegralTable)field(viewer,"integralTable");
                    if(!table.isShowing() || field(viewer,"mode")!=Viewer.Mode.VIEW || table.table.getRowCount()!=regions)throw new AssertionError("Integral tab did not display the table");
                    textButton(viewer.getContentPane(),"SPECTRUM").doClick();
                    if(!tabs.selected().equals("SPECTRUM") || (boolean)field(displayedPlot,"showFid"))throw new AssertionError("Spectrum tab did not restore spectrum display");
                    if(!((Component)field(viewer,"plot")).isShowing())throw new AssertionError("Spectrum tab did not restore plot");
                    if((double)field(displayedPlot,"viewLeft")!=spectrum.left || (double)field(displayedPlot,"viewRight")!=spectrum.right)throw new AssertionError("Spectrum inherited the FID zoom");
                    textButton(viewer.getContentPane(),"FID").doClick();
                    if((double)field(displayedPlot,"viewLeft")!=fidLeft || (double)field(displayedPlot,"viewRight")!=fidRight || !field(displayedPlot,"verticalRange").equals(fidVertical))throw new AssertionError("FID lost its own zoom or intensity scale");
                    command(viewer,".all");
                    if((double)field(displayedPlot,"viewLeft")!=0 || (double)field(displayedPlot,"viewRight")!=trace.time.end())throw new AssertionError("FID full-range reset used spectrum coordinates");
                    textButton(viewer.getContentPane(),"SPECTRUM").doClick();
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
                    selectionWorkflow(viewer,spectrum,plot);
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
                    textButton(viewer.getContentPane(),"FID").doClick();viewer.open(saved);
                    DatasetTabs reopenedTabs=(DatasetTabs)field(viewer,"datasetTabs");Spectrum openedAgain=(Spectrum)field(viewer,"spectrum");
                    if(!reopenedTabs.selected().equals("SPECTRUM") || (boolean)field(field(viewer,"plot"),"showFid"))throw new AssertionError("Opening another dataset retained the FID display state");
                    if(!Arrays.equals(reopened.real,openedAgain.real))throw new AssertionError("Tab navigation changed saved data");
                    BufferedImage image=new BufferedImage(viewer.getContentPane().getWidth()*2,viewer.getContentPane().getHeight()*2,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics=image.createGraphics();graphics.scale(2,2);viewer.getContentPane().printAll(graphics);graphics.dispose();ImageIO.write(image,"png",root.resolve("viewer.png").toFile());
                }catch(Exception error){throw new RuntimeException(error);}
            });
        } finally {SwingUtilities.invokeAndWait(()->reference[0].dispose());}
        System.out.println("PASS viewer open, spectrum/integral/FID tabs and reopen reset, phase preset/cancel/save/reopen, manual region dialog/drag/undo/cancel; "+root);
    }
}
