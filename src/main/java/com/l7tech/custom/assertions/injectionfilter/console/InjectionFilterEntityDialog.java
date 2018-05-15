package com.l7tech.custom.assertions.injectionfilter.console;

import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterSerializer;
import com.l7tech.custom.assertions.injectionfilter.InjectionFilterAssertion;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionFilterEntity;
import com.l7tech.custom.assertions.injectionfilter.entity.InjectionPatternEntity;
import com.l7tech.policy.assertion.ext.store.KeyValueStore;
import com.l7tech.policy.assertion.ext.store.KeyValueStoreServices;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.ResourceBundle;

class InjectionFilterEntityDialog extends JDialog {

    private JPanel contentPane;
    private JTextField nameField;
    private JCheckBox enabledCheckBox;
    private JTextField descTextField;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JButton addButton;
    private JButton editButton;
    private JButton removeButton;
    private JButton moveUpButton;
    private JButton moveDownButton;

    private final InjectionFilterEntity entity;
    private static final String GUI = "gui";

    private JTable entityTable;
    private final DefaultTableModel entityTableModel;
    private final transient Map consoleContext;

    private boolean confirmed = false;

     InjectionFilterEntityDialog(final Window owner, final InjectionFilterEntity entity, final Map consoleContext) {
        super(owner, "Injection Filter Properties");
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        this.consoleContext = consoleContext;

        addComponents();
        setContentPane(contentPane);

        this.entity = entity;

        entityTableModel = new DefaultTableModel(new Object[]{"Enabled", "Pattern Name", "Description"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        entityTable.setModel(entityTableModel);
        entityTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        entityTable.getColumnModel().getColumn(0).setPreferredWidth(60); //Enabled
        entityTable.getColumnModel().getColumn(1).setPreferredWidth(150); //Pattern name
        entityTable.getColumnModel().getColumn(2).setPreferredWidth(180); //Description


        entityTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    onEdit();
                }
            }
        });

        entityTable.getSelectionModel().addListSelectionListener(event -> enableDisableComponents());

        //Button Initializers
        buttonOK.addActionListener(event -> onOK());
        buttonCancel.addActionListener(event -> onCancel());
        addButton.addActionListener(event -> onAdd());
        editButton.addActionListener(event -> onEdit());
        removeButton.addActionListener(event -> onRemove());
        moveUpButton.addActionListener(event -> moveSelectedRow(true));
        moveDownButton.addActionListener(event -> moveSelectedRow(false));

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        //Fields Initializer
        nameField.setText(entity.getFilterName());
        descTextField.setText(entity.getDescription());
        enabledCheckBox.setSelected(entity.isEnabled());

        reloadTable();
        this.setLocationRelativeTo(owner);
        this.setSize(owner.getSize());
        pack();
    }

    /**
     * add a new pattern
     */
    private void onAdd() {
        updateOrSave(null);
    }

    /**
     * update the selected pattern name
     */
    private void onEdit() {
        final int selectedRow = entityTable.getSelectedRow();
        if (selectedRow < 0) return;
        final String selectedPatternName = (String) entityTable.getValueAt(selectedRow, 1);
        updateOrSave(selectedPatternName);
    }

    /**
     * remove the selected pattern and re-load table
     */
    private void onRemove() {
        final int selectedRow = entityTable.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }
        final String patternName = (String) entityTable.getValueAt(selectedRow, 1);
        entity.getPatterns().remove(entity.getPattern(patternName));
        this.reloadTable();
    }

    /**
     * Add a new pattern if pattern name is null
     * Update the pattern if a pattern name is provided
     *
     * @param patternName - patternName to be added or updated
     */
    private void updateOrSave(String patternName) {
        InjectionPatternEntity patternEntity;
        if (null == patternName) {
            patternEntity = new InjectionPatternEntity(); //create a new entity if it's a new pattern to be added
        } else {
            patternEntity = entity.getPattern(patternName); //find the pattern if the pattern name is provided
        }

        final java.util.List<String> existingPatterns = getExistingInjectionFilterPatternNames(entity.getFilterName()); // get the existing list of pattern names for the filter
        final InjectionPatternEntityDialog dialog = new InjectionPatternEntityDialog(InjectionFilterEntityDialog.this, patternEntity);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            final InjectionPatternEntity updatedPatternEntity = dialog.getEntity();
            final String updatedPatternName = updatedPatternEntity.getName();
            //if a new pattern was being added, but the pattern already exists or if pattern was being updated, but the name has changed and a pattern with the same name already exists
            if ((null == patternName && entity.patternExists(updatedPatternName)) || (patternName != null && !patternName.equals(updatedPatternName) && existingPatterns.contains(updatedPatternName))) {
                JOptionPane.showMessageDialog(dialog, String.format(ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.msg.pattern.save"), updatedPatternName), ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.title"), JOptionPane.WARNING_MESSAGE);
                dialog.unconfirm();
            } else {
                entity.addOrUpdatePattern(updatedPatternEntity);
                this.reloadTable();
            }
        }
    }

    /**
     * Reorder rows
     *
     * @param up boolean. Move row up if set to true, down if false
     */
    private void moveSelectedRow(final boolean up) {
        boolean reload = false;
        if (up) {
            if (entityTable.getSelectedRow() > 0) {
                entity.swapPatterns(entityTable.getSelectedRow(), entityTable.getSelectedRow() - 1);
                reload = true;
            }
        } else { //down
            if (entityTable.getSelectedRow() < entityTable.getRowCount() - 1 && entityTable.getSelectedRow() >= 0) {
                entity.swapPatterns(entityTable.getSelectedRow(), entityTable.getSelectedRow() + 1);
                reload = true;
            }
        }

        if (reload) {
            reloadTable();
        }
    }

    /**
     * Reload pattern tables
     */
    private void reloadTable() {
        entityTableModel.setRowCount(0);
        for (final InjectionPatternEntity patternEntity : entity.getPatterns()) {
            final String patternName = patternEntity.getName();
            entityTableModel.addRow(new Object[]{
                    patternEntity.isEnabled() ? "Yes" : "No",
                    patternName,
                    patternEntity.getDescription(),
                    patternEntity.getPattern()
            });
        }
    }

    /**
     * update entity on OK. Show an error if the filter name field is empty
     */
    private void onOK() {
        if (null == nameField.getText() || nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.msg.filter.name"), ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.title"), JOptionPane.ERROR_MESSAGE);
        } else {
            entity.setEnabled(enabledCheckBox.isSelected());
            entity.setFilterName(nameField.getText());
            entity.setDescription(descTextField.getText());
            confirmed = true;
            dispose();
        }
    }

    private void onCancel() {
        dispose();
    }

    boolean isConfirmed() {
        return confirmed;
    }

    void unconfirm() {
        this.confirmed = false;
    }

    public InjectionFilterEntity getEntity() {
        return entity;
    }

    /**
     * Enable remove and edit buttons if a row is selected
     */
    private void enableDisableComponents() {
        final boolean enabled = entityTable.getSelectedRow() != -1;
        editButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
    }

    /**
     * Get existing pattern names for a given filterName
     *
     * @param filterName - name of the filter
     * @return List of pattern names for the existing filter, or an empty list if the filter is new
     */
    private java.util.List<String> getExistingInjectionFilterPatternNames(final String filterName) {
        final KeyValueStore keyValueStore = getKeyValueStore();
        final Map<String, byte[]> existingFilter = keyValueStore.findAllWithKeyPrefix(InjectionFilterAssertion.INJECTION_FILTER_NAME_PREFIX);
        final java.util.List<String> patternNames = new ArrayList<>();
        for (final String filterKey : existingFilter.keySet()) {
            final InjectionFilterEntity injectionFilterEntity = new InjectionFilterSerializer().deserialize(keyValueStore.get(filterKey));
            if (injectionFilterEntity != null && injectionFilterEntity.getFilterName().equals(filterName)) {
                final java.util.List<InjectionPatternEntity> existingPatterns = injectionFilterEntity.getPatterns();
                for (final InjectionPatternEntity pattern : existingPatterns) {
                    if (pattern != null) {
                        patternNames.add(pattern.getName());
                    }
                }

                break;
            }
        }
        return patternNames;
    }

    /**
     * Get the keyValueStore object
     *
     * @return keyValueStore
     */
    private KeyValueStore getKeyValueStore() {
        final KeyValueStoreServices keyValueStoreServices = (KeyValueStoreServices) consoleContext.get(KeyValueStoreServices.CONSOLE_CONTEXT_KEY);
        return keyValueStoreServices.getKeyValueStore();
    }

    private void addComponents() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        contentPane.setMinimumSize(new Dimension(900, 578));
        contentPane.setPreferredSize(new Dimension(900, 578));

        addOkAndCancelButtons();
        addBody();
    }

    private void addBody() {
        GridBagConstraints gbc;
        final JPanel bodyPanel = new JPanel();
        bodyPanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, 1.0, GridBagConstraints.BOTH, null);
        contentPane.add(bodyPanel, gbc);

        addLabelsAndFields(bodyPanel);
        addButtons(bodyPanel);
        addScrollPanel(bodyPanel);
    }

    private void addLabelsAndFields(JPanel bodyPanel) {
        GridBagConstraints gbc;

        final JLabel nameLabel = new JLabel();
        nameLabel.setText("Name");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, null, null, null, GridBagConstraints.WEST);
        bodyPanel.add(nameLabel, gbc);

        nameField = new JTextField();
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, 1.0, null, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        bodyPanel.add(nameField, gbc);

        enabledCheckBox = new JCheckBox();
        enabledCheckBox.setText("Enabled");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(2, 0, null, null, null, GridBagConstraints.WEST);
        bodyPanel.add(enabledCheckBox, gbc);

        final JLabel descriptionLabel = new JLabel();
        descriptionLabel.setText("Description");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, null, null, null, GridBagConstraints.WEST);
        bodyPanel.add(descriptionLabel, gbc);

        descTextField = new JTextField();
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 1, 1.0, null, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        bodyPanel.add(descTextField, gbc);
    }

    private void addScrollPanel(JPanel bodyPanel) {
        GridBagConstraints gbc;
        final JPanel scrollPanel = new JPanel();
        scrollPanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 3, null, null, null, null);
        bodyPanel.add(scrollPanel, gbc);

        final JScrollPane scrollPane = new JScrollPane();
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 2, 1.0, 1.0, GridBagConstraints.BOTH, null);
        bodyPanel.add(scrollPane, gbc);

        entityTable = new JTable();
        scrollPane.setViewportView(entityTable);
    }

    private void addButtons(JPanel bodyPanel) {
        GridBagConstraints gbc;
        final JPanel modifyFiltersButtonsPanel = new JPanel();
        modifyFiltersButtonsPanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(2, 2, null, 1.0, GridBagConstraints.BOTH, null);
        bodyPanel.add(modifyFiltersButtonsPanel, gbc);

        editButton = new JButton();
        editButton.setText("Edit");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        modifyFiltersButtonsPanel.add(editButton, gbc);

        addButton = new JButton();
        addButton.setText("Add");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        modifyFiltersButtonsPanel.add(addButton, gbc);

        removeButton = new JButton();
        removeButton.setText("Remove");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 2, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        modifyFiltersButtonsPanel.add(removeButton, gbc);

        moveUpButton = new JButton();
        moveUpButton.setText("Move Up");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 4, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        modifyFiltersButtonsPanel.add(moveUpButton, gbc);

        moveDownButton = new JButton();
        moveDownButton.setText("Move Down");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 5, 1.0, null, GridBagConstraints.HORIZONTAL, null);
        modifyFiltersButtonsPanel.add(moveDownButton, gbc);
    }

    private void addOkAndCancelButtons() {
        final JPanel buttonsPanelParent = new JPanel();
        buttonsPanelParent.setLayout(new GridBagLayout());
        GridBagConstraints gbc;
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, null, GridBagConstraints.BOTH, null);
        contentPane.add(buttonsPanelParent, gbc);

        final JPanel buttonsPanel = new JPanel();
        buttonsPanel.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, null, 1.0, GridBagConstraints.BOTH, null);
        buttonsPanelParent.add(buttonsPanel, gbc);

        buttonOK = new JButton();
        buttonOK.setText("OK");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, null);
        buttonsPanel.add(buttonOK, gbc);

        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, null);
        buttonsPanel.add(buttonCancel, gbc);
    }

}


