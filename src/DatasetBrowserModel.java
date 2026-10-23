import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.Collator;
import java.util.*;

/** Filesystem-backed dataset hierarchy. Processing numbers sit directly below experiments. */
public final class DatasetBrowserModel {
    public enum SortOrder {
        NAME("Name"), NEWEST("Modified: newest first"), OLDEST("Modified: oldest first");
        public final String label;
        SortOrder(String label){this.label=label;}
    }
    public record Entry(Path path,String label,boolean dataset,String pulseProgram) {
        @Override public String toString(){return pulseProgram.isEmpty()?label:label+" - "+pulseProgram;}
    }
    public static int compareNames(String first,String second) {
        try{return Integer.compare(Integer.parseInt(first),Integer.parseInt(second));}
        catch(NumberFormatException ignored){return Collator.getInstance().compare(first.toLowerCase(),second.toLowerCase());}
    }
    public static int comparePaths(Path first,Path second) {
        Iterator<Path> a=first.iterator(),b=second.iterator();
        while(a.hasNext()) {
            if(!b.hasNext())return 1;
            int result=compareNames(a.next().toString(),b.next().toString());
            if(result!=0)return result;
        }
        return b.hasNext()?-1:0;
    }
    public static Entry entry(Path path,boolean root) {
        boolean dataset=Files.isRegularFile(path.resolve("procs")) || DatasetOpener.isRawProcessingDirectory(path);
        String pulseProgram="";
        if(!root && Files.isRegularFile(path.resolve("acqus")))try {
            pulseProgram=BrukerDataset.readParameters(path.resolve("acqus")).getOrDefault("PULPROG","");
            if(pulseProgram.startsWith("<") && pulseProgram.endsWith(">"))pulseProgram=pulseProgram.substring(1,pulseProgram.length()-1);
        }catch(IOException ignored){}
        return new Entry(path,root?path.toString():path.getFileName().toString(),dataset,pulseProgram);
    }
    public static List<Entry> children(Entry parent) throws IOException {
        return children(parent,SortOrder.NAME);
    }
    public static List<Entry> children(Entry parent,SortOrder order) throws IOException {
        if(parent.dataset())return List.of();
        Path path=parent.path();
        if(Files.isRegularFile(path.resolve("acqus")))
            return sorted(DatasetOpener.processingDirectories(path).stream().map(p->entry(p,false)).toList(),order);
        List<Entry> result=new ArrayList<>();
        try(var stream=Files.list(path)) {
            for(Path child:stream.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p->!p.getFileName().toString().startsWith(".")).sorted(DatasetBrowserModel::comparePaths).toList())
                result.add(entry(child,false));
        }
        return sorted(result,order);
    }
    private static List<Entry> sorted(List<Entry> entries,SortOrder order) throws IOException {
        if(order==SortOrder.NAME)return List.copyOf(entries);
        Map<Path,FileTime> times=new HashMap<>();
        for(Entry entry:entries)times.put(entry.path(),modified(entry.path()));
        List<Entry> result=new ArrayList<>(entries);
        result.sort((a,b)->{
            int comparison=times.get(a.path()).compareTo(times.get(b.path()));
            if(order==SortOrder.NEWEST)comparison=-comparison;
            return comparison==0?comparePaths(a.path(),b.path()):comparison;
        });
        return List.copyOf(result);
    }
    /** Directory mtimes miss changes to existing spectra. Include visible descendants. */
    static FileTime modified(Path path) throws IOException {
        FileTime[] newest={FileTime.fromMillis(0)};
        Files.walkFileTree(path,new SimpleFileVisitor<>(){
            private void include(BasicFileAttributes attributes){if(attributes.lastModifiedTime().compareTo(newest[0])>0)newest[0]=attributes.lastModifiedTime();}
            @Override public FileVisitResult preVisitDirectory(Path directory,BasicFileAttributes attributes){
                if(!directory.equals(path) && directory.getFileName().toString().startsWith("."))return FileVisitResult.SKIP_SUBTREE;
                include(attributes);return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attributes){if(!file.getFileName().toString().startsWith("."))include(attributes);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFileFailed(Path file,IOException error){return FileVisitResult.CONTINUE;}
        });
        return newest[0];
    }
    public static String title(Entry entry) {
        Path title=entry.path().resolve("title");
        if(!Files.isRegularFile(title) && Files.isRegularFile(entry.path().resolve("acqus"))) {
            try {
                List<Path> processing=DatasetOpener.processingDirectories(entry.path());
                if(!processing.isEmpty())title=processing.get(0).resolve("title");
            }catch(IOException ignored){}
        }
        try {return Files.isRegularFile(title)?Files.readString(title).strip().replaceAll("\\s+"," "):"";}
        catch(IOException ignored){return "";}
    }
    private DatasetBrowserModel(){}
}
