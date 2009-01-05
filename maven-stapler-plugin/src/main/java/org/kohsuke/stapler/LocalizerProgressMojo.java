package org.kohsuke.stapler;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Collection;

/**
 * Prints out the progress of localization.
 *
 * @author ssogabe
 * @see http://d.hatena.ne.jp/ssogabe/20081213/1229175653
 * @goal l10n-progress
 */
public class LocalizerProgressMojo extends AbstractMojo {
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        L10nProgress r = new L10nProgress();
        for( Resource root : (Collection<Resource>)project.getResources() ) {
            r.parseRecursively(new File(root.getDirectory()));
        }
        System.out.println(r.toHatena());
    }
}
