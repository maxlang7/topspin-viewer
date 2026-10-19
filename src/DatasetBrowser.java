import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

/** Lazy folder browsing and background search, with leaf-only dataset opening. */
public final class DatasetBrowser {
    private static final class Node extends DefaultMutableTreeNode {
        boolean loaded;
        boolean loading;
        Node(DatasetBrowserModel.Entry entry) {
            super(entry,!entry.dataset());loaded=entry.dataset();
            if(!loaded)add(new DefaultMutableTreeNode("Loading...",false));
        }
        DatasetBrowserModel.Entry entry(){return (DatasetBrowserModel.Entry)getUserObject();}
    }
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final DefaultTreeModel model;
    private final BrowserLocations locations;
    private final Consumer<String> status;
    private final Consumer<Path> open;
    private final Set<Path> expanded=new HashSet<>();
    private Set<Path> savedExpanded;
    private Path savedSelection;
    private String filter="";
    private int generation;
    private SwingWorker<DefaultMutableTreeNode,Void> search;

    public DatasetBrowser(JTree tree,DefaultMutableTreeNode root,BrowserLocations locations,Consumer<Path> open,Consumer<String> status) {
        this.tree=tree;this.root=root;this.locations=locations;this.open=open;this.status=status;
        model=(DefaultTreeModel)tree.getModel();
        tree.setToggleClickCount(2);tree.setToolTipText("");
        tree.addTreeWillExpandListener(new TreeWillExpandListener(){
            public void treeWillExpand(TreeExpansionEvent event){if(event.getPath().getLastPathComponent() instanceof Node node)load(node);}
            public void treeWillCollapse(TreeExpansionEvent event){}
        });
        tree.addTreeExpansionListener(new TreeExpansionListener(){
            public void treeExpanded(TreeExpansionEvent event){if(filter.isEmpty() && event.getPath().getLastPathComponent() instanceof Node node)expanded.add(node.entry().path());}
            public void treeCollapsed(TreeExpansionEvent event){if(filter.isEmpty() && event.getPath().getLastPathComponent() instanceof Node node)expanded.remove(node.entry().path());}
        });
        tree.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent event) {
                TreePath path=tree.getPathForLocation(event.getX(),event.getY());
                if(SwingUtilities.isLeftMouseButton(event) && event.getClickCount()==2 && path!=null
                    && path.getLastPathComponent() instanceof Node node && node.entry().dataset())open.accept(node.entry().path());
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"openDataset");
        tree.getActionMap().put("openDataset",new AbstractAction(){public void actionPerformed(ActionEvent event){
            if(tree.getLastSelectedPathComponent() instanceof Node node) {
                if(node.entry().dataset())open.accept(node.entry().path());
                else tree.expandPath(new TreePath(node.getPath()));
            }
        }});
        expanded.addAll(locations.roots());refresh();
    }
    public void bindSearch(JTextField field) {
        javax.swing.Timer timer=new javax.swing.Timer(180,event->filter(field.getText()));timer.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent event){timer.restart();}
            public void removeUpdate(DocumentEvent event){timer.restart();}
            public void changedUpdate(DocumentEvent event){timer.restart();}
        });
        field.addActionListener(event->{timer.stop();filter(field.getText());});
    }
    public void addRoot(Path path) throws IOException {
        if(locations.add(path)){expanded.add(path.toRealPath());refresh();}
    }
    public void opened(Spectrum spectrum) {
        Path location=spectrum.experiment==null?spectrum.path:spectrum.experiment;
        Path target=spectrum.path;
        if(locations.roots().stream().noneMatch(target::startsWith)) {
            Path parent=location.getParent();Path base=parent==null?location:parent;
            try{addRoot(base);}catch(IOException error){status.accept(error.getMessage());}
        }
        for(Path p=target;p!=null;p=p.getParent())expanded.add(p);
        if(filter.isEmpty()){savedSelection=target;restore(root);}
    }
    public void refresh() {
        generation++;if(search!=null)search.cancel(true);
        if(!filter.isEmpty()){startSearch();return;}
        Path selected=selection();if(selected!=null)savedSelection=selected;
        root.removeAllChildren();
        for(Path path:locations.roots())root.add(new Node(DatasetBrowserModel.entry(path,true)));
        model.reload();restore(root);
    }
    private Path selection(){return tree.getLastSelectedPathComponent() instanceof Node node?node.entry().path():null;}
    private void load(Node node) {
        if(node.loaded || node.loading || !filter.isEmpty())return;
        node.loading=true;int version=generation;
        new SwingWorker<java.util.List<DatasetBrowserModel.Entry>,Void>(){
            protected java.util.List<DatasetBrowserModel.Entry> doInBackground()throws IOException{return DatasetBrowserModel.children(node.entry());}
            protected void done(){
                if(version!=generation)return;
                node.loading=false;
                try {
                    var children=get();node.removeAllChildren();
                    for(var child:children)node.add(new Node(child));node.loaded=true;
                    model.nodeStructureChanged(node);restore(node);
                }catch(Exception error){
                    node.removeAllChildren();node.add(new DefaultMutableTreeNode("Cannot read directory",false));
                    model.nodeStructureChanged(node);status.accept("Cannot read "+node.entry().path());
                }
            }
        }.execute();
    }
    private void restore(DefaultMutableTreeNode start) {
        if(start instanceof Node node) {
            TreePath path=new TreePath(node.getPath());
            if(node.entry().path().equals(savedSelection)){tree.setSelectionPath(path);tree.scrollPathToVisible(path);}
            if(expanded.contains(node.entry().path()))tree.expandPath(path);
        }
        for(int i=0;i<start.getChildCount();i++)if(start.getChildAt(i) instanceof Node child)restore(child);
    }
    public void filter(String text) {
        String next=text.strip().toLowerCase(Locale.ROOT);if(next.equals(filter))return;
        if(filter.isEmpty()){savedExpanded=new HashSet<>(expanded);savedSelection=selection();}
        filter=next;
        if(filter.isEmpty() && savedExpanded!=null){expanded.clear();expanded.addAll(savedExpanded);savedExpanded=null;}
        refresh();
    }
    private void startSearch() {
        final int version=generation;final String query=filter;
        status.accept("Searching data folders...");
        search=new SwingWorker<DefaultMutableTreeNode,Void>(){
            protected DefaultMutableTreeNode doInBackground()throws IOException {
                DefaultMutableTreeNode results=new DefaultMutableTreeNode("Data");
                for(Path path:locations.roots()) {
                    Node node=find(DatasetBrowserModel.entry(path,true),false);
                    if(node!=null)results.add(node);
                }
                return results;
            }
            private Node find(DatasetBrowserModel.Entry entry,boolean inherited)throws IOException {
                if(isCancelled())throw new java.util.concurrent.CancellationException();
                boolean matches=inherited || entry.label().toLowerCase(Locale.ROOT).contains(query)
                    || DatasetBrowserModel.title(entry).toLowerCase(Locale.ROOT).contains(query);
                Node result=new Node(entry);result.removeAllChildren();result.loaded=true;
                if(!entry.dataset()) {
                    try{for(var child:DatasetBrowserModel.children(entry)){Node match=find(child,matches);if(match!=null)result.add(match);}}
                    catch(AccessDeniedException ignored){}
                }
                return matches || result.getChildCount()>0?result:null;
            }
            protected void done() {
                if(version!=generation || isCancelled())return;
                try {
                    DefaultMutableTreeNode results=get();root.removeAllChildren();
                    while(results.getChildCount()>0)root.add((DefaultMutableTreeNode)results.getFirstChild());
                    model.reload();for(int row=0;row<tree.getRowCount();row++)tree.expandRow(row);
                    status.accept(root.getChildCount()==0?"No matching datasets":"Search complete");
                }catch(Exception error){status.accept("Search failed: "+(error.getCause()==null?error:error.getCause()).getMessage());}
            }
        };search.execute();
    }
}
