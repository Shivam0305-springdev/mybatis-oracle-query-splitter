package com.enterprise.mybatis.engine;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Represents a single rule for detecting Oracle-specific SQL constructs.
 *
 * Rules are loaded from oracle-rules.yaml and define:
 * - What to detect (function name, keyword, pattern)
 * - How to detect it (AST inspection, pattern matching)
 * - Priority for evaluation
 * - Category for organization
 *
 * This design allows extending detection capabilities without modifying core engine logic.
 */
public abstract class OracleRule {

    protected final String name;
    protected final String category;
    protected final int priority;
    protected final String description;

    protected OracleRule(String name, String category, int priority, String description) {
        this.name = name;
        this.category = category;
        this.priority = priority;
        this.description = description != null ? description : "";
    }

    /**
     * Check if this rule matches the given SQL statement.
     * Returns true if Oracle-specific construct is detected.
     */
    public abstract boolean matches(Statement statement, String sqlText);

    /**
     * Check if this rule matches the given SQL expression.
     * Used for expression-level detection.
     */
    public abstract boolean matches(Expression expression, String sqlText);

    /**
     * Get all Oracle-specific tokens/patterns detected by this rule.
     * Used for fragment extraction.
     */
    public abstract Set<String> extractOracleTokens(String sqlText);

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("OracleRule[name=%s, category=%s, priority=%d]",
                name, category, priority);
    }

    /**
     * Rule that detects Oracle-specific function names.
     */
    public static class FunctionRule extends OracleRule {
        private final String functionName;

        public FunctionRule(String functionName, String category, int priority) {
            super(functionName, category, priority, "Oracle function: " + functionName);
            this.functionName = functionName.toUpperCase();
        }

        @Override
        public boolean matches(Statement statement, String sqlText) {
            return containsFunction(sqlText);
        }

        @Override
        public boolean matches(Expression expression, String sqlText) {
            return containsFunction(sqlText);
        }

        @Override
        public Set<String> extractOracleTokens(String sqlText) {
            if (containsFunction(sqlText)) {
                return Set.of(functionName);
            }
            return Set.of();
        }

        private boolean containsFunction(String sqlText) {
            String upperSql = sqlText.toUpperCase();
            // Look for function name followed by opening parenthesis
            String pattern = "\\b" + Pattern.quote(functionName) + "\\s*\\(";
            return Pattern.compile(pattern).matcher(upperSql).find();
        }

        public String getFunctionName() {
            return functionName;
        }
    }

    /**
     * Rule that detects Oracle-specific keywords.
     */
    public static class KeywordRule extends OracleRule {
        private final String keyword;
        private final Pattern pattern;

        public KeywordRule(String keyword, String category, int priority) {
            super(keyword, category, priority, "Oracle keyword: " + keyword);
            this.keyword = keyword.toUpperCase();
            // Create word boundary pattern for accurate detection
            this.pattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b",
                    Pattern.CASE_INSENSITIVE);
        }

        @Override
        public boolean matches(Statement statement, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public boolean matches(Expression expression, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public Set<String> extractOracleTokens(String sqlText) {
            if (pattern.matcher(sqlText).find()) {
                return Set.of(keyword);
            }
            return Set.of();
        }

        public String getKeyword() {
            return keyword;
        }
    }

    /**
     * Rule that detects Oracle-specific patterns using regex.
     * Used for complex constructs like hints, package calls, etc.
     */
    public static class PatternRule extends OracleRule {
        private final String patternString;
        private final Pattern pattern;

        public PatternRule(String name, String patternString, String category,
                           int priority, String description) {
            super(name, category, priority, description);
            this.patternString = patternString;
            this.pattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
        }

        @Override
        public boolean matches(Statement statement, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public boolean matches(Expression expression, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public Set<String> extractOracleTokens(String sqlText) {
            var matcher = pattern.matcher(sqlText);
            if (matcher.find()) {
                return Set.of(matcher.group());
            }
            return Set.of();
        }

        public String getPatternString() {
            return patternString;
        }
    }

    /**
     * Rule that detects Oracle-specific clauses (MODEL, MATCH_RECOGNIZE, etc.)
     */
    public static class ClauseRule extends OracleRule {
        private final String clause;
        private final Pattern pattern;

        public ClauseRule(String clause, String category, int priority, String description) {
            super(clause, category, priority, description);
            this.clause = clause.toUpperCase();
            // Match clause as a distinct keyword
            this.pattern = Pattern.compile("\\b" + Pattern.quote(clause) + "\\b",
                    Pattern.CASE_INSENSITIVE);
        }

        @Override
        public boolean matches(Statement statement, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public boolean matches(Expression expression, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public Set<String> extractOracleTokens(String sqlText) {
            if (pattern.matcher(sqlText).find()) {
                return Set.of(clause);
            }
            return Set.of();
        }

        public String getClause() {
            return clause;
        }
    }

    /**
     * Rule that detects Oracle-specific data types.
     */
    public static class DataTypeRule extends OracleRule {
        private final String dataType;
        private final Pattern pattern;

        public DataTypeRule(String dataType, String category, int priority) {
            super(dataType, category, priority, "Oracle data type: " + dataType);
            this.dataType = dataType.toUpperCase();
            // Match data type in context (after column names, in CAST, etc.)
            this.pattern = Pattern.compile("\\b" + Pattern.quote(dataType) + "\\b",
                    Pattern.CASE_INSENSITIVE);
        }

        @Override
        public boolean matches(Statement statement, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public boolean matches(Expression expression, String sqlText) {
            return pattern.matcher(sqlText).find();
        }

        @Override
        public Set<String> extractOracleTokens(String sqlText) {
            if (pattern.matcher(sqlText).find()) {
                return Set.of(dataType);
            }
            return Set.of();
        }

        public String getDataType() {
            return dataType;
        }
    }
}