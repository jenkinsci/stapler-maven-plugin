package org.kohsuke.stapler;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
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

    private static final String MESSAGES_FILE = "Messages.properties";

    private final Set<String> locales = new TreeSet<String>();

    private static class HudsonMessages {
        private File dir;
        private Map<String, Integer> map = new HashMap<String, Integer>();

        public HudsonMessages(final File dir) {
            this.dir = dir;
        }

        public String getDirectoryName() {
            return dir.getName();
        }

        public void setCnt(final String locale, final int cnt) {
            map.put(locale, cnt);
        }

        public int getCnt(final String locale) {
            final Integer cnt = map.get(locale);
            return cnt != null ? cnt : 0;
        }
    }

    /**
     * Returns the number of entries in the given property file.
     */
    private int getMessageCnt(final File file) {
        final Properties props = new Properties();
        int cnt = 0;
        try {
            props.load(new FileInputStream(file));
            cnt = props.size();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return cnt;
    }

    private String toHatena(final List<HudsonMessages> list) {
        final StringBuilder b = new StringBuilder();
        // header
        b.append("|*Messages(en)|");
        for (final String locale : locales) {
            b.append("*").append(locale).append("|");
        }
        b.append("\n");

        for (final HudsonMessages m : list) {
            b.append("|").append(m.getDirectoryName()).append("(").append(m.getCnt("en")).append(") |");
            for (final String locale : locales) {
                int p = (int) (((double) m.getCnt(locale) / m.getCnt("en")) * 100);
                b.append(m.getCnt(locale)).append("(").append(p).append("%)|");
            }
            b.append("\n");
        }
        return b.toString();
    }

    final Pattern FILENAME_PATTERN = Pattern.compile("^Messages_?([a-zA-Z_]*)\\.properties$");

    private List<HudsonMessages> parse(final List<File> dirs) {
        final List<HudsonMessages> list = new ArrayList<HudsonMessages>();
        for (final File dir : dirs) {
            final HudsonMessages messages = new HudsonMessages(dir);
            final File[] files = dir.listFiles();
            for (final File f : files) {
                final Matcher matcher = FILENAME_PATTERN.matcher(f.getName());
                if (matcher.matches()) {
                    final String locale = "".equals(matcher.group(1)) ? "en" : matcher.group(1);
                    if (!locale.equals("en")) {
                        locales.add(locale);
                    }
                    messages.setCnt(locale, getMessageCnt(f));
                }
            }
            list.add(messages);
        }
        return list;
    }

    /**
     * List up directories that contain {@code Message.properties}.
     */
    private List<File> scanDirectories(final File dir) {
        final List<File> l = new ArrayList<File>();
        final File[] files = dir.listFiles();
        for (final File f : files) {
            if (f.isDirectory()) {
                l.addAll(scanDirectories(f));
            } else if (f.isFile() && MESSAGES_FILE.equals(f.getName())) {
                l.add(f.getParentFile());
            }
        }
        return l;
    }

    public void execute(final String dir) {
        final List<File> files = scanDirectories(new File(dir));
        System.out.println(toHatena(parse(files)));
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        for( Resource root : (Collection<Resource>)project.getResources() ) {
            execute(root.getDirectory());
        }
    }
}
