import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.tree.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Standalone workbench for the measured TopSpin subset. */
public final class Viewer extends JFrame {
    enum Mode { VIEW, PHASE, INTEGRATE }
    private Spectrum spectrum;
    private Mode mode=Mode.VIEW;
    private final JPanel modeBar=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
    private EditingTools editingTools;
    private final Plot plot=new Plot();
    private final CardLayout surfaceLayout=new CardLayout();
    private final JPanel surface=new JPanel(surfaceLayout);
    private final IntegralTable integralTable=new IntegralTable();
    private final JLabel datasetLabel=new JLabel(" "),status=new JLabel(" ");
    private final JTextField command=new JTextField();
    private final DefaultMutableTreeNode root=new DefaultMutableTreeNode("Data");
    private final JTree tree=new JTree(root);
    private double ph0,ph1,sensitivity=.00625;
    private int phaseOrder;
    private int selectedIntegral=-1;
    private boolean defineRegion=true;
    private IntegrationRegions sessionIntegrals;
    private double savedBaseline;
    private double integrationSensitivity=1,integralBaseline=.2;
    private String integrationAction="select";
    private final ArrayDeque<double[]> zoomHistory=new ArrayDeque<>();
    private final boolean warpPointer;

    Viewer(){this(true);}
    Viewer(boolean warpPointer) {
        super("TopSpin Viewer");setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.warpPointer=warpPointer;
        setSize(1438,927);setMinimumSize(new Dimension(960,640));
        JPanel content=new JPanel(new BorderLayout());setContentPane(content);
        content.add(header(),BorderLayout.NORTH);
        JPanel center=new JPanel(new BorderLayout());center.add(viewToolbar(),BorderLayout.NORTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,browser(),workbench());
        split.setDividerLocation(328);split.setDividerSize(12);split.setBorder(BorderFactory.createEmptyBorder(0,9,0,9));
        center.add(split);content.add(center);content.add(footer(),BorderLayout.SOUTH);
        command.addActionListener(e->execute(command.getText()));
        getRootPane().registerKeyboardAction(e->chooseOpen(),KeyStroke.getKeyStroke(KeyEvent.VK_O,InputEvent.CTRL_DOWN_MASK),JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e->returnWithoutSave(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        setMode(Mode.VIEW);
    }

    private JPanel header() {
        Color top=new Color(5,65,105),lower=new Color(4,48,77),ink=new Color(195,208,217);
        JPanel header=new JPanel(new BorderLayout());header.setPreferredSize(new Dimension(1,104));header.setBackground(top);
        JPanel rows=new JPanel(new BorderLayout());rows.setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
        JPanel first=new JPanel(new BorderLayout());first.setBackground(top);first.setPreferredSize(new Dimension(1,50));
        CardLayout sections=new CardLayout();JPanel second=new JPanel(sections);second.setBackground(lower);
        JPanel nav=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));nav.setOpaque(false);
        JButton menu=Assets.button("phase","<html><b>Click here to access commands",this::fileMenu,65,50);
        menu.setBackground(top);nav.add(menu);
        NavigationTabs navigation=new NavigationTabs();
        navigation.addTab("Process",75,0,()->sections.show(second,"Process"));
        navigation.addTab("Analyze",74,1,()->sections.show(second,"Analyze"));nav.add(navigation);
        first.add(nav,BorderLayout.WEST);
        JLabel title=new JLabel("TopSpin Processing",SwingConstants.CENTER);title.setForeground(Color.WHITE);title.setFont(Assets.regular.deriveFont(14f));first.add(title);
        Ribbon processing=new Ribbon();
        processing.addCommand("Proc. Spectrum",Assets.icon("phase","text.Proc. Spectrum"),125,()->perform(()->{requireSpectrum();spectrum.processRaw();plot.full();status.setText("efp: finished");}));
        processing.addCommand("Adjust Phase",Assets.icon("phase","text.Adjust Phase"),111,()->setMode(Mode.PHASE));
        Ribbon analysis=new Ribbon();
        analysis.addCommand("Integrate",Assets.colored(Assets.icon("integration","text.Integration"),Ribbon.FOREGROUND),83,()->setMode(Mode.INTEGRATE));
        second.add(ribbonSection(processing),"Process");second.add(ribbonSection(analysis),"Analyze");
        rows.add(first,BorderLayout.NORTH);rows.add(second);header.add(rows);
        JLabel logo=new JLabel(Assets.image("4d72af01e262c2de.png"),SwingConstants.CENTER);logo.setPreferredSize(new Dimension(132,104));header.add(logo,BorderLayout.EAST);return header;
    }
    private JPanel ribbonSection(Ribbon ribbon) {
        JPanel panel=new JPanel(new BorderLayout()){@Override protected void paintComponent(Graphics g){super.paintComponent(g);g.setColor(Color.BLACK);g.fillRect(0,0,getWidth(),1);}};
        panel.setBackground(Ribbon.BACKGROUND);panel.setBorder(BorderFactory.createEmptyBorder(2,3,0,0));panel.add(ribbon);return panel;
    }
    private JPanel viewToolbar() {
        ViewerTools bar=new ViewerTools();
        String[] tools={"Larger by factor 2","Smaller by factor 2","Smaller/larger","Smooth zoom","Shift baseline up/down","Shift spectrum left","Shift baseline to bottom","Zoom in","Zoom out","Show the last zoom","Show full spectrum"};
        for(int i=0;i<tools.length;i++) {
            final int index=i;
            if(i==2 || i==4 || i==7)bar.separator();
            JButton b=bar.addTool(tools[i],()->{
                switch(index) {
                    case 0 -> plot.scale(2);case 1 -> plot.scale(.5);
                    case 6 -> plot.setBaseline(.05);case 7 -> plot.zoom(.5);case 8 -> plot.zoom(2);
                    case 9 -> plot.previous();case 10 -> plot.full();
                }plot.repaint();
            });
            if(i==2)new RelativeDragControl(b,()->{},plot::scale,warpPointer,PointerGesture.Kind.VERTICAL_SCALE);
            if(i==3)new RelativeDragControl(b,()->{},factor->plot.zoomFactor(factor<1?1.025:.9756097560975611),warpPointer,PointerGesture.Kind.HORIZONTAL_SCALE);
            if(i==4)new RelativeDragControl(b,()->{},amount->plot.setBaseline(plot.baseline-amount/400.0),warpPointer);
            if(i==5)new RelativeDragControl(b,()->{},plot::pan,warpPointer,PointerGesture.Kind.HORIZONTAL_SHIFT);

        }
        JPanel row=new JPanel(new BorderLayout());row.setBackground(new Color(245,245,245));
        row.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));row.add(bar,BorderLayout.WEST);return row;
    }
    private JPanel browser() {
        JPanel panel=new JPanel(new BorderLayout());panel.setBackground(new Color(207,207,207));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(142,142,142)),BorderFactory.createEmptyBorder(8,8,8,8)));
        JPanel title=new JPanel(new BorderLayout());title.setOpaque(false);
        JButton data=new JButton("☷  Data");data.setBackground(new Color(175,201,216));data.setPreferredSize(new Dimension(72,32));
        data.addActionListener(e->chooseOpen());title.add(data,BorderLayout.WEST);
        JPanel searchPanel=new JPanel(new BorderLayout(6,8));searchPanel.setBackground(Color.WHITE);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(8,4,8,4));
        JTextField search=new JTextField();search.putClientProperty("JTextField.placeholderText","Search...");
        searchPanel.add(search);JButton open=new JButton("Open...");open.addActionListener(e->chooseOpen());searchPanel.add(open,BorderLayout.EAST);
        JPanel north=new JPanel(new BorderLayout(0,8));north.setOpaque(false);north.add(title,BorderLayout.NORTH);north.add(searchPanel);panel.add(north,BorderLayout.NORTH);
        tree.setRootVisible(false);tree.setBackground(Color.WHITE);tree.setRowHeight(18);
        tree.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){
            if(e.getClickCount()==2 && tree.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Path p)open(p);
        }});
        JScrollPane scroll=new JScrollPane(tree);scroll.setBorder(BorderFactory.createEmptyBorder());panel.add(scroll);
        search.addActionListener(e->populateTree(search.getText()));
        populateTree("");return panel;
    }
    private void populateTree(String filter) {
        root.removeAllChildren();Path base=Path.of(System.getProperty("user.home"),"Documents/IGB Lab/NMRs");
        DefaultMutableTreeNode folder=new DefaultMutableTreeNode(base);root.add(folder);
        if(Files.isDirectory(base))try(var dirs=Files.list(base)) {
            for(Path p:dirs.filter(Files::isDirectory).sorted().toList()) {
                if(p.getFileName().toString().startsWith(".") || !p.getFileName().toString().toLowerCase().contains(filter.toLowerCase()))continue;
                DefaultMutableTreeNode n=new DefaultMutableTreeNode(p);folder.add(n);
                try(var experiments=Files.find(p,3,(path,attr)->attr.isRegularFile() && path.getFileName().toString().equals("acqus"))) {
                    for(Path acq:experiments.sorted().toList())n.add(new DefaultMutableTreeNode(acq.getParent()));
                }
            }
        }catch(Exception e){status.setText(e.getMessage());}
        ((DefaultTreeModel)tree.getModel()).reload();if(!filter.isEmpty())tree.expandRow(0);
    }
    private JPanel workbench() {
        JPanel panel=new JPanel(new BorderLayout());panel.setBackground(new Color(204,204,204));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(142,142,142)),BorderFactory.createEmptyBorder(8,8,2,8)));
        modeBar.setOpaque(false);modeBar.setPreferredSize(new Dimension(1,41));panel.add(modeBar,BorderLayout.NORTH);
        surface.add(plot,"plot");surface.add(integralTable,"integrals");panel.add(surface);return panel;
    }
    private JPanel footer() {
        JPanel panel=new JPanel(new BorderLayout(65,0));panel.setBorder(BorderFactory.createEmptyBorder(8,9,8,9));
        command.setPreferredSize(new Dimension(360,30));command.putClientProperty("JTextField.placeholderText","Command...");panel.add(command,BorderLayout.WEST);
        panel.add(datasetLabel);status.setPreferredSize(new Dimension(280,30));panel.add(status,BorderLayout.EAST);return panel;
    }
    private void fileMenu() {
        JPopupMenu menu=new JPopupMenu();JMenuItem open=new JMenuItem("Open...",KeyEvent.VK_O);open.addActionListener(e->chooseOpen());menu.add(open);
        JMenuItem save=new JMenuItem("Save");save.addActionListener(e->save());menu.add(save);menu.show(getContentPane(),8,50);
    }
    private void chooseOpen() {JFileChooser chooser=new JFileChooser(System.getProperty("user.home")+"/Documents/IGB Lab/NMRs");chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)open(chooser.getSelectedFile().toPath());}
    public void open(Path path) {perform(()->{
        java.util.List<Path> candidates=DatasetOpener.candidates(path);Path selected=candidates.get(0);
        if(candidates.size()>1) {
            Object choice=JOptionPane.showInputDialog(this,"Dataset","Open Bruker data",JOptionPane.PLAIN_MESSAGE,null,candidates.toArray(),selected);
            if(choice==null)return;selected=(Path)choice;
        }
        spectrum=new Spectrum(selected);selectedIntegral=-1;ph0=ph1=0;mode=Mode.VIEW;surfaceLayout.show(surface,"plot");datasetLabel.setText(spectrum.label()+"  "+spectrum.path);plot.full();rebuildModeBar();status.setText("Dataset opened");
    });}
    private void requireSpectrum() {if(spectrum==null)throw new IllegalStateException("Open a dataset first");}
    private void setMode(Mode next) {
        if(next!=Mode.VIEW && spectrum==null)return;
        if(next==Mode.PHASE && spectrum.imaginary==null){status.setText("Imaginary spectrum is required for phase correction");return;}
        if(mode==Mode.INTEGRATE && next!=Mode.INTEGRATE)plot.setBaseline(savedBaseline);
        mode=next;ph0=ph1=0;phaseOrder=0;
        surfaceLayout.show(surface,"plot");
        plot.showFid=false;
        if(next==Mode.INTEGRATE){sessionIntegrals=spectrum.integrals.copy();savedBaseline=plot.baseline;plot.setBaseline(.15);defineRegion=!spectrum.integrals.regions().isEmpty();integrationAction=defineRegion?"define":"select";}
        if(next==Mode.PHASE)plot.pivot=maxAmplitude(plot.start(),plot.end());
        rebuildModeBar();plot.repaint();
    }
    private void rebuildModeBar() {
        modeBar.removeAll();editingTools=null;
        if(mode==Mode.VIEW) {
            for(String name:new String[]{"SPECTRUM","INTEGRALS","FID"}) {
                JButton tab=new JButton(name);tab.setMargin(new Insets(0,8,0,8));tab.setPreferredSize(new Dimension(name.equals("SPECTRUM")?90:80,32));
                tab.addActionListener(e->{if(name.equals("INTEGRALS"))perform(()->{requireSpectrum();integralTable.load(spectrum);surfaceLayout.show(surface,"integrals");});else if(name.equals("FID"))perform(()->{requireSpectrum();if(spectrum.raw==null)spectrum.raw=new RawDataset(spectrum.experiment,spectrum.path.resolve("proc"));plot.showFid=true;surfaceLayout.show(surface,"plot");plot.repaint();});else{plot.showFid=false;surfaceLayout.show(surface,"plot");plot.repaint();}});modeBar.add(tab);
            }
        } else {
            ModeLabel label=new ModeLabel(mode==Mode.PHASE?"Interactive phase correction":"Integration",Assets.icon(mode==Mode.PHASE?"phase":"integration","text."+(mode==Mode.PHASE?"Interactive phase correction":"Integration")));
            modeBar.add(label);modeBar.add(Box.createHorizontalStrut(8));
            editingTools=new EditingTools();modeBar.add(editingTools);
            if(mode==Mode.PHASE) {
                phaseControl("0 order correction",0);phaseControl("1st order correction",1);
                tool("phase","Reset phase angles",()->{ph0=ph1=0;plot.repaint();});editingTools.separator();
                tool("phase","Add 90 degrees",()->{ph0=Numerics.wrapPhaseZero(ph0+90);plot.repaint();});tool("phase","Subtract 90 degrees",()->{ph0=Numerics.wrapPhaseZero(ph0-90);plot.repaint();});tool("phase","Add 180 degrees",()->{ph0=Numerics.wrapPhaseZero(ph0+180);plot.repaint();});editingTools.separator();
                tool("phase","Increase mouse sensitivity",()->sensitivity*=2);tool("phase","Decrease mouse sensitivity",()->sensitivity/=2);tool("phase","Reset mouse sensitivity",()->sensitivity=.2);editingTools.separator();
                tool("phase","Return & save phased spectrum",()->perform(()->{savePhaseResult();setMode(Mode.VIEW);}));
                tool("phase","Return, do NOT save",()->setMode(Mode.VIEW));
            } else {
                tool("integration","Define new region using cursor (toggle)",()->{defineRegion=!defineRegion;integrationAction=defineRegion?"define":"select";});
                tool("integration","Define new region via dialog",this::regionDialog);
                tool("integration","Cut integral in two parts (toggle)",()->{integrationAction=integrationAction.equals("cut")?"select":"cut";defineRegion=false;});editingTools.separator();
                tool("integration","Select/Deselect all regions (toggle)",()->{spectrum.integrals.toggleSelectAll();plot.repaint();});
                tool("integration","Delete selected regions",()->{if(spectrum.integrals.removeSelected()){spectrum.integrals.normalize(spectrum.real,spectrum.left,spectrum.right);selectedIntegral=-1;plot.repaint();}});
                tool("integration","Undo last region operation",()->{if(spectrum.integrals.undo()){spectrum.integrals.normalize(spectrum.real,spectrum.left,spectrum.right);selectedIntegral=-1;plot.repaint();}});editingTools.separator();
                integrationDragControl("Interactive bias correction","bias");
                integrationDragControl("Interactive slope correction","slope");
                tool("integration","Set slope and bias to zero",()->{for(int index:spectrum.integrals.selectedIndices()){var r=spectrum.integrals.regions().get(index);spectrum.integrals.replace(index,new IntegrationRegions.Region(r.left(),r.right(),0,0));}plot.repaint();});editingTools.separator();
                tool("integration","Increase mouse sensitivity",()->integrationSensitivity*=2);
                tool("integration","Decrease mouse sensitivity",()->integrationSensitivity/=2);
                tool("integration","Reset mouse sensitivity",()->integrationSensitivity=1);editingTools.separator();
                tool("integration","Integrals larger by factor 2",()->{spectrum.integrals.scaleSelected(2);plot.repaint();});
                tool("integration","Integrals smaller by factor 2",()->{spectrum.integrals.scaleSelected(.5);plot.repaint();});
                editingTools.separator();integrationDragControl("Shift integrals up/down","baseline");editingTools.separator();
                tool("integration","Return, save regions",()->perform(()->{saveResult();setMode(Mode.VIEW);}));tool("integration","Return, do NOT save regions",this::cancelIntegration);
            }
        }
        modeBar.revalidate();modeBar.repaint();
    }
    private void tool(String mode,String tooltip,Runnable action) {editingTools.addTool(mode,tooltip,action);}
    private void phaseControl(String tooltip,int order) {
        JButton button=editingTools.addTool("phase",tooltip,()->phaseOrder=order);
        new RelativeDragControl(button,()->phaseOrder=order,amount->adjustPhase(order,amount),warpPointer);
    }
    private void adjustPhase(int order,double amount) {
        var angles=Numerics.dragPhase(ph0,ph1,order,amount,1.0,sensitivity,plot.pivot,spectrum.stripStart,spectrum.ftSize,spectrum.real.length);
        ph0=Numerics.wrapPhaseZero(angles.zero());ph1=angles.first();
        plot.repaint();
    }
    private void integrationDragControl(String tooltip,String action) {
        JButton button=editingTools.addTool("integration",tooltip,()->{integrationAction=action;defineRegion=false;});
        new RelativeDragControl(button,()->{integrationAction=action;defineRegion=false;},amount->adjustIntegral(action,amount),warpPointer);
    }
    private void adjustIntegral(String action,double amount) {
        if(action.equals("baseline")){integralBaseline-=amount*.001;plot.repaint();return;}
        var selected=spectrum.integrals.selectedIndices();
        if(selected.isEmpty()){status.setText("Select an integral first");return;}
        for(int index:selected) {
            var r=spectrum.integrals.regions().get(index);
            if(selected.size()==1 && !(plot.viewLeft>r.left() && plot.viewRight<r.right())){status.setText("Integral region is outside the displayed region");return;}
            if(action.equals("bias"))spectrum.integrals.adjustBias(index,amount,integrationSensitivity);
            if(action.equals("slope"))spectrum.integrals.adjustSlope(index,amount,integrationSensitivity,spectrum.real,spectrum.left,spectrum.right);
        }
        plot.repaint();
    }
    private int maxAmplitude(int start,int end) {return Numerics.pivot(spectrum.real,spectrum.imaginary,start,end-start+1);}
    private void regionDialog() {
        JTextField left=new JTextField(),right=new JTextField();JPanel panel=new JPanel(new GridLayout(2,2,8,8));panel.add(new JLabel("Low field [ppm]"));panel.add(left);panel.add(new JLabel("High field [ppm]"));panel.add(right);
        if(JOptionPane.showConfirmDialog(this,panel,"Define integration region",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)perform(()->{addRegion(Double.parseDouble(left.getText()),Double.parseDouble(right.getText()));plot.repaint();});
    }
    private void calibrate() {
        if(selectedIntegral<0)return;String value=JOptionPane.showInputDialog(this,"Integral value","Calibrate integral",JOptionPane.PLAIN_MESSAGE);
        if(value!=null)perform(()->{spectrum.integrals.calibrate(selectedIntegral,Double.parseDouble(value),spectrum.real,spectrum.left,spectrum.right);plot.repaint();});
    }
    private void addRegion(double a,double b) {
        a=Math.min(spectrum.left,Math.max(spectrum.right,a));b=Math.min(spectrum.left,Math.max(spectrum.right,b));
        boolean first=spectrum.integrals.regions().isEmpty();spectrum.integrals.add(a,b);
        spectrum.integrals.normalize(spectrum.real,spectrum.left,spectrum.right);
        if(first)spectrum.integrals.preserveScale();
    }
    private void cancelIntegration() {if(sessionIntegrals!=null)spectrum.integrals=sessionIntegrals;setMode(Mode.VIEW);}
    private void returnWithoutSave() {if(mode==Mode.INTEGRATE)cancelIntegration();else setMode(Mode.VIEW);}
    private void savePhaseResult() throws Exception {
        requireSpectrum();Spectrum candidate=spectrum.copy();candidate.phase(ph0,ph1);saveResult(candidate);
    }
    private void saveResult() throws Exception {requireSpectrum();saveResult(spectrum);}
    private void saveResult(Spectrum candidate) throws Exception {
        Path saved=BrukerWriter.saveCurrent(candidate);spectrum=new Spectrum(saved);
        datasetLabel.setText(spectrum.label()+"  "+spectrum.path);status.setText("Saved processing "+saved.getFileName());
    }
    private void save() {perform(this::saveResult);}
    private void execute(String input) {String text=input.trim();command.setText("");switch(text){case ".ph"->setMode(Mode.PHASE);case ".int"->setMode(Mode.INTEGRATE);case ".ret"->returnWithoutSave();case ".all"->plot.full();case ".zi"->plot.zoom(.5);case ".zo"->plot.zoom(2);case ".zl"->plot.previous();case "*2"->plot.scale(2);case "/2"->plot.scale(.5);case "open"->chooseOpen();default->status.setText("Unknown command: "+text);}}
    @FunctionalInterface private interface Operation {void run() throws Exception;}
    private void perform(Operation op) {try{op.run();plot.repaint();}catch(Exception e){status.setText(e.getMessage());JOptionPane.showMessageDialog(this,e.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);}}

    private final class Plot extends JPanel {
        private BufferedImage logicalCanvas;
        private final Map<Integer,Rectangle> integralHitAreas=new LinkedHashMap<>();
        double viewLeft,viewRight,gain=1,baseline=.05;int pivot;
        ViewRanges.Range verticalRange;
        boolean showFid;String dragAction="zoom";
        int pressX,pressY,lastY,lastX,dragX=-1;
        Point cursorPoint;Double cursorValue;
        int[] cursorSamples;double relativeReference=1;
        Plot() {
            setBackground(new Color(245,245,245));
            MouseAdapter mouse=new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){
                    updateCursor(e);
                    pressX=lastX=e.getX();pressY=lastY=e.getY();
                    dragX=SwingUtilities.isLeftMouseButton(e) && (mode==Mode.INTEGRATE || drawingBounds().contains(e.getPoint()))?pressX:-1;
                    if(spectrum!=null && mode==Mode.INTEGRATE && SwingUtilities.isLeftMouseButton(e)) {
                        int hit=integralHit(e.getPoint());
                        if(hit>=0){selectedIntegral=spectrum.integrals.selectRegion(hit,ppm(e.getX()),spectrum.left-spectrum.right,e.isShiftDown(),e.isControlDown());dragX=-1;repaint();}
                    }
                    if(spectrum!=null && SwingUtilities.isRightMouseButton(e)) {
                        if(mode==Mode.PHASE && drawingBounds().contains(e.getPoint()))phasePopup(e);
                        else if(mode==Mode.INTEGRATE){selectRegion(ppm(e.getX()));JPopupMenu menu=new JPopupMenu();JMenuItem cal=new JMenuItem("Calibrate...");cal.addActionListener(x->calibrate());menu.add(cal);menu.show(Plot.this,e.getX(),e.getY());}
                    }
                }
                @Override public void mouseMoved(MouseEvent e){updateCursor(e);}
                @Override public void mouseDragged(MouseEvent e){
                    updateCursor(e);
                    if(spectrum==null || dragX<0 || (e.getModifiersEx()&InputEvent.BUTTON1_DOWN_MASK)==0)return;
                    if(mode!=Mode.INTEGRATE && !drawingBounds().contains(e.getPoint())){dragX=-1;repaint();return;}
                    dragX=e.getX();lastY=e.getY();lastX=e.getX();repaint();
                }
                @Override public void mouseReleased(MouseEvent e){
                    if(spectrum!=null && dragX>=0 && (mode==Mode.INTEGRATE || drawingBounds().contains(e.getPoint())) && SwingUtilities.isLeftMouseButton(e)) {
                        if(Math.abs(e.getX()-pressX)>3 && mode==Mode.INTEGRATE && defineRegion)addRegion(ppm(pressX),ppm(e.getX()));
                        else if(mode==Mode.INTEGRATE && integrationAction.equals("cut")){selectRegion(ppm(e.getX()));if(selectedIntegral>=0)spectrum.integrals.split(selectedIntegral,ppm(e.getX()),spectrum.left,spectrum.right,spectrum.real.length);}
                        else if(mode!=Mode.INTEGRATE && dragAction.equals("zoom")){
                            Rectangle selection=PlotSelection.horizontal(new Point(pressX,pressY),e.getPoint(),drawingBounds());
                            if(selection.width>0)setRange(ppm(selection.x),ppm(selection.x+selection.width));
                        }
                    }
                    dragX=-1;repaint();
                }
                @Override public void mouseExited(MouseEvent e){if(mode!=Mode.INTEGRATE){dragX=-1;cursorPoint=null;cursorValue=null;}repaint();}
                @Override public void mouseClicked(MouseEvent e){if(spectrum!=null && mode!=Mode.INTEGRATE && e.getClickCount()==2 && drawingBounds().contains(e.getPoint()))full();}
                @Override public void mouseWheelMoved(MouseWheelEvent e){if(spectrum==null || e.getWheelRotation()==0)return;int rotation=-e.getWheelRotation();if(e.isShiftDown())pan(5*rotation);else scale(Math.pow(1.189207115,rotation));}
            };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
        }
        private void updateCursor(MouseEvent event) {
            if(spectrum!=null && !showFid && drawingBounds().contains(event.getPoint())){cursorPoint=event.getPoint();cursorValue=ppm(event.getX());}
            else if(mode!=Mode.INTEGRATE){cursorPoint=null;cursorValue=null;}
            repaint();
        }
        private Rectangle drawingBounds(){return new Rectangle(2,top(),width(),height());}
        private void phasePopup(MouseEvent event) {
            int candidate=pickPivot(event.getX());JPopupMenu menu=new JPopupMenu();
            JMenuItem set=new JMenuItem("Set Pivot Point");set.addActionListener(e->{pivot=candidate;ph0=Numerics.wrapPhaseZero(Numerics.wrapPhaseZero(ph0));repaint();});menu.add(set);
            JMenuItem calculate=new JMenuItem("Calculate ph0");calculate.addActionListener(e->{if(pivot>=0 && pivot<spectrum.real.length){ph0=Numerics.wrapPhaseZero(Numerics.phaseAtPivot(spectrum.real[pivot],spectrum.imaginary[pivot]));repaint();}});menu.add(calculate);
            menu.show(this,event.getX()-1,event.getY()-2);
        }
        int width(){return Math.max(1,getWidth()-33);}int height(){return Math.max(1,getHeight()-top()-29);}int top(){return mode==Mode.PHASE?2+PhaseCaption.height():2;}
        double ppm(int x){return VisualNumerics.horizontal(viewLeft,viewRight,2,2+width()).physical(x);}
        double pixel(double value){return VisualNumerics.horizontal(viewLeft,viewRight,2,2+width()).screen(value,1);}
        int start(){return spectrum.index(viewLeft);}int end(){return spectrum.index(viewRight);}
        void full(){if(spectrum!=null){viewLeft=spectrum.left;viewRight=spectrum.right;cursorPoint=null;cursorValue=null;double minimum=Arrays.stream(spectrum.real).min().orElse(0),maximum=Arrays.stream(spectrum.real).max().orElse(1);if(maximum==minimum)minimum-=1;verticalRange=new ViewRanges.Range(minimum,maximum);}gain=1;setBaseline(.05);zoomHistory.clear();repaint();}
        void setBaseline(double value){baseline=value;if(verticalRange!=null)verticalRange=ViewRanges.baseline(verticalRange,value,0);repaint();}
        void scale(double factor){if(spectrum==null)return;if(showFid)gain*=factor;else if(verticalRange!=null)verticalRange=ViewRanges.scale(verticalRange,factor,0);repaint();}
        void setRange(double a,double b){zoomHistory.push(new double[]{viewLeft,viewRight});viewLeft=a;viewRight=b;repaint();}
        void zoom(double factor){zoomFactor(1.0/factor);}
        void zoomFactor(double factor){if(spectrum==null)return;var range=ViewRanges.zoom(new ViewRanges.Range(viewLeft,viewRight),factor,new ViewRanges.Range(spectrum.left,spectrum.right));if(range!=null)setRange(range.start(),range.end());}
        void pan(double amount){if(spectrum==null)return;var range=ViewRanges.pan(new ViewRanges.Range(viewLeft,viewRight),amount,width(),new ViewRanges.Range(spectrum.left,spectrum.right));setRange(range.start(),range.end());}
        void previous(){if(!zoomHistory.isEmpty()){double[] range=zoomHistory.pop();viewLeft=range[0];viewRight=range[1];repaint();}}
        int integralHit(Point point) {
            for(var entry:integralHitAreas.entrySet())if(entry.getValue().contains(point))return entry.getKey();
            return -1;
        }
        void selectRegion(double ppm){selectedIntegral=-1;var regions=spectrum.integrals.regions();for(int i=0;i<regions.size();i++)if(ppm<=regions.get(i).left() && ppm>=regions.get(i).right()){selectedIntegral=i;break;}spectrum.integrals.select(selectedIntegral);repaint();}
        int pickPivot(int px) {
            int from=start(),to=end();var compressed=VisualNumerics.compress(Arrays.copyOfRange(spectrum.real,from,to+1),from,width());
            if(compressed==null)return spectrum.index(ppm(px));
            int column=Math.max(0,Math.min(width()-1,px-2));int[] boundaries=compressed.boundaries();
            int a=Math.max(from,Math.min(to,boundaries[column])),b=Math.max(a,Math.min(to,boundaries[column+1]));
            return maxAmplitude(a,b);
        }
        void paintFid(Graphics2D g,int x,int y,int w,int h) {
            double[] raw=spectrum.raw.fid;int count=raw.length/2;
            double scale=Math.scalb(1.0,spectrum.raw.nc);double[] samples=new double[count];
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(int i=0;i<count;i++){samples[i]=raw[i*2]*scale;min=Math.min(min,samples[i]);max=Math.max(max,samples[i]);}
            double extent=max-min;if(extent==0)extent=1;
            var yMapper=VisualNumerics.vertical(-extent*.5,extent*.5,y,y+h);
            var compressed=VisualNumerics.compress(samples,0,w);
            double[] drawing=compressed==null?samples:compressed.values();
            g.setClip(x,y,w,h);g.setColor(Color.BLUE);int lastX=0,lastY=0;
            for(int i=0;i<drawing.length;i++) {
                int px=compressed==null?x+(int)(.5+i*w/(double)count):x+i/2;
                int py=yMapper.screen(drawing[i],gain);if(i>0)g.drawLine(lastX,lastY,px,py);lastX=px;lastY=py;
            }
            g.setClip(null);g.setColor(new Color(135,135,135));g.setFont(Assets.bold);
            double seconds=count/spectrum.raw.spectralWidth;
            for(int i=0;i<=4;i++){int px=x+i*w/4;g.drawLine(px,y+h,px,y+h+6);g.drawString(String.format(Locale.ROOT,"%.2f",seconds*i/4),px-12,y+h+21);}
            g.drawString("[sec]",x+w-35,y+h+21);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if(getWidth()<1 || getHeight()<1)return;
            if(logicalCanvas==null || logicalCanvas.getWidth()!=getWidth() || logicalCanvas.getHeight()!=getHeight())
                logicalCanvas=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_ARGB);
            Graphics2D canvas=logicalCanvas.createGraphics();canvas.setColor(getBackground());canvas.fillRect(0,0,getWidth(),getHeight());
            paintCanvas(canvas);canvas.dispose();
            // The source data panel paints its bitmap in logical pixels, then scales it on screen.
            Graphics2D display=(Graphics2D)graphics.create();
            display.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            display.drawImage(logicalCanvas,0,0,null);
            if(spectrum!=null && !showFid && (mode==Mode.INTEGRATE || cursorPoint!=null)) {
                String[] readout=cursorReadout();
                if(mode==Mode.INTEGRATE) {
                    double sum=0;for(var region:spectrum.integrals.regions())sum+=spectrum.integrals.calculate(region,spectrum.real,spectrum.left,spectrum.right).normalized(spectrum.integrals.normalizingFactor);
                    var format=java.text.NumberFormat.getNumberInstance(Locale.ROOT);format.setMinimumFractionDigits(4);format.setMaximumFractionDigits(4);
                    readout=new String[]{"Mouse Sensitivity: "+integrationSensitivity,readout==null?null:readout[0],"Sum = "+format.format(sum),null,null,null};
                }
                CursorMessagePainter.paint(display,readout,drawingBounds(),22,new Color(51,51,51));
            }
            if(spectrum!=null && cursorPoint!=null && drawingBounds().contains(cursorPoint) && !showFid) {
                display.setColor(mode==Mode.INTEGRATE && defineRegion?new Color(237,28,36):new Color(51,51,51));
                display.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
                display.setStroke(new BasicStroke(1,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
                display.draw(new java.awt.geom.Line2D.Float(cursorPoint.x,top(),cursorPoint.x,top()+height()));
            }
            display.dispose();
        }
        private String[] cursorReadout() {
            if(cursorPoint==null || cursorValue==null || cursorSamples==null || cursorSamples.length<2)return null;
            int column=Math.max(0,Math.min(width()-1,cursorPoint.x-2));
            int index=cursorSamples[column],count=cursorSamples[column+1]-index;
            if(index<0 || index>=spectrum.real.length)return null;
            double value=mode==Mode.PHASE?Numerics.phasePreview(spectrum.real,spectrum.imaginary,ph0,ph1,spectrum.ftSize,spectrum.stripStart,0,index+1)[index]:spectrum.real[index];
            double frequency=Double.parseDouble(spectrum.parameters.get("SF"));
            String[] labels=CursorReadout.labels(VisualNumerics.horizontal(viewLeft,viewRight,2,2+width()),drawingBounds(),cursorValue,frequency,index,count,value,relativeReference);
            if(mode==Mode.PHASE) {
                labels=Arrays.copyOf(labels,4);
                var format=new java.text.DecimalFormat("0.00",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
                labels[3]="Phase = "+format.format(Numerics.phaseAtPivot(spectrum.real[index],spectrum.imaginary[index]));
            }
            return labels;
        }
        private void paintCanvas(Graphics2D graphics) {
            Graphics2D g=(Graphics2D)graphics.create();g.setFont(Assets.regular);
            int x=2,y=top(),w=width(),h=height();g.setColor(Color.WHITE);g.fillRect(x,y,w,h);g.setColor(new Color(128,128,128));g.drawRect(x,y,w,h);
            if(spectrum==null){g.dispose();return;}
            if(mode==Mode.PHASE)PhaseCaption.paint(g,PhaseCaption.text(spectrum.ppm(pivot),sensitivity,ph0,ph1));
            g.setFont(Assets.bold.deriveFont(14f));g.setColor(new Color(135,135,135));g.drawString(spectrum.label(),6,y+16);
            if(showFid && spectrum.raw!=null){paintFid(g,x,y,w,h);g.dispose();return;}
            int from=start(),to=end();double[] data=spectrum.real;
            if(mode==Mode.PHASE){data=Numerics.phasePreview(spectrum.real,spectrum.imaginary,ph0,ph1,spectrum.ftSize,spectrum.stripStart,from,to-from+1);}
            double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
            for(double value:spectrum.real){minimum=Math.min(minimum,value);maximum=Math.max(maximum,value);}
            double extent=maximum-minimum;if(extent==0)extent=1;
            double valueLeft=verticalRange.start(),valueRight=verticalRange.end();
            var mapper=VisualNumerics.vertical(valueLeft,valueRight,y,y+h);
            double zero=mapper.screen(0,gain);
            g.setClip(x,y,w,h);g.setColor(Color.BLUE);
            double[] visible=mode==Mode.PHASE?data:Arrays.copyOfRange(data,from,to+1);
            var xMapper=VisualNumerics.horizontal(viewLeft,viewRight,x,x+w);
            SpectrumPainter.paintPanel(g,visible,from,x,y,w,h,xMapper,mapper,spectrum.left,spectrum.right,spectrum.real.length,gain);
            cursorSamples=VisualNumerics.cursorBoundaries(visible,from,w,x,xMapper,spectrum.left,spectrum.right,spectrum.real.length);
            g.setClip(null);g.setColor(new Color(128,128,128));g.setFont(Assets.bold.deriveFont(14f));
            AxisPainter.paint(g,new Rectangle(x,y+h,w,27),VisualNumerics.horizontal(viewLeft,viewRight,x,x+w),
                AxisTicks.calculate(viewLeft,viewRight,"ppm",null,true,true),Assets.bold.deriveFont(14f),new Color(128,128,128),1,0,cursorValue);
            double cy;
            try{cy=Double.parseDouble(spectrum.parameters.getOrDefault("CY","20"));}catch(NumberFormatException error){cy=20;}
            if(cy<=0)cy=20;
            double reference=maximum>0?cy/Math.abs(maximum):minimum!=0?cy/Math.abs(minimum):1;relativeReference=reference;
            AxisPainter.paintVertical(g,new Rectangle(x+w,y,27,h),valueLeft,valueRight,reference,gain,
                Assets.bold.deriveFont(14f),new Color(128,128,128),1,0);
            g.setClip(x,y,w,h);
            if(mode==Mode.PHASE){g.setColor(new Color(237,28,36));int px=(int)pixel(spectrum.ppm(pivot));if(px>x && px<x+w)g.drawLine(px,y,px,y+h);}
            integralHitAreas.clear();
            if(mode==Mode.INTEGRATE || !spectrum.integrals.regions().isEmpty()) {
                var regions=spectrum.integrals.regions();
                java.util.List<Numerics.Integral> integrals=new ArrayList<>();
                for(var r:regions)integrals.add(spectrum.integrals.calculate(r,spectrum.real,spectrum.left,spectrum.right));
                var common=IntegralCurvePainter.commonRange(integrals.stream().map(Numerics.Integral::curve).toList(),integralBaseline);
                for(int index=0;index<regions.size();index++) {
                    var r=regions.get(index);var integral=integrals.get(index);double[] curve=integral.curve();
                    double regionLeft=Math.min(r.left(),viewLeft),regionRight=Math.max(r.right(),viewRight);
                    if(regionLeft<regionRight)continue;
                    var regionMapper=VisualNumerics.horizontal(viewLeft,viewRight,x,x+w);
                    int begin=regionMapper.screen(regionLeft,1),finish=regionMapper.screen(regionRight,1),regionWidth=Math.max(1,finish-begin);
                    Rectangle regionViewport=new Rectangle(begin,y,regionWidth,h);
                    int lastPoint=IntegralCurvePainter.paint(g,curve,r.left(),r.right(),regionLeft,regionRight,regionViewport,regionMapper,
                        common,IntegralCurvePainter.reference(curve),spectrum.integrals.displayScale(index).factor());
                    integralHitAreas.put(index,IntegralLabelPainter.paint(g,new Rectangle(begin,y,regionWidth,h),integral.normalized(spectrum.integrals.normalizingFactor),4,spectrum.integrals.isSelected(index)));
                    var displayScale=spectrum.integrals.displayScale(index);
                    if(mode==Mode.INTEGRATE && (r.bias()!=0 || r.slope()!=0)) {
                        g.setClip(null);g.setColor(new Color(237,28,36));g.setFont(Assets.bold.deriveFont(14f));
                        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g.drawString("#",begin+regionWidth,Math.max(lastPoint+20,20));
                    }
                    if(!displayScale.hideFactor())IntegralLabelPainter.paintFactor(g,regionViewport,displayScale.relative());
                    g.setClip(x,y,w,h);
                }
            }
            if(dragX>=0){g.setColor(new Color(100,150,190,35));g.fillRect(Math.min(pressX,dragX),y,Math.abs(pressX-dragX),h);}
            g.dispose();
        }
    }
    public static void main(String[] args) throws Exception {
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        JFrame.setDefaultLookAndFeelDecorated(true);
        UIManager.put("Button.arc",0);UIManager.put("Component.arc",0);UIManager.put("TextComponent.arc",0);
        SwingUtilities.invokeLater(()->{Viewer viewer=new Viewer();viewer.setVisible(true);if(args.length>0)viewer.open(Path.of(args[0]));});
    }
}
