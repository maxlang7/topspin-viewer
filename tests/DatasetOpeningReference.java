import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class DatasetOpeningReference {
    public static void main(String[] args) throws Exception {
        Path experiment=Path.of(args[0]).toRealPath();Path root=Files.createTempDirectory(Path.of("artifacts"),"opening-reference-");
        Path archive=root.resolve("acquisition.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(archive,StandardOpenOption.CREATE_NEW));var files=Files.walk(experiment)) {
            for(Path file:files.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry("sample/10/"+experiment.relativize(file).toString().replace('\\','/')));
                Files.copy(file,zip);zip.closeEntry();
            }
        }
        Spectrum original=new Spectrum(experiment),opened=new Spectrum(DatasetOpener.candidates(archive).get(0));
        if(original.left!=opened.left || original.right!=opened.right || original.nc!=opened.nc
            || !Arrays.equals(original.real,opened.real) || !Arrays.equals(original.imaginary,opened.imaginary))
            throw new AssertionError("Archive opening changed data");
        if(!DatasetOpener.candidates(experiment.getParent()).contains(experiment))throw new AssertionError("Nested acquisition discovery failed");
        if(!DatasetOpener.candidates(experiment.resolve("fid")).equals(List.of(experiment)))throw new AssertionError("Raw-file opening failed");
        Path invalid=root.resolve("invalid.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(invalid,StandardOpenOption.CREATE_NEW))) {zip.putNextEntry(new ZipEntry("../../escape.txt"));zip.write(1);zip.closeEntry();}
        try {DatasetOpener.candidates(invalid);throw new AssertionError("Archive path escaped extraction root");}
        catch(java.io.IOException expected) {if(!expected.getMessage().equals("Invalid archive entry path"))throw expected;}
        System.out.println("PASS loose folder, nested folder, raw filename and ZIP opening; "+original.real.length+" complex samples preserved");
    }
}
