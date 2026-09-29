import com.android.apksig.ApkVerifier;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.*;

public final class VerifyPatchedApk {
    static void check(boolean value, String message) { if(!value) throw new AssertionError(message); }
    static Map<String,ClassDef> classes(String path) throws Exception {
        Map<String,ClassDef> result=new TreeMap<>();
        var dex=DexFileFactory.loadDexContainer(new File(path),Opcodes.getDefault());
        for(String name:dex.getDexEntryNames()) for(ClassDef c:dex.getEntry(name).getDexFile().getClasses())
            check(result.put(c.getType(),c)==null,"Duplicate class "+c.getType());
        return result;
    }
    static List<Instruction> code(Method method) {
        List<Instruction> result=new ArrayList<>();
        if(method.getImplementation()!=null) method.getImplementation().getInstructions().forEach(result::add);
        return result;
    }
    // Compare symbolic operands, not DEX pool indexes, which change during rebuilding.
    static String symbolic(Instruction i) {
        StringBuilder s=new StringBuilder(i.getOpcode().name().replace("_JUMBO",""));
        if(i instanceof OneRegisterInstruction r) s.append(" A").append(r.getRegisterA());
        if(i instanceof TwoRegisterInstruction r) s.append(" B").append(r.getRegisterB());
        if(i instanceof ThreeRegisterInstruction r) s.append(" C").append(r.getRegisterC());
        if(i instanceof FiveRegisterInstruction r) s.append(" regs").append(r.getRegisterCount()).append(':')
            .append(r.getRegisterC()).append(',').append(r.getRegisterD()).append(',').append(r.getRegisterE())
            .append(',').append(r.getRegisterF()).append(',').append(r.getRegisterG());
        if(i instanceof RegisterRangeInstruction r) s.append(" range").append(r.getStartRegister()).append(':').append(r.getRegisterCount());
        if(i instanceof WideLiteralInstruction r) s.append(" literal").append(r.getWideLiteral());
        if(i instanceof ReferenceInstruction r) s.append(" ref").append(r.getReference());
        return s.toString();
    }
    static Method method(ClassDef c,String name) {
        for(Method m:c.getMethods()) if(m.getName().equals(name))return m;
        throw new AssertionError("Missing "+name);
    }
    static List<String> semanticCode(List<Instruction> instructions, int skip) {
        Map<Integer,Integer> ordinals=new HashMap<>(), switchOrigins=new HashMap<>();
        int address=0,ordinal=0;
        for(int n=0;n<instructions.size();n++) {
            Instruction i=instructions.get(n);
            if(n>=skip && !i.getOpcode().name().equals("NOP")) ordinals.put(address,ordinal++);
            if(i.getOpcode().name().equals("PACKED_SWITCH")||i.getOpcode().name().equals("SPARSE_SWITCH"))
                switchOrigins.put(address+((OffsetInstruction)i).getCodeOffset(),address);
            address+=i.getCodeUnits();
        }
        List<String> result=new ArrayList<>();address=0;
        for(int n=0;n<instructions.size();n++) {
            Instruction i=instructions.get(n);
            if(n>=skip&&!i.getOpcode().name().equals("NOP")) {
                String s=symbolic(i);
                if(i instanceof OffsetInstruction o)s+=" target="+ordinals.get(address+o.getCodeOffset());
                if(i instanceof SwitchPayload p)for(var e:p.getSwitchElements())
                    s+=" case="+e.getKey()+":"+ordinals.get(switchOrigins.get(address)+e.getOffset());
                if(i instanceof ArrayPayload p)s+=" data="+p.getElementWidth()+":"+p.getArrayElements();
                result.add(s);
            }
            address+=i.getCodeUnits();
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        var verification=new ApkVerifier.Builder(new File(args[1])).setMinCheckedPlatformVersion(26).build().verify();
        check(verification.isVerified(),"APK signature: "+verification.getAllErrors());
        check(verification.isVerifiedUsingV2Scheme(),"APK v2 signing");
        Map<String,ClassDef> original=classes(args[0]),patched=classes(args[1]);
        check(patched.size()==original.size()+2,"Only two extension classes added");
        boolean is1635=original.containsKey("Lb/Hu7yEGV1H1r1bdCCcwuur8scnv1v;");
        String coroutine=is1635 ? "Lb/Hu7yEGV1H1r1bdCCcwuur8scnv1v;" : "Lb/hKoAGpeDNCeUKj;";
        String attribution=is1635 ? "Lb/V90i4gEMcGLCcnbrkYt;" : "Lb/vrhe4FRDCvomUW;";
        String fetchName=is1635 ? "A1Md0KwGu5VUxb7FHaW" : "QU0xcQ3PL93h1HkpS1";
        String attributionName=is1635 ? "Kj2k1AqaZsaVCT" : "scSVXSelHn0vGdEhMIkeOConMr";
        String bridge="Lapp/d0nj/extension/weather/NiagaraWeatherBridge;";
        check(patched.containsKey(bridge),"Bridge merged");
        check(patched.containsKey("Lapp/d0nj/extension/weather/OpenMeteoProvider;"),"Provider merged");
        Method oldMethod=method(original.get(coroutine),fetchName);
        Method newMethod=method(patched.get(coroutine),fetchName);
        List<Instruction> oldCode=code(oldMethod),newCode=code(newMethod);
        check(((NarrowLiteralInstruction)newCode.get(2)).getNarrowLiteral()==13,"Only weather branch 13");
        check(((OffsetInstruction)newCode.get(3)).getCodeOffset()==7,"Other branches jump to original entry");
        MethodReference call=(MethodReference)((ReferenceInstruction)newCode.get(4)).getReference();
        check(call.getDefiningClass().equals(bridge)&&call.getName().equals("fetch"),"Bridge call");
        RegisterRangeInstruction range=(RegisterRangeInstruction)newCode.get(4);
        check(range.getRegisterCount()==2 && range.getStartRegister()==newMethod.getImplementation().getRegisterCount()-2,"Valid p0/p1 argument registers");
        check(newCode.get(6).getOpcode().name().equals("RETURN_OBJECT"),"Weather returns before original backend");
        check(semanticCode(oldCode,0).equals(semanticCode(newCode,7)),"Original coroutine instructions/control flow preserved (excluding payload alignment NOPs)");
        int unchangedMethods=0;
        for(ClassDef c:original.values()) {
            ClassDef replacement=patched.get(c.getType());
            check(replacement!=null,"Original class retained "+c.getType());
            Iterator<? extends Method> before=c.getMethods().iterator(),after=replacement.getMethods().iterator();
            while(before.hasNext()) {
                check(after.hasNext(),"Methods retained");
                Method a=before.next(),b=after.next();
                check(a.toString().equals(b.toString()),"Method descriptor retained");
                if(c.getType().equals(coroutine)&&a.getName().equals(newMethod.getName()))continue;
                if(c.getType().equals(attribution)&&a.getName().equals(attributionName))continue;
                List<Instruction> ai=code(a),bi=code(b);
                check(semanticCode(ai,0).equals(semanticCode(bi,0)),"Untouched instructions and control flow "+a);
                unchangedMethods++;
            }
            check(!after.hasNext(),"No unexpected methods added");
        }
        int unchangedFiles=0;
        try(ZipFile a=new ZipFile(args[0]);ZipFile b=new ZipFile(args[1])) {
            var entries=a.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();
                if(e.isDirectory()||e.getName().matches("classes\\d*\\.dex")||e.getName().startsWith("META-INF/"))continue;
                ZipEntry target=b.getEntry(e.getName());check(target!=null,"Entry retained "+e.getName());
                try(InputStream x=a.getInputStream(e);InputStream y=b.getInputStream(target)) {
                    check(Arrays.equals(x.readAllBytes(),y.readAllBytes()),"Non-DEX entry unchanged "+e.getName());
                }
                unchangedFiles++;
            }
        }
        System.out.println("PASS: APK signature verified; weather-only hook; "+unchangedMethods+" other methods and "+unchangedFiles+" non-DEX files unchanged.");
    }
}

