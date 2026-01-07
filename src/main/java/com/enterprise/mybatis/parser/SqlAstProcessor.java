package com.enterprise.mybatis.parser;

import com.enterprise.mybatis.engine.GlobalOracleFragmentRegistry;
import com.enterprise.mybatis.engine.OracleRuleEngine;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AST-based SQL processor that extracts Oracle-specific constructs from SQL.
 *
 * This processor:
 * - Parses SQL into Abstract Syntax Tree
 * - Identifies Oracle-specific nodes using rule engine
 * - Extracts those nodes into fragments
 * - Replaces them with <include> references
 * - Returns rewritten SQL
 *
 * Design: AST-based processing is more reliable than regex for complex SQL.
 * However, we maintain text-based fallback for unparseable SQL (MyBatis expressions, etc.)
 */
public class SqlAstProcessor {
    private static final Logger log = LoggerFactory.getLogger(SqlAstProcessor.class);

    private final OracleRuleEngine ruleEngine;
    private final GlobalOracleFragmentRegistry fragmentRegistry;

    public SqlAstProcessor(OracleRuleEngine ruleEngine,
                           GlobalOracleFragmentRegistry fragmentRegistry) {
        this.ruleEngine = ruleEngine;
        this.fragmentRegistry = fragmentRegistry;
    }

    /**
     * Process SQL statement: extract Oracle features and rewrite to common SQL.
     *
     * @param sqlText Original SQL text
     * @param statementId Statement ID from mapper
     * @param sourceFile Source mapper file
     * @return Processing result with rewritten SQL and fragment references
     */
    public ProcessingResult process(String sqlText, String statementId, String sourceFile) {
        log.debug("Processing SQL statement: {}", statementId);

        ProcessingResult result = new ProcessingResult(sqlText, statementId);

        // Analyze SQL for Oracle features
        var detection = ruleEngine.analyze(sqlText, statementId);

        if (!detection.hasOracleFeatures()) {
            log.debug("No Oracle features found in: {}", statementId);
            result.setRewrittenSql(sqlText);
            result.setModified(false);
            return result;
        }

        // Try AST-based extraction
        try {
            Statement statement = CCJSqlParserUtil.parse(sqlText);
            String rewritten = processWithAst(statement, sqlText, detection,
                    sourceFile, statementId, result);
            result.setRewrittenSql(rewritten);
            result.setModified(true);
        } catch (Exception e) {
            log.warn("AST parsing failed for {}, falling back to text processing: {}",
                    statementId, e.getMessage());
            // Fallback to text-based extraction
            String rewritten = processWithText(sqlText, detection,
                    sourceFile, statementId, result);
            result.setRewrittenSql(rewritten);
            result.setModified(true);
        }

        log.info("Processed {}: extracted {} fragment(s)",
                statementId, result.getFragmentIds().size());

        return result;
    }

    /**
     * AST-based processing for parseable SQL.
     */
    private String processWithAst(Statement statement, String sqlText,
                                  OracleRuleEngine.DetectionResult detection,
                                  String sourceFile, String statementId,
                                  ProcessingResult result) {

        log.debug("Using AST-based extraction for: {}", statementId);

        // For complex Oracle features, extract entire clauses or expressions
        // This is a simplified approach - production code would use visitor pattern

        String rewritten = sqlText;

        // Extract Oracle-specific constructs identified by rules
        for (var rule : detection.getMatchedRules()) {
            String category = rule.getCategory();

            // Determine what to extract based on category
            switch (category) {
                case "HIERARCHICAL" -> {
                    // Extract entire CONNECT BY clause
                    rewritten = extractHierarchicalClause(rewritten, sourceFile,
                            statementId, result);
                }
                case "PSEUDO_COLUMN" -> {
                    // Extract ROWNUM/ROWID expressions
                    rewritten = extractPseudoColumns(rewritten, sourceFile,
                            statementId, result);
                }
                case "SEQUENCE" -> {
                    // Extract sequence.NEXTVAL/CURRVAL
                    rewritten = extractSequences(rewritten, sourceFile,
                            statementId, result);
                }
                case "NULL_HANDLING", "CONDITIONAL", "DATE_TIME", "AGGREGATE" -> {
                    // Extract function calls
                    rewritten = extractOracleFunctions(rewritten, rule, sourceFile,
                            statementId, result);
                }
                case "HINT" -> {
                    // Extract optimizer hints
                    rewritten = extractHints(rewritten, sourceFile, statementId, result);
                }
                default -> {
                    log.debug("Category {} handled by generic extraction", category);
                }
            }
        }

        return rewritten;
    }

    /**
     * Text-based processing for unparseable SQL or MyBatis expressions.
     */
    private String processWithText(String sqlText,
                                   OracleRuleEngine.DetectionResult detection,
                                   String sourceFile, String statementId,
                                   ProcessingResult result) {

        log.debug("Using text-based extraction for: {}", statementId);

        String rewritten = sqlText;

        // Extract each Oracle token found
        for (String token : detection.getOracleTokens()) {
            // Create fragment for this token
            String category = detection.getPrimaryCategory();
            String fragmentId = fragmentRegistry.registerFragment(
                    token, sourceFile, statementId, category
            );

            result.addFragmentId(fragmentId);

            // Replace with <include> reference
            // Note: In production, this would need more sophisticated replacement logic
            String includeRef = String.format("<include refid=\"%s\"/>", fragmentId);
            rewritten = rewritten.replace(token, includeRef);
        }

        return rewritten;
    }

    /**
     * Extract CONNECT BY hierarchical query clauses.
     */
    private String extractHierarchicalClause(String sql, String sourceFile,
                                             String statementId, ProcessingResult result) {
        // Look for START WITH ... CONNECT BY pattern
        int startWithPos = sql.toUpperCase().indexOf("START WITH");
        int connectByPos = sql.toUpperCase().indexOf("CONNECT BY");

        if (startWithPos < 0 && connectByPos < 0) {
            return sql;
        }

        // Extract the entire hierarchical clause
        int startPos = startWithPos >= 0 ? startWithPos : connectByPos;
        int endPos = findClauseEnd(sql, startPos);

        String clause = sql.substring(startPos, endPos).trim();

        // Register as fragment
        String fragmentId = fragmentRegistry.registerFragment(
                clause, sourceFile, statementId, "HIERARCHICAL"
        );
        result.addFragmentId(fragmentId);

        // Replace with include
        String before = sql.substring(0, startPos);
        String after = sql.substring(endPos);
        String includeRef = String.format("<include refid=\"%s\"/>", fragmentId);

        return before + includeRef + after;
    }

    /**
     * Extract ROWNUM, ROWID pseudo-column references.
     */
    private String extractPseudoColumns(String sql, String sourceFile,
                                        String statementId, ProcessingResult result) {
        String rewritten = sql;

        // Extract ROWNUM conditions (e.g., WHERE ROWNUM <= 10)
        if (sql.toUpperCase().contains("ROWNUM")) {
            // Simplified: extract entire WHERE clause with ROWNUM
            int wherePos = sql.toUpperCase().indexOf("WHERE");
            if (wherePos >= 0) {
                int clauseEnd = findClauseEnd(sql, wherePos);
                String whereClause = sql.substring(wherePos, clauseEnd);

                if (whereClause.toUpperCase().contains("ROWNUM")) {
                    String fragmentId = fragmentRegistry.registerFragment(
                            whereClause, sourceFile, statementId, "PSEUDO_COLUMN"
                    );
                    result.addFragmentId(fragmentId);

                    String includeRef = String.format("<include refid=\"%s\"/>", fragmentId);
                    rewritten = sql.substring(0, wherePos) + includeRef +
                            sql.substring(clauseEnd);
                }
            }
        }

        return rewritten;
    }

    /**
     * Extract sequence.NEXTVAL/CURRVAL references.
     */
    private String extractSequences(String sql, String sourceFile,
                                    String statementId, ProcessingResult result) {
        String rewritten = sql;

        // Find sequence references (pattern: identifier.NEXTVAL or identifier.CURRVAL)
        String[] tokens = sql.split("\\s+");
        for (String token : tokens) {
            if (token.toUpperCase().endsWith(".NEXTVAL") ||
                    token.toUpperCase().endsWith(".CURRVAL")) {

                String fragmentId = fragmentRegistry.registerFragment(
                        token, sourceFile, statementId, "SEQUENCE"
                );
                result.addFragmentId(fragmentId);

                String includeRef = String.format("<include refid=\"%s\"/>", fragmentId);
                rewritten = rewritten.replace(token, includeRef);
            }
        }

        return rewritten;
    }

    /**
     * Extract Oracle-specific function calls.
     */
    private String extractOracleFunctions(String sql,
                                          com.enterprise.mybatis.engine.OracleRule rule,
                                          String sourceFile, String statementId,
                                          ProcessingResult result) {
        // This would use more sophisticated extraction in production
        // For now, we register the function as detected

        if (rule instanceof com.enterprise.mybatis.engine.OracleRule.FunctionRule funcRule) {
            String functionName = funcRule.getFunctionName();
            // Find and extract function calls
            // Simplified: just track that we found it
            log.debug("Found Oracle function: {}", functionName);
        }

        return sql;
    }

    /**
     * Extract Oracle optimizer hints.
     */
    private String extractHints(String sql, String sourceFile,
                                String statementId, ProcessingResult result) {
        // Find /*+ ... */ patterns
        int hintStart = sql.indexOf("/*+");
        while (hintStart >= 0) {
            int hintEnd = sql.indexOf("*/", hintStart);
            if (hintEnd < 0) break;

            String hint = sql.substring(hintStart, hintEnd + 2);
            String fragmentId = fragmentRegistry.registerFragment(
                    hint, sourceFile, statementId, "HINT"
            );
            result.addFragmentId(fragmentId);

            String includeRef = String.format("<include refid=\"%s\"/>", fragmentId);
            sql = sql.substring(0, hintStart) + includeRef + sql.substring(hintEnd + 2);

            hintStart = sql.indexOf("/*+", hintStart + includeRef.length());
        }

        return sql;
    }

    /**
     * Find the end position of a SQL clause.
     */
    private int findClauseEnd(String sql, int startPos) {
        String[] clauseKeywords = {
                "ORDER BY", "GROUP BY", "HAVING", "UNION", "EXCEPT",
                "INTERSECT", "LIMIT", "OFFSET", "FETCH"
        };

        int endPos = sql.length();
        String upperSql = sql.toUpperCase();

        for (String keyword : clauseKeywords) {
            int pos = upperSql.indexOf(keyword, startPos);
            if (pos > startPos && pos < endPos) {
                endPos = pos;
            }
        }

        return endPos;
    }

    /**
     * Result of SQL processing.
     */
    public static class ProcessingResult {
        private final String originalSql;
        private final String statementId;
        private String rewrittenSql;
        private boolean modified;
        private final List<String> fragmentIds;

        public ProcessingResult(String originalSql, String statementId) {
            this.originalSql = originalSql;
            this.statementId = statementId;
            this.fragmentIds = new ArrayList<>();
            this.modified = false;
        }

        public void addFragmentId(String fragmentId) {
            fragmentIds.add(fragmentId);
        }

        public String getOriginalSql() {
            return originalSql;
        }

        public String getRewrittenSql() {
            return rewrittenSql;
        }

        public void setRewrittenSql(String rewrittenSql) {
            this.rewrittenSql = rewrittenSql;
        }

        public boolean isModified() {
            return modified;
        }

        public void setModified(boolean modified) {
            this.modified = modified;
        }

        public List<String> getFragmentIds() {
            return Collections.unmodifiableList(fragmentIds);
        }

        public String getStatementId() {
            return statementId;
        }
    }
}