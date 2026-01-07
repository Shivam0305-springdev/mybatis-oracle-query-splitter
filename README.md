# MyBatis Oracle Splitter

## Step 1: Build

```bash
cd mybatis-oracle-splitter
mvn clean package
```
## Step 2: Test

```bash
java -jar target/mybatis-oracle-splitter-1.0.0.jar --version
java -jar target/mybatis-oracle-splitter-1.0.0.jar --help
```

## Step 3: Run Example

```bash
java -jar target/mybatis-oracle-splitter-1.0.0.jar examples/UserMapper.xml -o output/
```


## Run Help: To get all supported commands

```bash
java -jar target/mybatis-oracle-splitter-1.0.0.jar --help
```