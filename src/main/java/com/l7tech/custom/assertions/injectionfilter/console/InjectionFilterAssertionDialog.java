package com.l7tech.custom.assertions.injectionfilter.console;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.policy.assertion.ext.AssertionEditor;
import com.l7tech.policy.assertion.ext.AssertionEditorSupport;
import com.l7tech.policy.assertion.ext.EditListener;
import com.l7tech.policy.assertion.ext.commonui.CommonUIServices;
import com.l7tech.policy.assertion.ext.commonui.CustomTargetVariablePanel;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreServices;
import com.l7tech.policy.assertion.ext.targetable.CustomMessageTargetableSupport;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;


class InjectionFilterAssertionDialog extends JDialog implements AssertionEditor {

    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox<FilterComboBoxItem> filterComboBox;
    private JCheckBox urlCheckBox;
    private JCheckBox bodyCheckBox;

    private final transient Map<String, FilterComboBoxItem> keyToFilterMap = new HashMap<>();

    private final transient AssertionEditorSupport editorSupport;
    private final InjectionFilterAssertion assertion;
    private final transient Map consoleContext;

    private transient CustomTargetVariablePanel customTargetVariablePanel; // Common UI Component.
    private JPanel variablePrefixPanelHolder; // A panel to add customTargetVariablePanel to.

    InjectionFilterAssertionDialog(final InjectionFilterAssertion customAssertion, final Map consoleContext) {
        super(Frame.getFrames().length > 0 ? Frame.getFrames()[0] : null, true);
        setTitle("Injection Filter Properties");
        this.assertion = customAssertion;
        this.editorSupport = new AssertionEditorSupport(this);
        this.consoleContext = consoleContext;
        addComponents();
        initialize();
        modelToView();
        pack();
    }

    private void initialize() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        final CommonUIServices commonUIServices = (CommonUIServices) consoleContext.get(CommonUIServices.CONSOLE_CONTEXT_KEY);

        // Create Target Variable Panel and add it to a panel.
        customTargetVariablePanel = commonUIServices.createTargetVariablePanel();
        customTargetVariablePanel.setAcceptEmpty(false);
        variablePrefixPanelHolder.setLayout(new BorderLayout());
        variablePrefixPanelHolder.add(customTargetVariablePanel.getPanel(), BorderLayout.CENTER);
        customTargetVariablePanel.addChangeListener(e -> enableDisableComponents());

        reloadInjectionFilterComboBox();

        urlCheckBox.addItemListener(event -> enableOkButton());
        bodyCheckBox.addItemListener(event -> enableOkButton());
        filterComboBox.addItemListener(event -> enableOkButton());
        buttonCancel.addActionListener(event -> onCancel());

        buttonOK.addActionListener(event -> onOK());

        enableDisableComponents();

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                onCancel();
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                onCancel();
            }
        });
        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(event -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void modelToView() {
        customTargetVariablePanel.setVariable(assertion.getTargetMessageVariable());

        if (CustomMessageTargetableSupport.TARGET_REQUEST.equals(assertion.getTargetMessageVariable())) {
            urlCheckBox.setSelected(assertion.isIncludeURL());
        }
        bodyCheckBox.setSelected(assertion.isIncludeBody());
        filterComboBox.setSelectedItem(keyToFilterMap.get(assertion.getInjectionFilterKey()));
    }

    private void viewToModel() {
        assertion.setTargetMessageVariable(customTargetVariablePanel.getVariable());
        if (CustomMessageTargetableSupport.TARGET_REQUEST.equals(assertion.getTargetMessageVariable())) {
            assertion.setIncludeURL(urlCheckBox.isSelected());
        }
        assertion.setIncludeBody(bodyCheckBox.isSelected());
        FilterComboBoxItem selectedItem = (FilterComboBoxItem) filterComboBox.getSelectedItem();
        if (selectedItem == null) {
            throw new IllegalArgumentException("Selected item cannot be null");
        }
        assertion.setInjectionFilterKey((selectedItem).getKey());
    }

    /**
     * Get the key value store
     *
     * @return KeyValueStore
     */
    private KeyValueStore getKeyValueStore() {
        final KeyValueStoreServices keyValueStoreServices = (KeyValueStoreServices) consoleContext.get(KeyValueStoreServices.CONSOLE_CONTEXT_KEY);
        return keyValueStoreServices.getKeyValueStore();
    }

    /**
     * populate filterComboBox
     */
    private void reloadInjectionFilterComboBox() {
        final InjectionFilterSerializer serializer = new InjectionFilterSerializer();
        filterComboBox.removeAllItems();
        keyToFilterMap.clear();

        final Map<String, byte[]> injectionFilterByteMap = getKeyValueStore().findAllWithKeyPrefix(InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX);
        FilterComboBoxItem comboBoxItem;
        for (final Map.Entry<String, byte[]> entry : injectionFilterByteMap.entrySet()) {
            final String key = entry.getKey();
            final InjectionFilterEntity entity = serializer.deserialize(entry.getValue());
            comboBoxItem = new FilterComboBoxItem(key, entity);
            filterComboBox.addItem(comboBoxItem);
            keyToFilterMap.put(key, comboBoxItem);
        }
    }

    private void onOK() {
        viewToModel();
        editorSupport.fireEditAccepted(assertion);
        dispose();
    }

    private void onCancel() {
        editorSupport.fireCancelled(assertion);
        dispose();
    }

    /**
     * Enable/disable the OK button if all settings are OK.
     */
    private void enableOkButton() {
        boolean ok = false;

        if (filterComboBox.getSelectedItem() != null) {
            ok = true;
        }
        // If applying to request messages, further ensures either URL or body is selected.
        if (CustomMessageTargetableSupport.TARGET_REQUEST.equals(assertion.getTargetMessageVariable())) {
            ok &= urlCheckBox.isSelected() || bodyCheckBox.isSelected();
        }

        buttonOK.setEnabled(ok);
    }

    private void enableDisableComponents() {
        //if target variable is not request, then only body will be scanned
        if (!CustomMessageTargetableSupport.TARGET_REQUEST.equals(customTargetVariablePanel.getVariable())) {
            bodyCheckBox.setSelected(true);
            bodyCheckBox.setEnabled(false);
            urlCheckBox.setEnabled(false);
            urlCheckBox.setSelected(false);
        } else {
            bodyCheckBox.setSelected(assertion.isIncludeBody());
            bodyCheckBox.setEnabled(true);
            urlCheckBox.setEnabled(true);
            urlCheckBox.setSelected(assertion.isIncludeURL());
        }
    }

    @Override
    public void edit() {
        setVisible(true);
    }

    @Override
    public void addEditListener(EditListener editListener) {
        editorSupport.addListener(editListener);
    }

    @Override
    public void removeEditListener(EditListener editListener) {
        editorSupport.removeListener(editListener);
    }

    private void addComponents() {
        GridBagConstraints gbc;

        contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());

        final JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 3, 1.0, null, GridBagConstraints.BOTH, null);
        contentPane.add(mainPanel, gbc);

        addInputMessageSection();
        addScanMessageSection();
        addButtons(mainPanel);
    }

    private void addInputMessageSection() {
        GridBagConstraints gbc;
        final JPanel inputMessagePanel = new JPanel();
        inputMessagePanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, 1.0, GridBagConstraints.BOTH, null);
        contentPane.add(inputMessagePanel, gbc);
        inputMessagePanel.setBorder(BorderFactory.createTitledBorder("Input Message"));

        final JLabel messageVariableLabel = new JLabel();
        messageVariableLabel.setText("Message Variable");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, null, 1.0, null, GridBagConstraints.WEST);
        inputMessagePanel.add(messageVariableLabel, gbc);

        variablePrefixPanelHolder = new JPanel();
        variablePrefixPanelHolder.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, 1.0, 1.0, GridBagConstraints.BOTH, null);
        inputMessagePanel.add(variablePrefixPanelHolder, gbc);
    }

    private void addScanMessageSection() {
        GridBagConstraints gbc;
        final JPanel scanMessagePanel = new JPanel();
        scanMessagePanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, 1.0, GridBagConstraints.BOTH, null);
        contentPane.add(scanMessagePanel, gbc);
        scanMessagePanel.setBorder(BorderFactory.createTitledBorder("Scan Message"));

        urlCheckBox = new JCheckBox();
        urlCheckBox.setText("URL");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 1, 1.0, 1.0, null, GridBagConstraints.WEST);
        scanMessagePanel.add(urlCheckBox, gbc);

        bodyCheckBox = new JCheckBox();
        bodyCheckBox.setText("Body");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(2, 1, 1.0, 1.0, null, GridBagConstraints.WEST);
        scanMessagePanel.add(bodyCheckBox, gbc);

        filterComboBox = new JComboBox<>();
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        gbc.gridwidth = 2;
        scanMessagePanel.add(filterComboBox, gbc);

        final JLabel filterNameLabel = new JLabel();
        filterNameLabel.setText("Filter Name");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, null, 1.0, null, GridBagConstraints.WEST);
        scanMessagePanel.add(filterNameLabel, gbc);
    }

    private void addButtons(JPanel mainPanel) {
        GridBagConstraints gbc;
        final JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, null, 1.0, GridBagConstraints.BOTH, null);
        mainPanel.add(buttonsPanel, gbc);

        buttonOK = new JButton();
        buttonOK.setText("OK");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, null);
        buttonsPanel.add(buttonOK, gbc);

        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, null);
        buttonsPanel.add(buttonCancel, gbc);
    }

    private class FilterComboBoxItem {
        private final String key;
        private final InjectionFilterEntity entity;

        FilterComboBoxItem(String key, InjectionFilterEntity entity) {
            this.key = key;
            this.entity = entity;
        }

        private String getKey() {
            return key;
        }

        public InjectionFilterEntity getEntity() {
            return entity;
        }

        public String toString() {
            return entity.getFilterName();
        }
    }
}
