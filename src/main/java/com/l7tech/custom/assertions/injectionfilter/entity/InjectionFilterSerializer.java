package com.l7tech.custom.assertions.injectionfilter.entity;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.policy.assertion.ext.entity.CustomEntitySerializer;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InjectionFilterSerializer implements CustomEntitySerializer<InjectionFilterEntity> {

    private static final Logger LOGGER = Logger.getLogger(InjectionFilterSerializer.class.getName());

    public String convertFilterUuidToKey(final String uuid) {
        return InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX + uuid;
    }

    /**
     * serialize the filter entity object
     *
     * @param filterEntity the filter entity object to serialize
     * @return filter entity object serialized in bytes[]
     */
    @Override
    public byte[] serialize(final InjectionFilterEntity filterEntity) {
        if (null == filterEntity) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(out)) {
            objectOutputStream.writeObject(filterEntity);
            return out.toByteArray();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error while serializing Injection Filter.", e);
        }
        return null;
    }

    /**
     * de-serialize the bytes into filter entity object
     *
     * @param bytes the bytes to deserialize
     * @return filter entity object
     */
    @Override
    public InjectionFilterEntity deserialize(final byte[] bytes) {
        if (null == bytes) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
            return (InjectionFilterEntity) objectInputStream.readObject();
        } catch (ClassNotFoundException | IOException e) {
            LOGGER.log(Level.WARNING, "Error while deserializing Injection Filter.", e);
            return null;
        }
    }
}