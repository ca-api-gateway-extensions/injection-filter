package com.l7tech.custom.assertions.injectionfilter.server;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionPatternEntity;
import com.l7tech.custom.assertions.injectionfilter.InjectionFilterCustomExtensionInterface;
import com.l7tech.policy.assertion.ext.cei.CustomExtensionInterfaceBinding;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreException;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


public class InjectionFilterExtensionInterfaceBinding extends CustomExtensionInterfaceBinding<InjectionFilterCustomExtensionInterface> {
    private static final Logger LOGGER = Logger.getLogger(InjectionFilterExtensionInterfaceBinding.class.getName());

    public InjectionFilterExtensionInterfaceBinding() {
        super(InjectionFilterCustomExtensionInterface.class, new InjectionFilterCustomExtensionInterface() {

            //sql injection filter
            private byte[] createSqlFilter() {
                final InjectionFilterSerializer serializer = new InjectionFilterSerializer();
                final InjectionFilterEntity sql = new InjectionFilterEntity();
                final List<InjectionPatternEntity> patterns = new ArrayList<>();

                final InjectionPatternEntity sqlP1 = new InjectionPatternEntity();
                sqlP1.setEnabled(true);
                sqlP1.setName("SQL OR/AND Attack");
                sqlP1.setDescription("' and  ' or");
                sqlP1.setPattern("'[\\s]*([aA][nN][dD]|[oO][rR])");

                final InjectionPatternEntity sqlP2 = new InjectionPatternEntity();
                sqlP2.setEnabled(true);
                sqlP2.setName("Single line comment");
                sqlP2.setDescription("--comment");
                sqlP2.setPattern("--[^\\r\\n]*");

                final InjectionPatternEntity sqlP3 = new InjectionPatternEntity();
                sqlP3.setEnabled(true);
                sqlP3.setName("Line breaks and tabs");
                sqlP3.setDescription("tab and new line");
                sqlP3.setPattern("[\\t\\r\\n]");

                final InjectionPatternEntity sqlP4 = new InjectionPatternEntity();
                sqlP4.setEnabled(true);
                sqlP4.setName("Multiple line comment");
                sqlP4.setDescription("/*comment*/");
                sqlP4.setPattern("/\\*[\\w\\W]*?(?=\\*/)\\*");

                final InjectionPatternEntity sqlP5 = new InjectionPatternEntity();
                sqlP5.setEnabled(true);
                sqlP5.setName("Text blocks");
                sqlP5.setDescription("'text'");
                sqlP5.setPattern("'(''|[^'])*'");

                final InjectionPatternEntity sqlP6 = new InjectionPatternEntity();
                sqlP6.setEnabled(true);
                sqlP6.setName("SQL LIKE% Match/Escape Sequences");
                sqlP6.setDescription("'%  '=  ';  '*  '+  ')");
                sqlP6.setPattern("('[\\s]*[\\%]+|'[\\s]*[\\=]+|'[\\s]*;|'[\\s]*[\\+]+|'[\\s]*[\\*]+|'[\\s]*[\\)]+)");

                final InjectionPatternEntity sqlP7 = new InjectionPatternEntity();
                sqlP7.setEnabled(true);
                sqlP7.setName("SQL Keywords Injection");
                sqlP7.setDescription("SQL Keywords");
                sqlP7.setPattern("^(insert|as|select|or|procedure|limit|order by|asc|desc|delete|update|distinct|having|truncate|replace|handler|like)$");

                final InjectionPatternEntity sqlP8 = new InjectionPatternEntity();
                sqlP8.setEnabled(true);
                sqlP8.setName("MySQL Commands");
                sqlP8.setDescription("MySQL commands");
                sqlP8.setPattern("\\b(exec sp|exec xp)\\b");

                final InjectionPatternEntity sqlP9 = new InjectionPatternEntity();
                sqlP9.setEnabled(false);
                sqlP9.setName("SQL Metacharacters");
                sqlP9.setDescription("= '--;");
                sqlP9.setPattern("((\\%3D)|(=))[^\\n]*((\\%27)|(\\')|(\\-\\-)|(\\%3B)|(;))");

                final InjectionPatternEntity sqlP10 = new InjectionPatternEntity();
                sqlP10.setEnabled(true);
                sqlP10.setName("Oracle Buffer Overflow");
                sqlP10.setDescription("");
                sqlP10.setPattern("\\b(bfilename|tz_offset|to_timestamp_tz)\\b");

                final InjectionPatternEntity sqlP11 = new InjectionPatternEntity();
                sqlP11.setEnabled(true);
                sqlP11.setName("UNION check");
                sqlP11.setDescription("x' UNION");
                sqlP11.setPattern("\\w*((\\%27)|(\\'))(u|U|(\\%55)|(\\%75))(n|N|(\\%6E)|(\\%4E))(i|I|(\\%69)|(\\%49))(o|O|(\\%6F)|(\\%4F))(n|N|(\\%6E)|(\\%4E))");

                final InjectionPatternEntity sqlP12 = new InjectionPatternEntity();
                sqlP12.setEnabled(true);
                sqlP12.setName("OR check");
                sqlP12.setDescription("x 'or");
                sqlP12.setPattern("\\w*((\\%27)|(\\'))((\\%6F)|o|O|(\\%4F))((\\%72)|r|R|(\\%52))");

                final InjectionPatternEntity sqlP13 = new InjectionPatternEntity();
                sqlP13.setEnabled(true);
                sqlP13.setName("SQL Simple Metacharacters");
                sqlP13.setDescription("'--#");
                sqlP13.setPattern("^('|--|#|\\\\x27|\\\\x23)$");

                patterns.add(sqlP1);
                patterns.add(sqlP2);
                patterns.add(sqlP3);
                patterns.add(sqlP4);
                patterns.add(sqlP5);
                patterns.add(sqlP6);
                patterns.add(sqlP7);
                patterns.add(sqlP8);
                patterns.add(sqlP9);
                patterns.add(sqlP10);
                patterns.add(sqlP12);
                patterns.add(sqlP11);
                patterns.add(sqlP13);

                sql.setEnabled(true);
                sql.setDescription("Regex used to protect against SQL Injection");
                sql.setFilterName("SQL Injection Filter");
                sql.setPatterns(patterns);

                return serializer.serialize(sql);
            }

            //ldap injection filter
            @SuppressWarnings("squid:S2068")    // suppress warning about hard-coded credentials (it is suspicious about the word 'password' appearing in the password injection pattern)
            private byte[] createLdapFilter() {
                final InjectionFilterSerializer serializer = new InjectionFilterSerializer();
                final InjectionFilterEntity ldap = new InjectionFilterEntity();
                final List<InjectionPatternEntity> patterns = new ArrayList<>();

                final InjectionPatternEntity p1 = new InjectionPatternEntity();
                p1.setEnabled(true);
                p1.setName("LDAP Metacharacters");
                p1.setDescription("Included metacharacters: | ! ( ) *|");
                p1.setPattern("(\\|)*!\\( \\) %28 %29 & %26 %21 %7C ((\\*\\|)|(%2A%7C))");
                patterns.add(p1);

                final InjectionPatternEntity p2 = new InjectionPatternEntity();
                p2.setEnabled(true);
                p2.setName("Null Character");
                p2.setDescription("Null");
                p2.setPattern("u0000|%00");
                patterns.add(p2);

                final InjectionPatternEntity p3 = new InjectionPatternEntity();
                p3.setEnabled(true);
                p3.setName("LDAP AND/OR Attack Patterns");
                p3.setDescription("Preven against attack using and or or and ()");
                p3.setPattern("\\(+\\s*\\w*\\W*([aA][nN][dD]|[oO][rR]|&|\\||%26|%7C|)\\s*\\w*\\W*=*\\s*\\w*\\W*\\)+");
                patterns.add(p3);

                final InjectionPatternEntity p4 = new InjectionPatternEntity();
                p4.setEnabled(true);
                p4.setName("LDAP Metadata");
                p4.setDescription("Pattern =*");
                p4.setPattern("(=|%3D)\\*\\w*\\W*\\)*");
                patterns.add(p4);

                final InjectionPatternEntity p5 = new InjectionPatternEntity();
                p5.setEnabled(true);
                p5.setName("LDAP Mail");
                p5.setDescription("*(|(mail=*))");
                p5.setPattern("(\\*|%2A)(\\(|%28)(\\||%7C)(\\(|%28)mail(=|%3D)(\\*|%2A)(\\)|%29)(\\)|%29)");
                patterns.add(p5);

                final InjectionPatternEntity p6 = new InjectionPatternEntity();
                p6.setEnabled(true);
                p6.setName("LDAP Objectclass");
                p6.setDescription("*(|(objectclass=*))");
                p6.setPattern("(\\*\\(\\|\\(objectclass=\\*\\)\\))|%2A%28%7C%28objectclass%3D%2A%29%29");
                patterns.add(p6);

                final InjectionPatternEntity p7 = new InjectionPatternEntity();
                p7.setEnabled(true);
                p7.setName("LDAP admin and password injection");
                p7.setDescription("admin*)((|userPassword=*)");
                p7.setPattern("admin\\*\\)\\(\\(\\|userPassword=\\*\\)");
                patterns.add(p7);

                final InjectionPatternEntity p8 = new InjectionPatternEntity();
                p8.setEnabled(true);
                p8.setName("LDAP uid");
                p8.setDescription("*)(uid=*))(|(uid=*");
                p8.setPattern("\\*\\)\\(uid=\\*\\)\\)\\(\\|\\(uid=\\*");
                patterns.add(p8);

                ldap.setEnabled(true);
                ldap.setDescription("Regex used to protect against LDAP Injection");
                ldap.setFilterName("LDAP Injection Filter");
                ldap.setPatterns(patterns);

                return serializer.serialize(ldap);
            }

            //Shell injection filter
            byte[] createShellFilter() {
                final InjectionFilterSerializer serializer = new InjectionFilterSerializer();
                final InjectionFilterEntity shell = new InjectionFilterEntity();
                final List<InjectionPatternEntity> patterns = new ArrayList<>();

                final InjectionPatternEntity p1 = new InjectionPatternEntity();
                p1.setEnabled(true);
                p1.setName("Command Path and Pipe");
                p1.setDescription("Prevent using / and |");
                p1.setPattern("\\/+\\w*\\|*");

                final InjectionPatternEntity p2 = new InjectionPatternEntity();
                p2.setEnabled(true);
                p2.setName("Shell Keywords");
                p2.setDescription("UNIX keywords");
                p2.setPattern("(passwd|ls|rm|exec|ptrace|cat|cd)\\s+");

                final InjectionPatternEntity p3 = new InjectionPatternEntity();
                p3.setEnabled(true);
                p3.setName("Unix Folders");
                p3.setDescription("Unix folders");
                p3.setPattern("\\/+(bin|etc|usr|sh|var)\\/+");

                final InjectionPatternEntity p4 = new InjectionPatternEntity();
                p4.setEnabled(true);
                p4.setName("Shell Special Characters");
                p4.setDescription("`&>;\\");
                p4.setPattern("`|%60|;|%3B|&|%26|>|%3E|\\\\|%5C");

                patterns.add(p1);
                patterns.add(p2);
                patterns.add(p3);
                patterns.add(p4);


                shell.setEnabled(true);
                shell.setDescription("Regex used to protect against Command Injection");
                shell.setFilterName("Shell Injection Filter");
                shell.setPatterns(patterns);
                return serializer.serialize(shell);
            }

            //xpath injection filter
            private byte[] createXpathFilter() {
                final InjectionFilterSerializer serializer = new InjectionFilterSerializer();
                final InjectionFilterEntity xpath = new InjectionFilterEntity();
                final List<InjectionPatternEntity> patterns = new ArrayList<>();

                final InjectionPatternEntity p1 = new InjectionPatternEntity();
                p1.setEnabled(true);
                p1.setName("Xpath Keywords");
                p1.setDescription("Keywords used in XPath");
                p1.setPattern("(count|string-length|substring|string)\\(");
                patterns.add(p1);

                final InjectionPatternEntity p2 = new InjectionPatternEntity();
                p2.setEnabled(true);
                p2.setName("XPath OR / AND");
                p2.setDescription("' or 'x'='x'");
                p2.setPattern("'\\s+([oO][rR]|[aA][nN][dD])\\s+'*\\s*\\w*\\d*\\s*'*\\s*=*\\s*'*\\s*\\w*\\d*");
                patterns.add(p2);

                final InjectionPatternEntity p3 = new InjectionPatternEntity();
                p3.setEnabled(true);
                p3.setName("Alpha-numeric characters between <> and /");
                p3.setDescription("<text> /text/");
                p3.setPattern("/*((\\%3C)|<)((\\%2F)|\\/)*[a-z0-9\\%]+((\\%3E)|>)(/i)*");
                patterns.add(p3);

                final InjectionPatternEntity p4 = new InjectionPatternEntity();
                p4.setEnabled(false);
                p4.setName("XPath Metacharacters");
                p4.setDescription("Characters \\^;()");
                p4.setPattern("(\\*)+|(\\^)+|(;)+|(\\()+|(\\))+");
                patterns.add(p4);

                final InjectionPatternEntity p5 = new InjectionPatternEntity();
                p5.setEnabled(true);
                p5.setName("XPath or Injection");
                p5.setDescription("'+or+'1'='1|'+or+''='|x'+or+1=1+or+'x'='y");
                p5.setPattern("('\\+[oO][rR]\\+'\\d+'='\\d+)|('\\+[oO][rR]\\+''=')|(\\w+'\\+[oO][rR]\\+\\d=\\d\\+[oO][rR]\\+'\\w'='\\w)");
                patterns.add(p5);

                final InjectionPatternEntity p6 = new InjectionPatternEntity();
                p6.setEnabled(true);
                p6.setName("XPath Count");
                p6.setDescription("count(/child::node())");
                p6.setPattern("count\\(/child::node\\(\\)\\)");
                patterns.add(p6);

                final InjectionPatternEntity p7 = new InjectionPatternEntity();
                p7.setEnabled(true);
                p7.setName("XPath username injection");
                p7.setDescription("x'+or+name()='username'+or+'x'='y");
                p7.setPattern("\\w'\\+[oO][rR]\\+name\\(\\)='\\w'\\+[oO][rR]\\+'\\w'='\\w");
                patterns.add(p7);

                final InjectionPatternEntity p8 = new InjectionPatternEntity();
                p8.setEnabled(true);
                p8.setName("XPath Metadata");
                p8.setDescription("// //* */* @*");
                p8.setPattern("\\/\\/|\\/\\/\\*|\\*\\/\\*|@\\*");
                patterns.add(p8);

                xpath.setEnabled(true);
                xpath.setDescription("Regex protection against XPath Injection");
                xpath.setFilterName("XPath Injection Filter");
                xpath.setPatterns(patterns);

                return serializer.serialize(xpath);
            }

            @Override
            public void loadPreDefinedFilters() {
                final KeyValueStoreServices keyValueStoreServices = getServiceFinder().lookupService(KeyValueStoreServices.class);
                final KeyValueStore keyValueStore = keyValueStoreServices.getKeyValueStore();
                final Map<String, byte[]> map = keyValueStore.findAllWithKeyPrefix(InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX);
                //only load pre-defined filters if there are no filters in the database, load the pre-defined filters. Otherwise don't load them.
                if (map == null || map.isEmpty()) {
                    try {
                        final InjectionFilterSerializer serializer = new InjectionFilterSerializer();
                        keyValueStore.save(serializer.convertFilterUuidToKey(UUID.randomUUID().toString()), this.createSqlFilter());
                        keyValueStore.save(serializer.convertFilterUuidToKey(UUID.randomUUID().toString()), this.createLdapFilter());
                        keyValueStore.save(serializer.convertFilterUuidToKey(UUID.randomUUID().toString()), this.createShellFilter());
                        keyValueStore.save(serializer.convertFilterUuidToKey(UUID.randomUUID().toString()), this.createXpathFilter());
                    } catch (KeyValueStoreException e) {
                        LOGGER.log(Level.FINE, "Error Loading pre-defined filters", e);
                    }
                }
            }
        });
    }
}