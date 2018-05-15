package com.l7tech.custom.assertions.injectionfilter.server;

import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionPatternEntity;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * InjectionFilterCacheTest tests the behaviour of the Cache
 */
@RunWith(MockitoJUnitRunner.class)
public class InjectionFilterCacheTest {

    public static final String NOKEY = "noSuchKey";

    // KeyValueStore used by the singleton cache
    private static KeyValueStore keyValueStore;

    @BeforeClass
    public static void setUp() {
        keyValueStore = mock(KeyValueStore.class);
        InjectionFilterCache.INSTANCE.init(keyValueStore);
    }

    @Before
    public void setUpTest() {
        // Reset the mock
        reset(keyValueStore);

        // Ensure the cache entries are empty
        InjectionFilterCache.INSTANCE.invalidateAll();
    }

    @Test
    public void whenKeyDoesNotExistsReturnNull() {
        when(keyValueStore.get(NOKEY)).thenReturn(null);
        assertNull(InjectionFilterCache.INSTANCE.get(NOKEY));
    }

    @Test
    public void whenKeyExistsReturnFilterEntry() {
        final String key = "key";
        final String name = "abc";
        final boolean enabled = true;

        InjectionFilterEntity entity = new InjectionFilterEntity();
        entity.setFilterName(name);
        entity.setEnabled(enabled);

        InjectionPatternEntity pattern = new InjectionPatternEntity();
        final String patternStr = "' and '";
        pattern.setEnabled(true);
        pattern.setName("test");
        pattern.setDescription("desc");
        pattern.setPattern(patternStr);

        entity.addPattern(pattern);


        InjectionFilterSerializer serializer = new InjectionFilterSerializer();

        when(keyValueStore.get(key)).thenReturn(serializer.serialize(entity));

        InjectionFilterCache.FilterEntry filterEntry = InjectionFilterCache.INSTANCE.get(key);
        assertNotNull(filterEntry);
        assertEquals(name, filterEntry.getFilterName());
        assertEquals(Boolean.TRUE, filterEntry.isFilterEnabled());
        assertEquals(1, filterEntry.getPatterns().size());
        assertEquals(patternStr, filterEntry.getPatterns().get(0).pattern());
    }

    @Test
    public void whenKeyExistsButNotEnabledReturnFilterEntryWithNoPatterns() {
        final String key = "key";
        final String name = "abc";
        final boolean enabled = false;

        InjectionFilterEntity entity = new InjectionFilterEntity();
        entity.setFilterName(name);
        entity.setEnabled(enabled);

        InjectionPatternEntity pattern = new InjectionPatternEntity();
        final String patternStr = "' and '";
        pattern.setEnabled(true);
        pattern.setName("test");
        pattern.setDescription("desc");
        pattern.setPattern(patternStr);

        entity.addPattern(pattern);


        InjectionFilterSerializer serializer = new InjectionFilterSerializer();

        when(keyValueStore.get(key)).thenReturn(serializer.serialize(entity));

        InjectionFilterCache.FilterEntry filterEntry = InjectionFilterCache.INSTANCE.get(key);
        assertNotNull(filterEntry);
        assertEquals(name, filterEntry.getFilterName());
        assertEquals(Boolean.FALSE, filterEntry.isFilterEnabled());
        assertEquals(0, filterEntry.getPatterns().size());
    }

    @Test
    public void whenGetIsCalledMultipleTimesEnsureKeyValueStoreIsCalledAtMostOnce() {
        when(keyValueStore.get(NOKEY)).thenReturn(null);
        assertNull(InjectionFilterCache.INSTANCE.get(NOKEY));
        assertNull(InjectionFilterCache.INSTANCE.get(NOKEY));
        verify(keyValueStore, times(1)).get(NOKEY);
    }

    @Test
    public void whenCacheIsInvalidatedKeyValueStoreShouldBeAccessToRetrieveValue() {
        when(keyValueStore.get(NOKEY)).thenReturn(null);
        assertNull(InjectionFilterCache.INSTANCE.get(NOKEY));
        InjectionFilterCache.INSTANCE.invalidate(NOKEY);
        assertNull(InjectionFilterCache.INSTANCE.get(NOKEY));
        verify(keyValueStore, times(2)).get(NOKEY);
    }
}
