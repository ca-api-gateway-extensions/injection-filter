package com.l7tech.custom.assertions.injectionfilter.server.serialization;

import static org.junit.Assert.assertEquals;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import org.junit.Test;

import java.io.ObjectStreamClass;

public class SerialVersionUidTest {

    /**
     * If this test fails, please revert your changes as they will break backwards compatibility
     */
    @Test
    public void testSerialUid() {
        assertEquals(7077450702941220821L, ObjectStreamClass.lookup(InjectionFilterAssertion.class).getSerialVersionUID());
    }
}
