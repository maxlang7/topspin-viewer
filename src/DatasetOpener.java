import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Opens loose Bruker directories and archived acquisitions without changing their inputs. */
public final class DatasetOpener {
    public static List<Path> processingDirectories(Path experiment) throws IOException {
        Path pdata=experiment.resolve("pdata");
        if(!Files.isDirectory(pdata))return List.of();
        try(var files=Files.list(pdata)) {
            return files.filter(Files::isDirectory).filter(p->Files.isRegularFile(p.resolve("procs")) || isRawProcessingDirectory(p))
                .sorted(DatasetBrowserModel::comparePaths).toList();
        }
    }
    public static List<Path> candidates(Path selected) throws IOException {
        Path root=selected.toRealPath();
        if(Files.isRegularFile(root)) {
            if(root.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))root=extract(root);
            else root=root.getParent();
        }
        if(Files.isRegularFile(root.resolve("procs")) || Files.isRegularFile(root.resolve("acqus")) || isRawProcessingDirectory(root))return List.of(root);
        if(root.getFileName()!=null && root.getFileName().toString().equals("pdata") && root.getParent()!=null) {
            List<Path> processing=processingDirectories(root.getParent());if(!processing.isEmpty())return processing;
        }
        List<Path> experiments=new ArrayList<>();
        try(var files=Files.find(root,6,(p,a)->a.isRegularFile() && p.getFileName().toString().equals("acqus"))) {
            for(Path file:files.sorted(DatasetBrowserModel::comparePaths).toList())experiments.add(file.getParent());
        }
        if(experiments.isEmpty())throw new IOException("No Bruker acquisitions found in "+selected);
        return List.copyOf(experiments);
    }
    static boolean isRawProcessingDirectory(Path directory) {
        Path pdata=directory.getParent(),experiment=pdata==null?null:pdata.getParent();
        return pdata!=null && "pdata".equals(pdata.getFileName().toString()) && experiment!=null
            && Files.isRegularFile(directory.resolve("proc")) && !Files.isRegularFile(directory.resolve("1r"))
            && Files.isRegularFile(experiment.resolve("acqus")) && Files.isRegularFile(experiment.resolve("fid"));
    }
    private static Path extract(Path archive) throws IOException {
        Path cache=Path.of("build/opened-archives").toAbsolutePath();Files.createDirectories(cache);
        Path root=Files.createTempDirectory(cache,"bruker-");long total=0;int entries=0;
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(archive))) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                if(++entries>100_000)throw new IOException("Archive contains too many entries");
                String name=entry.getName().replace('\\','/');Path target=root.resolve(name).normalize();
                if(!target.startsWith(root) || name.startsWith("/") || name.matches("^[A-Za-z]:.*"))
                    throw new IOException("Invalid archive entry path");
                if(entry.isDirectory()){Files.createDirectories(target);continue;}
                Files.createDirectories(target.getParent());
                try(var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer=new byte[65536];int count;
                    while((count=zip.read(buffer))!=-1) {
                        total+=count;if(total>1_073_741_824L)throw new IOException("Archive exceeds the supported uncompressed size");
                        output.write(buffer,0,count);
                    }
                }
                zip.closeEntry();
            }
        }
        return root;
    }
    private DatasetOpener() {}
}
