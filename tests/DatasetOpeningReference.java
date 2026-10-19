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
        Path rawExperiment=root.resolve("raw/10"),rawProcessing=rawExperiment.resolve("pdata/7");Files.createDirectories(rawProcessing);
        for(String name:new String[]{"acqus","fid"})Files.copy(experiment.resolve(name),rawExperiment.resolve(name));
        Files.copy(experiment.resolve("pdata/1/proc"),rawProcessing.resolve("proc"));
        Path rawCandidate=DatasetOpener.candidates(rawProcessing).get(0);
        if(!rawCandidate.equals(rawProcessing.toRealPath()))throw new AssertionError("Raw processing folder discovery failed");
        Spectrum rawOpened=new Spectrum(rawCandidate);
        var rawExpected=new RawDataset(rawExperiment,rawProcessing.resolve("proc")).process();
        if(!Arrays.equals(rawOpened.real,rawExpected.real()) || !Arrays.equals(rawOpened.imaginary,rawExpected.imaginary()) || rawOpened.nc!=rawExpected.nc())throw new AssertionError("Raw processing folder opening changed results");
        if(!DatasetOpener.candidates(rawProcessing.resolve("proc")).equals(List.of(rawProcessing.toRealPath())))throw new AssertionError("Raw processing parameter file discovery failed");
        Spectrum rawFileOpened=new Spectrum(rawProcessing.resolve("proc"));
        if(!Arrays.equals(rawFileOpened.real,rawOpened.real) || !Arrays.equals(rawFileOpened.imaginary,rawOpened.imaginary))throw new AssertionError("Raw parameter-file opening changed processing number or values");
        if(Files.exists(rawProcessing.resolve("1r")) || Files.exists(rawProcessing.resolve("procs")))throw new AssertionError("Opening raw data wrote processing output");
        if(!DatasetOpener.candidates(rawExperiment.resolve("pdata")).equals(List.of(rawProcessing.toRealPath())))throw new AssertionError("Opening pdata did not expose its processing numbers");
        Spectrum nonDefault=new Spectrum(rawExperiment);
        if(!nonDefault.path.equals(rawProcessing.toRealPath()) || !Arrays.equals(nonDefault.real,rawOpened.real))throw new AssertionError("Experiment without pdata/1 did not retain its available processing number");
        Path proc10=rawExperiment.resolve("pdata/10");Files.createDirectories(proc10);Files.copy(rawProcessing.resolve("proc"),proc10.resolve("proc"));
        if(!DatasetOpener.processingDirectories(rawExperiment).equals(List.of(rawProcessing,proc10)))throw new AssertionError("Processing choices are not numerically ordered");
        Path invalid=root.resolve("invalid.zip");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(invalid,StandardOpenOption.CREATE_NEW))) {zip.putNextEntry(new ZipEntry("../../escape.txt"));zip.write(1);zip.closeEntry();}
        try {DatasetOpener.candidates(invalid);throw new AssertionError("Archive path escaped extraction root");}
        catch(java.io.IOException expected) {if(!expected.getMessage().equals("Invalid archive entry path"))throw expected;}
        System.out.println("PASS loose/nested folder, raw filename, raw-only processing folder/parameter file, pdata choices, missing pdata/1 fallback and ZIP opening; "+original.real.length+" complex samples preserved");
    }
}
