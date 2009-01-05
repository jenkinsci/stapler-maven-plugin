package org.kohsuke.stapler;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Filer.Location;
import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Handles {@link QueryParameter} annotation and captures parameter names.
 * @author Kohsuke Kawaguchi
 */
public class QueryParameterAnnotationProcessor implements AnnotationProcessor {
    private final AnnotationProcessorEnvironment env;

    public QueryParameterAnnotationProcessor(AnnotationProcessorEnvironment env) {
        this.env = env;
    }

    public void process() {
        try {
            for( TypeDeclaration d : env.getTypeDeclarations() ) {
                if(!(d instanceof ClassDeclaration))    continue;

                ClassDeclaration cd = (ClassDeclaration) d;
                for( MethodDeclaration m : cd.getMethods()) {
                    if(hasQueryParameterAnnotation(m))
                        write(m);
                }
            }
        } catch (IOException e) {
            env.getMessager().printError(e.getMessage());
        }
    }

    private boolean hasQueryParameterAnnotation(MethodDeclaration m) {
        for (ParameterDeclaration p : m.getParameters())
            if(p.getAnnotation(QueryParameter.class)!=null)
                return true;
        return false;
    }

    private void write(MethodDeclaration m) throws IOException {
        StringBuffer buf = new StringBuffer();
        for( ParameterDeclaration p : m.getParameters() ) {
            if(buf.length()>0)  buf.append(',');
            buf.append(p.getSimpleName());
        }

        File f = new File(m.getDeclaringType().getQualifiedName().replace('.', '/')+"/"+m.getSimpleName()+ ".stapler");
        env.getMessager().printNotice("Generating "+f);
        OutputStream os = env.getFiler().createBinaryFile(Location.CLASS_TREE,"", f);

        IOUtils.write(buf,os,"UTF-8");
        os.close();
    }
}

