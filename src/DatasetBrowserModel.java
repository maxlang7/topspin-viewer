import java.io.IOException;
import java.nio.file.*;
import java.text.Collator;
import java.util.*;

/** Filesystem-backed dataset hierarchy. Processing numbers sit directly below experiments. */
public final class DatasetBrowserModel {
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
        if(parent.dataset())return List.of();
        Path path=parent.path();
        if(Files.isRegularFile(path.resolve("acqus")))
            return DatasetOpener.processingDirectories(path).stream().map(p->entry(p,false)).toList();
        List<Entry> result=new ArrayList<>();
        try(var stream=Files.list(path)) {
            for(Path child:stream.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))
                .filter(p->!p.getFileName().toString().startsWith(".")).sorted(DatasetBrowserModel::comparePaths).toList())
                result.add(entry(child,false));
        }
        return List.copyOf(result);
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
