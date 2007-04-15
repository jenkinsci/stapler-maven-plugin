package org.kohsuke.stapler;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.declaration.FieldDeclaration;
import com.sun.mirror.declaration.MemberDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.util.SimpleDeclarationVisitor;
import org.kohsuke.stapler.export.Exposed;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Handles 'ExposedBean' and 'Exposed' annotations.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ExposedBeanAnnotationProcessor implements AnnotationProcessor {
    private final AnnotationProcessorEnvironment env;

    public ExposedBeanAnnotationProcessor(AnnotationProcessorEnvironment env) {
        this.env = env;
    }

    public void process() {
        File out = new File(env.getOptions().get("-d"));

        AnnotationTypeDeclaration $exposed =
            (AnnotationTypeDeclaration) env.getTypeDeclaration(Exposed.class.getName());

        // collect all exposed properties
        Map<TypeDeclaration, List<MemberDeclaration>> props =
            new HashMap<TypeDeclaration, List<MemberDeclaration>>();

        for( Declaration d : env.getDeclarationsAnnotatedWith($exposed)) {
            MemberDeclaration md = (MemberDeclaration) d;
            TypeDeclaration owner = md.getDeclaringType();
            List<MemberDeclaration> list = props.get(owner);
            if(list==null)
                props.put(owner,list=new ArrayList<MemberDeclaration>());
            list.add(md);
        }

        for (Entry<TypeDeclaration, List<MemberDeclaration>> e : props.entrySet()) {
            final Properties javadocs = new Properties();
            for (MemberDeclaration md : e.getValue()) {
                md.accept(new SimpleDeclarationVisitor() {
                    public void visitFieldDeclaration(FieldDeclaration f) {
                        String javadoc = f.getDocComment();
                        if(javadoc!=null)
                            javadocs.put(f.getSimpleName(), javadoc);
                    }
                    public void visitMethodDeclaration(MethodDeclaration m) {
                        String javadoc = m.getDocComment();
                        if(javadoc!=null)
                            javadocs.put(m.getSimpleName()+"()", javadoc);
                    }

                    // way too tedious.
                    //private String getSignature(MethodDeclaration m) {
                    //    final StringBuilder buf = new StringBuilder(m.getSimpleName());
                    //    buf.append('(');
                    //    boolean first=true;
                    //    for (ParameterDeclaration p : m.getParameters()) {
                    //        if(first)   first = false;
                    //        else        buf.append(',');
                    //        p.getType().accept(new SimpleTypeVisitor() {
                    //            public void visitPrimitiveType(PrimitiveType pt) {
                    //                buf.append(pt.getKind().toString().toLowerCase());
                    //            }
                    //            public void visitDeclaredType(DeclaredType dt) {
                    //                buf.append(dt.getDeclaration().getQualifiedName());
                    //            }
                    //
                    //            public void visitArrayType(ArrayType at) {
                    //                at.getComponentType().accept(this);
                    //                buf.append("[]");
                    //            }
                    //
                    //            public void visitTypeVariable(TypeVariable tv) {
                    //
                    //                // TODO
                    //                super.visitTypeVariable(typeVariable);
                    //            }
                    //
                    //            public void visitVoidType(VoidType voidType) {
                    //                // TODO
                    //                super.visitVoidType(voidType);
                    //            }
                    //        });
                    //    }
                    //    buf.append(')');
                    //    // TODO
                    //    return null;
                    //}
                });
            }

            try {
                File javadocFile = new File(out, e.getKey().getQualifiedName().replace('.', '/') + ".javadoc");
                javadocFile.getParentFile().mkdirs();
                env.getMessager().printNotice("Generating "+javadocFile);
                OutputStream os = new FileOutputStream(javadocFile);
                try {
                    javadocs.store(os,null);
                } finally {
                    os.close();
                }
            } catch (IOException x) {
                env.getMessager().printError(x.toString());
            }
        }
    }
}
