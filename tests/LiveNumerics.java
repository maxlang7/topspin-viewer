package oracle;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import de.bruker.nmr.jview.data.*;
import de.bruker.nmr.jview.ifc.*;
import de.bruker.nmr.prsc.datacomps.integral.Integral;
import bruker.bio.topspin.procphase.phas1d.PhasedData;

/** Test-only Java 11 oracle, loaded inside the actual running Windows TopSpin JVM. */
public class LiveNumerics {
    public static class Phase extends PhasedData {
        public Phase(double[] real,double[] imag) {
            super();dataRe=real;dataIm=imag;dataOut=new double[real.length];ftSize=real.length;stripStart=0;
        }
        @Override public IndexRangeIfc[] getCachedIndexRange(){return null;}
        @Override public synchronized double[] getDataDouble(IndexRangeIfc[] range){return Arrays.copyOfRange(dataOut,range[0].getStart(),range[0].getStart()+range[0].getNum());}
        public double[] run(int start,int count,double p0,double p1) throws Exception {return super.getDataDouble(new IndexRangeIfc[]{new IndexRange(start,count)},p0,p1);}
    }
    public static class Data extends VirtualSpecData {
        public Data(double[] data){super();setDimension(1);setSizes(new int[]{data.length});setFullSizes(new int[]{data.length});setDataDouble(data);setFullPhysicalRange(new PhysicalRangeIfc[]{new PhysicalRange(12,-2)});}
    }
    public static class Area extends Integral {
        public Area(double[] data,boolean correction,int points){super();integratedData=new Data(data);integratedIndexRange=new IndexRange(0,data.length);setDimension(1);setSizes(new int[]{data.length});setFullSizes(new int[]{data.length});setDataDouble(new double[data.length]);setBaselinCorr(correction);setBaselinPoints(points);}
    }
    private static void write(Path path,double[] values) throws Exception {
        ByteBuffer buffer=ByteBuffer.allocate(values.length*8).order(ByteOrder.LITTLE_ENDIAN);for(double v:values)buffer.putDouble(v);Files.write(path,buffer.array(),StandardOpenOption.CREATE_NEW);
    }
    public static void capture(String destination) throws Exception {
        Path root=Paths.get(destination);Files.createDirectory(root);
        Files.write(root.resolve("jvm.txt"),(System.getProperty("java.version")+"\n"+System.getProperty("java.vm.name")+"\n"+System.getProperty("os.name")).getBytes("UTF-8"));
        int n=65536;Random random=new Random(0x544f505350494eL);double[] real=new double[n],imag=new double[n];
        for(int i=0;i<n;i++){real[i]=random.nextGaussian()*1e8;imag[i]=random.nextGaussian()*1e8;}
        write(root.resolve("real.f64"),real);write(root.resolve("imag.f64"),imag);
        StringBuilder manifest=new StringBuilder();int index=0;
        for(double p0:new double[]{0,12.345,-41.5206061238053,90,180,359.99999})for(double p1:new double[]{0,-23.456,-180,720})for(int start:new int[]{0,n/3}) {
            String name="phase-"+(index++)+".f64";write(root.resolve(name),new Phase(real,imag).run(start,n-start,p0,p1));
            manifest.append(name).append('\t').append(p0).append('\t').append(p1).append('\t').append(start).append('\n');
        }
        Files.write(root.resolve("phase.tsv"),manifest.toString().getBytes("UTF-8"));
        index=0;manifest.setLength(0);
        for(boolean corrected:new boolean[]{false,true})for(int points:new int[]{1,4})for(double bias:new double[]{0,1234.567,-112345.6})for(double slope:new double[]{0,12.3,-1.234}) {
            Area area=new Area(real,corrected,points);area.calculateIntegral(bias,slope);
            String name="integral-"+(index++)+".f64";write(root.resolve(name),area.getDataDouble());
            manifest.append(name).append('\t').append(corrected).append('\t').append(points).append('\t').append(bias).append('\t').append(slope).append('\n');
        }
        Files.write(root.resolve("integral.tsv"),manifest.toString().getBytes("UTF-8"));
    }
}
