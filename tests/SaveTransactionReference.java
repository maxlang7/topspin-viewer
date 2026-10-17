import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Exercise replacement and rollback only inside a newly copied acquisition. */
public class SaveTransactionReference {
    private static Set<String> names(Path folder) throws IOException {
        try(var files=Files.list(folder)){return new HashSet<>(files.map(p->p.getFileName().toString()).toList());}
    }
    private static void unchanged(Map<Path,byte[]> originals) throws IOException {
        for(var entry:originals.entrySet())if(!Arrays.equals(entry.getValue(),Files.readAllBytes(entry.getKey())))throw new AssertionError("Failed save changed "+entry.getKey());
    }
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]).toRealPath(),root=Files.createTempDirectory(Path.of("artifacts"),"save-transaction-");
        Path experiment=root.resolve("sample/10");Map<Path,byte[]> sourceOriginals=new HashMap<>();
        try(var files=Files.walk(source)) {for(Path file:files.toList()) {
            if(Files.isDirectory(file)){Files.createDirectories(experiment.resolve(source.relativize(file)));continue;}
            sourceOriginals.put(file,Files.readAllBytes(file));
            if(Set.of("intrng","integrals.txt").contains(file.getFileName().toString()))continue;
            Files.copy(file,experiment.resolve(source.relativize(file)));
        }}
        Path current=experiment.resolve("pdata/1");Spectrum original=new Spectrum(current),candidate=original.copy();
        double[] originalReal=original.real.clone();candidate.phase(90,0);candidate.integrals.add(8.3,7.8);
        if(!Arrays.equals(originalReal,original.real))throw new AssertionError("Preparing a phase save changed the original model");
        Set<String> beforeNames=names(current);Map<Path,byte[]> before=new HashMap<>();
        for(String name:beforeNames)if(Files.isRegularFile(current.resolve(name)))before.put(current.resolve(name),Files.readAllBytes(current.resolve(name)));
        int[] installs={0};
        try {
            BrukerWriter.saveCurrent(candidate,(staged,target)->{
                if(++installs[0]==4)throw new IOException("Injected failure after replacing components and creating a table");
                Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            });throw new AssertionError("Injected disk failure did not propagate");
        }catch(IOException expected){if(installs[0]!=4)throw new AssertionError("Save failed before exercising rollback",expected);}
        unchanged(before);
        if(!names(current).equals(beforeNames))throw new AssertionError("Rollback left additional processing files");
        Spectrum invalid=candidate.copy();invalid.real[0]=Double.NaN;
        try{BrukerWriter.saveCurrent(invalid);throw new AssertionError("Invalid spectrum was saved");}catch(IOException expected){unchanged(before);}
        Path saved=BrukerWriter.saveCurrent(candidate);
        if(!saved.equals(current.toRealPath()) || Files.exists(experiment.resolve("pdata/2")))throw new AssertionError("Current-processing save changed its number");
        Spectrum reopened=new Spectrum(current);long points=0;
        for(int i=0;i<candidate.real.length;i++) {
            if(Double.doubleToRawLongBits(candidate.real[i])!=Double.doubleToRawLongBits(reopened.real[i]) ||
                Double.doubleToRawLongBits(candidate.imaginary[i])!=Double.doubleToRawLongBits(reopened.imaginary[i]))throw new AssertionError("Current processing save changed a component");points+=2;
        }
        if(!Files.readString(current.resolve("integrals.txt")).contains("PROCNO = 1"))throw new AssertionError("Integral table contains the staging directory instead of the processing number");
        Path history=experiment.resolve("pdata/.topspin-viewer-history/1");Path complete;
        try(var snapshots=Files.list(history)){complete=snapshots.filter(p->{try{return Files.isRegularFile(p.resolve("state.txt")) && Files.readString(p.resolve("state.txt")).startsWith("complete\n");}catch(IOException e){throw new RuntimeException(e);}}).findFirst().orElseThrow();}
        for(var entry:before.entrySet()) {
            Path backup=complete.resolve("before").resolve(entry.getKey().getFileName());
            if(Files.exists(backup) && !Arrays.equals(entry.getValue(),Files.readAllBytes(backup)))throw new AssertionError("Original backup differs");
        }
        unchanged(sourceOriginals);
        System.out.println("PASS current-processing save, staged-data rejection, rollback after partial replacement, retained originals and "+points+" bitwise component comparisons; "+root);
    }
}
