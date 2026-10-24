import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** An opened acquisition and its current processed spectrum. */
public final class Spectrum {
    public final Path path, experiment;
    public final Map<String,String> parameters;
    public double[] real, imaginary;
    public double left,right,phc0,phc1;
    public int nc,ftSize,stripStart;
    public IntegrationRegions integrals;
    public RawDataset raw;

    private Spectrum(Spectrum source) {
        path=source.path;experiment=source.experiment;parameters=new LinkedHashMap<>(source.parameters);
        real=source.real.clone();imaginary=source.imaginary==null?null:source.imaginary.clone();
        left=source.left;right=source.right;phc0=source.phc0;phc1=source.phc1;nc=source.nc;ftSize=source.ftSize;stripStart=source.stripStart;
        integrals=source.integrals.copy();raw=source.raw;
    }
    public Spectrum copy(){return new Spectrum(this);}

    public Spectrum(Path selected) throws IOException {
        Path input=selected.toRealPath();if(Files.isRegularFile(input))input=input.getParent();
        if(Files.isRegularFile(input.resolve("procs")) || DatasetOpener.isRawProcessingDirectory(input))path=input;
        else if(Files.isRegularFile(input.resolve("acqus"))) {
            var available=DatasetOpener.processingDirectories(input);
            Path pdata=input.resolve("pdata/1");
            if(!available.contains(pdata)) {
                if(available.isEmpty())throw new IOException("No processing parameters found in "+input.resolve("pdata"));
                pdata=available.get(0);
            }
            path=pdata;
        } else throw new IOException("Choose a Bruker experiment or processed-spectrum directory");
        experiment=path.getParent()!=null && path.getParent().getFileName()!=null && "pdata".equals(path.getParent().getFileName().toString())
            ?path.getParent().getParent():null;
        if(Files.isRegularFile(path.resolve("1r"))) {
            BrukerDataset data=new BrukerDataset(path);parameters=new LinkedHashMap<>(data.parameters);
            real=data.real;imaginary=data.imaginary;left=data.left;right=data.right;
            nc=data.integer("NC_proc");ftSize=data.ftSize;stripStart=data.stripStart;
            phc0=data.number("PHC0");phc1=data.number("PHC1");
        } else {
            if(experiment==null)throw new IOException("Raw acquisition not found");
            raw=new RawDataset(experiment,path.resolve("proc"));
            parameters=new LinkedHashMap<>(raw.processing);
            processRaw();
        }
        integrals=IntegrationRegions.read(path.resolve("intrng"),nc,
            Double.parseDouble(parameters.getOrDefault("INTSCL","1")));
    }
    public void processRaw() throws IOException {
        if(raw==null) {
            if(experiment==null)throw new IOException("Raw acquisition not found");
            raw=new RawDataset(experiment,path.resolve("proc"));
        }
        var result=raw.process();real=result.real();imaginary=result.imaginary();nc=result.nc();
        left=raw.left;right=raw.right;phc0=raw.ph0;phc1=raw.ph1;ftSize=raw.size;stripStart=0;
        parameters.put("OFFSET",Double.toString(left));parameters.put("NC_proc",Integer.toString(nc));
        parameters.put("SW_p",Double.toString(raw.spectralWidth));parameters.put("FTSIZE",Integer.toString(ftSize));
        parameters.put("STSI",Integer.toString(ftSize));parameters.put("STSR","0");
        parameters.put("FT_mod","6");parameters.put("BC_mod","0");parameters.put("DTYPP","0");
    }
    public void phase(double ph0,double ph1) {
        var result=NativeKernel.savePhase(real,imaginary,ph0,ph1,nc);
        real=result.real();imaginary=result.imaginary();nc=result.nc();phc0+=ph0;phc1+=ph1;
        parameters.put("NC_proc",Integer.toString(nc));parameters.put("PHC0",Double.toString(phc0));
        parameters.put("PHC1",Double.toString(phc1));
    }
    public AxisCalibration.Result calibrateAxis(double clicked,double target) {
        if(!Double.isFinite(clicked) || !Double.isFinite(target))throw new IllegalArgumentException("Enter a finite ppm value");
        double frequency=Double.parseDouble(parameters.get("SF")),offset=Double.parseDouble(parameters.get("OFFSET"));
        var result=AxisCalibration.calculate(frequency,offset,clicked,target,false);
        if(!Double.isFinite(result.frequency()) || result.frequency()<=0 || !Double.isFinite(result.offset()))throw new IllegalArgumentException("Invalid calibration frequency");
        parameters.put("SF",AxisCalibration.scalar(result.frequency()));parameters.put("OFFSET",AxisCalibration.scalar(result.offset()));
        left=Double.parseDouble(parameters.get("OFFSET"));right=left-Double.parseDouble(parameters.get("SW_p"))/Double.parseDouble(parameters.get("SF"));raw=null;
        return result;
    }
    public String label() {
        return experiment==null?path.toString():experiment.getParent().getFileName()+"  "+experiment.getFileName()+"  "+path.getFileName();
    }
    public double ppm(int index) {return left+((right-left)/real.length)*index;}
    public int index(double ppm) {return Math.max(0,Math.min(real.length-1,Numerics.index(ppm,left,right,real.length)));}
}
