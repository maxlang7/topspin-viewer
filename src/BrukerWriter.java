import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Saves a separate processing result; existing files are never overwritten. */
public final class BrukerWriter {
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
                spectrum.experiment.getFileName().toString(),target.getFileName().toString(),
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
