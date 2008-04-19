package org.kohsuke.stapler;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Resource;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Alias for <tt>stapler:l10n</tt> mojo. Left for compatibility.
 *
 * @author Kohsuke Kawaguchi
 * @goal i18n
 */
public class LocalizerMojo extends AbstractMojo {
    /**
     * The locale to generate properties for.
     *
     * @parameter expression="${locale}"
     * @required
     */
    protected String locale;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    public void execute() throws MojoExecutionException, MojoFailureException {
        // create parser
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            parser = spf.newSAXParser();
        } catch (SAXException e) {
            throw new Error(e); // impossible
        } catch (ParserConfigurationException e) {
            throw new Error(e); // impossible
        }

        for( Resource res : (List<Resource>)project.getResources() ) {
            File dir = new File(res.getDirectory());
            processDirectory(dir);
        }
    }

    private void process(File file) throws MojoExecutionException {
        if(file.isDirectory())
            processDirectory(file);
        else
        if(file.getName().endsWith(".jelly"))
            processJelly(file);
    }

    private void processDirectory(File dir) throws MojoExecutionException {
        File[] children = dir.listFiles();
        if(children==null)  return;
        for (File child : children)
            process(child);
    }

    private void processJelly(File file) throws MojoExecutionException {
        Set<String> props = findAllProperties(file);
        if(props.isEmpty())
            return; // nothing to generate here.

        String fileName = file.getName();
        fileName=fileName.substring(0,fileName.length()-".jelly".length());
        fileName+='_'+locale+".properties";
        File resourceFile = new File(file.getParentFile(),fileName);

        if(resourceFile.exists()) {
            Properties resource;
            try {
                resource = new Properties(resourceFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read "+resourceFile,e);
            }

            // find unnecessary properties = those which are present in the resource file but not in Jelly
            HashSet<String> unnecessaries = new HashSet<String>((Set) resource.keySet());
            unnecessaries.removeAll(props);
            for (String s : unnecessaries)
                getLog().warn("Unused property "+s+" in "+resourceFile);

            // figure out missing properties
            props.removeAll(resource.keySet());

            // add NL to the end if necessary
            try {
                // then add them to the end
                RandomAccessFile f = new RandomAccessFile(resourceFile,"rw");
                if(f.length()>0) {
                    // add the terminating line end if needed
                    f.seek(f.length()-1);
                    int ch = f.read();
                    if(!(ch=='\r' || ch=='\n'))
                        f.write(System.getProperty("line.separator").getBytes());
                }
                f.close();
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to write "+resourceFile,e);
            }
        }

        if(props.isEmpty())
            return; // no change to make

        getLog().info("Updating "+resourceFile);

        try {
            // then add them to the end
            RandomAccessFile f = new RandomAccessFile(resourceFile,"rw");
            if(f.length()>0) {
                // add the terminating line end if needed
                f.seek(f.length()-1);
                int ch = f.read();
                if(!(ch=='\r' || ch=='\n'))
                    f.write(System.getProperty("line.separator").getBytes());
            }
            f.close();
            PrintWriter w = new PrintWriter(new FileWriter(resourceFile,true));
            for (String p : props) {
                w.println(escape(p)+"=");
            }
            w.close();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write "+resourceFile,e);
        }
    }

    /**
     * Escapes the property key in the proper format.
     */
    private String escape(String key) {
        StringBuilder buf = new StringBuilder(key.length());
        for( int i=0; i<key.length(); i++ ) {
            char ch = key.charAt(i);
            switch (ch) {
            case ' ':   buf.append("\\ ");break;
            case '\t':  buf.append("\\t");break;
            case '\n':  buf.append("\\n");break;
            case '=':
            case ':':
            case '#':
            case '!':
                buf.append('\\').append(ch);
                break;
            default:
                // TODO: non ASCII char escape
                buf.append(ch);
                break;
            }
        }
        return buf.toString();
    }

    /**
     * Parses a Jelly script and lists up all the property names used in there.
     */
    private Set<String> findAllProperties(File file) throws MojoExecutionException {
        getLog().debug("Parsing "+file);
        try {
            // we'd like to preserve order, but don't want duplicates
            final Set<String> properties = new LinkedHashSet<String>();

            parser.parse(file,new DefaultHandler() {
                private final StringBuilder buf = new StringBuilder();
                private Locator locator;

                public void setDocumentLocator(Locator locator) {
                    this.locator = locator;
                }

                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    findExpressions();
                    for( int i=0; i<attributes.getLength(); i++ ) {
                        buf.append(attributes.getValue(i));
                        findExpressions();
                    }
                }

                public void endElement(String uri, String localName, String qName) throws SAXException {
                    findExpressions();
                }

                public void characters(char ch[], int start, int length) throws SAXException {
                    buf.append(ch,start,length);
                }

                /**
                 * Find property references of the form "${%xxx(...)}" from {@link #buf}
                 * and list up property names.
                 */
                private void findExpressions() throws SAXParseException {
                    int idx=-1;
                    do {
                        idx = buf.indexOf("${",idx+1);
                        if(idx<0)   break;

                        int end = buf.indexOf("}",idx);
                        if(end==-1)
                            throw new SAXParseException("Missing '}'",locator);

                        onJexlExpression(buf.substring(idx+2,end));
                    } while(true);

                    buf.setLength(0);
                }

                /**
                 * Found a JEXL expression.
                 */
                private void onJexlExpression(String exp) {
                    if(exp.startsWith("%")) {
                        getLog().debug("Found "+exp);
                        exp = exp.substring(1);

                        // if parameters follow, remove them
                        int op = exp.indexOf('(');
                        if(op>=0)   exp=exp.substring(0,op);
                        properties.add(exp);
                    } else {
                        Matcher m = RESOURCE_LITERAL_STRING.matcher(exp);
                        while(m.find()) {
                            String literal = m.group();
                            getLog().debug("Found "+literal);
                            literal = literal.substring(2,literal.length()-1); // unquote and remove '%'

                            // if parameters follow, remove them
                            int op = literal.indexOf('(');
                            if(op>=0)   literal=literal.substring(0,op);
                            properties.add(literal);
                        }
                    }
                }
            });

            return properties;
        } catch (SAXException e) {
            throw new MojoExecutionException("Failed to parse "+file, e);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to parse "+file, e);
        }
    }

    SAXParser parser;

    // "%...."    string literal that starts with '%'
    private static final Pattern RESOURCE_LITERAL_STRING = Pattern.compile("(\"%[^\"]+\")|('%[^']+')");
}
