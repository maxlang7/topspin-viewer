import java.io.*;
import java.nio.file.*;
import java.util.*;

/** User-owned browser locations and last Open dialog directory. */
public final class BrowserLocations {
    private final Path file;
    private final List<Path> roots=new ArrayList<>();
    private Path lastDirectory;
    private DatasetBrowserModel.SortOrder sortOrder=DatasetBrowserModel.SortOrder.NAME;
    public BrowserLocations(Path file,Path initial) {
        this.file=file;Properties props=new Properties();
        if(Files.isRegularFile(file))try(var input=Files.newInputStream(file)){props.load(input);}catch(IOException ignored){}
        try{sortOrder=DatasetBrowserModel.SortOrder.valueOf(props.getProperty("sortOrder","NAME"));}catch(IllegalArgumentException ignored){}
        int count;try{count=Math.min(1000,Integer.parseInt(props.getProperty("root.count","0")));}catch(NumberFormatException ignored){count=0;}
        for(int i=0;i<count;i++) {
            try{Path root=Path.of(props.getProperty("root."+i,"")).toAbsolutePath().normalize();
                if(Files.isDirectory(root) && !roots.contains(root))roots.add(root);
            }catch(InvalidPathException ignored){}
        }
        if(roots.isEmpty())roots.add(initial.toAbsolutePath().normalize());
        try{lastDirectory=Path.of(props.getProperty("lastDirectory",roots.get(0).toString()));}
        catch(InvalidPathException ignored){lastDirectory=roots.get(0);}
        if(!Files.isDirectory(lastDirectory))lastDirectory=roots.get(0);
    }
    public static BrowserLocations userDefault() {
        String config=System.getenv("XDG_CONFIG_HOME");
        Path base=config==null || config.isBlank()?Path.of(System.getProperty("user.home"),".config"):Path.of(config);
        Path initial=Path.of(System.getProperty("user.home"),"Documents/IGB Lab/NMRs");
        if(!Files.isDirectory(initial))initial=Path.of(System.getProperty("user.home"));
        return new BrowserLocations(base.resolve("topspin-viewer/browser.properties"),initial);
    }
    public List<Path> roots(){return List.copyOf(roots);}
    public Path lastDirectory(){return lastDirectory;}
    public DatasetBrowserModel.SortOrder sortOrder(){return sortOrder;}
    public void sortOrder(DatasetBrowserModel.SortOrder order)throws IOException {sortOrder=order;save();}
    public boolean add(Path directory) throws IOException {
        Path root=directory.toRealPath();
        if(!Files.isDirectory(root))throw new IOException("Choose a data directory");
        if(roots.contains(root))return false;
        roots.add(root);save();return true;
    }
    public void remember(Path directory) throws IOException {
        lastDirectory=directory.toRealPath();save();
    }
    private void save() throws IOException {
        Properties props=new Properties();props.setProperty("root.count",Integer.toString(roots.size()));
        for(int i=0;i<roots.size();i++)props.setProperty("root."+i,roots.get(i).toString());
        props.setProperty("lastDirectory",lastDirectory.toString());
        props.setProperty("sortOrder",sortOrder.name());
        Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"browser-",".properties");
        try(var output=Files.newOutputStream(temp)){props.store(output,"TopSpin Viewer browser");}
        try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ignored){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
    }
}
