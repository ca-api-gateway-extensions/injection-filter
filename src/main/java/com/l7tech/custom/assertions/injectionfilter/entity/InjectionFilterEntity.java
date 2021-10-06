package com.l7tech.custom.assertions.injectionfilter.entity;

import com.google.common.base.Strings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InjectionFilterEntity implements Serializable {
    private static final long serialVersionUID = 3231009958979046483L;

    private String filterName;
    private String description;
    private final List<InjectionPatternEntity> patterns;
    private boolean enabled = true;

    public InjectionFilterEntity() {
        patterns = new ArrayList<>();
    }

    public String getFilterName() {
        return filterName;
    }

    public void setFilterName(final String filterName) {
        this.filterName = filterName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public List<InjectionPatternEntity> getPatterns() {
        return patterns;
    }

    public void setPatterns(final List<InjectionPatternEntity> patterns) {
        this.patterns.clear();
        this.patterns.addAll(patterns);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Insert a new InjectionPatternEntity to the end of the List
     *
     * @param pattern A new pattern to add to the List
     */
    public void addPattern(final InjectionPatternEntity pattern) {
        getPatterns().add(pattern);
    }

    /**
     * Adds a new InjectionPatternEntity to the end of the list or if existing updates a current InjectionPatternEntity
     *
     * @param pattern pattern to create or update
     */
    public void addOrUpdatePattern(final InjectionPatternEntity pattern) {
        if (patternExists(pattern.getName())) {
            updatePattern(pattern);
        } else {
            addPattern(pattern);
        }
    }

    /**
     * find and update InjectionPatternEntity. If InjectionPatternEntity doesn't exist nothing changes
     *
     * @param pattern pattern to update
     */
    private void updatePattern(final InjectionPatternEntity pattern) {
        for (int i = 0; i < patterns.size(); i++) {
            if (pattern.getName().equals((patterns.get(i)).getName())) {
                patterns.set(i, pattern);
                break;
            }
        }
    }

    /**
     * Check to see if InjectionPatternEntity exists in the List
     *
     * @param patternName a pattern name to search for
     * @return boolean true if the pattern exists
     */
    public boolean patternExists(final String patternName) {
        return !Strings.nullToEmpty(patternName).trim().isEmpty() &&
                getPatterns().stream()
                        .map(InjectionPatternEntity::getName)
                        .anyMatch(patternName::equals);
    }

    /**
     * Swap the order of two Entities.
     *
     * @param index1 index of the first row
     * @param index2 index of the second row
     */
    public void swapPatterns(final int index1, final int index2) {
        Collections.swap(patterns, index1, index2);
    }

    /**
     * Given the pattern name, return the pattern entity. if a pattern with the given name doesn't exists, return null
     *
     * @param patternName the name of the pattern to fetch
     * @return PatternEntity the pattern matching this name, or null if none exists
     */
    public InjectionPatternEntity getPattern(final String patternName) {
        for (final InjectionPatternEntity patternEntity : getPatterns()) {
            if (patternEntity.getName().equals(patternName)) {
                return patternEntity;
            }
        }
        return null;
    }
}
