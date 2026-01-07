# Setup Instructions

## Step 1: Copy Files from Claude Artifacts

Go back to the Claude conversation and copy each artifact content:

### Core Files (copy to project root):
1. pom.xml
2. README.md
3. EXECUTION_EXAMPLE.md
4. IMPLEMENTATION_GUIDE.md

### Resources (copy to src/main/resources/):
5. oracle-rules.yaml
6. logback.xml

### Java Source Files (copy to respective directories):

#### src/main/java/com/enterprise/mybatis/cli/
7. Main.java

#### src/main/java/com/enterprise/mybatis/engine/
8. OracleSplitterEngine.java
9. OracleRule.java
10. OracleRuleCatalog.java
11. OracleRuleEngine.java
12. GlobalOracleFragmentRegistry.java

#### src/main/java/com/enterprise/mybatis/parser/
13. SqlAstProcessor.java

#### src/main/java/com/enterprise/mybatis/xml/
14. MapperFileProcessor.java
15. OracleFragmentXmlWriter.java

#### src/main/java/com/enterprise/mybatis/util/
16. FileUtils.java

### Example Files (copy to examples/):
17. UserMapper.xml (example input)
18. output-example.xml

## Step 2: Build

```bash
cd mybatis-oracle-splitter
mvn clean package
```

## Step 3: Test

```bash
java -jar target/mybatis-oracle-splitter-1.0.0.jar --version
java -jar target/mybatis-oracle-splitter-1.0.0.jar --help
```

## Step 4: Run Example

```bash
java -jar target/mybatis-oracle-splitter-1.0.0.jar examples/UserMapper.xml -o output/
```

