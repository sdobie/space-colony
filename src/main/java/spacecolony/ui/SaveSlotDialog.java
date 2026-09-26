package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import spacecolony.engine.EdtGuard;
import spacecolony.save.SaveSlots;
import spacecolony.save.SlotInfo;

/**
 * Slot picker for Save As… and Load… (Plan 5 §5.3). Lists saves newest first with their kind,
 * real-world timestamp, in-game date and credits. The list is read in a {@code SwingWorker}.
 */
public final class SaveSlotDialog {
    enum Mode { SAVE_AS, LOAD }

    private static final DateTimeFormatter WHEN =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private SaveSlotDialog() {}

    /** Asks for a slot name to save into. Returns the trimmed name, or null if cancelled. */
    public static String saveAs(Component owner, SaveSlots slots) {
        Content c = new Content(slots, Mode.SAVE_AS);
        showModal(owner, c, "Save As");
        return (String) c.result;
    }

    /** Asks for a save to load. Returns it, or null if cancelled. */
    public static SlotInfo load(Component owner, SaveSlots slots) {
        Content c = new Content(slots, Mode.LOAD);
        showModal(owner, c, "Load Game");
        return (SlotInfo) c.result;
    }

    static Content contentForTest(SaveSlots slots, Mode mode) { return new Content(slots, mode); }

    private static void showModal(Component owner, Content c, String title) {
        Window w = owner instanceof Window win ? win : owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, title, Dialog.ModalityType.APPLICATION_MODAL);
        c.onClose = d::dispose;
        d.setContentPane(c);
        d.getRootPane().setDefaultButton(c.primary);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    /** "Y3 D120" from the tick, or why the file can't be loaded. */
    static String gameDate(SlotInfo i) {
        return switch (i.status()) {
            case OK -> String.format("Y%d D%d", i.tick() / 365, i.tick() % 365 + 1);
            case OTHER_SCHEMA -> "(schema " + i.schemaVersion() + ")";
            case UNREADABLE -> "(unreadable)";
        };
    }

    static final class Content extends JPanel {
        final SaveSlots slots;
        final Mode mode;
        final DefaultTableModel model = new DefaultTableModel(
                new Object[] {"Name", "Kind", "Saved", "Game date", "Credits"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        final JTable table = new JTable(model);
        final JTextField nameField = new JTextField(24);
        final JLabel error = new JLabel(" ");
        final JLabel status = new JLabel("Loading…");
        final JButton primary;
        final JButton delete = new JButton("Delete");
        final JButton cancel = new JButton("Cancel");
        final List<SlotInfo> rows = new ArrayList<>();
        Object result;
        Runnable onClose = () -> {};

        Content(SaveSlots slots, Mode mode) {
            super(new BorderLayout(0, 6));
            this.slots = slots;
            this.mode = mode;
            this.primary = new JButton(mode == Mode.SAVE_AS ? "Save" : "Load");
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getTableHeader().setReorderingAllowed(false);
            table.getColumnModel().getColumn(0).setPreferredWidth(160);
            table.getColumnModel().getColumn(2).setPreferredWidth(120);
            table.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                SlotInfo s = selected();
                if (mode == Mode.SAVE_AS && s != null) nameField.setText(s.name());
                updateButtons();
            });
            table.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (mode == Mode.LOAD && e.getClickCount() == 2 && primary.isEnabled()) onPrimary();
                }
            });
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(560, 240));
            add(status, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);

            JPanel south = new JPanel();
            south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
            if (mode == Mode.SAVE_AS) {
                JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                nameRow.add(new JLabel("Name:"));
                nameRow.add(nameField);
                south.add(nameRow);
                error.setForeground(UiColors.ERROR);
                JPanel errRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
                errRow.add(error);
                south.add(errRow);
                nameField.getDocument().addDocumentListener(new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent e) { updateButtons(); }
                    @Override public void removeUpdate(DocumentEvent e) { updateButtons(); }
                    @Override public void changedUpdate(DocumentEvent e) { updateButtons(); }
                });
            }
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            if (mode == Mode.LOAD) buttons.add(delete);
            buttons.add(cancel);
            buttons.add(primary);
            south.add(buttons);
            add(south, BorderLayout.SOUTH);

            primary.addActionListener(e -> onPrimary());
            delete.addActionListener(e -> onDelete());
            cancel.addActionListener(e -> { result = null; onClose.run(); });
            updateButtons();
            reload();
        }

        /** Re-reads the directory off the EDT and refills the table. */
        void reload() {
            status.setText("Loading…");
            new SwingWorker<List<SlotInfo>, Void>() {
                @Override protected List<SlotInfo> doInBackground() throws IOException { return slots.list(); }
                @Override protected void done() {
                    EdtGuard.assertEdt();
                    try {
                        fill(get());
                    } catch (InterruptedException | ExecutionException ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        fill(List.of());
                        status.setText("Could not read saves: " + cause.getMessage());
                    }
                }
            }.execute();
        }

        private void fill(List<SlotInfo> all) {
            rows.clear();
            model.setRowCount(0);
            for (SlotInfo i : all) {
                // Save As only offers real slots to overwrite; autosaves and broken files are Load-only.
                if (mode == Mode.SAVE_AS && (i.autosave() || i.status() != SlotInfo.Status.OK)) continue;
                rows.add(i);
                model.addRow(new Object[] {
                    i.name().equals(SaveSlots.UNNAMED_AUTOSAVE) ? "(unnamed game)" : i.name(),
                    i.autosave() ? "autosave" : "slot",
                    WHEN.format(i.modified()),
                    gameDate(i),
                    i.status() == SlotInfo.Status.OK ? Long.toString(i.credits()) : ""});
            }
            status.setText(rows.isEmpty() ? "No saves yet." : " ");
            updateButtons();
        }

        SlotInfo selected() {
            int i = table.getSelectedRow();
            return i < 0 || i >= rows.size() ? null : rows.get(i);
        }

        void updateButtons() {
            SlotInfo s = selected();
            if (mode == Mode.SAVE_AS) {
                String err = SaveSlots.validateName(nameField.getText());
                // Say nothing about an empty field until the player has typed something.
                error.setText(err == null || nameField.getText().isEmpty() ? " " : err);
                primary.setEnabled(err == null);
            } else {
                primary.setEnabled(s != null && s.status() == SlotInfo.Status.OK);
                delete.setEnabled(s != null);
            }
        }

        void onPrimary() {
            if (!primary.isEnabled()) return;
            if (mode == Mode.SAVE_AS) {
                String name = nameField.getText().trim();
                if (Files.exists(slots.slotPath(name))) {
                    int ok = JOptionPane.showConfirmDialog(this, "Overwrite “" + name + "”?",
                        "Save As", JOptionPane.OK_CANCEL_OPTION);
                    if (ok != JOptionPane.OK_OPTION) return;
                }
                result = name;
            } else {
                result = selected();
            }
            onClose.run();
        }

        private void onDelete() {
            SlotInfo s = selected();
            if (s == null) return;
            String label = "“" + s.name() + "”" + (s.autosave() ? " (autosave)" : "");
            int ok = JOptionPane.showConfirmDialog(this, "Delete " + label + "?",
                "Delete Save", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return;
            try {
                slots.delete(s);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Could not delete: " + ex.getMessage(),
                    "Delete Save", JOptionPane.ERROR_MESSAGE);
            }
            reload();
        }
    }
}
