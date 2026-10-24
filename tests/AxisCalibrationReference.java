import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/** Executes unchanged arithmetic instructions lifted from the installed Calib.calib1d method. */
public final class AxisCalibrationReference {
    static List<AbstractInsnNode> code;
    static void method(ClassWriter writer,String name,boolean hz){
        MethodVisitor mv=writer.visitMethod(ACC_PUBLIC|ACC_STATIC,name,"(DDDD)[D",null,null);mv.visitCode();
        mv.visitVarInsn(DLOAD,0);mv.visitVarInsn(DSTORE,13);mv.visitVarInsn(DLOAD,2);mv.visitVarInsn(DSTORE,11);
        mv.visitVarInsn(DLOAD,4);mv.visitVarInsn(DSTORE,1);
        mv.visitInsn(ICONST_1);mv.visitIntInsn(NEWARRAY,T_DOUBLE);mv.visitInsn(DUP);mv.visitInsn(ICONST_0);mv.visitVarInsn(DLOAD,6);mv.visitInsn(DASTORE);mv.visitVarInsn(ASTORE,19);
        int start=-1,end=-1;
        for(int i=0;i<code.size()-3;i++){
            if(code.get(i) instanceof VarInsnNode v && v.getOpcode()==DLOAD && v.var==13 && code.get(i+1).getOpcode()==(hz?DLOAD:DCONST_1)){
                if(hz && (!(code.get(i+1) instanceof VarInsnNode next) || next.var!=1))continue;
                start=i;break;
            }
        }
        if(start<0)throw new AssertionError("Original calibration block missing");
        for(int i=start;i<code.size();i++){
            var instruction=code.get(i);
            if(instruction instanceof JumpInsnNode || instruction instanceof VarInsnNode v && v.getOpcode()==DSTORE && v.var==5){
                if(instruction instanceof VarInsnNode)instruction.accept(mv);
                end=i;break;
            }
            instruction.accept(mv);
        }
        if(end<0)throw new AssertionError("Original block did not end at offset calculation");
        int delta=-1;
        for(int i=end;i<code.size()-3;i++)if(code.get(i) instanceof VarInsnNode a && a.getOpcode()==DLOAD && a.var==5 && code.get(i+1) instanceof VarInsnNode b && b.var==11 && code.get(i+2).getOpcode()==DSUB && code.get(i+3) instanceof VarInsnNode c && c.var==9){delta=i;break;}
        if(delta<0)throw new AssertionError("Original shift block missing");
        for(int i=delta;i<delta+4;i++)code.get(i).accept(mv);
        mv.visitInsn(ICONST_3);mv.visitIntInsn(NEWARRAY,T_DOUBLE);
        for(int i=0;i<3;i++){mv.visitInsn(DUP);mv.visitInsn(ICONST_0+i);mv.visitVarInsn(DLOAD,new int[]{7,5,9}[i]);mv.visitInsn(DASTORE);}
        mv.visitInsn(ARETURN);mv.visitMaxs(0,0);mv.visitEnd();
    }
    public static void main(String[] args)throws Throwable {
        Path vendor=Path.of(args[0]);byte[] original;
        try(JarFile jar=new JarFile(vendor.resolve("classes/lib/top_base.jar").toFile())){original=jar.getInputStream(jar.getJarEntry("de/bruker/nmr/pr/util/Calib.class")).readAllBytes();}
        ClassNode node=new ClassNode();new ClassReader(original).accept(node,0);
        MethodNode nativeMethod=node.methods.stream().filter(m->m.name.equals("calib1d")).findFirst().orElseThrow();
        code=new ArrayList<>();for(var instruction:nativeMethod.instructions)if(instruction.getOpcode()>=0)code.add(instruction);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(V1_8,ACC_PUBLIC,"OriginalAxisArithmetic",null,"java/lang/Object",null);
        method(writer,"ppm",false);method(writer,"hertz",true);writer.visitEnd();
        Class<?> oracle=MethodHandles.lookup().defineClass(writer.toByteArray());Random random=new Random(0x43414c4942415849L);int cases=0;
        for(boolean hz:new boolean[]{false,true})for(int i=0;i<10000;i++){
            double sf=40+random.nextDouble()*1200,offset=random.nextDouble()*1000-500,clicked=random.nextDouble()*200-100,target=random.nextDouble()*200-100;
            double[] expected=(double[])oracle.getMethod(hz?"hertz":"ppm",double.class,double.class,double.class,double.class).invoke(null,sf,offset,clicked,target);
            var result=AxisCalibration.calculate(sf,offset,clicked,target,hz);double[] actual={result.frequency(),result.offset(),result.shift()};
            for(int j=0;j<3;j++)if(Double.doubleToRawLongBits(actual[j])!=Double.doubleToRawLongBits(expected[j]))throw new AssertionError("Calibration arithmetic differs");cases++;
        }
        Class<?> dialog=Class.forName("de.bruker.nmr.pr.util.CalibAxisDialog");Field singleton=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");singleton.setAccessible(true);var unsafe=(sun.misc.Unsafe)singleton.get(null);Object instance=unsafe.allocateInstance(dialog);
        Field def=dialog.getDeclaredField("def"),res=dialog.getDeclaredField("res");def.setAccessible(true);res.setAccessible(true);
        for(int i=0;i<2000;i++){
            double a=random.nextDouble()*10,b=i%3==0?a:a+(random.nextDouble()-.5)*.0001;
            def.set(null,new double[]{a});res.set(instance,new double[]{b});
            if((boolean)dialog.getMethod("isModification").invoke(instance)!=AxisCalibration.changed(a,b))throw new AssertionError("Original dialog threshold differs");
        }
        System.out.println("PASS "+cases+" original lifted JVM calibration blocks, 60000 bitwise values, ppm and Hz; 2000 original dialog threshold decisions; class SHA256="+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(original)));
    }
}
