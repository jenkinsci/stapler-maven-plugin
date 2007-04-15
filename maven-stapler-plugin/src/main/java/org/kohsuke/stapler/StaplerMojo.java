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
