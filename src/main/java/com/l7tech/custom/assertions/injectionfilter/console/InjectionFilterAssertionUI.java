package com.l7tech.custom.assertions.injectionfilter.console;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.policy.assertion.ext.AssertionEditor;
import com.l7tech.policy.assertion.ext.CustomAssertion;
import com.l7tech.policy.assertion.ext.CustomAssertionUI;
import com.l7tech.policy.assertion.ext.cei.UsesConsoleContext;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.net.URL;
import java.util.Map;
import java.util.Objects;

public class InjectionFilterAssertionUI implements CustomAssertionUI, UsesConsoleContext, Serializable {

    private transient Map consoleContext;

    @Override
    public AssertionEditor getEditor(CustomAssertion customAssertion) {
        if (!(customAssertion instanceof InjectionFilterAssertion)) {
            throw new IllegalArgumentException(InjectionFilterAssertion.class + " type is required");
        }
        return new InjectionFilterAssertionDialog((InjectionFilterAssertion) customAssertion, consoleContext);
    }

    @Override
    public ImageIcon getSmallIcon() {
        URL icon = getClass().getClassLoader().getResource("RedCrossSign16.gif");
        Objects.requireNonNull(icon);
        return new ImageIcon(icon);
    }

    @Override
    public ImageIcon getLargeIcon() {
        return getSmallIcon();  // No large icon currently available
    }

    @Override
    public void setConsoleContextUsed(Map map) {
        consoleContext = map;
    }

    static GridBagConstraints getGridBagConstraints(int gridx, int gridy, Double weightx, Double weighty, Integer fill, Integer anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = gridx;
        gbc.gridy = gridy;
        if (weightx != null) gbc.weightx = weightx;
        if (weighty != null) gbc.weighty = weighty;
        gbc.fill = (fill == null) ? GridBagConstraints.NONE : fill;
        gbc.anchor = (anchor == null) ? GridBagConstraints.CENTER : anchor;
        return gbc;
    }
}