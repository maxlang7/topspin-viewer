import java.nio.*;
import java.nio.file.*;
import java.util.*;
/** Full component buffers from the retained validated Java/C processor, on original reads only. */
public class RawStackOracle {
 public static void main(String[] args)throws Exception{
  Path out=Path.of(args[0]);Files.createDirectories(out);List<String> manifest=new ArrayList<>();int valid=0,rejected=0;
  for(String line:Files.readAllLines(Path.of(args[1]))){Path path=Path.of(line);try{
    var s=new Spectrum(path);int m=s.imaginary==null?0:s.imaginary.length;ByteBuffer b=ByteBuffer.allocate(24+(s.real.length+m)*8).order(ByteOrder.LITTLE_ENDIAN);b.putInt(s.real.length).putInt(m).putDouble(s.left).putDouble(s.right);for(double v:s.real)b.putDouble(v);if(s.imaginary!=null)for(double v:s.imaginary)b.putDouble(v);String name="raw-"+valid+".bin";Files.write(out.resolve(name),b.array());manifest.add(path+"\t"+name);valid++;
  }catch(Exception e){manifest.add(path+"\tREJECT");System.out.println("REFERENCE REJECT "+path+": "+e.getMessage());rejected++;}}
  Files.write(out.resolve("manifest.tsv"),manifest);System.out.println("RAW_REFERENCE valid="+valid+" rejected="+rejected);
 }
}
