package com.enterprise.mybatis.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

/**
 * Custom EntityResolver to handle MyBatis DTD references locally.
 * This prevents network calls and DTD validation errors.
 */
public class DtdEntityResolver implements EntityResolver {
    private static final Logger log = LoggerFactory.getLogger(DtdEntityResolver.class);

    // MyBatis 3 Mapper DTD (simplified for parsing purposes)
    private static final String MYBATIS_MAPPER_DTD = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <!ELEMENT mapper (cache-ref | cache | resultMap* | parameterMap* | sql* | 
                         insert* | update* | delete* | select*)+>
        <!ATTLIST mapper namespace CDATA #IMPLIED>
        <!ELEMENT select (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST select
            id CDATA #REQUIRED
            parameterMap CDATA #IMPLIED
            parameterType CDATA #IMPLIED
            resultMap CDATA #IMPLIED
            resultType CDATA #IMPLIED
            resultSetType (FORWARD_ONLY | SCROLL_INSENSITIVE | SCROLL_SENSITIVE) #IMPLIED
            statementType (STATEMENT|PREPARED|CALLABLE) #IMPLIED
            fetchSize CDATA #IMPLIED
            timeout CDATA #IMPLIED
            flushCache (true|false) #IMPLIED
            useCache (true|false) #IMPLIED
            databaseId CDATA #IMPLIED
            lang CDATA #IMPLIED
            resultOrdered (true|false) #IMPLIED
            resultSets CDATA #IMPLIED>
        <!ELEMENT insert (#PCDATA | selectKey | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST insert
            id CDATA #REQUIRED
            parameterMap CDATA #IMPLIED
            parameterType CDATA #IMPLIED
            timeout CDATA #IMPLIED
            flushCache (true|false) #IMPLIED
            statementType (STATEMENT|PREPARED|CALLABLE) #IMPLIED
            keyProperty CDATA #IMPLIED
            useGeneratedKeys (true|false) #IMPLIED
            keyColumn CDATA #IMPLIED
            databaseId CDATA #IMPLIED
            lang CDATA #IMPLIED>
        <!ELEMENT update (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST update
            id CDATA #REQUIRED
            parameterMap CDATA #IMPLIED
            parameterType CDATA #IMPLIED
            timeout CDATA #IMPLIED
            flushCache (true|false) #IMPLIED
            statementType (STATEMENT|PREPARED|CALLABLE) #IMPLIED
            databaseId CDATA #IMPLIED
            lang CDATA #IMPLIED>
        <!ELEMENT delete (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST delete
            id CDATA #REQUIRED
            parameterMap CDATA #IMPLIED
            parameterType CDATA #IMPLIED
            timeout CDATA #IMPLIED
            flushCache (true|false) #IMPLIED
            statementType (STATEMENT|PREPARED|CALLABLE) #IMPLIED
            databaseId CDATA #IMPLIED
            lang CDATA #IMPLIED>
        <!ELEMENT sql (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST sql
            id CDATA #REQUIRED
            lang CDATA #IMPLIED
            databaseId CDATA #IMPLIED>
        <!ELEMENT include (property*)>
        <!ATTLIST include refid CDATA #REQUIRED>
        <!ELEMENT if (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST if test CDATA #REQUIRED>
        <!ELEMENT choose (when* , otherwise?)>
        <!ELEMENT when (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST when test CDATA #REQUIRED>
        <!ELEMENT otherwise (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ELEMENT foreach (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST foreach
            collection CDATA #REQUIRED
            item CDATA #IMPLIED
            index CDATA #IMPLIED
            open CDATA #IMPLIED
            close CDATA #IMPLIED
            separator CDATA #IMPLIED>
        <!ELEMENT trim (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ATTLIST trim
            prefix CDATA #IMPLIED
            prefixOverrides CDATA #IMPLIED
            suffix CDATA #IMPLIED
            suffixOverrides CDATA #IMPLIED>
        <!ELEMENT where (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ELEMENT set (#PCDATA | include | trim | where | set | foreach | choose | if | bind)*>
        <!ELEMENT bind EMPTY>
        <!ATTLIST bind
            name CDATA #REQUIRED
            value CDATA #REQUIRED>
        """;

    @Override
    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException, IOException {

        log.debug("Resolving entity: publicId={}, systemId={}", publicId, systemId);

        // Handle MyBatis mapper DTD
        if (systemId != null && systemId.contains("mybatis-3-mapper.dtd")) {
            log.debug("Using local MyBatis mapper DTD");
            return new InputSource(new StringReader(MYBATIS_MAPPER_DTD));
        }

        // Try to load from classpath
        if (systemId != null) {
            String fileName = systemId.substring(systemId.lastIndexOf('/') + 1);
            InputStream stream = getClass().getClassLoader()
                    .getResourceAsStream("dtd/" + fileName);

            if (stream != null) {
                log.debug("Loaded DTD from classpath: {}", fileName);
                return new InputSource(stream);
            }
        }

        // Return empty source to prevent network lookup
        // This allows parsing to continue without validation
        log.debug("Returning empty DTD source");
        return new InputSource(new StringReader(""));
    }
}