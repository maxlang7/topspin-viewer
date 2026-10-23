import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.*;
import com.formdev.flatlaf.FlatLightLaf;

public class BrowserWorkflowReference {
    private static JTree tree;
    private static DatasetBrowser browser;
    private static JTextField search;
    private static JFrame frame;
    private static final List<Path> opened=new ArrayList<>();
    private static String status="";
    private static DefaultMutableTreeNode find(Path path) {
        var nodes=((DefaultMutableTreeNode)tree.getModel().getRoot()).depthFirstEnumeration();
        while(nodes.hasMoreElements()) {
            var node=(DefaultMutableTreeNode)nodes.nextElement();
            if(node.getUserObject() instanceof DatasetBrowserModel.Entry entry && entry.path().equals(path))return node;
        }
        return null;
    }
    private static boolean exists(Path path){return find(path)!=null;}
    private static void await(BooleanSupplier condition,String message)throws Exception {
        for(int i=0;i<200;i++) {
            boolean[] done={false};SwingUtilities.invokeAndWait(()->done[0]=condition.getAsBoolean());
            if(done[0])return;Thread.sleep(25);
        }
        throw new AssertionError(message+"; status="+status);
    }
    private static void expand(Path path)throws Exception {
        await(()->exists(path),"Missing "+path);
        SwingUtilities.invokeAndWait(()->tree.expandPath(new TreePath(find(path).getPath())));
    }
    public static void main(String[] args)throws Exception {
        Path work=Files.createTempDirectory(Path.of("artifacts").toAbsolutePath(),"browser-workflow-");
        Path data=work.resolve("data");
        for(String exp:List.of("2","10","100")) {
            Path experiment=data.resolve("sample/"+exp);Files.createDirectories(experiment);
            Files.writeString(experiment.resolve("acqus"),"fixture");Files.write(experiment.resolve("fid"),new byte[8]);
            for(String proc:List.of("2","10")) {
                Path processing=experiment.resolve("pdata/"+proc);Files.createDirectories(processing);
                Files.writeString(processing.resolve("procs"),"fixture");
                Files.writeString(processing.resolve("title"),exp.equals("10")?"Ethanol identity\nsecond line":"other spectrum");
            }
        }
        Path second=work.resolve("other");Files.createDirectories(second.resolve("nested/deep"));
        Path config=work.resolve("config/browser.properties");BrowserLocations locations=new BrowserLocations(config,data);
        locations.add(second);locations.remember(second.resolve("nested"));
        BrowserLocations loaded=new BrowserLocations(config,data);
        if(!loaded.roots().equals(List.of(data,second)) || !loaded.lastDirectory().equals(second.resolve("nested")))throw new AssertionError("Browser locations were not retained");
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        SwingUtilities.invokeAndWait(()->{
            var root=new DefaultMutableTreeNode("Data");tree=new BrowserTree(root);search=new BrowserSearchField();
            browser=new DatasetBrowser(tree,root,loaded,opened::add,text->status=text);browser.bindSearch(search);
            frame=new JFrame("Dataset browser verification");frame.setSize(520,650);frame.add(search,BorderLayout.NORTH);frame.add(new JScrollPane(tree));frame.setVisible(true);
        });
        try {
            expand(second);expand(second.resolve("nested"));expand(second.resolve("nested/deep"));
            await(()->find(second.resolve("nested/deep")).getChildCount()==0,"Empty directory did not finish loading");
            SwingUtilities.invokeAndWait(()->{if(find(second.resolve("nested/deep")).isLeaf())throw new AssertionError("Empty folder appeared as a dataset leaf");});
            Path actualCopy=Path.of("artifacts/live-fid-reference-20261019/datasets/panel-modes-fresh/10");
            var capturedEntry=DatasetBrowserModel.entry(actualCopy,false);
            if(!capturedEntry.toString().equals("10 - zg45"))throw new AssertionError("Live Windows experiment label differs");
            SwingUtilities.invokeAndWait(()->{
                var renderer=tree.getCellRenderer();
                JLabel leaf=(JLabel)renderer.getTreeCellRendererComponent(tree,new DefaultMutableTreeNode("1"),false,false,true,0,false);
                if(leaf.getIcon()!=null)throw new AssertionError("Processing row has an icon absent in live TopSpin");
            });
            expand(data);expand(data.resolve("sample"));
            await(()->exists(data.resolve("sample/100")),"Experiments not loaded");
            SwingUtilities.invokeAndWait(()->{
                var sample=find(data.resolve("sample"));
                if(!sample.getChildAt(0).toString().equals("2") || !sample.getChildAt(1).toString().equals("10") || !sample.getChildAt(2).toString().equals("100"))throw new AssertionError("Experiments are not in numerical order");
                TreePath path=new TreePath(sample.getPath());Rectangle rect=tree.getPathBounds(path);
                tree.dispatchEvent(new MouseEvent(tree,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,rect.x+12,rect.y+5,2,false,MouseEvent.BUTTON1));
                if(!opened.isEmpty())throw new AssertionError("Double-clicking a folder opened a dataset");
            });
            expand(data.resolve("sample/10"));
            Path proc=data.resolve("sample/10/pdata/10");await(()->exists(proc),"Processing numbers missing");
            SwingUtilities.invokeAndWait(()->{
                var node=find(proc);TreePath path=new TreePath(node.getPath());tree.setSelectionPath(path);tree.scrollPathToVisible(path);
                if(!node.isLeaf() || !node.toString().equals("10") || !node.getParent().toString().equals("10"))throw new AssertionError("Processing hierarchy contains full paths or pdata");
                Rectangle rect=tree.getPathBounds(path);
                tree.dispatchEvent(new MouseEvent(tree,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,rect.x+12,rect.y+5,2,false,MouseEvent.BUTTON1));
                tree.getActionMap().get("openDataset").actionPerformed(new ActionEvent(tree,0,""));
                if(!opened.equals(List.of(proc,proc)))throw new AssertionError("Double-click/Enter did not open the chosen processing number");
                search.setText("ethanol");
            });
            await(()->status.equals("Search complete") && exists(proc) && !exists(data.resolve("sample/2")),"Title search failed");
            SwingUtilities.invokeAndWait(()->search.setText("no matching title"));
            await(()->status.equals("No matching datasets"),"Empty search did not finish");
            SwingUtilities.invokeAndWait(()->{search.setText("other spectrum");search.setText("");search.postActionEvent();});
            await(()->exists(proc),"Clearing search did not restore expanded paths");
            SwingUtilities.invokeAndWait(()->{
                tree.setSelectionPath(new TreePath(find(proc).getPath()));browser.refresh();
            });
            await(()->exists(proc) && tree.getSelectionPath()!=null && tree.getLastSelectedPathComponent()==find(proc),"Refresh lost the selected processing number or expanded paths");
            SwingUtilities.invokeAndWait(()->{
                var image=new java.awt.image.BufferedImage(frame.getWidth(),frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var g=image.createGraphics();frame.paint(g);g.dispose();
                try{ImageIO.write(image,"png",work.resolve("browser.png").toFile());}catch(Exception error){throw new RuntimeException(error);}
            });
            System.out.println("PASS browser hierarchy, numeric order, leaf-only double-click, Enter, live title search, clear, refresh and persisted locations: "+work);
        }finally{SwingUtilities.invokeAndWait(()->{browser.close();frame.dispose();});}
    }
}
