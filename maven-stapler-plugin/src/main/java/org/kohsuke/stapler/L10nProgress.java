package org.kohsuke.stapler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the progress of l10n effort.
 *
 * @author ssogabe
 * @see http://d.hatena.ne.jp/ssogabe/20081213/1229175653
 */
public class L10nProgress {
    /**
     * Locales used in the set of files parsed.
     */
    private final Set<String> locales = new TreeSet<String>();

    private final List<HudsonMessages> messages = new ArrayList<HudsonMessages>();

    /**
     * Information per directory.
     */
    public final class HudsonMessages {
        private final File dir;
        private final Map<String, Integer> map = new HashMap<String, Integer>();

        public HudsonMessages(final File dir) {
            this.dir = dir;
        }

        public String getDirectoryName() {
            return dir.getName();
        }

        private void setCnt(final String locale, final int cnt) {
            map.put(locale, cnt);
        }

        public int getCnt(final String locale) {
            final Integer cnt = map.get(locale);
            return cnt != null ? cnt : 0;
        }

        /**
         * Gets the ratio of localization against the default locale.
         */
        public int ratio(String locale) {
            return (int) (((double) getCnt(locale) / getCnt("")) * 100);
        }

        /**
         * Dumps this object as a row in the Hatena diary format.
         */
        public void toHatena(StringBuilder b) {
            b.append("|").append(getDirectoryName()).append("(").append(getCnt("")).append(") |");
            for (final String locale : locales) {
                b.append(getCnt(locale)).append("(").append(ratio(locale)).append("%)|");
            }
            b.append("\n");
        }
    }

    /**
     * Gets the pseudo {@link HudsonMessages} that represents the sum of all {@link #messages}.
     */
    public HudsonMessages getTotal() {
        HudsonMessages sum = new HudsonMessages(new File("total"));
        ArrayList<String> localesPlusOne = new ArrayList<String>(locales);
        localesPlusOne.add("");
        for (String locale : localesPlusOne) {
            int cnt=0;
            for (HudsonMessages m : messages)
                cnt += m.getCnt(locale);
            sum.setCnt(locale,cnt);
        }
        return sum;
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

    /**
     * Prints the result in the Hatena diary table format.
     */
    public String toHatena() {
        final StringBuilder b = new StringBuilder();
        // header
        b.append("|*Messages(#)|");
        for (final String locale : locales) {
            b.append("*").append(locale).append("|");
        }
        b.append("\n");

        for (final HudsonMessages m : messages)
            m.toHatena(b);
        getTotal().toHatena(b);
        return b.toString();
    }

    public void parse(File dir) {
        final HudsonMessages m = new HudsonMessages(dir);
        final File[] files = dir.listFiles();
        for (final File f : files) {
            final Matcher matcher = FILENAME_PATTERN.matcher(f.getName());
            if (matcher.matches()) {
                final String locale = matcher.group(1);
                if (!locale.equals("")) {
                    locales.add(locale);
                }
                m.setCnt(locale, getMessageCnt(f));
            }
        }
        messages.add(m);
    }

    public void parse(Collection<File> dirs) {
        for (final File dir : dirs)
            parse(dir);
    }

    /**
     * Parse the given directory and all its descendants.
     */
    public void parseRecursively(final File dir) {
        for (final File f : dir.listFiles()) {
            if (f.isDirectory()) {
                parseRecursively(f);
            } else if (f.isFile() && MESSAGES_FILE.equals(f.getName())) {
                parse(f.getParentFile());
            }
        }
    }

    private static final String MESSAGES_FILE = "Messages.properties";
    private static final Pattern FILENAME_PATTERN = Pattern.compile("^Messages_?([a-zA-Z_]*)\\.properties$");
}
