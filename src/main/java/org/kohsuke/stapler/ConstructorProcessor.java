package org.kohsuke.stapler;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Filer.Location;
import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.ConstructorDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Handles {@link DataBoundConstructor} annotation and captures parameter names.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ConstructorProcessor implements AnnotationProcessor {
    private final AnnotationProcessorEnvironment env;

    public ConstructorProcessor(AnnotationProcessorEnvironment env) {
        this.env = env;
    }

    public void process() {
        try {
            for( TypeDeclaration d : env.getTypeDeclarations() ) {
                if(!(d instanceof ClassDeclaration))    continue;

                ClassDeclaration cd = (ClassDeclaration) d;
                for( ConstructorDeclaration c : cd.getConstructors()) {
                    if(c.getAnnotation(DataBoundConstructor.class)!=null) {
                        write(c);
                        continue;
                    }
                    String javadoc = c.getDocComment();
                    if(javadoc!=null && javadoc.contains("@stapler-constructor")) {
                        write(c);
                    }
                }
            }
        } catch (IOException e) {
            env.getMessager().printError(e.getMessage());
        }
    }

    private void write(ConstructorDeclaration c) throws IOException {
        StringBuffer buf = new StringBuffer();
        for( ParameterDeclaration p : c.getParameters() ) {
            if(buf.length()>0)  buf.append(',');
            buf.append(p.getSimpleName());
        }

        File f = new File(c.getDeclaringType().getQualifiedName().replace('.', '/') + ".stapler");
        env.getMessager().printNotice("Generating "+f);
        OutputStream os = env.getFiler().createBinaryFile(Location.CLASS_TREE,"", f);

        Properties p = new Properties();
        p.put("constructor",buf.toString());
        p.store(os,null);
        os.close();
    }
}
