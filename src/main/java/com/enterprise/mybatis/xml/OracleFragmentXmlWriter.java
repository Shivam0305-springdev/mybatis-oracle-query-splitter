package com.enterprise.mybatis.xml;

import com.enterprise.mybatis.engine.GlobalOracleFragmentRegistry;
import com.enterprise.mybatis.engine.GlobalOracleFragmentRegistry.OracleFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writes consolidated oracle-fragments.xml file containing all extracted Oracle SQL.
 *
 * This writer:
 * - Generates well-formed MyBatis mapper XML
 * - Groups fragments by category
 * - Adds metadata comments
 * - Tags all fragments with databaseId="oracle"
 * - Ensures proper formatting and readability
 *
 * Output structure:
 * <mapper namespace="oracle.fragments">
 *   <!-- Category: HIERARCHICAL -->
 *   <sql id="oracle_..." databaseId="oracle">...</sql>
 *
 *   <!-- Category: PSEUDO_COLUMN -->
 *   <sql id="oracle_..." databaseId="oracle">...</sql>
 *   ...
 * </mapper>
 */
public class OracleFragmentXmlWriter {
    private static final Logger log = LoggerFactory.getLogger(OracleFragmentXmlWriter.class);

    private static final String NAMESPACE = "oracle.fragments";
    private static final String MAPPER_DTD_PUBLIC = "-//mybatis.org//DTD Mapper 3.0//EN";
    private static final String MAPPER_DTD_SYSTEM = "http://mybatis.org/dtd/mybatis-3-mapper.dtd";

    private boolean useCdata = false;

    /**
     * Set whether to use CDATA sections.
     */
    public void setUseCdata(boolean useCdata) {
        this.useCdata = useCdata;
    }

    /**
     * Write all fragments from registry to oracle-fragments.xml.
     *
     * @param fragmentRegistry Source of all Oracle fragments
     * @param outputPath Path for oracle-fragments.xml
     */
    public void writeFragments(GlobalOracleFragmentRegistry fragmentRegistry,
                               Path outputPath) throws Exception {

        log.info("Writing Oracle fragments to: {}", outputPath);

        Collection<OracleFragment> allFragments = fragmentRegistry.getAllFragments();

        if (allFragments.isEmpty()) {
            log.warn("No Oracle fragments to write");
            writeEmptyMapper(outputPath);
            return;
        }

        // Group fragments by category for organized output
        Map<String, List<OracleFragment>> fragmentsByCategory = allFragments.stream()
                .collect(Collectors.groupingBy(
                        OracleFragment::getCategory,
                        TreeMap::new,
                        Collectors.toList()
                ));

        // Sort fragments within each category by ID
        fragmentsByCategory.values().forEach(list ->
                list.sort(Comparator.comparing(OracleFragment::getId))
        );

        // Write XML
        Files.createDirectories(outputPath.getParent());

        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            // Write XML declaration
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            // Write DOCTYPE
            writer.writeDTD(String.format(
                    "<!DOCTYPE mapper PUBLIC \"%s\" \"%s\">",
                    MAPPER_DTD_PUBLIC, MAPPER_DTD_SYSTEM
            ));
            writer.writeCharacters("\n");

            // Write mapper root element
            writer.writeStartElement("mapper");
            writer.writeAttribute("namespace", NAMESPACE);
            writer.writeCharacters("\n");

            // Write header comment
            writeHeaderComment(writer, fragmentRegistry);

            // Write fragments grouped by category
            for (Map.Entry<String, List<OracleFragment>> entry :
                    fragmentsByCategory.entrySet()) {

                String category = entry.getKey();
                List<OracleFragment> fragments = entry.getValue();

                writeCategorySection(writer, category, fragments);
            }

            // Write footer comment
            writeFooterComment(writer);

            // Close mapper element
            writer.writeEndElement();
            writer.writeCharacters("\n");

            writer.writeEndDocument();
            writer.close();
        }

        var stats = fragmentRegistry.getStatistics();
        log.info("Wrote {} Oracle fragments across {} categories",
                stats.getTotalFragments(), stats.getFragmentsByCategory().size());
    }

    /**
     * Write header comment with metadata.
     */
    private void writeHeaderComment(XMLStreamWriter writer,
                                    GlobalOracleFragmentRegistry registry)
            throws Exception {

        var stats = registry.getStatistics();
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        writer.writeCharacters("    ");
        writer.writeComment(String.format("""
            
                Oracle-Specific SQL Fragments
                
                This file contains all Oracle-specific SQL constructs extracted from MyBatis mappers.
                
                Generated: %s
                Total Fragments: %d
                Source Files: %d
                Categories: %d
                
                Usage: Include these fragments in your mappers using:
                    <include refid="oracle_fragment_id"/>
                
                These fragments are tagged with databaseId="oracle" to ensure they only
                execute in Oracle database environments.
                
            """, timestamp, stats.getTotalFragments(), stats.getUniqueSourceFiles(),
                stats.getFragmentsByCategory().size()));
        writer.writeCharacters("\n\n");
    }

    /**
     * Write a category section with all its fragments.
     */
    private void writeCategorySection(XMLStreamWriter writer, String category,
                                      List<OracleFragment> fragments) throws Exception {

        writer.writeCharacters("    ");
        writer.writeComment(String.format(" Category: %s (%d fragment%s) ",
                category, fragments.size(), fragments.size() == 1 ? "" : "s"));
        writer.writeCharacters("\n");

        for (OracleFragment fragment : fragments) {
            writeFragment(writer, fragment);
        }

        writer.writeCharacters("\n");
    }

    /**
     * Write a single SQL fragment.
     */
    private void writeFragment(XMLStreamWriter writer, OracleFragment fragment)
            throws Exception {

        writer.writeCharacters("    ");

        // Write <sql> element
        writer.writeStartElement("sql");
        writer.writeAttribute("id", fragment.getId());
        writer.writeAttribute("databaseId", "oracle");

        // Add metadata comment
        writer.writeCharacters("\n        ");
        writer.writeComment(String.format(" Source: %s | Statement: %s ",
                fragment.getSourceFile(), fragment.getStatementId()));

        // Write SQL content
        writer.writeCharacters("\n        ");

        if (useCdata) {
            // Use CDATA section
            writer.writeCData("\n            " + fragment.getSqlContent().trim() + "\n        ");
        } else {
            // Use plain text with XML escaping (cleaner output)
            writer.writeCharacters(formatSqlContent(fragment.getSqlContent()));
        }

        writer.writeCharacters("\n    ");

        writer.writeEndElement(); // </sql>
        writer.writeCharacters("\n");
    }

    /**
     * Format SQL content for readability.
     */
    private String formatSqlContent(String sql) {
        // Basic formatting: clean up and escape special characters if needed
        String formatted = sql.trim();

        // Escape XML special characters
        formatted = formatted
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");

        // For multi-line SQL, preserve formatting
        if (formatted.contains("\n")) {
            String[] lines = formatted.split("\n");
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                result.append(line.trim()).append("\n        ");
            }
            return result.toString().trim();
        }

        return formatted;
    }

    /**
     * Write footer comment.
     */
    private void writeFooterComment(XMLStreamWriter writer) throws Exception {
        writer.writeCharacters("    ");
        writer.writeComment(" End of Oracle-Specific SQL Fragments ");
        writer.writeCharacters("\n");
    }

    /**
     * Write an empty mapper file when no fragments found.
     */
    private void writeEmptyMapper(Path outputPath) throws Exception {
        Files.createDirectories(outputPath.getParent());

        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            XMLStreamWriter writer = factory.createXMLStreamWriter(fos, "UTF-8");

            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeCharacters("\n");

            writer.writeDTD(String.format(
                    "<!DOCTYPE mapper PUBLIC \"%s\" \"%s\">",
                    MAPPER_DTD_PUBLIC, MAPPER_DTD_SYSTEM
            ));
            writer.writeCharacters("\n");

            writer.writeStartElement("mapper");
            writer.writeAttribute("namespace", NAMESPACE);
            writer.writeCharacters("\n    ");

            writer.writeComment(" No Oracle-specific SQL fragments found ");
            writer.writeCharacters("\n");

            writer.writeEndElement();
            writer.writeCharacters("\n");

            writer.writeEndDocument();
            writer.close();
        }

        log.info("Created empty oracle-fragments.xml");
    }
}