package com.enterprise.mybatis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File utility methods for MyBatis Oracle Splitter.
 */
public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Create default oracle-rules.yaml if it doesn't exist.
     */
    public static void createDefaultRulesFile(Path outputPath) throws IOException {
        String defaultRules = """
# Oracle-Specific SQL Detection Rules
version: "1.0"
description: "Oracle SQL feature detection rules for MyBatis mapper extraction"

oracle_functions:
  - name: "NVL"
    category: "NULL_HANDLING"
    priority: 100
  - name: "NVL2"
    category: "NULL_HANDLING"
    priority: 100
  - name: "DECODE"
    category: "CONDITIONAL"
    priority: 100
  - name: "SYSDATE"
    category: "DATE_TIME"
    priority: 100
  - name: "SYSTIMESTAMP"
    category: "DATE_TIME"
    priority: 100
  - name: "LISTAGG"
    category: "AGGREGATE"
    priority: 100

oracle_keywords:
  - keyword: "ROWNUM"
    category: "PSEUDO_COLUMN"
    priority: 100
  - keyword: "DUAL"
    category: "PSEUDO_TABLE"
    priority: 100
  - keyword: "NEXTVAL"
    category: "SEQUENCE"
    priority: 100
  - keyword: "CURRVAL"
    category: "SEQUENCE"
    priority: 100
  - keyword: "CONNECT BY"
    category: "HIERARCHICAL"
    priority: 100
  - keyword: "START WITH"
    category: "HIERARCHICAL"
    priority: 100

oracle_package_patterns:
  - pattern: "DBMS_\\\\w+"
    category: "PACKAGE"
    priority: 95

oracle_hint_patterns:
  - pattern: "/\\\\*\\\\+\\\\s*\\\\w+"
    category: "HINT"
    priority: 85
    description: "Oracle optimizer hints"

oracle_clauses:
  - clause: "MODEL"
    category: "ANALYTICAL"
    priority: 90
    description: "MODEL clause for array processing"
  - clause: "MATCH_RECOGNIZE"
    category: "ANALYTICAL"
    priority: 90
    description: "Pattern matching in SQL"

oracle_data_types:
  - type: "VARCHAR2"
    category: "DATA_TYPE"
    priority: 80
  - type: "NUMBER"
    category: "DATA_TYPE"
    priority: 80

oracle_join_syntax:
  - pattern: "\\\\(\\\\+\\\\)"
    category: "JOIN"
    priority: 100
    description: "Oracle outer join syntax"
""";

        Files.writeString(outputPath, defaultRules);
        log.info("Created default rules file: {}", outputPath);
    }

    /**
     * Validate that a path is a regular file.
     */
    public static boolean isRegularFile(Path path) {
        return Files.exists(path) && Files.isRegularFile(path);
    }

    /**
     * Get file extension.
     */
    public static String getExtension(Path path) {
        String fileName = path.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    /**
     * Get filename without extension.
     */
    public static String getBaseName(Path path) {
        String fileName = path.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }
}