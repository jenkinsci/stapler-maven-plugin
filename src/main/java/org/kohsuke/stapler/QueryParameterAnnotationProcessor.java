/*
 * Copyright (c) 2004-2010, Kohsuke Kawaguchi
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this list of
 *       conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
 * @deprecated
 *      Requiring 1.6 for development-time. Annotation processing is now a part of the core stapler.
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

