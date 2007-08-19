// TAKEN FROM GLASSFISH. TODO: FIND CDDL HEADER
package org.kohsuke.stapler;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.CompilationFailureException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.component.manager.ComponentManager;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

import com.sun.mirror.apt.AnnotationProcessorFactory;

/**
 * @goal apt-compile
 * @phase compile
 * @requiresDependencyResolution compile
 * @author Kohsuke Kawaguchi
 */
public class AptMojo extends CompilerMojo {
    /**
     * Current project.
     *
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * @component
     * @required
     * @readonly
     */
    private PluginManager pluginManager;

    /**
     * Maven doesn't let me pass a value from here to {@link AptCompiler},
     * so do so by using thread local variable. UGLY.
     */
    protected static final ThreadLocal<List<AnnotationProcessorFactory>> compilerArgs =
        new ThreadLocal<List<AnnotationProcessorFactory>>();

    public void execute() throws MojoExecutionException, CompilationFailureException {
        // overwrite the compilerId value. This seems to be the only way to
        //do so without touching the copied files.
        setField("compilerId", "stapler-apt");

        List<AnnotationProcessorFactory> old = compilerArgs.get();
        compilerArgs.set(findExtension(project, AnnotationProcessorFactory.class));
        try {
            super.execute();
        } finally {
            compilerArgs.set(old);
        }
    }

    private void setField(String name, String value) {
        try {
            Field field = AbstractCompilerMojo.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e); // impossible
        } catch (IllegalAccessException e) {
            throw new AssertionError(e); // impossible
        }
    }

    private <T> List<T> findExtension(MavenProject project, Class<T> role) throws MojoExecutionException {
        List<T> objects  = new ArrayList<T>();

        for (Plugin plugin : (List<Plugin>)project.getBuildPlugins()) {
            try {
                T o = role.cast(pluginManager.getPluginComponent(plugin, role.getName(), ""));
                if(o!=null)
                    objects.add(o);
            } catch (ComponentLookupException e) {
                // no component found in this plugin
                e.printStackTrace();
            } catch (PluginManagerException e) {
                throw new MojoExecutionException(
                    "Error getting "+role+" from the plugin '" + plugin.getKey() + "': " + e.getMessage(), e);
            }
        }

        return objects;
    }
}
