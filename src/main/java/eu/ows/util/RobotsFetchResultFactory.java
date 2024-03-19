package eu.ows.util;

import com.digitalpebble.stormcrawler.Metadata;
import com.digitalpebble.stormcrawler.util.ConfUtils;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRules.RobotRulesMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RobotsFetchResultFactory {

    private static class SingletonHelper {
        private static RobotsFetchResultFactory INSTANCE = null;
    }

    public static synchronized RobotsFetchResultFactory getInstance(
            final Map<String, Object> conf) {
        if (SingletonHelper.INSTANCE == null) {
            SingletonHelper.INSTANCE = new RobotsFetchResultFactory(conf);
        }
        return SingletonHelper.INSTANCE;
    }

    private final List<String> agentNames;
    private final Map<String, BaseRobotRules> default_rules = new HashMap<>();

    private RobotsFetchResultFactory(final Map<String, Object> conf) {
        agentNames = new ArrayList<>();
        agentNames.add("CompletelyRandomReferenceUserAgent");
        for (final String agentName : ConfUtils.loadListFromConf("fetcher.parse.agents", conf)) {
            agentNames.add(agentName.toLowerCase(Locale.ROOT));
        }
        for (final String agentName : agentNames) {
            default_rules.put(agentName, new SimpleRobotRules(RobotRulesMode.ALLOW_ALL));
        }
    }

    public RobotsFetchResult create(
            final Metadata metadata, final Map<String, BaseRobotRules> rules) {
        return new RobotsFetchResult(metadata, rules);
    }

    public RobotsFetchResult create(final Metadata metadata) {
        return new RobotsFetchResult(metadata, default_rules);
    }

    public class RobotsFetchResult {

        private final Metadata metadata;
        private final Map<String, BaseRobotRules> rules;

        public RobotsFetchResult(final Metadata metadata, final Map<String, BaseRobotRules> rules) {
            this.metadata = metadata;
            this.rules = rules;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public Map<String, BaseRobotRules> getRules() {
            return rules;
        }
    }
}
