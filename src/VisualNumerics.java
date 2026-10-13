/** Reconstructed display compression for uniformly sampled 1D spectra. */
public final class VisualNumerics {
    public record Mapper(long offset,double factor,boolean vertical) {
        public double physical(int screen) {return ((double)((long)screen-offset))/factor;}
        public int screen(double value,double gain) {
            int result=(int)(0.5+(double)offset+(gain*value)*factor);
            return vertical?Math.max(-1024,Math.min(16384,result)):result;
        }
    }
    public static Mapper horizontal(double start,double end,int screenMin,int screenMax) {
        double extent=end-start;
        return new Mapper((long)(.5+(screenMin*end-screenMax*start)/extent),
            (screenMax-screenMin)/extent,false);
    }
    public static Mapper vertical(double start,double end,int screenMin,int screenMax) {
        double extent=start-end;
        if(extent==0 || Math.abs(extent)<1e-10)return new Mapper((screenMax-screenMin)/2,1,true);
        return new Mapper((int)(.5+((screenMin+2)*start-(screenMax-2)*end)/extent),
            (screenMax-screenMin-4)/extent,true);
    }
    public record Compressed(double[] values,int[] boundaries) {}
    public static Compressed compress(double[] data,int start,int width) {
        if(width<1 || data.length<2 || width>=data.length)return null;
        double[] result=new double[width*2];int[] boundaries=new int[width+1];boundaries[0]=start;
        double maximum=data[0],minimum=data[0],step=width/(data.length-1.0),position=0;
        int maxIndex=0,minIndex=0,output=0,boundary=1;
        for(int i=0;i<data.length;i++) {
            if(data[i]>maximum){maximum=data[i];maxIndex=i;}
            else if(data[i]<minimum){minimum=data[i];minIndex=i;}
            if(position>=1) {
                position-=1;
                result[output++]=maxIndex<minIndex?maximum:minimum;
                result[output++]=maxIndex<minIndex?minimum:maximum;
                boundaries[boundary++]=i+start;
                maximum=minimum=data[i];maxIndex=minIndex=i;
            }
            position+=step;
        }
        if(output<result.length) {
            result[output++]=maxIndex<minIndex?maximum:minimum;
            result[output++]=maxIndex<minIndex?minimum:maximum;
            boundaries[boundary++]=data.length+start;
        }
        if(boundary>=0 && boundary<width+1)boundaries[boundary]=data.length+start;
        return new Compressed(result,boundaries);
    }
    private VisualNumerics() {}
}
