package org.kohsuke.stapler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class Properties extends java.util.Properties {
    public Properties() {
        super();
    }

    /**
     * Loads from the file.
     */
    public Properties(File src) throws IOException {
        FileInputStream in = new FileInputStream(src);
        try {
            load(in);
        } finally {
            in.close();
        }
    }
}
