import java.awt.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import javax.swing.tree.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Real filesystem events must update the visible tree without manual refresh or reopening. */
public final class BrowserLiveReference {
    private static JTree tree;
    private static DatasetBrowser browser;
    private static JFrame frame;
    private static JTextField search;
    private static DefaultMutableTreeNode find(Path path){
        var nodes=((DefaultMutableTreeNode)tree.getModel().getRoot()).depthFirstEnumeration();
        while(nodes.hasMoreElements()){
            var node=(DefaultMutableTreeNode)nodes.nextElement();
            if(node.getUserObject() instanceof DatasetBrowserModel.Entry entry && entry.path().equals(path))return node;
        }
        return null;
    }
    private static void await(BooleanSupplier condition,String message)throws Exception {
        long deadline=System.nanoTime()+15_000_000_000L;
        while(System.nanoTime()<deadline){
            boolean[] done={false};SwingUtilities.invokeAndWait(()->done[0]=condition.getAsBoolean());
            if(done[0])return;Thread.sleep(30);
        }
        throw new AssertionError(message);
    }
    private static void expand(Path path)throws Exception {
        await(()->find(path)!=null,"Missing folder "+path);
        SwingUtilities.invokeAndWait(()->tree.expandPath(new TreePath(find(path).getPath())));
    }
    private static Path dataset(Path parent,String name)throws Exception {
        Path experiment=parent.resolve(name+"/10"),processing=experiment.resolve("pdata/1");
        Files.createDirectories(processing);
        Files.writeString(experiment.resolve("acqus"),"##$PULPROG= <zg45>\n");
        Files.writeString(processing.resolve("procs"),"fixture");Files.writeString(processing.resolve("title"),"live matching spectrum");
        Files.write(processing.resolve("1r"),new byte[8]);return processing;
    }
    private static Path first(Path parent){
        var node=find(parent);if(node==null || node.getChildCount()==0)return null;
        Object entry=((DefaultMutableTreeNode)node.getChildAt(0)).getUserObject();
        return entry instanceof DatasetBrowserModel.Entry value?value.path():null;
    }
    private static boolean selected(Path path){return tree.getLastSelectedPathComponent()==find(path) && find(path)!=null;}
    public static void main(String[] args)throws Exception {
        Path work=Files.createTempDirectory(Path.of("artifacts").toAbsolutePath(),"browser-live-");
        Path data=work.resolve("data"),config=work.resolve("browser.properties");
        Path a=dataset(data,"a"),b=dataset(data,"b");long now=System.currentTimeMillis();
        try(var paths=Files.walk(data)){for(Path path:paths.toList())Files.setLastModifiedTime(path,FileTime.fromMillis(now-100_000));}
        Files.setLastModifiedTime(a.resolve("1r"),FileTime.fromMillis(now-50_000));
        Files.setLastModifiedTime(b.resolve("1r"),FileTime.fromMillis(now-25_000));
        BrowserLocations locations=new BrowserLocations(config,data);List<Path> opened=new ArrayList<>();
        Assets.load();FlatLightLaf.setup();UIManager.put("defaultFont",Assets.regular);
        SwingUtilities.invokeAndWait(()->{
            tree=new BrowserTree(new DefaultMutableTreeNode("Data"));search=new JTextField();
            browser=new DatasetBrowser(tree,(DefaultMutableTreeNode)tree.getModel().getRoot(),locations,opened::add,text->{});browser.bindSearch(search);
            frame=new JFrame("Live browser verification");frame.setSize(540,640);frame.add(search,BorderLayout.NORTH);frame.add(new JScrollPane(tree));frame.setVisible(true);
        });
        try {
            expand(data);expand(data.resolve("a"));expand(data.resolve("a/10"));
            await(()->find(a)!=null,"Initial processing number missing");
            SwingUtilities.invokeAndWait(()->{tree.setSelectionPath(new TreePath(find(a).getPath()));try{browser.sortOrder(DatasetBrowserModel.SortOrder.NEWEST);}catch(Exception error){throw new RuntimeException(error);}});
            await(()->data.resolve("b").equals(first(data)) && selected(a),"Newest file sort or selection restoration failed");
            if(new BrowserLocations(config,data).sortOrder()!=DatasetBrowserModel.SortOrder.NEWEST)throw new AssertionError("Sort preference not saved");
            // Copy a complete, nested dataset into the watched folder, retaining all its descendants.
            Path staging=work.resolve("staging");Path c=dataset(staging,"c");
            Files.move(staging.resolve("c"),data.resolve("c"));c=data.resolve("c/10/pdata/1");
            await(()->find(data.resolve("c"))!=null && selected(a),"Copied dataset did not appear automatically or selection was lost");
            expand(data.resolve("c"));expand(data.resolve("c/10"));
            Path newProcessing=c;
            await(()->find(newProcessing)!=null,"Copied processing number missing");
            FileTime parentTime=Files.getLastModifiedTime(a.getParent().getParent().getParent());
            Files.setLastModifiedTime(a.resolve("1r"),FileTime.fromMillis(now+10_000));
            await(()->data.resolve("a").equals(first(data)) && selected(a),"Existing deep spectrum edit did not reorder ancestors live");
            if(!parentTime.equals(Files.getLastModifiedTime(a.getParent().getParent().getParent())))throw new AssertionError("Fixture unexpectedly changed parent mtime");
            Path secondProc=data.resolve("c/10/pdata/2");Files.createDirectories(secondProc);
            Files.writeString(secondProc.resolve("procs"),"fixture");Files.writeString(secondProc.resolve("title"),"live matching spectrum");
            await(()->find(secondProc)!=null,"New processing number did not appear automatically");
            SwingUtilities.invokeAndWait(()->{search.setText("live matching");search.postActionEvent();});
            await(()->find(secondProc)!=null && find(a)!=null,"Active search did not finish");
            Files.move(data.resolve("c"),data.resolve("renamed"));
            Path renamed=data.resolve("renamed/10/pdata/2");
            await(()->find(renamed)!=null && find(secondProc)==null,"Rename did not refresh active search results");
            // An experiment matching through its first title intentionally includes all processing numbers.
            Files.writeString(renamed.getParent().resolve("1/title"),"parent no longer matches");
            Files.writeString(renamed.resolve("title"),"changed after directory rename");
            await(()->find(renamed)==null,"Renamed subtree stopped watching its files");
            Files.writeString(renamed.resolve("title"),"live matching again");
            await(()->find(renamed)!=null,"Renamed subtree did not return to search");
            Files.writeString(a.resolve("title"),"a different title");
            await(()->find(a)==null && find(renamed)!=null,"Changed title did not update active search matches");
            Files.move(data.resolve("renamed"),work.resolve("moved-out"));
            await(()->find(renamed)==null,"Moved-out dataset remained in active search");
            SwingUtilities.invokeAndWait(()->{search.setText("");search.postActionEvent();try{browser.sortOrder(DatasetBrowserModel.SortOrder.OLDEST);}catch(Exception error){throw new RuntimeException(error);}});
            await(()->data.resolve("b").equals(first(data)),"Oldest-first order incorrect");
            Path extra=work.resolve("extra");Files.createDirectories(extra);
            SwingUtilities.invokeAndWait(()->{try{browser.addRoot(extra);}catch(Exception error){throw new RuntimeException(error);}});
            dataset(extra,"late");
            await(()->find(extra.resolve("late"))!=null,"Added root was not watched");
            if(!opened.isEmpty())throw new AssertionError("A filesystem refresh opened a dataset");
            System.out.println("PASS real folder copy, new processing number, deep-file mtime reordering, rename/move, live title search, added root, selection, newest/oldest sorting and persisted preference; "+work);
        }finally{SwingUtilities.invokeAndWait(()->{browser.close();frame.dispose();});}
        // Closing a browser releases its actual watch service and daemon thread.
        BrowserWatcher watcher=new BrowserWatcher(List.of(data),()->{});watcher.close();
        long deadline=System.nanoTime()+2_000_000_000L;while(watcher.alive() && System.nanoTime()<deadline)Thread.sleep(20);
        if(watcher.alive())throw new AssertionError("Folder watcher remained alive after close");
    }
}
