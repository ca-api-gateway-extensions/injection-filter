package com.l7tech.custom.assertions.injectionfilter;

import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.custom.assertions.injectionfilter.server.InjectionFilterKeyValueStoreChangeEventCallback;
import com.l7tech.custom.assertions.injectionfilter.server.InjectionFilterCache;
import com.l7tech.policy.assertion.ext.CustomAssertion;
import com.l7tech.policy.assertion.ext.CustomDynamicLoader;
import com.l7tech.policy.assertion.ext.CustomLoaderException;
import com.l7tech.policy.assertion.ext.ServiceFinder;
import com.l7tech.policy.assertion.ext.entity.CustomReferenceEntities;
import com.l7tech.policy.assertion.ext.entity.CustomReferenceEntitiesSupport;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreChangeEventListener;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreServices;
import com.l7tech.policy.assertion.ext.targetable.CustomMessageTargetable;
import com.l7tech.policy.assertion.ext.targetable.CustomMessageTargetableSupport;
import com.l7tech.policy.variable.VariableMetadata;

public class InjectionFilterAssertion implements CustomAssertion, CustomMessageTargetable, CustomDynamicLoader, CustomReferenceEntities {
    private static final long serialVersionUID = 7077450702941220821L;

    private static final String ASSERTION_NAME = "Injection Filter Assertion";
    private final CustomMessageTargetableSupport messageTarget = new CustomMessageTargetableSupport(CustomMessageTargetableSupport.TARGET_REQUEST);
    private boolean includeURL = true;
    private boolean includeBody = true;

    private final CustomReferenceEntitiesSupport referenceSupport = new CustomReferenceEntitiesSupport();
    private static final String INJECTION_FILTER_ATTRIBUTE_NAME = "InjectionFilterKey";
    public static final String INJECTION_FILTER_NAME_PREFIX = InjectionFilterEntity.class.getName() + ".";

    @Override
    public String getName() {
        return ASSERTION_NAME;
    }

    @Override
    public String getTargetMessageVariable() {
        return messageTarget.getTargetMessageVariable();
    }

    @Override
    public void setTargetMessageVariable(final String other) {
        messageTarget.setTargetMessageVariable(other);
    }

    @Override
    public String getTargetName() {
        return messageTarget.getTargetName();
    }

    @Override
    public boolean isTargetModifiedByGateway() {
        return messageTarget.isTargetModifiedByGateway();
    }

    @Override
    public VariableMetadata[] getVariablesSet() {
        return messageTarget.getVariablesSet();
    }

    @Override
    public String[] getVariablesUsed() {
        return messageTarget.getVariablesUsed();
    }

    public boolean isIncludeURL() {
        return this.includeURL;
    }

    public void setIncludeURL(final boolean includeURL) {
        this.includeURL = includeURL;
    }

    public boolean isIncludeBody() {
        return this.includeBody;
    }

    public void setIncludeBody(final boolean includeBody) {
        this.includeBody = includeBody;
    }

    public String getInjectionFilterKey() {
        return getReferenceEntitiesSupport().getReference(INJECTION_FILTER_ATTRIBUTE_NAME);
    }

    public void setInjectionFilterKey(final String key) {
        if (null == key || key.trim().isEmpty()) {
            getReferenceEntitiesSupport().removeReference(INJECTION_FILTER_ATTRIBUTE_NAME);
        } else {
            getReferenceEntitiesSupport().setKeyValueStoreReference(
                    INJECTION_FILTER_ATTRIBUTE_NAME,
                    key,
                    InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX,
                    new InjectionFilterSerializer());
        }
    }

    @Override
    public void onLoad(final ServiceFinder serviceFinder) throws CustomLoaderException {

        final KeyValueStoreServices keyValueStoreServices = serviceFinder.lookupService(KeyValueStoreServices.class);
        final KeyValueStore keyValueStore = keyValueStoreServices.getKeyValueStore();
        final KeyValueStoreChangeEventListener listener = keyValueStore.getListener(KeyValueStoreChangeEventListener.class);

        if (null == listener) {
            throw new CustomLoaderException("Key value store change event listener is missing");
        } else {
            listener.add(InjectionFilterKeyValueStoreChangeEventCallback.INSTANCE);
        }
        //initialize InjectFilterCache
        InjectionFilterCache.INSTANCE.init(keyValueStore);
    }

    @Override
    public void onUnload(final ServiceFinder serviceFinder) {

        final KeyValueStoreServices keyValueStoreServices = serviceFinder.lookupService(KeyValueStoreServices.class);
        final KeyValueStore keyValueStore = keyValueStoreServices.getKeyValueStore();
        final KeyValueStoreChangeEventListener listener = keyValueStore.getListener(KeyValueStoreChangeEventListener.class);

        if (listener != null) {
            listener.remove(InjectionFilterKeyValueStoreChangeEventCallback.INSTANCE);
        }

        // Invalidate all entries in the cache
        InjectionFilterCache.INSTANCE.invalidateAll();
    }

    @Override
    public CustomReferenceEntitiesSupport getReferenceEntitiesSupport() {
        return referenceSupport;
    }
}