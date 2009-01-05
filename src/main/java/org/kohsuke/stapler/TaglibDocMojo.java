package org.kohsuke.stapler;

import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.output.StreamSerializer;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.doxia.sink.Sink;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jvnet.maven.jellydoc.Attribute;
import org.jvnet.maven.jellydoc.JellydocMojo;
import org.jvnet.maven.jellydoc.Library;
import org.jvnet.maven.jellydoc.Tag;
import org.jvnet.maven.jellydoc.Tags;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scans Jelly tag libraries from tag files, and generate <tt>taglib.xml</tt>
 * compatible with <tt>maven-jellydoc-plugin</tt>
 *
 * <p>
 * For productive debugging of this mojo, run "mvn site:run" with debugger.
 * Every request will trigger a whole rendering, and you can do hot-swap of
 * byte code for changes. 
 *
 * @author Kohsuke Kawaguchi
 * @goal jelly-taglibdoc
 * @phase generate-sources
 * @requiresDependencyResolution compile
 */
public class TaglibDocMojo extends AbstractMojo implements MavenReport {
    /**
     * The Maven Project Object
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The plugin dependencies.
     *
     * @parameter expression="${plugin.artifacts}"
     * @required
     * @readonly
     */
    private List<Artifact> pluginArtifacts;

    /**
     * Version of this plugin.
     *
     * @parameter expression="${plugin.version}"
     * @required
     * @readonly
     */
    private String pluginVersion;

    /**
     * Regular expression for taglib URIs. If specified,
     * only those taglibs that match these patterns will be generated into
     * documentation.
     *
     * @parameter expression="${patterns}"
     */
    private String[] patterns = new String[]{".*"};

    /**
     * Factory for creating artifact objects
     *
     * @component
     */
    private ArtifactFactory factory;

    /**
     * Used for resolving artifacts
     *
     * @component
     */
    private ArtifactResolver resolver;

    /**
     * The local repository where the artifacts are located.
     *
     * @parameter expression="${localRepository}"
     */
    private ArtifactRepository localRepository;

    /**
     * @component
     */
    private MavenProjectHelper helper;

    private JellydocMojo jellydoc;

    public void execute() throws MojoExecutionException, MojoFailureException {
        writeTaglibXml();

        getJellydocMojo().generateSchema();
    }

    private JellydocMojo getJellydocMojo() {
        if(jellydoc==null) {
            jellydoc = new JellydocMojo() {
                @Override
                public void execute() throws MojoExecutionException, MojoFailureException {
                    TaglibDocMojo.this.execute();
                }
            };
            jellydoc.factory = factory;
            jellydoc.helper = helper;
            jellydoc.localRepository = localRepository;
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
            for(Resource res : (List<Resource>)project.getResources())
                scanTagLibs(new File(res.getDirectory()),"",tags);
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
            if(match)
                parseTagLib(dir,uri,tags.library());
        }

        // scan subdirs
        File[] subdirs = dir.listFiles(new FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory();
            }
        });
        if(subdirs==null)   return;
        for (File subdir : subdirs)
            scanTagLibs(subdir,uri+'/'+subdir.getName(), tags);
    }

    private void parseTagLib(File dir, String uri, Library lib) throws IOException {
        getLog().info("Processing "+dir);

        List markerFile = FileUtils.readLines(new File(dir, "taglib"));
        if(markerFile.size()==0)
            markerFile.add(uri);

        // write the attributes
        lib.name(markerFile.get(0).toString());
        lib.prefix(uri.substring(uri.lastIndexOf('/')+1)).uri(uri);
        // doc
        lib.doc()._pcdata(join(markerFile));

        File[] tagFiles = dir.listFiles(new FileFilter() {
            public boolean accept(File f) {
                return f.getName().endsWith(".jelly");
            }
        });
        if(tagFiles==null)  return;
        for (File tagFile : tagFiles)
            parseTagFile(tagFile,lib.tag());
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
            if(jelly.selectSingleNode("//d:invokeBody")==null)
                tag.noContent(true);

            if(doc==null) {
                tag.doc("");
            } else {
                tag.doc(doc.getText());
                for(Element attr : (List<Element>)doc.selectNodes("s:attribute")) {
                    Attribute aw = tag.attribute();
                    for (org.dom4j.Attribute a : (List<org.dom4j.Attribute>)attr.attributes())
                        aw._attribute(a.getName(),a.getValue());
                    aw.doc(attr.getText());
                }
            }
        } catch (DocumentException e) {
            IOException x = new IOException("Failed to parse " + tagFile);
            x.initCause(e);
            throw x;
        }
    }

    private String join(List list) {
        StringBuilder buf = new StringBuilder();
        for (Object item : list) {
            if(buf.length()>0)  buf.append('\n');
            buf.append(item);
        }
        return buf.toString();
    }

//
// MavenReport implementation
//
    public void generate(Sink sink, Locale locale) throws MavenReportException {
        getJellydocMojo().generate(sink,locale);
    }

    public String getOutputName() {
        return getJellydocMojo().getOutputName();
    }

    public String getName(Locale locale) {
        return getJellydocMojo().getName(locale);
    }

    public String getCategoryName() {
        return getJellydocMojo().getCategoryName();
    }

    public String getDescription(Locale locale) {
        return getJellydocMojo().getDescription(locale);
    }

    public void setReportOutputDirectory(File outputDirectory) {
        getJellydocMojo().setReportOutputDirectory(outputDirectory);
    }

    public File getReportOutputDirectory() {
        return getJellydocMojo().getReportOutputDirectory();
    }

    public boolean isExternalReport() {
        return getJellydocMojo().isExternalReport();
    }

    public boolean canGenerateReport() {
        return getJellydocMojo().canGenerateReport();
    }

    private static final Map<String,String> NAMESPACE_MAP = new HashMap<String, String>();
    static {
        NAMESPACE_MAP.put("s", "jelly:stapler");
        NAMESPACE_MAP.put("d", "jelly:define");
    }
}
