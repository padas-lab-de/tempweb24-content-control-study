package eu.ows.util;

import com.digitalpebble.stormcrawler.Metadata;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RobotsParser {

    private static final SimpleRobotRulesParser simpleRobotRulesParser =
            new SimpleRobotRulesParser();

    public static Metadata parseContent(
            final String robotsUrl,
            final byte[] content,
            final String contentType,
            final List<String> agentNames) {
        final Metadata metadata = new Metadata();

        if (content == null) {
            return metadata;
        }

        int contentLength = content.length;
        metadata.setValue("content-length", String.valueOf(contentLength));

        if (contentLength == 0) {
            return metadata;
        }

        if (contentType != null) {
            metadata.setValue("content-type", contentType);
            if (contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
                return metadata;
            }
        }

        /*
         * RFC 9309 requires that is "UTF-8 encoded" (<a href=
         * "https://www.rfc-editor.org/rfc/rfc9309.html#name-access-method"> RFC
         * 9309, section 2.3 Access Method</a>), but
         * "Implementors MAY bridge encoding mismatches if they detect that the robots.txt file is not UTF-8 encoded."
         * (<a href=
         * "https://www.rfc-editor.org/rfc/rfc9309.html#name-the-allow-and-disallow-line"
         * > RFC 9309, section 2.2.2. The "Allow" and "Disallow" Lines</a>)
         */
        int offset = 0;
        Charset encoding = StandardCharsets.UTF_8;

        // Check for a UTF-8 BOM at the beginning (EF BB BF)
        if ((contentLength >= 3)
                && (content[0] == (byte) 0xEF)
                && (content[1] == (byte) 0xBB)
                && (content[2] == (byte) 0xBF)) {
            offset = 3;
            contentLength -= 3;
            encoding = StandardCharsets.UTF_8;
        }
        // Check for UTF-16LE BOM at the beginning (FF FE)
        else if ((contentLength >= 2)
                && (content[0] == (byte) 0xFF)
                && (content[1] == (byte) 0xFE)) {
            offset = 2;
            contentLength -= 2;
            encoding = StandardCharsets.UTF_16LE;
        }
        // Check for UTF-16BE BOM at the beginning (FE FF)
        else if ((contentLength >= 2)
                && (content[0] == (byte) 0xFE)
                && (content[1] == (byte) 0xFF)) {
            offset = 2;
            contentLength -= 2;
            encoding = StandardCharsets.UTF_16BE;
        }

        final String contentString = new String(content, offset, contentLength, encoding);
        int numLines = 0;
        int numAllow = 0;
        int numDisallow = 0;
        int numDisallowEmpty = 0;
        int crawlDelay = -1;
        int numSitemaps = 0;
        final Set<String> sitemaps = new HashSet<>();
        final Set<String> userAgents = new HashSet<>();
        final Map<String, Map<String, Integer>> userAgentsMap = new HashMap<>();
        final List<String> lastUserAgents = new ArrayList<>();
        final Set<String> directories = new HashSet<>();
        boolean lastDirectiveWasUserAgent = false;

        // Break on anything that might be used as a line ending. Since
        // tokenizer doesn't return empty tokens, a \r\n sequence still
        // works since it looks like an empty string between the \r and \n.
        final StringTokenizer lineParser =
                new StringTokenizer(contentString, "\n\r\u0085\u2028\u2029");
        while (lineParser.hasMoreTokens()) {
            String line = lineParser.nextToken();
            numLines++;

            // trim out comments and whitespace
            int hashPosition = line.indexOf("#");
            if (hashPosition >= 0) {
                line = line.substring(0, hashPosition);
            }

            line = line.trim();
            if (line.length() == 0) {
                continue;
            }

            RobotToken token = tokenize(line);
            switch (token.getDirective()) {
                case USER_AGENT:
                    userAgents.add(token.getData().toLowerCase(Locale.ROOT));
                    if (!lastDirectiveWasUserAgent) {
                        lastUserAgents.clear();
                    }
                    lastUserAgents.add(token.getData().toLowerCase(Locale.ROOT));
                    lastDirectiveWasUserAgent = true;
                    break;

                case DISALLOW:
                    if (token.getData().trim().length() > 0) {
                        numDisallow++;
                        for (final String lastUserAgent : lastUserAgents) {
                            if (!agentNames.contains(lastUserAgent) && !lastUserAgent.equals("*")) {
                                continue;
                            }
                            Map<String, Integer> countMap =
                                    userAgentsMap.getOrDefault(lastUserAgent, new HashMap<>());
                            int numDisallowUserAgent = countMap.getOrDefault("num-disallow", 0);
                            numDisallowUserAgent++;
                            countMap.put("num-disallow", numDisallowUserAgent);
                            userAgentsMap.put(lastUserAgent, countMap);
                        }
                        String directory = token.getData().trim().toLowerCase(Locale.ROOT);
                        if (directory.endsWith("/*")) {
                            directory = directory.substring(0, directory.length() - 1);
                        }
                        if (directory.startsWith("/") && directory.endsWith("/")) {
                            directories.add(directory);
                        }
                    } else {
                        numDisallowEmpty++;
                        for (final String lastUserAgent : lastUserAgents) {
                            if (!agentNames.contains(lastUserAgent) && !lastUserAgent.equals("*")) {
                                continue;
                            }
                            Map<String, Integer> countMap =
                                    userAgentsMap.getOrDefault(lastUserAgent, new HashMap<>());
                            int numDisallowEmptyUserAgent =
                                    countMap.getOrDefault("num-disallow-empty", 0);
                            numDisallowEmptyUserAgent++;
                            countMap.put("num-disallow-empty", numDisallowEmptyUserAgent);
                            userAgentsMap.put(lastUserAgent, countMap);
                        }
                    }
                    lastDirectiveWasUserAgent = false;
                    break;

                case ALLOW:
                    if (token.getData().trim().length() > 0) {
                        numAllow++;
                        for (final String lastUserAgent : lastUserAgents) {
                            if (!agentNames.contains(lastUserAgent) && !lastUserAgent.equals("*")) {
                                continue;
                            }
                            final Map<String, Integer> countMap =
                                    userAgentsMap.getOrDefault(lastUserAgent, new HashMap<>());
                            int numAllowUserAgent = countMap.getOrDefault("num-allow", 0);
                            numAllowUserAgent++;
                            countMap.put("num-allow", numAllowUserAgent);
                            userAgentsMap.put(lastUserAgent, countMap);
                        }
                        String directory = token.getData().trim().toLowerCase(Locale.ROOT);
                        if (directory.endsWith("/*")) {
                            directory = directory.substring(0, directory.length() - 1);
                        }
                        if (directory.startsWith("/") && directory.endsWith("/")) {
                            directories.add(directory);
                        }
                    }
                    lastDirectiveWasUserAgent = false;
                    break;

                case CRAWL_DELAY:
                    try {
                        final int parsedCrawlDelay = Integer.parseInt(token.getData());
                        if (parsedCrawlDelay > crawlDelay) {
                            crawlDelay = parsedCrawlDelay;
                        }
                    } catch (Exception e) {
                    }
                    break;

                case SITEMAP:
                    numSitemaps++;
                    sitemaps.add(token.getData().toLowerCase(Locale.ROOT));
                    break;

                default:
                    break;
            }
        }

        metadata.setValue("num-lines", String.valueOf(numLines));
        metadata.setValue("num-disallow", String.valueOf(numDisallow));
        metadata.setValue("num-disallow-empty", String.valueOf(numDisallowEmpty));
        metadata.setValue("num-allow", String.valueOf(numAllow));
        metadata.setValue("num-sitemaps", String.valueOf(numSitemaps));
        metadata.setValues("sitemaps", sitemaps.toArray(new String[sitemaps.size()]));
        metadata.setValue("num-useragents", String.valueOf(userAgents.size()));
        metadata.setValues("useragents", userAgents.toArray(new String[userAgents.size()]));

        for (final String userAgent : userAgents) {
            if (!agentNames.contains(userAgent) && !userAgent.equals("*")) {
                continue;
            }
            final Map<String, Integer> countMap =
                    userAgentsMap.getOrDefault(userAgent, new HashMap<>());
            int numAllowUserAgent = countMap.getOrDefault("num-allow", 0);
            int numDisallowUserAgent = countMap.getOrDefault("num-disallow", 0);
            int numDisallowEmptyUserAgent = countMap.getOrDefault("num-disallow-empty", 0);
            if (numAllowUserAgent + numDisallowUserAgent + numDisallowEmptyUserAgent > 0) {
                String mdKey = userAgent.toLowerCase(Locale.ROOT);
                if (userAgent.equals("*")) {
                    mdKey = "asterisk";
                }
                metadata.setValue(
                        "num-allow-per-agent." + mdKey, String.valueOf(numAllowUserAgent));
                metadata.setValue(
                        "num-disallow-per-agent." + mdKey, String.valueOf(numDisallowUserAgent));
                if (numDisallowEmptyUserAgent > 0) {
                    metadata.setValue(
                            "num-disallow-empty-per-agent." + mdKey,
                            String.valueOf(numDisallowEmptyUserAgent));
                }
            }
        }

        if (crawlDelay != -1) {
            metadata.setValue("crawldelay", String.valueOf(crawlDelay));
        }

        final String globalUserAgent =
                "CompletelyRandomReferenceUserAgent".toLowerCase(Locale.ROOT);
        final SimpleRobotRules simpleRobotRulesAsterisk =
                simpleRobotRulesParser.parseContent(
                        robotsUrl, content, contentType, List.of(globalUserAgent));
        final Map<String, Integer> countMapAsterisk =
                userAgentsMap.getOrDefault("*", new HashMap<>());
        String hostUrl = robotsUrl;
        if (robotsUrl.endsWith("/robots.txt")) {
            hostUrl = robotsUrl.substring(0, robotsUrl.length() - 11);
        }
        if (isDisallowAll(hostUrl, simpleRobotRulesAsterisk, countMapAsterisk)) {
            metadata.setValue("disallow-all.asterisk", String.valueOf(true));
        }

        for (final String agentName : agentNames) {
            if (!userAgents.contains(agentName)) {
                continue;
            }
            final SimpleRobotRules simpleRobotRules =
                    simpleRobotRulesParser.parseContent(
                            robotsUrl, content, contentType, List.of(agentName));
            final Map<String, Integer> countMap =
                    userAgentsMap.getOrDefault(agentName, new HashMap<>());
            if (isDisallowAll(hostUrl, simpleRobotRules, countMap)) {
                metadata.setValue("disallow-all." + agentName, String.valueOf(true));
            }

            final int bias =
                    getBias(
                            agentName,
                            hostUrl,
                            simpleRobotRules,
                            simpleRobotRulesAsterisk,
                            directories);
            metadata.setValue("bias." + agentName, String.valueOf(bias));
        }

        return metadata;
    }

    private static boolean isDisallowAll(
            final String hostUrl,
            final SimpleRobotRules simpleRobotRules,
            final Map<String, Integer> countMap) {
        boolean isDisallowAll = false;
        if (!simpleRobotRules.isAllowed(hostUrl + "/42")
                && countMap.getOrDefault("num-disallow", 0) == 1
                && countMap.getOrDefault("num-disallow-empty", 0) == 0
                && countMap.getOrDefault("num-allow", 0) == 0) {
            isDisallowAll = true;
        }
        return isDisallowAll;
    }

    private static int getBias(
            final String agentName,
            final String hostUrl,
            final SimpleRobotRules simpleRobotRules,
            final SimpleRobotRules simpleRobotRulesAsterisk,
            final Set<String> directories) {
        int bias = 0;
        for (final String directory : directories) {
            if (simpleRobotRulesAsterisk.isAllowed(hostUrl + directory + "42")) {
                bias--;
            }
            if (simpleRobotRules.isAllowed(hostUrl + directory + "42")) {
                bias++;
            }
        }
        return bias;
    }

    private static class RobotToken {
        private RobotDirective _directive;
        private String _data;

        public RobotToken(RobotDirective directive, String data) {
            _directive = directive;
            _data = data;
        }

        public RobotDirective getDirective() {
            return _directive;
        }

        public String getData() {
            return _data;
        }
    }

    private static Map<String, RobotDirective> DIRECTIVE_PREFIX =
            new HashMap<String, RobotDirective>();

    static {
        for (RobotDirective directive : RobotDirective.values()) {
            if (!directive.isSpecial()) {
                String prefix = directive.name().toLowerCase(Locale.ROOT).replaceAll("_", "-");
                DIRECTIVE_PREFIX.put(prefix, directive);
            }
        }

        DIRECTIVE_PREFIX.put("useragent", RobotDirective.USER_AGENT);
        DIRECTIVE_PREFIX.put("useg-agent", RobotDirective.USER_AGENT);
        DIRECTIVE_PREFIX.put("ser-agent", RobotDirective.USER_AGENT);
        DIRECTIVE_PREFIX.put("users-agent", RobotDirective.USER_AGENT);
        DIRECTIVE_PREFIX.put("user agent", RobotDirective.USER_AGENT);
        DIRECTIVE_PREFIX.put("user-agnet", RobotDirective.USER_AGENT);
        DIRECTIVE_PREFIX.put("user-agents", RobotDirective.USER_AGENT);

        DIRECTIVE_PREFIX.put("desallow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("dissallow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("dissalow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("disalow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("dssalow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("dsallow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("diasllow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("disallaw", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("diallow", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("disallows", RobotDirective.DISALLOW);
        DIRECTIVE_PREFIX.put("disllow", RobotDirective.DISALLOW);

        DIRECTIVE_PREFIX.put("crawl delay", RobotDirective.CRAWL_DELAY);
        DIRECTIVE_PREFIX.put("clawl-delay", RobotDirective.CRAWL_DELAY);
        DIRECTIVE_PREFIX.put("craw-delay", RobotDirective.CRAWL_DELAY);
        DIRECTIVE_PREFIX.put("crawl-deley", RobotDirective.CRAWL_DELAY);

        DIRECTIVE_PREFIX.put("sitemaps", RobotDirective.SITEMAP);

        DIRECTIVE_PREFIX.put("https", RobotDirective.HTTP);
    }

    // separator is either one or more spaces/tabs, or a colon
    private static final Pattern COLON_DIRECTIVE_DELIMITER = Pattern.compile("[ \t]*:[ \t]*(.*)");
    private static final Pattern BLANK_DIRECTIVE_DELIMITER = Pattern.compile("[ \t]+(.*)");

    // match the rest of the directive, up until whitespace or colon
    private static final Pattern DIRECTIVE_SUFFIX_PATTERN = Pattern.compile("[^: \t]+(.*)");

    /**
     * Figure out directive on line of text from robots.txt file. We assume the line has been
     * lower-cased
     *
     * @param line
     * @return robot command found on line
     */
    private static RobotToken tokenize(String line) {
        String lowerLine = line.toLowerCase(Locale.ROOT);
        for (String prefix : DIRECTIVE_PREFIX.keySet()) {
            int prefixLength = prefix.length();
            if (lowerLine.startsWith(prefix)) {
                RobotDirective directive = DIRECTIVE_PREFIX.get(prefix);
                String dataPortion = line.substring(prefixLength);

                if (directive.isPrefix()) {
                    Matcher m = DIRECTIVE_SUFFIX_PATTERN.matcher(dataPortion);
                    if (m.matches()) {
                        dataPortion = m.group(1);
                    } else {
                        continue;
                    }
                }

                Matcher m = COLON_DIRECTIVE_DELIMITER.matcher(dataPortion);
                if (!m.matches()) {
                    m = BLANK_DIRECTIVE_DELIMITER.matcher(dataPortion);
                }

                if (m.matches()) {
                    return new RobotToken(directive, m.group(1).trim());
                }
            }
        }

        Matcher m = COLON_DIRECTIVE_DELIMITER.matcher(lowerLine);
        if (m.matches()) {
            return new RobotToken(RobotDirective.UNKNOWN, line);
        } else {
            return new RobotToken(RobotDirective.MISSING, line);
        }
    }

    private enum RobotDirective {
        USER_AGENT,
        DISALLOW,

        ALLOW,
        CRAWL_DELAY,
        SITEMAP,

        /**
         * The &quot;Host&quot; directive was used by Yandex to indicate the main or canonical host
         * of a set of mirrored web sites, see <a href=
         * "https://web.archive.org/web/20130509230548/http://help.yandex.com/webmaster/?id=1113851#1113856">Yandex'
         * Using robots.txt (archived version)</a>
         */
        HOST,

        // Google extension
        NO_INDEX,

        /**
         * <a href= "https://en.wikipedia.org/wiki/Automated_Content_Access_Protocol">Automated
         * Content Access Protocol</a> directives all start with <code>ACAP-</code>.
         */
        ACAP_(true, false),

        // Extensions to the standard
        REQUEST_RATE,
        VISIT_TIME,
        ROBOT_VERSION,
        COMMENT,

        // Line starts with http:, which we treat as sitemap directive.
        HTTP,

        // Line has no known directive on it.
        UNKNOWN(false, true),

        // Line has no directive on it
        MISSING(false, true);

        private boolean _prefix;
        private boolean _special;

        private RobotDirective() {
            _prefix = false;
            _special = false;
        }

        private RobotDirective(boolean isPrefix, boolean isSpecial) {
            _prefix = isPrefix;
            _special = isSpecial;
        }

        public boolean isSpecial() {
            return _special;
        }

        public boolean isPrefix() {
            return _prefix;
        }
    }
}
