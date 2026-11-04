import java.nio.file.*;
/** Independent Java reader digest for differential checking of every sample and axis bit. */
public class RustReaderParity {
  static long digest(double[] values) {
    long hash=0xcbf29ce484222325L;
    if(values!=null)for(double v:values){hash^=Double.doubleToRawLongBits(v);hash*=0x100000001b3L;}
    return hash;
  }
  public static void main(String[] args)throws Exception {
    for(String arg:args){try {var d=new BrukerDataset(Path.of(arg));System.out.printf("%s\t%d\t%016x\t%016x\t%016x\t%016x%n",d.path,d.real.length,Double.doubleToRawLongBits(d.left),Double.doubleToRawLongBits(d.right),digest(d.real),digest(d.imaginary));}catch(Exception error){System.out.println(Path.of(arg).toRealPath()+"\tREJECT");}}
  }
}
