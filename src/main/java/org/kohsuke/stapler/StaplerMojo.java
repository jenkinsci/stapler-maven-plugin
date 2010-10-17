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

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaParameter;
import com.thoughtworks.qdox.model.JavaSource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Looks for '@stapler-constructor' on a constructor.
 *
 * @author Kohsuke Kawaguchi
 * @goal stapler
 * @phase generate-resources
 * @deprecated replaced by the apt mojo.
 */
public class StaplerMojo extends AbstractMojo {

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The directory to place generated property files.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    protected File classesDirectory;


    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            // parse the source
            JavaDocBuilder builder = new JavaDocBuilder();
            for (Object o : project.getCompileSourceRoots())
                builder.addSourceTree(new File((String) o));

            // look for a constructor that has '@stapler-constructor'
            for( JavaSource js : builder.getSources() ) {
                for (JavaClass jc : js.getClasses()) {
                    for( JavaMethod jm : jc.getMethods() ) {
                        if(jm.getTagByName("stapler-constructor")!=null) {
                            if(!jm.isConstructor())
                                throw new MojoExecutionException(
                                    jc.getFullyQualifiedName()+'#'+jm.getName()+" is not a constructor");

                            StringBuffer buf = new StringBuffer();
                            for( JavaParameter p : jm.getParameters() ) {
                                if(buf.length()>0)  buf.append(',');
                                buf.append(p.getName());
                            }

                            File dst = new File(classesDirectory,jc.getFullyQualifiedName().replace('.',File.separatorChar)+".stapler");
                            dst.getParentFile().mkdirs();
                            OutputStream os = new FileOutputStream(dst);

                            Properties p = new Properties();
                            p.put("constructor",buf.toString());
                            p.store(os,null);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to process @stapler-constructor",e);
        }
    }
}
