package com.l7tech.custom.assertions.injectionfilter.server;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreChangeEventListener;

import java.util.List;

/**
 * InjectionFilterKeyValueStoreChangeEventCallback class listens for change events for the Injection filter in the key value store
 */
public enum InjectionFilterKeyValueStoreChangeEventCallback implements KeyValueStoreChangeEventListener.Callback {
    INSTANCE;

    InjectionFilterKeyValueStoreChangeEventCallback() {
    }

    @Override
    public String getKeyPrefix() {
        return InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX;
    }

    @Override
    public void onEvent(List<KeyValueStoreChangeEventListener.Event> events) {
        for (KeyValueStoreChangeEventListener.Event event : events) {
            // When the filter is updated/removed, invalidate the cache
            switch (event.getOperation()) {
                case CREATE:
                case UPDATE:
                case DELETE:
                    InjectionFilterCache.INSTANCE.invalidate(event.getKey());
                    break;
                default:
                    break;
            }
        }
    }
}
