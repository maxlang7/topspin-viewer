import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Writes exact processed components and retains recoverable versions on replacement. */
public final class BrukerWriter {
    @FunctionalInterface interface Installer {void install(Path staged,Path target) throws IOException;}
    public static Path saveCurrent(Spectrum spectrum) throws IOException {
        return saveCurrent(spectrum,(staged,target)->Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING));
    }
    static Path saveCurrent(Spectrum spectrum,Installer installer) throws IOException {
        return saveCurrent(spectrum,installer,false);
    }
    public static Path saveCalibration(Spectrum spectrum) throws IOException {
        return saveCurrent(spectrum,(staged,target)->Files.move(staged,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING),true);
    }
    private static Path saveCurrent(Spectrum spectrum,Installer installer,boolean calibrationOnly) throws IOException {
        if(spectrum.experiment==null || !spectrum.path.getFileName().toString().matches("[1-9][0-9]*"))
            throw new IOException("Choose an acquisition processing number before saving");
        Path target=spectrum.path;
        Path history=spectrum.experiment.resolve("pdata/.topspin-viewer-history").resolve(target.getFileName());
        Files.createDirectories(history);
        Path snapshot=Files.createTempDirectory(history,"save-"),after=Files.createDirectory(snapshot.resolve("after"));
        write(spectrum,after,target.getFileName().toString(),calibrationOnly);
        Path before=Files.createDirectory(snapshot.resolve("before"));
        List<Path> replacements;
        try(var files=Files.list(after)){replacements=files.sorted().map(Path::getFileName).toList();}
        for(Path name:replacements) {
            Path existing=target.resolve(name);
            if(Files.exists(existing,LinkOption.NOFOLLOW_LINKS)) {
                if(Files.isSymbolicLink(existing) || !Files.isRegularFile(existing))throw new IOException("Cannot replace non-regular processing file: "+existing);
                Files.copy(existing,before.resolve(name),StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        Path manifest=snapshot.resolve("state.txt");Files.writeString(manifest,"prepared\n"+target+"\n");
        List<Path> attempted=new ArrayList<>();
        try {
            for(Path name:replacements) {
                Path staging=snapshot.resolve("install-"+name);Files.copy(after.resolve(name),staging);
                attempted.add(name);installer.install(staging,target.resolve(name));
            }
            Files.writeString(manifest,"complete\n"+target+"\n");
        } catch(IOException | RuntimeException failure) {
            IOException rollbackFailure=null;
            for(Path name:attempted)try {
                Path original=before.resolve(name),current=target.resolve(name);
                if(Files.exists(original)) {
                    Path restore=snapshot.resolve("restore-"+name);Files.copy(original,restore,StandardCopyOption.COPY_ATTRIBUTES);
                    Files.move(restore,current,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                } else if(Files.exists(current))Files.move(current,snapshot.resolve("rolled-back-"+name),StandardCopyOption.ATOMIC_MOVE);
            } catch(IOException rollback){if(rollbackFailure==null)rollbackFailure=rollback;else rollbackFailure.addSuppressed(rollback);}
            IOException error=new IOException("Processing save failed; recoverable versions are in "+snapshot,failure);
            if(rollbackFailure!=null)error.addSuppressed(rollbackFailure);
            try{Files.writeString(manifest,(rollbackFailure==null?"rolled-back":"rollback-incomplete")+"\n"+target+"\n");}catch(IOException report){error.addSuppressed(report);}
            throw error;
        }
        return target;
    }
    public static Path saveNext(Spectrum spectrum) throws IOException {
        if(spectrum.experiment==null)throw new IOException("Choose an acquisition before saving a processing result");
        Path parent=spectrum.experiment.resolve("pdata");
        Files.createDirectories(parent);
        for(int procno=1;procno<Integer.MAX_VALUE;procno++) {
            Path target=parent.resolve(Integer.toString(procno));
            try {Files.createDirectory(target);}catch(FileAlreadyExistsException e){continue;}
            write(spectrum,target);return target;
        }
        throw new IOException("No available processing number");
    }
    static void write(Spectrum spectrum,Path target) throws IOException {
        write(spectrum,target,target.getFileName().toString());
    }
    private static void write(Spectrum spectrum,Path target,String processingNumber) throws IOException {
        write(spectrum,target,processingNumber,false);
    }
    private static void write(Spectrum spectrum,Path target,String processingNumber,boolean calibrationOnly) throws IOException {
        if(calibrationOnly){
            Map<String,String> axis=Map.of("SF",spectrum.parameters.get("SF"),"OFFSET",spectrum.parameters.get("OFFSET"));
            for(String name:new String[]{"proc","procs"}){
                Path source=spectrum.path.resolve(name);if(!Files.isRegularFile(source))source=spectrum.path.resolve("procs");
                Files.writeString(target.resolve(name),replace(Files.readString(source,StandardCharsets.ISO_8859_1),axis),StandardCharsets.ISO_8859_1,StandardOpenOption.CREATE_NEW);
            }
            return;
        }
        Map<String,String> params=new LinkedHashMap<>(spectrum.parameters);
        params.put("NC_proc",Integer.toString(spectrum.nc));params.put("BYTORDP","0");params.put("DTYPP","0");
        params.put("PHC0",scalar(spectrum.phc0));params.put("PHC1",scalar(spectrum.phc1));
        params.put("INTSCL",scalar(spectrum.integrals.normalizingFactor*Math.pow(2.0,spectrum.nc)));
        params.put("SI",Integer.toString(spectrum.real.length));
        writeComponent(target.resolve("1r"),spectrum.real,spectrum.nc);
        if(spectrum.imaginary!=null)writeComponent(target.resolve("1i"),spectrum.imaginary,spectrum.nc);
        for(String name:new String[]{"proc","procs"}) {
            Path source=spectrum.path.resolve(name);
            if(!Files.isRegularFile(source))source=spectrum.path.resolve("proc");
            String template=Files.readString(source,StandardCharsets.ISO_8859_1);
            Files.writeString(target.resolve(name),replace(template,params),StandardCharsets.ISO_8859_1,StandardOpenOption.CREATE_NEW);
        }
        Files.writeString(target.resolve("intrng"),spectrum.integrals.serialize(spectrum.nc),StandardOpenOption.CREATE_NEW);
        if(spectrum.experiment!=null) {
            String table=spectrum.integrals.table(spectrum.experiment.getParent().getFileName().toString(),
                spectrum.experiment.getFileName().toString(),processingNumber,
                spectrum.experiment.getParent().getParent().toString(),spectrum.real,spectrum.left,spectrum.right);
            Files.writeString(target.resolve("integrals.txt"),table.replace("\n","\r\n"),StandardOpenOption.CREATE_NEW);
        }
        Path title=spectrum.path.resolve("title");
        if(Files.isRegularFile(title))Files.copy(title,target.resolve("title"));
    }
    private static void writeComponent(Path file,double[] samples,int nc) throws IOException {
        ByteBuffer buffer=ByteBuffer.allocate(Math.multiplyExact(samples.length,4)).order(ByteOrder.LITTLE_ENDIAN);
        double inverse=Math.scalb(1.0,-nc);
        for(double sample:samples) {
            double integer=sample*inverse;
            if(integer!=Math.rint(integer) || integer<Integer.MIN_VALUE || integer>Integer.MAX_VALUE)
                throw new IOException("Spectrum sample is not representable in its integer scaling");
            buffer.putInt((int)integer);
        }
        Files.write(file,buffer.array(),StandardOpenOption.CREATE_NEW);
    }
    private static String replace(String text,Map<String,String> params) {
        Set<String> seen=new HashSet<>();Matcher match=Pattern.compile("(?m)^##\\$(\\w+)=.*$").matcher(text);
        StringBuffer result=new StringBuffer();
        while(match.find()) {
            String key=match.group(1);seen.add(key);
            match.appendReplacement(result,Matcher.quoteReplacement(params.containsKey(key)?"##$"+key+"= "+params.get(key):match.group()));
        }
        match.appendTail(result);
        String extra="";for(var entry:params.entrySet())if(!seen.contains(entry.getKey()))extra+="##$"+entry.getKey()+"= "+entry.getValue()+"\n";
        return result.toString().replace("##END=",extra+"##END=");
    }
    private static String scalar(double value) {return String.format(Locale.ROOT,"%.15g",value);}
    private BrukerWriter() {}
}
