package com.enterprise.mybatis.cli;

import com.enterprise.mybatis.engine.OracleSplitterEngine;
import com.enterprise.mybatis.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Main CLI entry point for MyBatis Oracle Splitter.
 *
 * Usage: java -jar mybatis-oracle-splitter.jar file1.xml file2.xml -o output/
 *
 * This tool extracts Oracle-specific SQL constructs from MyBatis mapper files
 * and consolidates them into a single oracle-fragments.xml file while rewriting
 * the original mappers to use common SQL with <include> references.
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String VERSION = "1.0.0";
    private static final String DEFAULT_OUTPUT_DIR = "output";
    private static final String RULES_FILE = "oracle-rules.yaml";

    public static void main(String[] args) {
        try {
            log.info("MyBatis Oracle Splitter v{}", VERSION);
            log.info("=====================================");

            // Parse command line arguments
            CliArguments cliArgs = parseArguments(args);

            if (cliArgs.showHelp) {
                printHelp();
                System.exit(0);
            }

            if (cliArgs.showVersion) {
                System.out.println("Version: " + VERSION);
                System.exit(0);
            }

            // Validate input files
            if (cliArgs.inputFiles.isEmpty() && cliArgs.inputDirectories.isEmpty()) {
                log.error("No input files or directories specified");
                printHelp();
                System.exit(1);
            }

            // Collect all input files from directories if specified
            List<Path> allInputFiles = new ArrayList<>(cliArgs.inputFiles);
            for (Path dir : cliArgs.inputDirectories) {
                allInputFiles.addAll(collectMapperFiles(dir, cliArgs.recursive));
            }

            if (allInputFiles.isEmpty()) {
                log.error("No mapper XML files found");
                System.exit(1);
            }

            validateInputFiles(allInputFiles);

            // Create output directory
            Path outputDir = Paths.get(cliArgs.outputDirectory);
            Files.createDirectories(outputDir);
            log.info("Output directory: {}", outputDir.toAbsolutePath());

            // Load rules configuration
            Path rulesPath = findRulesFile(cliArgs.rulesFile);
            log.info("Using rules file: {}", rulesPath.toAbsolutePath());

            // Initialize and run the splitter engine
            OracleSplitterEngine engine = new OracleSplitterEngine(rulesPath);
            engine.process(allInputFiles, outputDir);

            log.info("=====================================");
            log.info("Processing completed successfully!");
            log.info("Processed {} mapper file(s)", allInputFiles.size());
            log.info("Output written to: {}", outputDir.toAbsolutePath());

        } catch (Exception e) {
            log.error("Fatal error during processing", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Collect all mapper XML files from a directory.
     */
    private static List<Path> collectMapperFiles(Path directory, boolean recursive) throws Exception {
        log.info("Scanning directory: {} (recursive: {})", directory, recursive);

        List<Path> mapperFiles = new ArrayList<>();

        if (recursive) {
            // Recursive directory walk
            try (var stream = Files.walk(directory)) {
                mapperFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".xml"))
                        .filter(p -> !p.toString().contains("/target/"))  // Skip Maven target
                        .filter(p -> !p.toString().contains("/build/"))   // Skip Gradle build
                        .toList();
            }
        } else {
            // Single directory only
            try (var stream = Files.list(directory)) {
                mapperFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".xml"))
                        .toList();
            }
        }

        log.info("Found {} XML file(s) in {}", mapperFiles.size(), directory);
        return mapperFiles;
    }

    /**
     * Parse command line arguments into structured format.
     */
    private static CliArguments parseArguments(String[] args) {
        CliArguments cliArgs = new CliArguments();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "-o", "--output" -> {
                    if (i + 1 < args.length) {
                        cliArgs.outputDirectory = args[++i];
                    } else {
                        throw new IllegalArgumentException("Missing value for " + arg);
                    }
                }
                case "-r", "--rules" -> {
                    if (i + 1 < args.length) {
                        cliArgs.rulesFile = args[++i];
                    } else {
                        throw new IllegalArgumentException("Missing value for " + arg);
                    }
                }
                case "-h", "--help" -> cliArgs.showHelp = true;
                case "-v", "--version" -> cliArgs.showVersion = true;
                case "-d", "--debug" -> {
                    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
                    log.info("Debug logging enabled");
                }
                case "-R", "--recursive" -> cliArgs.recursive = true;
                case "--use-cdata" -> cliArgs.useCdata = true;
                case "--no-cdata" -> cliArgs.useCdata = false;
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    // Treat as input file or directory
                    Path path = Paths.get(arg);
                    if (Files.isDirectory(path)) {
                        cliArgs.inputDirectories.add(path);
                    } else {
                        cliArgs.inputFiles.add(path);
                    }
                }
            }
        }

        return cliArgs;
    }

    /**
     * Validate that all input files exist and are readable.
     */
    private static void validateInputFiles(List<Path> inputFiles) {
        log.info("Validating {} input file(s)...", inputFiles.size());

        for (Path file : inputFiles) {
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("File not found: " + file);
            }
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Not a regular file: " + file);
            }
            if (!Files.isReadable(file)) {
                throw new IllegalArgumentException("File not readable: " + file);
            }
            if (!file.toString().endsWith(".xml")) {
                log.warn("File does not have .xml extension: {}", file);
            }
            log.debug("  ✓ {}", file);
        }

        log.info("All input files validated successfully");
    }

    /**
     * Find the rules configuration file.
     * Search order: 1) specified path, 2) current directory, 3) classpath
     */
    private static Path findRulesFile(String specifiedPath) throws Exception {
        // If explicitly specified, use that
        if (specifiedPath != null) {
            Path path = Paths.get(specifiedPath);
            if (Files.exists(path)) {
                return path;
            }
            throw new IllegalArgumentException("Specified rules file not found: " + specifiedPath);
        }

        // Try current directory
        Path currentDir = Paths.get(RULES_FILE);
        if (Files.exists(currentDir)) {
            return currentDir;
        }

        // Try classpath
        var resource = Main.class.getClassLoader().getResource(RULES_FILE);
        if (resource != null) {
            return Paths.get(resource.toURI());
        }

        // Create default rules file in current directory
        log.warn("Rules file not found, creating default: {}", RULES_FILE);
        FileUtils.createDefaultRulesFile(Paths.get(RULES_FILE));
        return Paths.get(RULES_FILE);
    }

    /**
     * Print usage help.
     */
    private static void printHelp() {
        System.out.println("""
            MyBatis Oracle Splitter - Extract Oracle-specific SQL from MyBatis mappers
            
            USAGE:
                java -jar mybatis-oracle-splitter.jar [OPTIONS] <input-files-or-dirs...>
            
            OPTIONS:
                -o, --output <dir>      Output directory (default: output/)
                -r, --rules <file>      Rules configuration file (default: oracle-rules.yaml)
                -R, --recursive         Recursively scan directories for XML files
                -d, --debug             Enable debug logging
                -h, --help              Show this help message
                -v, --version           Show version information
            
            EXAMPLES:
                # Process single file
                java -jar mybatis-oracle-splitter.jar UserMapper.xml -o output/
                
                # Process multiple files
                java -jar mybatis-oracle-splitter.jar User*.xml Order*.xml -o output/
                
                # Process entire directory
                java -jar mybatis-oracle-splitter.jar /path/to/mappers/ -o output/
                
                # Process directory recursively
                java -jar mybatis-oracle-splitter.jar /path/to/project -R -o output/
                
                # Multiple directories from different projects
                java -jar mybatis-oracle-splitter.jar \\
                    /project1/src/main/resources/mappers \\
                    /project2/src/main/resources/mappers \\
                    -R -o output/
                
                # Use custom rules
                java -jar mybatis-oracle-splitter.jar /path/to/mappers -r custom-rules.yaml -o output/
            
            OUTPUT:
                output/
                 ├── <original-name>.xml      (rewritten with common SQL only)
                 └── oracle-fragments.xml     (all Oracle-specific fragments)
            
            For more information, visit: https://github.com/enterprise/mybatis-oracle-splitter
            """);
    }

    /**
     * Container for parsed CLI arguments.
     */
    private static class CliArguments {
        List<Path> inputFiles = new ArrayList<>();
        List<Path> inputDirectories = new ArrayList<>();
        String outputDirectory = DEFAULT_OUTPUT_DIR;
        String rulesFile = null;
        boolean recursive = false;
        boolean showHelp = false;
        boolean showVersion = false;
        boolean useCdata = false;
    }
}