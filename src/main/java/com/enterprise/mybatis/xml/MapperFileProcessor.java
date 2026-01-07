package com.enterprise.mybatis.xml;

import com.enterprise.mybatis.engine.GlobalOracleFragmentRegistry;
import com.enterprise.mybatis.parser.SqlAstProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Processes MyBatis mapper XML files to extract Oracle-specific SQL.
 *
 * This processor:
 * - Parses mapper XML using DOM
 * - Identifies SQL statements (select, insert, update, delete, sql)
 * - Processes each statement through SqlAstProcessor
 * - Rewrites statements with <include> references
 * - Generates modified mapper XML
 *
 * Design: DOM-based processing maintains XML structure and formatting.
 */
public class MapperFileProcessor {
    private static final Logger log = LoggerFactory.getLogger(MapperFileProcessor.class);

    private final SqlAstProcessor sqlProcessor;
    private final GlobalOracleFragmentRegistry fragmentRegistry;

    private static final String[] SQL_STATEMENT_TAGS = {
            "select", "insert", "update", "delete", "sql"
    };

    public MapperFileProcessor(SqlAstProcessor sqlProcessor,
                               GlobalOracleFragmentRegistry fragmentRegistry) {
        this.sqlProcessor = sqlProcessor;
        this.fragmentRegistry = fragmentRegistry;
    }

    /**
     * Process a single mapper file.
     *
     * @param inputFile Path to input mapper XML
     * @param outputFile Path for rewritten mapper XML
     * @return Processing statistics
     */
    public ProcessingStats processMapperFile(Path inputFile, Path outputFile) throws Exception {
        log.info("Processing mapper file: {}", inputFile.getFileName());

        ProcessingStats stats = new ProcessingStats(inputFile.toString());

        // Parse XML with custom DTD resolver
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);

        // Disable external DTD/entity resolution for security
        try {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            log.warn("Could not set DTD features: {}", e.getMessage());
        }

        DocumentBuilder builder = factory.newDocumentBuilder();

        // Use custom entity resolver to handle DTD references locally
        builder.setEntityResolver(new DtdEntityResolver());

        Document doc = builder.parse(inputFile.toFile());

        // Process all SQL statements
        for (String tagName : SQL_STATEMENT_TAGS) {
            NodeList nodes = doc.getElementsByTagName(tagName);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                processStatement(element, inputFile.toString(), stats);
            }
        }

        // Write modified XML
        writeDocument(doc, outputFile);

        log.info("Completed processing: {} - {} statements, {} modified",
                inputFile.getFileName(), stats.totalStatements, stats.modifiedStatements);

        return stats;
    }

    /**
     * Process a single SQL statement element.
     */
    private void processStatement(Element element, String sourceFile, ProcessingStats stats) {
        stats.totalStatements++;

        // Get statement ID
        String statementId = element.getAttribute("id");
        if (statementId == null || statementId.isEmpty()) {
            statementId = "statement_" + stats.totalStatements;
        }

        // Extract SQL text from element
        String sqlText = extractSqlText(element);
        if (sqlText == null || sqlText.trim().isEmpty()) {
            log.debug("Skipping empty statement: {}", statementId);
            return;
        }

        // Process SQL through AST processor
        var result = sqlProcessor.process(sqlText, statementId, sourceFile);

        if (result.isModified()) {
            // Replace element content with rewritten SQL
            replaceElementContent(element, result.getRewrittenSql());
            stats.modifiedStatements++;
            stats.extractedFragments += result.getFragmentIds().size();

            log.debug("Modified statement {}: extracted {} fragment(s)",
                    statementId, result.getFragmentIds().size());
        }
    }

    /**
     * Extract SQL text from element, including all child text nodes.
     */
    private String extractSqlText(Element element) {
        StringBuilder sql = new StringBuilder();

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.TEXT_NODE ||
                    child.getNodeType() == Node.CDATA_SECTION_NODE) {
                sql.append(child.getNodeValue());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                // Handle nested elements like <if>, <where>, etc.
                Element childElement = (Element) child;
                String tagName = childElement.getTagName();

                // For dynamic SQL elements, preserve them but process their content
                if (isDynamicSqlElement(tagName)) {
                    sql.append("<").append(tagName);

                    // Preserve attributes
                    NamedNodeMap attrs = childElement.getAttributes();
                    for (int j = 0; j < attrs.getLength(); j++) {
                        Node attr = attrs.item(j);
                        sql.append(" ").append(attr.getNodeName())
                                .append("=\"").append(attr.getNodeValue()).append("\"");
                    }

                    sql.append(">");
                    sql.append(extractSqlText(childElement));
                    sql.append("</").append(tagName).append(">");
                } else {
                    sql.append(extractSqlText(childElement));
                }
            }
        }

        return sql.toString();
    }

    /**
     * Replace element content with new SQL text.
     */
    private void replaceElementContent(Element element, String newSqlText) {
        // Remove all child nodes
        while (element.hasChildNodes()) {
            element.removeChild(element.getFirstChild());
        }

        // Add new content as text node (not CDATA) for cleaner output
        Document doc = element.getOwnerDocument();

        // Format the SQL with proper indentation
        String indentedSql = "\n        " + newSqlText.trim() + "\n    ";

        Text textNode = doc.createTextNode(indentedSql);
        element.appendChild(textNode);
    }

    /**
     * Check if element is a MyBatis dynamic SQL element.
     */
    private boolean isDynamicSqlElement(String tagName) {
        return switch (tagName) {
            case "if", "choose", "when", "otherwise", "trim", "where",
                 "set", "foreach", "bind", "include" -> true;
            default -> false;
        };
    }

    /**
     * Write DOM document to file with proper formatting.
     */
    private void writeDocument(Document doc, Path outputFile) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        // Configure output properties
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                "-//mybatis.org//DTD Mapper 3.0//EN");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                "http://mybatis.org/dtd/mybatis-3-mapper.dtd");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);

        // Ensure output directory exists
        Files.createDirectories(outputFile.getParent());

        StreamResult result = new StreamResult(outputFile.toFile());
        transformer.transform(source, result);

        log.debug("Wrote mapper file: {}", outputFile);
    }

    /**
     * Statistics for mapper file processing.
     */
    public static class ProcessingStats {
        private final String fileName;
        private int totalStatements;
        private int modifiedStatements;
        private int extractedFragments;

        public ProcessingStats(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }

        public int getTotalStatements() {
            return totalStatements;
        }

        public int getModifiedStatements() {
            return modifiedStatements;
        }

        public int getExtractedFragments() {
            return extractedFragments;
        }

        public boolean hasModifications() {
            return modifiedStatements > 0;
        }

        @Override
        public String toString() {
            return String.format("ProcessingStats[file=%s, total=%d, modified=%d, fragments=%d]",
                    fileName, totalStatements, modifiedStatements, extractedFragments);
        }
    }
}