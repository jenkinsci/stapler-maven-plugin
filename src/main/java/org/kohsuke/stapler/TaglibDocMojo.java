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

import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.output.StreamSerializer;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.codehaus.doxia.sink.Sink;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.jvnet.maven.jellydoc.Attribute;
import org.jvnet.maven.jellydoc.JellydocMojo;
import org.jvnet.maven.jellydoc.Library;
import org.jvnet.maven.jellydoc.Tag;
import org.jvnet.maven.jellydoc.Tags;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scans Jelly tag libraries from tag files, and generate {@code taglib.xml}
 * compatible with {@code maven-jellydoc-plugin}
 *
 * <p>
 * For productive debugging of this mojo, run "mvn site:run" with debugger.
 * Every request will trigger a whole rendering, and you can do hot-swap of
 * byte code for changes. 
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "jelly-taglibdoc", requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(phase = LifecyclePhase.GENERATE_SOURCES)
public class TaglibDocMojo extends AbstractMojo implements MavenReport {
    /**
     * The Maven Project Object
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The Maven session object.
     */
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    protected MavenSession session;

    /**
     * The plugin dependencies.
     */
    @Parameter(defaultValue = "${plugin.artifacts}", required = true, readonly = true)
    private List<Artifact> pluginArtifacts;

    /**
     * Version of this plugin.
     */
    @Parameter(defaultValue = "${plugin.version}", required = true, readonly = true)
    private String pluginVersion;

    /**
     * Regular expression for taglib URIs. If specified,
     * only those taglibs that match these patterns will be generated into
     * documentation.
     */
    @Parameter(defaultValue = "${patterns}")
    private String[] patterns = new String[]{".*"};

    /**
     * Factory for creating artifact objects
     */
    @Component
    private ArtifactFactory factory;

    /**
     * Used for resolving artifacts
     */
    @Component
    private ArtifactResolver resolver;

    @Component
    private MavenProjectHelper helper;

    private JellydocMojo jellydoc;

    @Override
    public void execute() throws MojoExecutionException {
        writeTaglibXml();

        getJellydocMojo().generateSchema();
    }

    private JellydocMojo getJellydocMojo() {
        if(jellydoc==null) {
            jellydoc = new JellydocMojo() {
                @Override
                public void execute() throws MojoExecutionException {
                    TaglibDocMojo.this.execute();
                }
            };
            jellydoc.factory = factory;
            jellydoc.helper = helper;
            jellydoc.session = session;
            jellydoc.project = project;
            jellydoc.resolver = resolver;
        }
        return jellydoc;
    }

    private void writeTaglibXml() throws MojoExecutionException {
        try {
            File taglibsXml = new File(project.getBasedir(), "target/taglib.xml");
            taglibsXml.getParentFile().mkdirs();
            Tags tags = TXW.create(Tags.class,new StreamSerializer(new FileOutputStream(taglibsXml)));
            for (Resource res : project.getResources()) {
                scanTagLibs(new File(res.getDirectory()),"",tags);
            }
            tags.commit();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate taglibs.xml",e);
        }
    }

    /**
     * Recurisely search for taglibs and call {@link #parseTagLib(File, String, Library)}.
     */
    private void scanTagLibs(File dir, String uri, Tags tags) throws IOException {
        if(new File(dir,"taglib").exists()) {
            boolean match = patterns.length==0;
            for (String p : patterns) {
                if(Pattern.matches(p,uri)) {
                    match = true;
                    break;
                }
            }
            if (match) {
                parseTagLib(dir,uri,tags.library());
            }
        }

        // scan subdirs
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs == null) {
            return;
        }
        for (File subdir : subdirs) {
            scanTagLibs(subdir,uri+'/'+subdir.getName(), tags);
        }
    }

    private void parseTagLib(File dir, String uri, Library lib) throws IOException {
        getLog().info("Processing "+dir);

        List<String> markerFile = new ArrayList<>(
                Files.readAllLines(dir.toPath().resolve("taglib"), StandardCharsets.UTF_8));
        if (markerFile.size() == 0) {
            markerFile.add(uri);
        }

        // write the attributes
        lib.name(markerFile.get(0));
        lib.prefix(uri.substring(uri.lastIndexOf('/')+1)).uri(uri);
        // doc
        lib.doc()._pcdata(String.join("\n", markerFile));

        File[] tagFiles = dir.listFiles(f -> f.getName().endsWith(".jelly"));
        if (tagFiles == null) {
            return;
        }
        for (File tagFile : tagFiles) {
            parseTagFile(tagFile,lib.tag());
        }
    }

    /**
     * Parses a given tag file and writes to {@link Tag}.
     */
    private void parseTagFile(File tagFile, Tag tag) throws IOException {
        try {
            String name = tagFile.getName();
            name = name.substring(0,name.length()-6); // cut off ".jelly"
            tag.name(name);

            DocumentFactory f = new DocumentFactory();
            f.setXPathNamespaceURIs(NAMESPACE_MAP);
            Document jelly = new SAXReader(f).read(tagFile);
            Element doc = (Element) jelly.selectSingleNode(".//s:documentation");

            // does this tag have a body?
            if (jelly.selectSingleNode("//d:invokeBody") == null) {
                tag.noContent(true);
            }

            if(doc==null) {
                tag.doc("");
            } else {
                tag.doc(doc.getText());
                for (Node node : doc.selectNodes("s:attribute")) {
                    Element attr = (Element) node;
                    Attribute aw = tag.attribute();
                    for (org.dom4j.Attribute a : attr.attributes()) {
                        aw._attribute(a.getName(),a.getValue());
                    }
                    aw.doc(attr.getText());
                }
            }
        } catch (DocumentException e) {
            throw new IOException("Failed to parse " + tagFile, e);
        }
    }

//
// MavenReport implementation
//
    @Override
    public void generate(Sink sink, Locale locale) throws MavenReportException {
        getJellydocMojo().generate(sink,locale);
    }

    @Override
    public String getOutputName() {
        return getJellydocMojo().getOutputName();
    }

    @Override
    public String getName(Locale locale) {
        return getJellydocMojo().getName(locale);
    }

    @Override
    public String getCategoryName() {
        return getJellydocMojo().getCategoryName();
    }

    @Override
    public String getDescription(Locale locale) {
        return getJellydocMojo().getDescription(locale);
    }

    @Override
    public void setReportOutputDirectory(File outputDirectory) {
        getJellydocMojo().setReportOutputDirectory(outputDirectory);
    }

    @Override
    public File getReportOutputDirectory() {
        return getJellydocMojo().getReportOutputDirectory();
    }

    @Override
    public boolean isExternalReport() {
        return getJellydocMojo().isExternalReport();
    }

    @Override
    public boolean canGenerateReport() {
        return getJellydocMojo().canGenerateReport();
    }

    private static final Map<String,String> NAMESPACE_MAP = new HashMap<>();
    static {
        NAMESPACE_MAP.put("s", "jelly:stapler");
        NAMESPACE_MAP.put("d", "jelly:define");
    }
}
