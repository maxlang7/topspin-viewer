import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Watches visible data directories recursively, including subtrees copied in after startup. */
public final class BrowserWatcher implements AutoCloseable {
    private final WatchService service;
    private final Map<WatchKey,Path> keys=new HashMap<>();
    private final Set<Path> registered=new HashSet<>();
    private final Runnable changed;
    private final Thread thread;
    private volatile List<Path> roots=List.of();
    private volatile boolean closed;
    public BrowserWatcher(List<Path> roots,Runnable changed)throws IOException {
        this.service=FileSystems.getDefault().newWatchService();this.changed=changed;
        roots(roots);
        thread=new Thread(this::run,"nmr-browser-watch");thread.setDaemon(true);thread.start();
    }
    public void roots(List<Path> paths){
        roots=paths.stream().map(path->{try{return path.toRealPath();}catch(IOException error){return path.toAbsolutePath().normalize();}}).distinct().toList();
    }
    private void forgetTree(Path removed){
        var iterator=keys.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(entry.getValue().startsWith(removed)){entry.getKey().cancel();iterator.remove();}
        }
        registered.removeIf(path->path.startsWith(removed));
    }
    private void registerTree(Path root)throws IOException {
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))return;
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException {
                if(closed)return FileVisitResult.TERMINATE;
                if(!path.equals(root) && path.getFileName().toString().startsWith("."))return FileVisitResult.SKIP_SUBTREE;
                if(registered.add(path))try {
                    keys.put(path.register(service,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_DELETE,StandardWatchEventKinds.ENTRY_MODIFY),path);
                }catch(IOException error){registered.remove(path);return FileVisitResult.SKIP_SUBTREE;}
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path,IOException error){return FileVisitResult.CONTINUE;}
        });
    }
    private void run() {
        try {
            while(!closed) {
                // Also discovers newly added roots and roots recreated after removal.
                for(Path root:roots)if(!registered.contains(root) && Files.isDirectory(root)){
                    try{registerTree(root);changed.run();}catch(IOException ignored){}
                }
                WatchKey key=service.poll(500,TimeUnit.MILLISECONDS);if(key==null)continue;
                Path directory=keys.get(key);boolean relevant=false;
                for(WatchEvent<?> event:key.pollEvents()) {
                    if(event.kind()==StandardWatchEventKinds.OVERFLOW) {
                        for(Path root:roots)try{registerTree(root);}catch(IOException ignored){}
                        relevant=true;continue;
                    }
                    if(directory==null)continue;
                    Path name=(Path)event.context();
                    if(name.toString().startsWith("."))continue;
                    Path path=directory.resolve(name);
                    if(event.kind()==StandardWatchEventKinds.ENTRY_DELETE)forgetTree(path);
                    if(event.kind()==StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(path))try{registerTree(path);}catch(IOException ignored){}
                    relevant=true;
                }
                if(!key.reset()){Path path=keys.remove(key);registered.remove(path);relevant=true;}
                if(relevant)changed.run();
            }
        }catch(InterruptedException error){Thread.currentThread().interrupt();}
        catch(ClosedWatchServiceException ignored){}
    }
    @Override public void close(){closed=true;try{service.close();}catch(IOException ignored){}}
    boolean alive(){return thread.isAlive();}
}
