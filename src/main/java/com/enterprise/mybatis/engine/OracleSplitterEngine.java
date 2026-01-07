package com.enterprise.mybatis.engine;

import com.enterprise.mybatis.parser.SqlAstProcessor;
import com.enterprise.mybatis.xml.MapperFileProcessor;
import com.enterprise.mybatis.xml.OracleFragmentXmlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestration engine for MyBatis Oracle SQL splitter.
 *
 * This engine coordinates:
 * - Rule loading and initialization
 * - Mapper file processing
 * - Fragment extraction and consolidation
 * - Output generation
 *
 * Architecture:
 *
 *   OracleSplitterEngine
 *         |
 *         +-- OracleRuleCatalog (loads rules from YAML)
 *         |
 *         +-- OracleRuleEngine (applies rules to detect Oracle SQL)
 *         |
 *         +-- GlobalOracleFragmentRegistry (tracks all fragments)
 *         |
 *         +-- SqlAstProcessor (extracts Oracle SQL using AST)
 *         |
 *         +-- MapperFileProcessor (rewrites mapper XMLs)
 *         |
 *         +-- OracleFragmentXmlWriter (generates oracle-fragments.xml)
 *
 * Design principle: Separation of concerns - each component has single responsibility.
 */
public class OracleSplitterEngine {
    private static final Logger log = LoggerFactory.getLogger(OracleSplitterEngine.class);

    private static final String ORACLE_FRAGMENTS_FILE = "oracle-fragments.xml";

    private final OracleRuleEngine ruleEngine;
    private final GlobalOracleFragmentRegistry fragmentRegistry;
    private final SqlAstProcessor sqlProcessor;
    private final MapperFileProcessor mapperProcessor;
    private final OracleFragmentXmlWriter fragmentWriter;

    /**
     * Initialize engine with rules from configuration file.
     */
    public OracleSplitterEngine(Path rulesPath) throws Exception {
        log.info("Initializing Oracle Splitter Engine");

        // Load rule catalog
        OracleRuleCatalog ruleCatalog = OracleRuleCatalog.fromYaml(rulesPath);
        log.info("Loaded {} Oracle detection rules", ruleCatalog.getRuleCount());

        // Initialize components
        this.ruleEngine = new OracleRuleEngine(ruleCatalog);
        this.fragmentRegistry = new GlobalOracleFragmentRegistry();
        this.sqlProcessor = new SqlAstProcessor(ruleEngine, fragmentRegistry);
        this.mapperProcessor = new MapperFileProcessor(sqlProcessor, fragmentRegistry);
        this.fragmentWriter = new OracleFragmentXmlWriter();

        log.info("Engine initialized successfully");
    }

    /**
     * Process multiple mapper files and generate output.
     *
     * @param inputFiles List of input mapper XML files
     * @param outputDir Output directory for rewritten mappers and fragments
     */
    public void process(List<Path> inputFiles, Path outputDir) throws Exception {
        log.info("Starting processing of {} mapper file(s)", inputFiles.size());
        log.info("Output directory: {}", outputDir);

        List<MapperFileProcessor.ProcessingStats> allStats = new ArrayList<>();

        // Process each mapper file
        for (Path inputFile : inputFiles) {
            try {
                Path outputFile = outputDir.resolve(inputFile.getFileName());
                var stats = mapperProcessor.processMapperFile(inputFile, outputFile);
                allStats.add(stats);

            } catch (Exception e) {
                log.error("Failed to process file: {}", inputFile, e);
                throw new RuntimeException("Processing failed for " + inputFile, e);
            }
        }

        // Write consolidated oracle-fragments.xml
        Path fragmentsFile = outputDir.resolve(ORACLE_FRAGMENTS_FILE);
        fragmentWriter.writeFragments(fragmentRegistry, fragmentsFile);

        // Print summary
        printProcessingSummary(allStats);
    }

    /**
     * Print processing summary with statistics.
     */
    private void printProcessingSummary(List<MapperFileProcessor.ProcessingStats> allStats) {
        log.info("=====================================");
        log.info("PROCESSING SUMMARY");
        log.info("=====================================");

        int totalFiles = allStats.size();
        int modifiedFiles = (int) allStats.stream()
                .filter(MapperFileProcessor.ProcessingStats::hasModifications)
                .count();

        int totalStatements = allStats.stream()
                .mapToInt(MapperFileProcessor.ProcessingStats::getTotalStatements)
                .sum();

        int modifiedStatements = allStats.stream()
                .mapToInt(MapperFileProcessor.ProcessingStats::getModifiedStatements)
                .sum();

        int totalFragments = fragmentRegistry.getFragmentCount();

        log.info("Files processed:        {}", totalFiles);
        log.info("Files modified:         {}", modifiedFiles);
        log.info("Total SQL statements:   {}", totalStatements);
        log.info("Modified statements:    {}", modifiedStatements);
        log.info("Extracted fragments:    {}", totalFragments);

        // Category breakdown
        var stats = fragmentRegistry.getStatistics();
        if (!stats.getFragmentsByCategory().isEmpty()) {
            log.info("");
            log.info("Fragments by category:");
            stats.getFragmentsByCategory().entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .forEach(entry ->
                            log.info("  {}: {}", entry.getKey(), entry.getValue())
                    );
        }

        // Per-file breakdown
        if (log.isDebugEnabled()) {
            log.debug("");
            log.debug("Per-file statistics:");
            for (var stat : allStats) {
                if (stat.hasModifications()) {
                    log.debug("  {}: {} modified, {} fragments",
                            Path.of(stat.getFileName()).getFileName(),
                            stat.getModifiedStatements(),
                            stat.getExtractedFragments());
                }
            }
        }

        log.info("=====================================");
    }

    /**
     * Get rule engine for inspection/testing.
     */
    public OracleRuleEngine getRuleEngine() {
        return ruleEngine;
    }

    /**
     * Get fragment registry for inspection/testing.
     */
    public GlobalOracleFragmentRegistry getFragmentRegistry() {
        return fragmentRegistry;
    }
}