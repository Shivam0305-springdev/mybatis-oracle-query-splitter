# Processing Directories and Multiple Projects

## Quick Reference

| Scenario | Command |
|----------|---------|
| Single directory | `java -jar mybatis-oracle-splitter.jar /path/to/mappers/ -o output/` |
| Recursive scan | `java -jar mybatis-oracle-splitter.jar /path/to/project -R -o output/` |
| Multiple directories | `java -jar mybatis-oracle-splitter.jar /path1/ /path2/ -R -o output/` |
| Multiple projects | `java -jar mybatis-oracle-splitter.jar /proj1/src /proj2/src -R -o output/` |
| Wildcard files | `java -jar mybatis-oracle-splitter.jar /path/*.xml -o output/` |

## Detailed Examples

### Example 1: Process Single Directory (Non-Recursive)

```bash
# Only process XML files directly in the mappers/ directory
java -jar mybatis-oracle-splitter.jar \
    /home/user/project/src/main/resources/mappers/ \
    -o output/
```

**Result**: Processes only files like:
- `/home/user/project/src/main/resources/mappers/UserMapper.xml`
- `/home/user/project/src/main/resources/mappers/OrderMapper.xml`

**Skips** subdirectories like:
- `/home/user/project/src/main/resources/mappers/user/UserDetailMapper.xml`

---

### Example 2: Process Directory Recursively

```bash
# Process all XML files in entire project structure
java -jar mybatis-oracle-splitter.jar \
    /home/user/project/src/main/resources \
    -R \
    -o output/
```

**Result**: Recursively finds and processes:
```
/home/user/project/src/main/resources/
├── mappers/
│   ├── UserMapper.xml          ✓ processed
│   ├── OrderMapper.xml         ✓ processed
│   └── user/
│       └── UserDetailMapper.xml ✓ processed
└── mybatis/
    └── ProductMapper.xml       ✓ processed
```

**Automatically skips**: `/target/`, `/build/` directories

---

### Example 3: Multiple Projects from Different Locations

```bash
# Process mappers from 3 different projects
java -jar mybatis-oracle-splitter.jar \
    /home/user/project-a/src/main/resources/mappers \
    /home/user/project-b/src/main/resources/mybatis \
    /var/projects/legacy-app/mappers \
    -R \
    -o consolidated-output/
```

**Result**: All Oracle fragments from all 3 projects go into ONE `oracle-fragments.xml`

---

### Example 4: Absolute Paths from Other Projects

```bash
# Linux/Mac
java -jar mybatis-oracle-splitter.jar \
    /opt/projects/crm-system/src/main/resources/mappers \
    /opt/projects/billing-system/src/main/resources/mappers \
    -R -o /tmp/oracle-migration/

# Windows
java -jar mybatis-oracle-splitter.jar \
    "C:\Projects\CRM\src\main\resources\mappers" \
    "C:\Projects\Billing\src\main\resources\mappers" \
    -R -o "C:\Migration\output"
```

---

### Example 5: Mixed Files and Directories

```bash
# Process specific files AND entire directories
java -jar mybatis-oracle-splitter.jar \
    /project1/UserMapper.xml \
    /project1/OrderMapper.xml \
    /project2/src/main/resources/mappers/ \
    /project3/legacy/ \
    -R \
    -o output/
```

---

### Example 6: Using Shell Scripts for Complex Selection

**Bash script for selective processing:**

```bash
#!/bin/bash
# process-mappers.sh

JAR="mybatis-oracle-splitter.jar"
OUTPUT="output"

# Find all *Mapper.xml files from multiple projects
find ~/projects/project1 ~/projects/project2 \
     -name "*Mapper.xml" \
     -type f \
     ! -path "*/test/*" \
     ! -path "*/target/*" \
     -print0 | \
xargs -0 java -jar $JAR -o $OUTPUT
```

**Make executable and run:**
```bash
chmod +x process-mappers.sh
./process-mappers.sh
```

---

### Example 7: Environment-Based Configuration

```bash
#!/bin/bash
# process-all-envs.sh

# Development environment
java -jar mybatis-oracle-splitter.jar \
    $DEV_PROJECT_ROOT/src/main/resources/mappers \
    -R -o output/dev/

# QA environment
java -jar mybatis-oracle-splitter.jar \
    $QA_PROJECT_ROOT/src/main/resources/mappers \
    -R -o output/qa/

# Production environment
java -jar mybatis-oracle-splitter.jar \
    $PROD_PROJECT_ROOT/src/main/resources/mappers \
    -R -o output/prod/
```

---

### Example 8: Using `find` for Advanced Filtering

```bash
# Process only files modified in last 7 days
find /path/to/project -name "*.xml" -type f -mtime -7 | \
    xargs java -jar mybatis-oracle-splitter.jar -o output/

# Process files larger than 10KB (likely to have complex SQL)
find /path/to/project -name "*Mapper.xml" -type f -size +10k | \
    xargs java -jar mybatis-oracle-splitter.jar -o output/

# Process files matching specific pattern
find /path/to/project -name "*User*.xml" -type f | \
    xargs java -jar mybatis-oracle-splitter.jar -o output/
```

---

### Example 9: Parallel Processing for Large Projects

```bash
#!/bin/bash
# parallel-process.sh

# Split directories into batches and process in parallel
java -jar mybatis-oracle-splitter.jar /project/module1/mappers -R -o output/batch1 &
java -jar mybatis-oracle-splitter.jar /project/module2/mappers -R -o output/batch2 &
java -jar mybatis-oracle-splitter.jar /project/module3/mappers -R -o output/batch3 &

wait

# Merge oracle-fragments.xml files (manual step)
echo "All batches complete. Merge oracle-fragments.xml files manually."
```

---

## Output Structure Examples

### Single Project Output

```
Input:
/home/user/project/src/main/resources/mappers/
├── UserMapper.xml
├── OrderMapper.xml
└── ProductMapper.xml

Output:
output/
├── UserMapper.xml           (rewritten)
├── OrderMapper.xml          (rewritten)
├── ProductMapper.xml        (rewritten)
└── oracle-fragments.xml     (all Oracle SQL)
```

### Multiple Projects Output

```
Input:
/project1/mappers/UserMapper.xml
/project2/mappers/OrderMapper.xml
/project3/mappers/ProductMapper.xml

Output:
output/
├── UserMapper.xml           (from project1, rewritten)
├── OrderMapper.xml          (from project2, rewritten)
├── ProductMapper.xml        (from project3, rewritten)
└── oracle-fragments.xml     (Oracle SQL from ALL projects)
```

**Note**: All files are flattened into output directory. Name conflicts are preserved (last file wins).

---

## Preserving Directory Structure

If you need to preserve the directory structure from input:

```bash
#!/bin/bash
# preserve-structure.sh

BASE_DIR="/path/to/project"
OUTPUT_DIR="output"

# Find all mapper XMLs with relative paths
cd "$BASE_DIR"
find . -name "*Mapper.xml" -type f | while read file; do
    # Get relative directory
    dir=$(dirname "$file")
    
    # Create output directory structure
    mkdir -p "$OUTPUT_DIR/$dir"
    
    # Process single file
    java -jar mybatis-oracle-splitter.jar "$BASE_DIR/$file" -o "$OUTPUT_DIR/$dir"
done

# Merge all oracle-fragments.xml files
find "$OUTPUT_DIR" -name "oracle-fragments.xml" -exec cat {} \; > "$OUTPUT_DIR/oracle-fragments-merged.xml"
```

---

## Common Patterns

### Maven Multi-Module Project

```bash
# Process all modules in a Maven multi-module project
java -jar mybatis-oracle-splitter.jar \
    /project/module-a/src/main/resources/mappers \
    /project/module-b/src/main/resources/mappers \
    /project/module-c/src/main/resources/mappers \
    /project/module-common/src/main/resources/mappers \
    -R -o output/
```

### Gradle Multi-Project

```bash
# Process all Gradle subprojects
java -jar mybatis-oracle-splitter.jar \
    /project/user-service/src/main/resources/mappers \
    /project/order-service/src/main/resources/mappers \
    /project/product-service/src/main/resources/mappers \
    -R -o output/
```

### Monorepo Structure

```bash
# Process multiple apps in a monorepo
java -jar mybatis-oracle-splitter.jar \
    /monorepo/apps/web/src/mappers \
    /monorepo/apps/api/src/mappers \
    /monorepo/apps/batch/src/mappers \
    /monorepo/libs/common/src/mappers \
    -R -o output/
```

---

## Best Practices

### 1. Always Use Absolute Paths for Clarity

```bash
# Good
java -jar mybatis-oracle-splitter.jar /home/user/project/mappers -R -o /tmp/output

# Avoid (relative paths can be confusing)
java -jar mybatis-oracle-splitter.jar ../../project/mappers -R -o output
```

### 2. Test with Small Subset First

```bash
# Test on single directory first
java -jar mybatis-oracle-splitter.jar /project/module1/mappers -o test-output/

# If successful, run on all modules
java -jar mybatis-oracle-splitter.jar /project/*/src/main/resources/mappers -R -o output/
```

### 3. Use Debug Mode for Troubleshooting

```bash
java -jar mybatis-oracle-splitter.jar \
    /path/to/project \
    -R -d \
    -o output/ \
    2>&1 | tee processing.log
```

### 4. Verify Before and After

```bash
# Count XML files before
find /project -name "*.xml" | wc -l

# Run tool
java -jar mybatis-oracle-splitter.jar /project -R -o output/

# Count output files
ls output/*.xml | wc -l

# Should match (minus 1 for oracle-fragments.xml)
```

### 5. Backup Before Processing

```bash
# Create timestamped backup
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
cp -r /project/src/main/resources/mappers "/tmp/mappers_backup_$TIMESTAMP"

# Run tool
java -jar mybatis-oracle-splitter.jar /project/src/main/resources/mappers -R -o output/
```

---

## Troubleshooting

### Issue: "No mapper XML files found"

```bash
# Check if directory exists
ls -la /path/to/directory

# Check if it contains XML files
find /path/to/directory -name "*.xml"

# Use -d flag to see what's being scanned
java -jar mybatis-oracle-splitter.jar /path/to/directory -R -d -o output/
```

### Issue: "Permission denied"

```bash
# Check read permissions
ls -la /path/to/directory

# Check write permissions on output
ls -la output/

# Fix permissions if needed
chmod -R 755 /path/to/directory
```

### Issue: Name Conflicts (Multiple Files with Same Name)

```bash
# This will overwrite:
# /project1/UserMapper.xml
# /project2/UserMapper.xml
# Result: output/UserMapper.xml (only last one)

# Solution: Process separately with different output dirs
java -jar mybatis-oracle-splitter.jar /project1 -R -o output/project1
java -jar mybatis-oracle-splitter.jar /project2 -R -o output/project2
```

---

## Performance Tips

### Large Projects (1000+ files)

```bash
# Increase heap memory
java -Xmx2g -jar mybatis-oracle-splitter.jar /large/project -R -o output/

# Process in batches
java -jar mybatis-oracle-splitter.jar /project/module1 -R -o output/batch1 &
java -jar mybatis-oracle-splitter.jar /project/module2 -R -o output/batch2 &
wait
```

### Network File Systems

```bash
# Copy to local disk first for better performance
rsync -av /network/share/project/ /tmp/local-project/
java -jar mybatis-oracle-splitter.jar /tmp/local-project -R -o output/
```

---

## Summary of Flags

| Flag | Description | Example |
|------|-------------|---------|
| `-R` or `--recursive` | Scan directories recursively | `-R` |
| `-o` or `--output` | Output directory | `-o output/` |
| `-r` or `--rules` | Custom rules file | `-r custom-rules.yaml` |
| `-d` or `--debug` | Debug logging | `-d` |

**Full example:**
```bash
java -jar mybatis-oracle-splitter.jar \
    /project1/mappers \
    /project2/mappers \
    -R \
    -o consolidated-output \
    -r custom-oracle-rules.yaml \
    -d
```