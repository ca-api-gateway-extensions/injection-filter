package com.l7tech.custom.assertions.injectionfilter.server;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionPatternEntity;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * InjectionFilterCache provides an in-memory cache for the InjectionFilter with pre-compiled Patterns for InjectionFilterEntity
 * The default cache size is 100 and can be changed by setting the InjectionFilterAssertion.FilterCache.size in System Properties.
 */
public enum InjectionFilterCache {
    INSTANCE;

    private static final Logger LOGGER = Logger.getLogger(InjectionFilterCache.class.getName());

    private static final int DEFAULT_MAX_CACHE_SIZE = 100;
    private LoadingCache<String, Optional<FilterEntry>> filterCache;

    /**
     * Constructor
     */
    InjectionFilterCache() {
        // Empty Constructor
    }

    /**
     * Initialize the Cache
     */
    public void init(final KeyValueStore keyValueStore) {
        final String cacheSizeStr = System.getProperty("InjectionFilterAssertion.FilterCache.size",
                Integer.toString(DEFAULT_MAX_CACHE_SIZE));

        LOGGER.log(Level.FINE, "Initializing Cache with max entries = {0}", cacheSizeStr);

        this.filterCache = CacheBuilder.newBuilder()
                .maximumSize(Integer.parseInt(cacheSizeStr))
                .build(new InjectionFilterCacheLoader(keyValueStore));
    }

    /**
     * Determines if the Cache is initialized
     */
    private void checkInit() {
        if (filterCache == null) {
            throw new IllegalStateException(getClass().getName() + " is not initialized!");
        }
    }

    /**
     * Invalidate the cache entry
     */
    public void invalidate(final String key) {
        checkInit();
        LOGGER.log(Level.FINE, "Invalidating cache entry for key {0}", key);
        filterCache.invalidate(key);
    }

    /**
     * Get FilterEntry from cache
     *
     * @return FilterEntry
     */
    public FilterEntry get(final String key) {
        checkInit();
        LOGGER.log(Level.FINE, "Retrieving cache entry for key {0}", key);
        try {
            return filterCache.get(key).orElse(null);
        } catch (ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Error loading filter entry for key {0}", key);
            return null;
        }
    }

    /**
     * Invalidate all entries in the cache
     */
    public void invalidateAll() {
        checkInit();
        LOGGER.log(Level.FINE, "Invalidating all entries in cache");
        filterCache.invalidateAll();
    }

    /**
     * The Filter cache entry
     */
    public class FilterEntry {
        private final String name;
        private final boolean enabled;
        private final List<Pattern> patterns;

        FilterEntry(final String name, final boolean enabled, final List<Pattern> patterns) {
            this.name = name;
            this.enabled = enabled;
            this.patterns = (patterns == null) ? Collections.unmodifiableList(new ArrayList<Pattern>(0)) : Collections.unmodifiableList(patterns);
        }

        String getFilterName() {
            return name;
        }

        boolean isFilterEnabled() {
            return enabled;
        }

        List<Pattern> getPatterns() {
            return patterns;
        }
    }

    /**
     * InjectionFilterCacheLoader defines how the cache entries are loaded
     */
    private class InjectionFilterCacheLoader extends CacheLoader<String, Optional<FilterEntry>> {

        private final KeyValueStore keyValueStore;

        InjectionFilterCacheLoader(final KeyValueStore keyValueStore) {
            this.keyValueStore = keyValueStore;
        }

        @Override
        public Optional<FilterEntry> load(final String key) {
            LOGGER.log(Level.FINE, "Creating filter pattern cache entry for key {0}", key);

            final byte[] entityBytes = keyValueStore.get(key);

            // InjectionFilterEntity not found in KeyValueStore
            if (entityBytes == null) {
                LOGGER.log(Level.FINE, "Injection Filter Entity with key {0} cannot be found", key);
                return Optional.empty();
            }

            final InjectionFilterEntity entity = new InjectionFilterSerializer().deserialize(entityBytes);

            FilterEntry filterEntry;

            if (entity.isEnabled()) {
                final List<Pattern> listPatterns = new ArrayList<>(entity.getPatterns().size());

                for (final InjectionPatternEntity patternEntity : entity.getPatterns()) {
                    if (patternEntity.isEnabled()) {
                        try {
                            final Pattern pattern = InjectionPatternEntity.getCompiledPattern(patternEntity.getPattern());
                            listPatterns.add(pattern);
                        } catch (PatternSyntaxException pse) {
                            LOGGER.log(Level.SEVERE, "Unable to compile the injection filter {0}",
                                    patternEntity.getPattern());
                            throw pse;
                        }
                    }
                }

                filterEntry = new FilterEntry(entity.getFilterName(), true, listPatterns);
            } else {
                LOGGER.log(Level.FINE, "Injection filter is disabled for key {0}", key);
                filterEntry = new FilterEntry(entity.getFilterName(), false, null);
            }

            return Optional.of(filterEntry);
        }
    }
}
