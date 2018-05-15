package com.l7tech.custom.assertions.injectionfilter.console;

import com.l7tech.custom.assertions.injectionfilter.entity.InjectionPatternEntity;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


class InjectionPatternEntityDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField patternTextFiled;
    private JTextField nameTextField;
    private JCheckBox enabledCheckBox;
    private JTextField descTextField;
    private JTextArea textInputTextArea;
    private JTextPane testResultTextPane;
    private final InjectionPatternEntity entity;
    private boolean confirmed = false;
    private static final String GUI = "gui";

    public InjectionPatternEntityDialog(Window owner, final InjectionPatternEntity entity) {
        super(owner, ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.InjectionPatternEntityDialog.title"));
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        addComponents();
        setContentPane(contentPane);

        this.entity = entity;

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

        //Button Initializer
        buttonOK.addActionListener(event -> onOK());

        buttonCancel.addActionListener(event -> onCancel());

        //Fields Initializer
        patternTextFiled.setText(entity.getPattern());
        enabledCheckBox.setSelected(entity.isEnabled());
        nameTextField.setText(entity.getName());
        descTextField.setText(entity.getDescription());

        final DocumentListener doTestListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                doTest();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                doTest();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                doTest();
            }
        };

        patternTextFiled.getDocument().addDocumentListener(doTestListener);
        textInputTextArea.getDocument().addDocumentListener(doTestListener);
        testResultTextPane.setEditable(false);
        testResultTextPane.setFont(textInputTextArea.getFont());

        this.setLocationRelativeTo(owner);
        this.setSize(owner.getSize());
        pack();
    }

    /**
     * Validate and update patternTextFiled entry. Values are not persisted but passed back to parent entity.
     */
    private void onOK() {
        if (null == patternTextFiled.getText() || patternTextFiled.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.msg.pattern.name"), ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.title"), JOptionPane.ERROR_MESSAGE);
        } else if (isPatternValid(patternTextFiled.getText())) {
            entity.setPattern(patternTextFiled.getText());
            entity.setEnabled(enabledCheckBox.isSelected());
            entity.setName(nameTextField.getText());
            entity.setDescription(descTextField.getText());
            confirmed = true;
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, String.format(ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.msg.pattern.compile"), patternTextFiled.getText()), ResourceBundle.getBundle(GUI).getString("InjectionFilterAssertion.ui.error.title"), JOptionPane.ERROR_MESSAGE);
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

    public InjectionPatternEntity getEntity() {
        return entity;
    }

    private void collectHighlightsForMatch(Matcher matcher, StyledDocument doc, SimpleAttributeSet nas, Collection<int[]> highlights) {
        final String testInputString = textInputTextArea.getText();
        try {
            doc.insertString(0, testInputString, nas);
        } catch (BadLocationException e1) {
            throw new IllegalArgumentException(e1);
        }

        while (matcher.find()) {
            highlights.add(new int[]{matcher.start(), matcher.end() - matcher.start()});
        }
    }

    private void doTest() {
        testResultTextPane.setText("");
        final Pattern regex = InjectionPatternEntity.getCompiledPattern(patternTextFiled.getText());

        final Matcher matcher = regex.matcher(textInputTextArea.getText());
        final StyledDocument doc = (StyledDocument) testResultTextPane.getDocument();
        final SimpleAttributeSet sas = new SimpleAttributeSet();
        sas.addAttribute(StyleConstants.ColorConstants.Background, Color.yellow);
        final SimpleAttributeSet nas = new SimpleAttributeSet();

        final Collection<int[]> highlights = new ArrayList<>();
        collectHighlightsForMatch(matcher, doc, nas, highlights);

        for (final Object highlight : highlights) {
            final int[] pos = (int[]) highlight;
            doc.setCharacterAttributes(pos[0], pos[1], sas, true);
        }
    }

    private boolean isPatternValid(final String pattern) {
        try {
            InjectionPatternEntity.getCompiledPattern(pattern);
        } catch (PatternSyntaxException pse) {
            return false;
        }
        return true;
    }


    private void addComponents() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridBagLayout());
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        GridBagConstraints gbc;
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 2, 1.0, null, GridBagConstraints.BOTH, null);
        contentPane.add(panel1, gbc);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(2, 0, null, 1.0, GridBagConstraints.BOTH, null);
        panel1.add(panel2, gbc);
        buttonOK = new JButton();
        buttonOK.setText("OK");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, null);
        panel2.add(buttonOK, gbc);
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, null);
        panel2.add(buttonCancel, gbc);
        enabledCheckBox = new JCheckBox();
        enabledCheckBox.setText("Enabled");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, null, 1.0, null, GridBagConstraints.WEST);
        panel1.add(enabledCheckBox, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, 1.0, GridBagConstraints.BOTH, null);
        contentPane.add(panel3, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Pattern");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 2, null, 1.0, null, GridBagConstraints.WEST);
        panel3.add(label1, gbc);
        patternTextFiled = new JTextField();
        patternTextFiled.setText("");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 2, 1.0, 1.0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        panel3.add(patternTextFiled, gbc);
        nameTextField = new JTextField();
        nameTextField.setText("");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 0, 1.0, 1.0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        panel3.add(nameTextField, gbc);
        final JLabel label2 = new JLabel();
        label2.setText("Name");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, null, 1.0, null, GridBagConstraints.WEST);
        panel3.add(label2, gbc);
        descTextField = new JTextField();
        descTextField.setText("");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(1, 1, 1.0, 1.0, GridBagConstraints.HORIZONTAL, GridBagConstraints.WEST);
        panel3.add(descTextField, gbc);
        final JLabel label3 = new JLabel();
        label3.setText("Description");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, null, 1.0, null, GridBagConstraints.WEST);
        panel3.add(label3, gbc);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridBagLayout());
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, 1.0, GridBagConstraints.BOTH, null);
        contentPane.add(panel4, gbc);
        final JSplitPane splitPane1 = new JSplitPane();
        splitPane1.setContinuousLayout(true);
        splitPane1.setDividerLocation(110);
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, 1.0, GridBagConstraints.BOTH, null);
        panel4.add(splitPane1, gbc);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridBagLayout());
        splitPane1.setLeftComponent(panel5);
        final JScrollPane scrollPane1 = new JScrollPane();
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, 1.0, GridBagConstraints.BOTH, null);
        panel5.add(scrollPane1, gbc);
        textInputTextArea = new JTextArea();
        scrollPane1.setViewportView(textInputTextArea);
        final JLabel label4 = new JLabel();
        label4.setText("Test Input");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, null, null, GridBagConstraints.WEST);
        panel5.add(label4, gbc);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridBagLayout());
        splitPane1.setRightComponent(panel6);
        final JLabel label5 = new JLabel();
        label5.setText("Test Result");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, null, null, GridBagConstraints.WEST);
        panel6.add(label5, gbc);
        final JScrollPane scrollPane2 = new JScrollPane();
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 1, 1.0, null, GridBagConstraints.BOTH, null);
        panel6.add(scrollPane2, gbc);
        testResultTextPane = new JTextPane();
        testResultTextPane.setMinimumSize(new Dimension(900, 678));
        scrollPane2.setViewportView(testResultTextPane);
        final JLabel label6 = new JLabel();
        label6.setText("Test Regular Expression");
        gbc = InjectionFilterAssertionUI.getGridBagConstraints(0, 0, 1.0, null, null, GridBagConstraints.WEST);
        panel4.add(label6, gbc);
    }
}
