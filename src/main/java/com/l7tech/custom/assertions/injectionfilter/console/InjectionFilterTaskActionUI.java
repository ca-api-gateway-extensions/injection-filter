package com.l7tech.custom.assertions.injectionfilter.console;

import com.l7tech.policy.assertion.ext.action.CustomTaskActionUI;
import com.l7tech.policy.assertion.ext.cei.UsesConsoleContext;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.net.URL;
import java.util.Map;
import java.util.Objects;


public class InjectionFilterTaskActionUI implements CustomTaskActionUI, UsesConsoleContext, Serializable {

    private transient Map consoleContext;

    @Override
    public String getName() {
        return "Manage Injection Filter patterns";
    }

    @Override
    public String getDescription() {
        return "Create, edit, and remove Injection Filter Patterns";
    }

    @Override
    public ImageIcon getIcon() {
        URL icon = getClass().getClassLoader().getResource("RedCrossSign16.gif");
        Objects.requireNonNull(icon);
        return new ImageIcon(icon);
    }

    @Override
    public JDialog getDialog(Frame frame) {
        return new ManageInjectionFilterEntitiesDialog(frame, consoleContext);
    }

    @Override
    public void setConsoleContextUsed(final Map consoleContext) {
        this.consoleContext = consoleContext;
    }
}
