import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
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
    private double integrationSensitivity=1,integralGain=1,integralBaseline=.2;
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
        JButton menu=Assets.button("phase","<html><b>Click here to access commands",this::fileMenu,66,50);
        menu.setBackground(top);nav.add(menu);
        for(String name:new String[]{"Process","Analyze"}) {
            JButton tab=new JButton(name);tab.setForeground(ink);tab.setBackground(lower);
            tab.setFont(Assets.regular.deriveFont(14f));
            tab.setPreferredSize(new Dimension(75,50));tab.setBorder(BorderFactory.createLineBorder(new Color(0,24,43)));
            tab.addActionListener(e->sections.show(second,name));nav.add(tab);
        }
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
        JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));bar.setPreferredSize(new Dimension(1,38));
        String[] tools={"Larger by factor 2","Smaller by factor 2","Smaller/larger","Smooth zoom","Shift baseline up/down","Shift spectrum left","Shift baseline to bottom","Zoom in","Zoom out","Show the last zoom","Show full spectrum"};
        for(int i=0;i<tools.length;i++) {
            final int index=i;
            JButton b=Assets.button("phase",tools[i],()->{
                switch(index) {
                    case 0 -> plot.scale(2);case 1 -> plot.scale(.5);
                    case 6 -> plot.setBaseline(.05);case 7 -> plot.zoom(.5);case 8 -> plot.zoom(2);
                    case 9 -> plot.previous();case 10 -> plot.full();
                }plot.repaint();
            },34,34);
            if(i==2)new RelativeDragControl(b,()->{},plot::scale,warpPointer,PointerGesture.Kind.VERTICAL_SCALE);
            if(i==3)new RelativeDragControl(b,()->{},factor->plot.zoomFactor(factor<1?1.025:.9756097560975611),warpPointer,PointerGesture.Kind.HORIZONTAL_SCALE);
            if(i==4)new RelativeDragControl(b,()->{},amount->plot.setBaseline(plot.baseline-amount/400.0),warpPointer);
            if(i==5)new RelativeDragControl(b,()->{},plot::pan,warpPointer,PointerGesture.Kind.HORIZONTAL_SHIFT);
            bar.add(b);
            if(i==1 || i==3 || i==6) {JSeparator s=new JSeparator(SwingConstants.VERTICAL);s.setPreferredSize(new Dimension(8,24));bar.add(s);}
        }return bar;
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
        JPanel panel=new JPanel(new BorderLayout());panel.setBackground(new Color(207,207,207));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(142,142,142)),BorderFactory.createEmptyBorder(8,8,2,8)));
        modeBar.setOpaque(false);modeBar.setPreferredSize(new Dimension(1,42));panel.add(modeBar,BorderLayout.NORTH);
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
        modeBar.removeAll();
        if(mode==Mode.VIEW) {
            for(String name:new String[]{"SPECTRUM","INTEGRALS","FID"}) {
                JButton tab=new JButton(name);tab.setMargin(new Insets(0,8,0,8));tab.setPreferredSize(new Dimension(name.equals("SPECTRUM")?90:80,32));
                tab.addActionListener(e->{if(name.equals("INTEGRALS"))perform(()->{requireSpectrum();integralTable.load(spectrum);surfaceLayout.show(surface,"integrals");});else if(name.equals("FID"))perform(()->{requireSpectrum();if(spectrum.raw==null)spectrum.raw=new RawDataset(spectrum.experiment,spectrum.path.resolve("proc"));plot.showFid=true;surfaceLayout.show(surface,"plot");plot.repaint();});else{plot.showFid=false;surfaceLayout.show(surface,"plot");plot.repaint();}});modeBar.add(tab);
            }
        } else {
            JLabel label=new JLabel(mode==Mode.PHASE?"Interactive phase correction":"Integration",Assets.icon(mode==Mode.PHASE?"phase":"integration","text."+(mode==Mode.PHASE?"Interactive phase correction":"Integration")),SwingConstants.LEFT);
            label.setOpaque(true);label.setBackground(new Color(175,201,216));label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(55,76,86)),BorderFactory.createEmptyBorder(4,12,4,12)));
            label.setPreferredSize(new Dimension(mode==Mode.PHASE?198:130,32));modeBar.add(label);modeBar.add(Box.createHorizontalStrut(8));
            if(mode==Mode.PHASE) {
                phaseControl("0 order correction",0);phaseControl("1st order correction",1);
                tool("phase","Reset phase angles",()->{ph0=ph1=0;plot.repaint();});
                tool("phase","Add 90 degrees",()->{ph0=Numerics.wrapPhaseZero(ph0+90);plot.repaint();});tool("phase","Subtract 90 degrees",()->{ph0=Numerics.wrapPhaseZero(ph0-90);plot.repaint();});tool("phase","Add 180 degrees",()->{ph0=Numerics.wrapPhaseZero(ph0+180);plot.repaint();});
                tool("phase","Increase mouse sensitivity",()->sensitivity*=2);tool("phase","Decrease mouse sensitivity",()->sensitivity/=2);tool("phase","Reset mouse sensitivity",()->sensitivity=.2);
                tool("phase","Return & save phased spectrum",()->perform(()->{savePhaseResult();setMode(Mode.VIEW);}));
                tool("phase","Return, do NOT save",()->setMode(Mode.VIEW));
            } else {
                tool("integration","Define new region using cursor (toggle)",()->{defineRegion=!defineRegion;integrationAction=defineRegion?"define":"select";});
                tool("integration","Define new region via dialog",this::regionDialog);
                tool("integration","Cut integral in two parts (toggle)",()->{integrationAction=integrationAction.equals("cut")?"select":"cut";defineRegion=false;});
                tool("integration","Delete selected regions",()->{if(selectedIntegral>=0){spectrum.integrals.remove(selectedIntegral);spectrum.integrals.normalize(spectrum.real,spectrum.left,spectrum.right);selectedIntegral=-1;plot.repaint();}});
                tool("integration","Undo last region operation",()->{if(spectrum.integrals.undo()){spectrum.integrals.normalize(spectrum.real,spectrum.left,spectrum.right);selectedIntegral=-1;plot.repaint();}});
                integrationDragControl("Interactive bias correction","bias");
                integrationDragControl("Interactive slope correction","slope");
                tool("integration","Set slope and bias to zero",()->{if(selectedIntegral>=0){var r=spectrum.integrals.regions().get(selectedIntegral);spectrum.integrals.replace(selectedIntegral,new IntegrationRegions.Region(r.left(),r.right(),0,0));plot.repaint();}});
                tool("integration","Increase mouse sensitivity",()->integrationSensitivity*=2);
                tool("integration","Decrease mouse sensitivity",()->integrationSensitivity/=2);
                tool("integration","Reset mouse sensitivity",()->integrationSensitivity=1);
                tool("integration","Integrals larger by factor 2",()->{integralGain*=2;plot.repaint();});
                tool("integration","Integrals smaller by factor 2",()->{integralGain/=2;plot.repaint();});
                integrationDragControl("Shift integrals up/down","baseline");
                JButton calibrate=new JButton("Calibrate");calibrate.addActionListener(e->calibrate());modeBar.add(calibrate);
                tool("integration","Return, save regions",()->perform(()->{saveResult();setMode(Mode.VIEW);}));tool("integration","Return, do NOT save regions",this::cancelIntegration);
            }
        }
        modeBar.revalidate();modeBar.repaint();
    }
    private void tool(String mode,String tooltip,Runnable action) {modeBar.add(Assets.button(mode,tooltip,action,30,32));}
    private void phaseControl(String tooltip,int order) {
        JButton button=Assets.button("phase",tooltip,()->phaseOrder=order,30,32);
        new RelativeDragControl(button,()->phaseOrder=order,amount->adjustPhase(order,amount),warpPointer);
        modeBar.add(button);
    }
    private void adjustPhase(int order,double amount) {
        var angles=Numerics.dragPhase(ph0,ph1,order,amount,1.0,sensitivity,plot.pivot,spectrum.stripStart,spectrum.ftSize,spectrum.real.length);
        ph0=Numerics.wrapPhaseZero(angles.zero());ph1=angles.first();
        plot.repaint();
    }
    private void integrationDragControl(String tooltip,String action) {
        JButton button=Assets.button("integration",tooltip,()->{integrationAction=action;defineRegion=false;},30,32);
        new RelativeDragControl(button,()->{integrationAction=action;defineRegion=false;},amount->adjustIntegral(action,amount),warpPointer);modeBar.add(button);
    }
    private void adjustIntegral(String action,double amount) {
        if(action.equals("baseline")){integralBaseline-=amount*.001;plot.repaint();return;}
        if(selectedIntegral<0){status.setText("Select an integral first");return;}
        var r=spectrum.integrals.regions().get(selectedIntegral);
        if(!(plot.viewLeft>r.left() && plot.viewRight<r.right())){status.setText("Integral region is outside the displayed region");return;}
        if(action.equals("bias"))spectrum.integrals.adjustBias(selectedIntegral,amount,integrationSensitivity);
        if(action.equals("slope"))spectrum.integrals.adjustSlope(selectedIntegral,amount,integrationSensitivity,spectrum.real,spectrum.left,spectrum.right);
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
        double viewLeft,viewRight,gain=1,baseline=.05;int pivot;
        ViewRanges.Range verticalRange;
        boolean showFid;String dragAction="zoom";
        int pressX,pressY,lastY,lastX,dragX=-1;
        Plot() {
            setBackground(new Color(245,245,245));
            MouseAdapter mouse=new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){
                    pressX=lastX=e.getX();pressY=lastY=e.getY();
                    dragX=SwingUtilities.isLeftMouseButton(e) && (mode==Mode.INTEGRATE || drawingBounds().contains(e.getPoint()))?pressX:-1;
                    if(spectrum!=null && SwingUtilities.isRightMouseButton(e)) {
                        if(mode==Mode.PHASE && drawingBounds().contains(e.getPoint()))phasePopup(e);
                        else if(mode==Mode.INTEGRATE){selectRegion(ppm(e.getX()));JPopupMenu menu=new JPopupMenu();JMenuItem cal=new JMenuItem("Calibrate...");cal.addActionListener(x->calibrate());menu.add(cal);menu.show(Plot.this,e.getX(),e.getY());}
                    }
                }
                @Override public void mouseDragged(MouseEvent e){
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
                        }else if(mode==Mode.INTEGRATE)selectRegion(ppm(e.getX()));
                    }
                    dragX=-1;repaint();
                }
                @Override public void mouseExited(MouseEvent e){if(mode!=Mode.INTEGRATE)dragX=-1;repaint();}
                @Override public void mouseClicked(MouseEvent e){if(spectrum!=null && mode!=Mode.INTEGRATE && e.getClickCount()==2 && drawingBounds().contains(e.getPoint()))full();}
                @Override public void mouseWheelMoved(MouseWheelEvent e){if(spectrum==null || e.getWheelRotation()==0)return;int rotation=-e.getWheelRotation();if(e.isShiftDown())pan(5*rotation);else scale(Math.pow(1.189207115,rotation));}
            };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
        }
        private Rectangle drawingBounds(){return new Rectangle(2,top(),width(),height());}
        private void phasePopup(MouseEvent event) {
            int candidate=pickPivot(event.getX());JPopupMenu menu=new JPopupMenu();
            JMenuItem set=new JMenuItem("Set Pivot Point");set.addActionListener(e->{pivot=candidate;ph0=Numerics.wrapPhaseZero(Numerics.wrapPhaseZero(ph0));repaint();});menu.add(set);
            JMenuItem calculate=new JMenuItem("Calculate ph0");calculate.addActionListener(e->{if(pivot>=0 && pivot<spectrum.real.length){ph0=Numerics.wrapPhaseZero(Numerics.phaseAtPivot(spectrum.real[pivot],spectrum.imaginary[pivot]));repaint();}});menu.add(calculate);
            menu.show(this,event.getX()-1,event.getY()-2);
        }
        int width(){return Math.max(1,getWidth()-40);}int height(){return Math.max(1,getHeight()-54);}int top(){return mode==Mode.PHASE?18:10;}
        double ppm(int x){return VisualNumerics.horizontal(viewLeft,viewRight,2,2+width()-1).physical(x);}
        double pixel(double value){return VisualNumerics.horizontal(viewLeft,viewRight,2,2+width()-1).screen(value,1);}
        int start(){return spectrum.index(viewLeft);}int end(){return spectrum.index(viewRight);}
        void full(){if(spectrum!=null){viewLeft=spectrum.left;viewRight=spectrum.right;double minimum=Arrays.stream(spectrum.real).min().orElse(0),maximum=Arrays.stream(spectrum.real).max().orElse(1);if(maximum==minimum)minimum-=1;verticalRange=new ViewRanges.Range(minimum,maximum);}gain=1;setBaseline(.05);zoomHistory.clear();repaint();}
        void setBaseline(double value){baseline=value;if(verticalRange!=null)verticalRange=ViewRanges.baseline(verticalRange,value,0);repaint();}
        void scale(double factor){if(spectrum==null)return;if(showFid)gain*=factor;else if(verticalRange!=null)verticalRange=ViewRanges.scale(verticalRange,factor,0);repaint();}
        void setRange(double a,double b){zoomHistory.push(new double[]{viewLeft,viewRight});viewLeft=a;viewRight=b;repaint();}
        void zoom(double factor){zoomFactor(1.0/factor);}
        void zoomFactor(double factor){if(spectrum==null)return;var range=ViewRanges.zoom(new ViewRanges.Range(viewLeft,viewRight),factor,new ViewRanges.Range(spectrum.left,spectrum.right));if(range!=null)setRange(range.start(),range.end());}
        void pan(double amount){if(spectrum==null)return;var range=ViewRanges.pan(new ViewRanges.Range(viewLeft,viewRight),amount,width(),new ViewRanges.Range(spectrum.left,spectrum.right));setRange(range.start(),range.end());}
        void previous(){if(!zoomHistory.isEmpty()){double[] range=zoomHistory.pop();viewLeft=range[0];viewRight=range[1];repaint();}}
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
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setFont(Assets.regular);
            int x=2,y=top(),w=width(),h=height();g.setColor(Color.WHITE);g.fillRect(x,y,w,h);g.setColor(new Color(135,135,135));g.drawRect(x,y,w,h);
            if(spectrum==null){g.dispose();return;}
            if(mode==Mode.PHASE){g.setFont(Assets.mono);g.setColor(new Color(255,70,70));java.text.DecimalFormat increment=new java.text.DecimalFormat("0.00####",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));g.drawString(String.format(Locale.ROOT,"pivot = %.2f ppm  Phase increment = %s  ph0 = %.2f  ph1 = %.2f",spectrum.ppm(pivot),increment.format(sensitivity),ph0,ph1),8,11);}
            g.setFont(Assets.bold.deriveFont(14f));g.setColor(new Color(135,135,135));g.drawString(spectrum.label(),6,y+16);
            if(mode==Mode.INTEGRATE) {
                g.setFont(Assets.mono);g.setColor(new Color(128,128,128));g.drawString("Mouse Sensitivity: "+integrationSensitivity,6,y+36);
                double sum=0;for(var region:spectrum.integrals.regions())sum+=spectrum.integrals.calculate(region,spectrum.real,spectrum.left,spectrum.right).normalized(spectrum.integrals.normalizingFactor);
                java.text.NumberFormat format=java.text.NumberFormat.getNumberInstance(Locale.ROOT);format.setMinimumFractionDigits(4);format.setMaximumFractionDigits(4);
                g.drawString("Sum = "+format.format(sum),6,y+64);
            }
            if(showFid && spectrum.raw!=null){paintFid(g,x,y,w,h);g.dispose();return;}
            int from=start(),to=end();double[] data=spectrum.real;
            if(mode==Mode.PHASE){data=Numerics.phasePreview(spectrum.real,spectrum.imaginary,ph0,ph1,spectrum.ftSize,spectrum.stripStart,from,to-from+1);}
            double minimum=Double.POSITIVE_INFINITY,maximum=Double.NEGATIVE_INFINITY;
            for(double value:spectrum.real){minimum=Math.min(minimum,value);maximum=Math.max(maximum,value);}
            double extent=maximum-minimum;if(extent==0)extent=1;
            double valueLeft=verticalRange.start(),valueRight=verticalRange.end();
            var mapper=VisualNumerics.vertical(valueLeft,valueRight,y,y+h-1);
            double zero=mapper.screen(0,gain);
            g.setClip(x,y,w,h);g.setColor(Color.BLUE);
            int previousX=0,previousY=0;boolean first=true;
            double[] visible=mode==Mode.PHASE?data:Arrays.copyOfRange(data,from,to+1);
            var xMapper=VisualNumerics.horizontal(viewLeft,viewRight,x,x+w-1);
            SpectrumPainter.paint(g,visible,from,x,y,w,h,xMapper,mapper,spectrum.left,spectrum.right,spectrum.real.length,gain);
            if(mode==Mode.PHASE){g.setColor(new Color(255,70,70));int px=(int)Math.round(pixel(spectrum.ppm(pivot)));g.drawLine(px,y,px,y+h);}
            if(mode==Mode.INTEGRATE || !spectrum.integrals.regions().isEmpty()) {
                var regions=spectrum.integrals.regions();double integralExtent=0;
                java.util.List<Numerics.Integral> integrals=new ArrayList<>();
                for(var r:regions) {
                    var area=spectrum.integrals.calculate(r,spectrum.real,spectrum.left,spectrum.right);integrals.add(area);
                    double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
                    for(double value:area.curve()){low=Math.min(low,value);high=Math.max(high,value);}
                    integralExtent=Math.max(integralExtent,high-low);
                }
                if(integralExtent==0)integralExtent=1;
                double intLeft=-integralExtent*integralBaseline,intRight=intLeft+integralExtent;
                double factor=.8*integralGain;
                double scaledExtent=intRight/factor-intLeft/factor;
                intLeft=-scaledExtent*integralBaseline;intRight=intLeft+scaledExtent;
                var intMapper=VisualNumerics.vertical(intLeft,intRight,y,y+h-1);
                java.text.DecimalFormat labelFormat=new java.text.DecimalFormat("0.0000",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT));
                for(int index=0;index<regions.size();index++) {
                    var r=regions.get(index);var integral=integrals.get(index);double[] curve=integral.curve();
                    int begin=(int)pixel(r.left()),finish=(int)pixel(r.right()),regionWidth=Math.max(1,finish-begin);
                    g.setColor(new Color(237,28,36));first=true;
                    var comp=VisualNumerics.compress(curve,0,regionWidth);
                    double[] vertices=comp==null || curve.length<2*regionWidth?curve:comp.values();
                    boolean reduced=vertices!=curve;
                    for(int i=0;i<vertices.length;i++) {
                        double physical=r.left()+(r.right()-r.left())/curve.length*i;
                        int px=reduced?begin+i/2:(int)pixel(physical),py=intMapper.screen(vertices[i],1);
                        if(!first)g.drawLine(previousX,previousY,px,py);first=false;previousX=px;previousY=py;
                    }
                    g.setClip(null);g.setFont(Assets.bold.deriveFont(14f));
                    String label=labelFormat.format(integral.normalized(spectrum.integrals.normalizingFactor));
                    int middle=(begin+finish)/2,bottom=y+h-1,labelHeight=g.getFontMetrics().stringWidth(label);
                    int half=g.getFontMetrics().getHeight()/2;
                    int[] bx={begin,begin,middle-half,middle-half,middle+half,middle+half,finish,finish};
                    int[] by={bottom,bottom-8,bottom-12,bottom-12-labelHeight,bottom-12-labelHeight,bottom-12,bottom-8,bottom};
                    g.drawPolyline(bx,by,bx.length);
                    Graphics2D text=(Graphics2D)g.create();text.translate(middle+5,bottom-14);text.rotate(-Math.PI/2);text.drawString(label,0,0);text.dispose();
                    g.setClip(x,y,w,h);
                }
            }
            if(dragX>=0){g.setColor(new Color(100,150,190,35));g.fillRect(Math.min(pressX,dragX),y,Math.abs(pressX-dragX),h);}
            g.setClip(null);g.setColor(new Color(128,128,128));g.setFont(Assets.bold.deriveFont(14f));
            double span=viewLeft-viewRight,major=Math.pow(10,Math.floor(Math.log10(span/4)));if(span/major>10)major*=5;else if(span/major>5)major*=2;
            for(double tick=Math.ceil(viewRight/major)*major;tick<=viewLeft;tick+=major){int px=(int)Math.round(pixel(tick));g.drawLine(px,y+h,px,y+h+6);String label=String.format(Locale.ROOT,major>=1?"%.0f":"%.2f",tick==0?0.0:tick);g.drawString(label,px-g.getFontMetrics().stringWidth(label)/2,y+h+21);}
            for(double tick=Math.ceil(viewRight/(major/5))*(major/5);tick<=viewLeft;tick+=major/5){int px=(int)pixel(tick);g.drawLine(px,y+h,px,y+h+4);}
            g.drawString("[ppm]",x+w-40,y+h+21);
            double reference=maximum==0?1:15.0/maximum;
            double relLeft=valueLeft*reference/gain,relRight=valueRight*reference/gain;
            double yStep=Math.pow(10,Math.floor(Math.log10((relRight-relLeft)/4)));
            if((relRight-relLeft)/yStep>10)yStep*=5;else if((relRight-relLeft)/yStep>5)yStep*=2;
            for(double tick=Math.ceil(relLeft/yStep)*yStep;tick<=relRight;tick+=yStep) {
                int py=mapper.screen(tick/reference,gain);g.drawLine(x+w,py,x+w+6,py);
                Graphics2D text=(Graphics2D)g.create();text.translate(x+w+20,py+4);text.rotate(-Math.PI/2);
                text.drawString(String.format(Locale.ROOT,yStep>=1?"%.0f":"%.2f",tick==0?0.0:tick),0,0);text.dispose();
            }
            Graphics2D units=(Graphics2D)g.create();units.translate(x+w+20,y+33);units.rotate(-Math.PI/2);units.drawString("[rel]",0,0);units.dispose();
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
