package com.l7tech.custom.assertions.injectionfilter.console;

import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.custom.assertions.injectionfilter.InjectionFilterCustomExtensionInterface;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.policy.assertion.ext.ServiceException;
import com.l7tech.policy.assertion.ext.cei.CustomExtensionInterfaceFinder;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreException;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreServices;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

class ManageInjectionFilterEntitiesDialog extends JDialog {
    private static final Logger LOGGER = Logger.getLogger(ManageInjectionFilterEntitiesDialog.class.getName());
    public static final String ERROR_MESSAGE_TITLE = "InjectionFilterAssertion.ui.error.title";

    private JPanel contentPane;
    private JButton addButton;
    private JButton closeButton;
    private JButton removeButton;
    private JButton editButton;
    private JTable entityTable;
    private final DefaultTableModel entityTableModel;

    private final Map<String, String> filterKeys = new HashMap<>();
    private final transient Map consoleContext;
    private final InjectionFilterSerializer serializer = new InjectionFilterSerializer();
    private final transient KeyValueStore keyValueStore;
    private static final String GUI = "gui";


    public ManageInjectionFilterEntitiesDialog(Window owner, Map consoleContext) {
        super(owner);
        setTitle(ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.ManageInjectionFilterEntitiesDialog.title"));
        setModal(true);
        getRootPane().setDefaultButton(closeButton);
        this.consoleContext = consoleContext;
        keyValueStore = getKeyValueStore();
        addComponents();
        setContentPane(contentPane);
        entityTableModel = new DefaultTableModel(new Object[]{ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.ManageInjectionFilterEntitiesDialog.table.column.enabled"), ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.ManageInjectionFilterEntitiesDialog.table.column.filterName")}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        entityTable.setModel(entityTableModel);
        entityTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        entityTable.getColumnModel().getColumn(0).setPreferredWidth(60); //Enabled
        entityTable.getColumnModel().getColumn(1).setPreferredWidth(150); //Filter name
        entityTable.getSelectionModel().addListSelectionListener(event -> enableDisableComponents());

        //Edit on double click
        entityTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    onEdit();
                }
            }
        });

        //Button Initializer
        closeButton.addActionListener(event -> onCancel());
        addButton.addActionListener(event -> onAdd());
        editButton.addActionListener(event -> onEdit());
        removeButton.addActionListener(event -> onRemove());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(event -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        loadPreDefinedFilters(owner);
        reloadTable();
    }

    private void onAdd() {
        updateOrSave(null);
    }

    private void onEdit() {
        final int selectedRow = entityTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        final String selectedPatternName = (String) entityTable.getValueAt(selectedRow, 1);
        updateOrSave(selectedPatternName);
    }

    private void onRemove() {
        final int selectedRow = entityTable.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }
        final String filterName = (String) entityTable.getValueAt(selectedRow, 1);
        keyValueStore.delete(filterKeys.get(filterName));

        this.reloadTable();
    }

    /**
     * Add a new filter if filter name is null
     * Update the filter if a filter name is provided
     *
     * @param filterName the name of the filter to update, or null if adding a new filter
     */
    private void updateOrSave(final String filterName) {
        final InjectionFilterEntity filterEntity = filterName == null ? new InjectionFilterEntity() : serializer.deserialize(keyValueStore.get(filterKeys.get(filterName)));
        final InjectionFilterEntityDialog dialog = new InjectionFilterEntityDialog(ManageInjectionFilterEntitiesDialog.this, filterEntity, consoleContext);
        boolean done = false;
        while (!done) {
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                if (!filterNameIsValid(filterName, filterEntity, dialog)) continue;
                try {
                    if (null == filterName) {
                        keyValueStore.save(serializer.convertFilterUuidToKey(UUID.randomUUID().toString()), serializer.serialize(filterEntity));
                    } else {
                        keyValueStore.update(filterKeys.get(filterName), serializer.serialize(filterEntity));
                    }
                } catch (KeyValueStoreException e) {
                    JOptionPane.showMessageDialog(dialog, String.format(ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.msg.filter.save"), filterName, e.getMessage()), ResourceBundle.getBundle(GUI).getString(ERROR_MESSAGE_TITLE), JOptionPane.WARNING_MESSAGE);
                }
                done = true;
                this.reloadTable();
            } else {
                done = true;
            }
        }
    }

    private boolean filterNameIsValid(String filterName, InjectionFilterEntity filterEntity, InjectionFilterEntityDialog dialog) {
        final String newFilterName = filterEntity.getFilterName();
        // Refresh existing filter list, just in case someone else updates the filter db table at the same time.
        final List<String> existingFilterNames = getExistingFilters();
        //if a new filter is being added, but the another filter with the same name exists, or if an existing filter is being updated and the filter name has changed but the filter with the same name already exists
        if ((filterName == null && existingFilterNames.contains(newFilterName)) ||
                (filterName != null && !filterName.equals(newFilterName) && existingFilterNames.contains(newFilterName))) {
            JOptionPane.showMessageDialog(dialog, String.format(ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.msg.filter.exists"), newFilterName), ResourceBundle.getBundle(GUI).getString(ERROR_MESSAGE_TITLE), JOptionPane.WARNING_MESSAGE);
            dialog.unconfirm();
            return false;
        }
        return true;
    }

    private void reloadTable() {
        entityTableModel.setRowCount(0);
        filterKeys.clear();

        final KeyValueStore store = getKeyValueStore();
        final Map<String, byte[]> map = store.findAllWithKeyPrefix(InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX);

        for (final Map.Entry<String, byte[]> entry : map.entrySet()) {
            final InjectionFilterEntity filterEntity = serializer.deserialize(entry.getValue());
            if (null == filterEntity) {
                LOGGER.log(Level.FINE, "Ignoring null filters retrieved from Key Value Store: " + entry.getKey());
            } else {
                final String filterName = filterEntity.getFilterName();
                filterKeys.put(filterName, entry.getKey());

                if (filterName != null) {
                    entityTableModel.addRow(new Object[]{
                            filterEntity.isEnabled() ? ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.ManageInjectionFilterEntitiesDialog.enabled.yes") : ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.ManageInjectionFilterEntitiesDialog.enabled.no"),
                            filterName,
                    });
                }
            }
        }
    }

    private void onCancel() {
        dispose();
    }

    /**
     * Enable remove and edit buttons if a row is selected
     */
    private void enableDisableComponents() {
        final boolean enabled = entityTable.getSelectedRow() != -1;
        editButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
    }

    private KeyValueStore getKeyValueStore() {
        final KeyValueStoreServices keyValueStoreServices = (KeyValueStoreServices) consoleContext.get(KeyValueStoreServices.CONSOLE_CONTEXT_KEY);
        return keyValueStoreServices.getKeyValueStore();
    }

    private InjectionFilterCustomExtensionInterface getExtensionInterface() throws ServiceException {
        InjectionFilterCustomExtensionInterface extensionInterface = null;
        final CustomExtensionInterfaceFinder extensionInterfaceFinder =
                (CustomExtensionInterfaceFinder) consoleContext.get(CustomExtensionInterfaceFinder.CONSOLE_CONTEXT_KEY);
        if (extensionInterfaceFinder != null) {
            extensionInterface = extensionInterfaceFinder.getExtensionInterface(InjectionFilterCustomExtensionInterface.class);
        }
        return extensionInterface;
    }

    /**
     * load pre-defined filters only if there are no filters in the db.
     */
    private void loadPreDefinedFilters(Window owner) {
        try {
            InjectionFilterCustomExtensionInterface extensionInterface = getExtensionInterface();
            Objects.requireNonNull(extensionInterface);
            extensionInterface.loadPreDefinedFilters();
        } catch (ServiceException e) {
            LOGGER.log(Level.WARNING, "Error Loading pre-defined filters", e);
            JOptionPane.showMessageDialog(owner, String.format(ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.msg.load.predefined.filters"), e.getMessage()), ResourceBundle.getBundle(GUI).getString(ERROR_MESSAGE_TITLE), JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * helper function to get the names of the existing filters
     *
     * @return list of filter names or an empty list if there are no filters in the db
     */
    private List<String> getExistingFilters() {
        final Map<String, byte[]> existingFilter = keyValueStore.findAllWithKeyPrefix(InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX);
        final List<String> existingFilterNames = new ArrayList<>();

        for (final String filterKey : existingFilter.keySet()) {
            final InjectionFilterEntity injectionFilterEntity = serializer.deserialize(keyValueStore.get(filterKey));
            if (injectionFilterEntity != null) {
                existingFilterNames.add(injectionFilterEntity.getFilterName());
            }
        }
        return existingFilterNames;
    }

    private void addComponents() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        contentPane.setEnabled(false);
        contentPane.setForeground(new Color(-16777216));

        final JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc;
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, null, 1.0, GridBagConstraints.BOTH, null);
        contentPane.add(panel, gbc);

        closeButton = new JButton();
        closeButton.setText(ResourceBundle.getBundle("gui").getString("InjectionFilterAssertion.ui.button.close"));
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 4, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        panel.add(closeButton, gbc);

        removeButton = new JButton();
        removeButton.setText(ResourceBundle.getBundle("gui").getString("InjectionFilterAssertion.ui.button.remove"));
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 2, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        panel.add(removeButton, gbc);

        editButton = new JButton();
        editButton.setText(ResourceBundle.getBundle("gui").getString("InjectionFilterAssertion.ui.button.edit"));
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        panel.add(editButton, gbc);

        addButton = new JButton();
        addButton.setText(ResourceBundle.getBundle("gui").getString("InjectionFilterAssertion.ui.button.add"));
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        panel.add(addButton, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, 1.0, GridBagConstraints.BOTH, null);

        contentPane.add(scrollPane1, gbc);
        entityTable = new JTable();
        scrollPane1.setViewportView(entityTable);
    }

}
