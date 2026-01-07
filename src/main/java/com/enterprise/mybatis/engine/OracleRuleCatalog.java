package com.enterprise.mybatis.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Catalog of all Oracle detection rules loaded from configuration.
 *
 * This class is responsible for:
 * - Loading rules from YAML configuration
 * - Building concrete OracleRule instances
 * - Providing rule lookup by category, priority, etc.
 * - Managing rule lifecycle
 *
 * Design principle: Rules are DATA, not CODE. This allows extending
 * detection capabilities by editing configuration, not Java code.
 */
public class OracleRuleCatalog {
    private static final Logger log = LoggerFactory.getLogger(OracleRuleCatalog.class);

    private final List<OracleRule> allRules;
    private final Map<String, List<OracleRule>> rulesByCategory;
    private final List<OracleRule> sortedByPriority;

    /**
     * Load rules from YAML configuration file.
     */
    public static OracleRuleCatalog fromYaml(Path yamlPath) throws Exception {
        log.info("Loading Oracle rules from: {}", yamlPath);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        String yamlContent = Files.readString(yamlPath);

        @SuppressWarnings("unchecked")
        Map<String, Object> config = mapper.readValue(yamlContent, Map.class);

        List<OracleRule> rules = new ArrayList<>();

        // Load function rules
        rules.addAll(loadFunctionRules(config));

        // Load keyword rules
        rules.addAll(loadKeywordRules(config));

        // Load pattern rules
        rules.addAll(loadPatternRules(config));

        // Load clause rules
        rules.addAll(loadClauseRules(config));

        // Load data type rules
        rules.addAll(loadDataTypeRules(config));

        log.info("Loaded {} Oracle detection rules", rules.size());

        return new OracleRuleCatalog(rules);
    }

    private OracleRuleCatalog(List<OracleRule> rules) {
        this.allRules = new ArrayList<>(rules);

        // Index by category
        this.rulesByCategory = rules.stream()
                .collect(Collectors.groupingBy(OracleRule::getCategory));

        // Sort by priority (higher priority first)
        this.sortedByPriority = rules.stream()
                .sorted(Comparator.comparingInt(OracleRule::getPriority).reversed())
                .collect(Collectors.toList());

        log.debug("Rules organized into {} categories", rulesByCategory.size());
    }

    /**
     * Get all rules sorted by priority (highest first).
     */
    public List<OracleRule> getRulesByPriority() {
        return Collections.unmodifiableList(sortedByPriority);
    }

    /**
     * Get rules for a specific category.
     */
    public List<OracleRule> getRulesByCategory(String category) {
        return rulesByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Get all categories.
     */
    public Set<String> getCategories() {
        return rulesByCategory.keySet();
    }

    /**
     * Get total rule count.
     */
    public int getRuleCount() {
        return allRules.size();
    }

    /**
     * Load Oracle function rules from config.
     */
    @SuppressWarnings("unchecked")
    private static List<OracleRule> loadFunctionRules(Map<String, Object> config) {
        List<OracleRule> rules = new ArrayList<>();

        List<Map<String, Object>> functions =
                (List<Map<String, Object>>) config.get("oracle_functions");

        if (functions != null) {
            for (Map<String, Object> func : functions) {
                String name = (String) func.get("name");
                String category = (String) func.get("category");
                int priority = (int) func.get("priority");

                rules.add(new OracleRule.FunctionRule(name, category, priority));
            }
            log.debug("Loaded {} function rules", rules.size());
        }

        return rules;
    }

    /**
     * Load Oracle keyword rules from config.
     */
    @SuppressWarnings("unchecked")
    private static List<OracleRule> loadKeywordRules(Map<String, Object> config) {
        List<OracleRule> rules = new ArrayList<>();

        List<Map<String, Object>> keywords =
                (List<Map<String, Object>>) config.get("oracle_keywords");

        if (keywords != null) {
            for (Map<String, Object> kw : keywords) {
                String keyword = (String) kw.get("keyword");
                String category = (String) kw.get("category");
                int priority = (int) kw.get("priority");

                rules.add(new OracleRule.KeywordRule(keyword, category, priority));
            }
            log.debug("Loaded {} keyword rules", rules.size());
        }

        return rules;
    }

    /**
     * Load Oracle pattern rules (regex-based) from config.
     */
    @SuppressWarnings("unchecked")
    private static List<OracleRule> loadPatternRules(Map<String, Object> config) {
        List<OracleRule> rules = new ArrayList<>();

        // Load package patterns
        List<Map<String, Object>> packagePatterns =
                (List<Map<String, Object>>) config.get("oracle_package_patterns");

        if (packagePatterns != null) {
            for (Map<String, Object> pp : packagePatterns) {
                String pattern = (String) pp.get("pattern");
                String category = (String) pp.get("category");
                int priority = (int) pp.get("priority");
                String name = "PATTERN_" + category;

                rules.add(new OracleRule.PatternRule(name, pattern, category,
                        priority, "Package pattern"));
            }
        }

        // Load hint patterns
        List<Map<String, Object>> hintPatterns =
                (List<Map<String, Object>>) config.get("oracle_hint_patterns");

        if (hintPatterns != null) {
            for (Map<String, Object> hp : hintPatterns) {
                String pattern = (String) hp.get("pattern");
                String category = (String) hp.get("category");
                int priority = (int) hp.get("priority");
                String description = (String) hp.get("description");
                String name = "HINT_PATTERN";

                rules.add(new OracleRule.PatternRule(name, pattern, category,
                        priority, description));
            }
        }

        // Load join syntax patterns
        List<Map<String, Object>> joinPatterns =
                (List<Map<String, Object>>) config.get("oracle_join_syntax");

        if (joinPatterns != null) {
            for (Map<String, Object> jp : joinPatterns) {
                String pattern = (String) jp.get("pattern");
                String category = (String) jp.get("category");
                int priority = (int) jp.get("priority");
                String description = (String) jp.get("description");
                String name = "JOIN_SYNTAX";

                rules.add(new OracleRule.PatternRule(name, pattern, category,
                        priority, description));
            }
        }

        log.debug("Loaded {} pattern rules", rules.size());
        return rules;
    }

    /**
     * Load Oracle clause rules from config.
     */
    @SuppressWarnings("unchecked")
    private static List<OracleRule> loadClauseRules(Map<String, Object> config) {
        List<OracleRule> rules = new ArrayList<>();

        List<Map<String, Object>> clauses =
                (List<Map<String, Object>>) config.get("oracle_clauses");

        if (clauses != null) {
            for (Map<String, Object> clause : clauses) {
                String clauseName = (String) clause.get("clause");
                String category = (String) clause.get("category");
                int priority = (int) clause.get("priority");
                String description = (String) clause.get("description");

                rules.add(new OracleRule.ClauseRule(clauseName, category,
                        priority, description));
            }
            log.debug("Loaded {} clause rules", rules.size());
        }

        return rules;
    }

    /**
     * Load Oracle data type rules from config.
     */
    @SuppressWarnings("unchecked")
    private static List<OracleRule> loadDataTypeRules(Map<String, Object> config) {
        List<OracleRule> rules = new ArrayList<>();

        List<Map<String, Object>> dataTypes =
                (List<Map<String, Object>>) config.get("oracle_data_types");

        if (dataTypes != null) {
            for (Map<String, Object> dt : dataTypes) {
                String type = (String) dt.get("type");
                String category = (String) dt.get("category");
                int priority = (int) dt.get("priority");

                rules.add(new OracleRule.DataTypeRule(type, category, priority));
            }
            log.debug("Loaded {} data type rules", rules.size());
        }

        return rules;
    }
}