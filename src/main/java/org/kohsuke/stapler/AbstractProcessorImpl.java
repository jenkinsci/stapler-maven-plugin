package org.kohsuke.stapler;

import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import static javax.tools.Diagnostic.Kind.*;
import static javax.tools.StandardLocation.*;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"Since15"})
abstract class AbstractProcessorImpl extends AbstractProcessor {
    protected void error(String msg) {
        processingEnv.getMessager().printMessage(ERROR, msg);
    }

    protected String getJavadoc(Element md) {
        return processingEnv.getElementUtils().getDocComment(md);
    }

    protected void notice(String msg, Element location) {
        processingEnv.getMessager().printMessage(NOTE, msg, location);
    }

    protected void writePropertyFile(Properties p, String name) throws IOException {
        FileObject f = createResource(name);
        OutputStream os = f.openOutputStream();
        p.store(os,null);
        os.close();
    }

    protected FileObject getResource(String name) throws IOException {
        return processingEnv.getFiler().getResource(CLASS_OUTPUT, "", name);
    }

    protected FileObject createResource(String name) throws IOException {
        return processingEnv.getFiler().createResource(CLASS_OUTPUT, "", name);
    }
}
