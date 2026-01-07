package com.enterprise.mybatis.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry for all Oracle SQL fragments extracted across all mapper files.
 *
 * This registry ensures:
 * - No duplicate fragments (same SQL content gets same ID)
 * - Deterministic fragment IDs
 * - Global uniqueness across all processed files
 * - Efficient lookup and deduplication
 *
 * Fragment IDs are generated using: source file + statement ID + category + content hash
 * This ensures fragments are traceable and deterministic across runs.
 */
public class GlobalOracleFragmentRegistry {
    private static final Logger log = LoggerFactory.getLogger(GlobalOracleFragmentRegistry.class);

    // Map: fragment ID -> OracleFragment
    private final Map<String, OracleFragment> fragmentsById;

    // Map: content hash -> fragment ID (for deduplication)
    private final Map<String, String> hashToId;

    // Track fragments by source file
    private final Map<String, List<String>> fragmentsByFile;

    public GlobalOracleFragmentRegistry() {
        this.fragmentsById = new ConcurrentHashMap<>();
        this.hashToId = new ConcurrentHashMap<>();
        this.fragmentsByFile = new ConcurrentHashMap<>();
    }

    /**
     * Register a new Oracle fragment or return existing if duplicate.
     *
     * @param sqlContent The Oracle-specific SQL content
     * @param sourceFile Source mapper file name
     * @param statementId Original statement ID from mapper
     * @param category Oracle feature category
     * @return Fragment ID to use in <include refid="..."/>
     */
    public String registerFragment(String sqlContent, String sourceFile,
                                   String statementId, String category) {

        // Normalize SQL content for comparison
        String normalizedContent = normalizeSql(sqlContent);
        String contentHash = computeHash(normalizedContent);

        // Check if we've seen this exact content before
        String existingId = hashToId.get(contentHash);
        if (existingId != null) {
            log.debug("Fragment already registered: {} (reusing)", existingId);
            return existingId;
        }

        // Generate new unique fragment ID
        String fragmentId = generateFragmentId(sourceFile, statementId, category);

        // Ensure uniqueness (handle collisions)
        fragmentId = ensureUnique(fragmentId);

        // Create and register fragment
        OracleFragment fragment = new OracleFragment(
                fragmentId, sqlContent, sourceFile, statementId, category
        );

        fragmentsById.put(fragmentId, fragment);
        hashToId.put(contentHash, fragmentId);

        // Track by source file
        fragmentsByFile.computeIfAbsent(sourceFile, k -> new ArrayList<>())
                .add(fragmentId);

        log.info("Registered Oracle fragment: {} (category: {}, source: {})",
                fragmentId, category, sourceFile);

        return fragmentId;
    }

    /**
     * Get fragment by ID.
     */
    public OracleFragment getFragment(String fragmentId) {
        return fragmentsById.get(fragmentId);
    }

    /**
     * Get all registered fragments.
     */
    public Collection<OracleFragment> getAllFragments() {
        return Collections.unmodifiableCollection(fragmentsById.values());
    }

    /**
     * Get fragments from a specific source file.
     */
    public List<OracleFragment> getFragmentsByFile(String sourceFile) {
        return fragmentsByFile.getOrDefault(sourceFile, Collections.emptyList())
                .stream()
                .map(fragmentsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get total fragment count.
     */
    public int getFragmentCount() {
        return fragmentsById.size();
    }

    /**
     * Get statistics about registered fragments.
     */
    public RegistryStatistics getStatistics() {
        return new RegistryStatistics(this);
    }

    /**
     * Generate deterministic fragment ID from metadata.
     */
    private String generateFragmentId(String sourceFile, String statementId, String category) {
        // Extract base filename without extension
        String baseFileName = sourceFile;
        int lastSlash = baseFileName.lastIndexOf('/');
        if (lastSlash >= 0) {
            baseFileName = baseFileName.substring(lastSlash + 1);
        }
        int lastDot = baseFileName.lastIndexOf('.');
        if (lastDot > 0) {
            baseFileName = baseFileName.substring(0, lastDot);
        }

        // Clean up statement ID
        String cleanStatementId = statementId.replaceAll("[^a-zA-Z0-9_]", "_");

        // Clean up category
        String cleanCategory = category.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        // Build fragment ID: oracle_{file}_{statement}_{category}
        return String.format("oracle_%s_%s_%s",
                baseFileName, cleanStatementId, cleanCategory);
    }

    /**
     * Ensure fragment ID is unique by appending counter if needed.
     */
    private String ensureUnique(String baseId) {
        String fragmentId = baseId;
        int counter = 1;

        while (fragmentsById.containsKey(fragmentId)) {
            fragmentId = baseId + "_" + counter;
            counter++;
        }

        return fragmentId;
    }

    /**
     * Normalize SQL for comparison (remove extra whitespace, etc.)
     */
    private String normalizeSql(String sql) {
        return sql.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ",")
                .replaceAll("\\s*\\(\\s*", "(")
                .replaceAll("\\s*\\)\\s*", ")");
    }

    /**
     * Compute SHA-256 hash of SQL content for deduplication.
     */
    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            // Fallback to simple hash
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * Represents a single Oracle SQL fragment.
     */
    public static class OracleFragment {
        private final String id;
        private final String sqlContent;
        private final String sourceFile;
        private final String statementId;
        private final String category;
        private final long timestamp;

        public OracleFragment(String id, String sqlContent, String sourceFile,
                              String statementId, String category) {
            this.id = id;
            this.sqlContent = sqlContent;
            this.sourceFile = sourceFile;
            this.statementId = statementId;
            this.category = category;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() {
            return id;
        }

        public String getSqlContent() {
            return sqlContent;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public String getStatementId() {
            return statementId;
        }

        public String getCategory() {
            return category;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("OracleFragment[id=%s, category=%s, source=%s]",
                    id, category, sourceFile);
        }
    }

    /**
     * Statistics about the fragment registry.
     */
    public static class RegistryStatistics {
        private final int totalFragments;
        private final int uniqueSourceFiles;
        private final Map<String, Integer> fragmentsByCategory;

        public RegistryStatistics(GlobalOracleFragmentRegistry registry) {
            this.totalFragments = registry.fragmentsById.size();
            this.uniqueSourceFiles = registry.fragmentsByFile.size();

            this.fragmentsByCategory = new HashMap<>();
            for (OracleFragment fragment : registry.fragmentsById.values()) {
                fragmentsByCategory.merge(fragment.getCategory(), 1, Integer::sum);
            }
        }

        public int getTotalFragments() {
            return totalFragments;
        }

        public int getUniqueSourceFiles() {
            return uniqueSourceFiles;
        }

        public Map<String, Integer> getFragmentsByCategory() {
            return Collections.unmodifiableMap(fragmentsByCategory);
        }

        @Override
        public String toString() {
            return String.format("RegistryStatistics[total=%d, files=%d, categories=%d]",
                    totalFragments, uniqueSourceFiles, fragmentsByCategory.size());
        }
    }
}