package com.l7tech.custom.assertions.injectionfilter.entity;


import java.io.Serializable;
import java.util.regex.Pattern;

public class InjectionPatternEntity implements Serializable {

    private String name;
    private String description;
    private String pattern;
    private boolean enabled;

    public InjectionPatternEntity() {
        enabled = true;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(final String pattern) {
        this.pattern = pattern;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public static Pattern getCompiledPattern(final String patternStr) {
        final int flags = Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE;
        return Pattern.compile(patternStr, flags);
    }
}
