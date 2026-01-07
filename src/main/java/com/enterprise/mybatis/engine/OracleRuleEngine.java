package com.enterprise.mybatis.engine;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Core rule evaluation engine that determines which SQL constructs are Oracle-specific.
 *
 * This engine:
 * - Applies rules to SQL statements
 * - Tracks which rules matched
 * - Extracts Oracle-specific tokens/patterns
 * - Provides detection results for fragment extraction
 *
 * Design: Rules are evaluated in priority order. Once a match is found,
 * the engine continues to find ALL matches for comprehensive extraction.
 */
public class OracleRuleEngine {
    private static final Logger log = LoggerFactory.getLogger(OracleRuleEngine.class);

    private final OracleRuleCatalog ruleCatalog;

    public OracleRuleEngine(OracleRuleCatalog ruleCatalog) {
        this.ruleCatalog = ruleCatalog;
    }

    /**
     * Analyze SQL text and detect all Oracle-specific constructs.
     *
     * @param sqlText The SQL statement to analyze
     * @param statementId Identifier for logging purposes
     * @return Detection result with matched rules and tokens
     */
    public DetectionResult analyze(String sqlText, String statementId) {
        log.debug("Analyzing SQL statement: {}", statementId);

        DetectionResult result = new DetectionResult(sqlText, statementId);

        // Parse SQL into AST for structural analysis
        Statement statement = null;
        try {
            statement = CCJSqlParserUtil.parse(sqlText);
        } catch (Exception e) {
            log.warn("Failed to parse SQL for {}, using text-only analysis: {}",
                    statementId, e.getMessage());
            // Continue with text-only analysis
        }

        // Apply all rules in priority order
        for (OracleRule rule : ruleCatalog.getRulesByPriority()) {
            boolean matches = false;

            try {
                if (statement != null) {
                    matches = rule.matches(statement, sqlText);
                } else {
                    // Fallback to text-only matching for unparseable SQL
                    matches = rule.matches((Statement) null, sqlText);
                }

                if (matches) {
                    result.addMatchedRule(rule);
                    Set<String> tokens = rule.extractOracleTokens(sqlText);
                    result.addOracleTokens(tokens);

                    log.debug("  ✓ Rule matched: {} (category: {}, priority: {})",
                            rule.getName(), rule.getCategory(), rule.getPriority());
                }
            } catch (Exception e) {
                log.error("Error applying rule {}: {}", rule.getName(), e.getMessage());
            }
        }

        if (result.hasOracleFeatures()) {
            log.info("Statement {} contains Oracle-specific features: {} rule(s) matched",
                    statementId, result.getMatchedRules().size());
        } else {
            log.debug("Statement {} is database-agnostic", statementId);
        }

        return result;
    }

    /**
     * Quick check if SQL contains ANY Oracle-specific features.
     */
    public boolean containsOracleFeatures(String sqlText) {
        return analyze(sqlText, "quick-check").hasOracleFeatures();
    }

    /**
     * Get rule catalog for inspection.
     */
    public OracleRuleCatalog getRuleCatalog() {
        return ruleCatalog;
    }

    /**
     * Result of Oracle feature detection.
     */
    public static class DetectionResult {
        private final String sqlText;
        private final String statementId;
        private final List<OracleRule> matchedRules;
        private final Set<String> oracleTokens;
        private final Map<String, Integer> categoryMatches;

        public DetectionResult(String sqlText, String statementId) {
            this.sqlText = sqlText;
            this.statementId = statementId;
            this.matchedRules = new ArrayList<>();
            this.oracleTokens = new LinkedHashSet<>();
            this.categoryMatches = new HashMap<>();
        }

        void addMatchedRule(OracleRule rule) {
            matchedRules.add(rule);
            categoryMatches.merge(rule.getCategory(), 1, Integer::sum);
        }

        void addOracleTokens(Set<String> tokens) {
            oracleTokens.addAll(tokens);
        }

        public boolean hasOracleFeatures() {
            return !matchedRules.isEmpty();
        }

        public String getSqlText() {
            return sqlText;
        }

        public String getStatementId() {
            return statementId;
        }

        public List<OracleRule> getMatchedRules() {
            return Collections.unmodifiableList(matchedRules);
        }

        public Set<String> getOracleTokens() {
            return Collections.unmodifiableSet(oracleTokens);
        }

        public Map<String, Integer> getCategoryMatches() {
            return Collections.unmodifiableMap(categoryMatches);
        }

        /**
         * Get highest priority category matched.
         */
        public String getPrimaryCategory() {
            return matchedRules.stream()
                    .max(Comparator.comparingInt(OracleRule::getPriority))
                    .map(OracleRule::getCategory)
                    .orElse("UNKNOWN");
        }

        /**
         * Calculate complexity score based on number and type of matches.
         */
        public int getComplexityScore() {
            int score = 0;
            for (OracleRule rule : matchedRules) {
                // Weight by priority
                score += rule.getPriority() / 10;
            }
            return score;
        }

        @Override
        public String toString() {
            return String.format("DetectionResult[id=%s, oracleFeatures=%s, rules=%d, tokens=%d]",
                    statementId, hasOracleFeatures(), matchedRules.size(), oracleTokens.size());
        }
    }
}