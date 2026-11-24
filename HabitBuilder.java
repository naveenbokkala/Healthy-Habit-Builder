import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.TextStyle;
import java.util.List;
import java.util.*;

/**
 * Healthy Habit Builder - Java Swing PBL project.
 *  - Add / delete healthy habits
 *  - Day-by-day checkboxes for each month
 *  - Monthly progress graphs (bar chart per habit + line chart per day)
 *  - Data is saved automatically to habits_data.txt in your home folder
 */
public class HabitBuilder extends JFrame {

    // ---------- data ----------
    private final List<String> names = new ArrayList<>();
    private final Map<String, Set<LocalDate>> done = new HashMap<>();
    private YearMonth current = YearMonth.now();
    private final Path saveFile = Paths.get(System.getProperty("user.home"), "habits_data.txt");

    // ---------- UI ----------
    private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JTable table = new JTable();
    private final ChartPanel chart = new ChartPanel();

    private final AbstractTableModel model = new AbstractTableModel() {
        public int getRowCount() { return names.size(); }
        public int getColumnCount() { return current.lengthOfMonth() + 2; }

        public String getColumnName(int c) {
            if (c == 0) return "Habit";
            if (c == getColumnCount() - 1) return "Done";
            return String.valueOf(c);
        }

        public Class<?> getColumnClass(int c) {
            return (c == 0 || c == getColumnCount() - 1) ? String.class : Boolean.class;
        }

        // only past/today days can be ticked
        public boolean isCellEditable(int r, int c) {
            return c > 0 && c < getColumnCount() - 1 && !current.atDay(c).isAfter(LocalDate.now());
        }

        public Object getValueAt(int r, int c) {
            String n = names.get(r);
            if (c == 0) return n;
            if (c == getColumnCount() - 1) return countMonth(n) + "/" + current.lengthOfMonth();
            return done.get(n).contains(current.atDay(c));
        }

        public void setValueAt(Object v, int r, int c) {
            Set<LocalDate> s = done.get(names.get(r));
            LocalDate d = current.atDay(c);
            if ((Boolean) v) s.add(d); else s.remove(d);
            save();
            fireTableRowsUpdated(r, r);
            chart.repaint();
        }
    };

    public HabitBuilder() {
        super("Healthy Habit Builder");
        load();
        buildUI();
        refresh();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 680);
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        // top bar: month navigation
        JButton prev = new JButton("< Prev");
        JButton next = new JButton("Next >");
        JButton today = new JButton("Today");
        prev.addActionListener(e -> { current = current.minusMonths(1); refresh(); });
        next.addActionListener(e -> { current = current.plusMonths(1); refresh(); });
        today.addActionListener(e -> { current = YearMonth.now(); refresh(); });
        monthLabel.setFont(monthLabel.getFont().deriveFont(Font.BOLD, 20f));

        JPanel nav = new JPanel(new BorderLayout(10, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        left.add(prev); left.add(next); left.add(today);
        nav.add(left, BorderLayout.WEST);
        nav.add(monthLabel, BorderLayout.CENTER);
        nav.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // bottom bar: add / delete habits
        JButton add = new JButton("+ Add Habit");
        JButton del = new JButton("Delete Selected Habit");
        add.addActionListener(e -> addHabit());
        del.addActionListener(e -> deleteHabit());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bottom.add(add); bottom.add(del);
        bottom.add(new JLabel("   Tick a box for each day you completed the habit."));

        // table
        table.setModel(model);
        table.setRowHeight(30);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);

        JPanel trackerTab = new JPanel(new BorderLayout());
        trackerTab.add(new JScrollPane(table), BorderLayout.CENTER);
        trackerTab.add(bottom, BorderLayout.SOUTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Daily Tracker", trackerTab);
        tabs.addTab("Monthly Progress", chart);
        tabs.addChangeListener(e -> chart.repaint());

        add(nav, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /** Rebuild the table for the selected month and redraw everything. */
    private void refresh() {
        monthLabel.setText(current.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + current.getYear());
        model.fireTableStructureChanged();
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(200);
        for (int c = 1; c < cm.getColumnCount() - 1; c++) cm.getColumn(c).setPreferredWidth(30);
        cm.getColumn(cm.getColumnCount() - 1).setPreferredWidth(60);
        chart.repaint();
    }

    private void addHabit() {
        String n = JOptionPane.showInputDialog(this, "Enter new healthy habit:", "Add Habit",
                JOptionPane.PLAIN_MESSAGE);
        if (n == null) return;
        n = n.trim().replace("|", "-");
        if (n.isEmpty()) return;
        if (done.containsKey(n)) {
            JOptionPane.showMessageDialog(this, "That habit already exists.");
            return;
        }
        names.add(n);
        done.put(n, new TreeSet<>());
        save();
        refresh();
    }

    private void deleteHabit() {
        int r = table.getSelectedRow();
        if (r < 0) {
            JOptionPane.showMessageDialog(this, "Select a habit row first.");
            return;
        }
        String n = names.get(r);
        int ok = JOptionPane.showConfirmDialog(this, "Delete \"" + n + "\" and all its history?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            names.remove(r);
            done.remove(n);
            save();
            refresh();
        }
    }

    // ---------- statistics ----------
    private int countMonth(String habit) {
        int n = 0;
        for (LocalDate d : done.get(habit)) if (YearMonth.from(d).equals(current)) n++;
        return n;
    }

    /** Number of days of the selected month that count (up to today for the current month). */
    private int daysCounted() {
        YearMonth now = YearMonth.now();
        if (current.equals(now)) return LocalDate.now().getDayOfMonth();
        if (current.isAfter(now)) return 0;
        return current.lengthOfMonth();
    }

    private double habitPct(String habit) {
        int dc = daysCounted();
        return dc == 0 ? 0 : countMonth(habit) * 100.0 / dc;
    }

    private double dayPct(int day) {
        if (names.isEmpty()) return 0;
        int c = 0;
        for (String n : names) if (done.get(n).contains(current.atDay(day))) c++;
        return c * 100.0 / names.size();
    }

    private double overall() {
        if (names.isEmpty()) return 0;
        double s = 0;
        for (String n : names) s += habitPct(n);
        return s / names.size();
    }

    // ---------- save / load ----------
    private void save() {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(saveFile))) {
            for (String n : names) {
                StringBuilder sb = new StringBuilder(n).append("|");
                for (LocalDate d : done.get(n)) sb.append(d).append(",");
                pw.println(sb);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save data: " + ex.getMessage());
        }
    }

    private void load() {
        if (Files.exists(saveFile)) {
            try {
                for (String line : Files.readAllLines(saveFile)) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\\|", -1);
                    Set<LocalDate> s = new TreeSet<>();
                    for (String d : parts[1].split(","))
                        if (!d.isBlank()) s.add(LocalDate.parse(d.trim()));
                    names.add(parts[0]);
                    done.put(parts[0], s);
                }
                return;
            } catch (Exception ex) {
                names.clear();
                done.clear();
            }
        }
        for (String h : new String[]{"Drink 8 glasses of water", "Exercise 30 minutes",
                "Sleep 7-8 hours", "Eat fruits & vegetables", "Meditate 10 minutes", "No junk food"}) {
            names.add(h);
            done.put(h, new TreeSet<>());
        }
    }

    // ---------- graphs ----------
    private class ChartPanel extends JPanel {
        ChartPanel() { setBackground(Color.WHITE); }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (names.isEmpty()) {
                g.drawString("No habits yet - add some in the Daily Tracker tab.", 30, 40);
                return;
            }
            drawBars(g, 0, 0, w, h / 2);
            drawLine(g, 0, h / 2, w, h - h / 2);
        }

        private void grid(Graphics2D g, int l, int t, int r, int b) {
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
            for (int p = 0; p <= 100; p += 25) {
                int yy = b - (b - t) * p / 100;
                g.setColor(new Color(225, 225, 225));
                g.drawLine(l, yy, r, yy);
                g.setColor(Color.GRAY);
                g.drawString(p + "%", l - 36, yy + 4);
            }
            g.setColor(Color.BLACK);
            g.drawLine(l, t, l, b);
            g.drawLine(l, b, r, b);
        }

        private void title(Graphics2D g, String s, int x, int y) {
            g.setColor(new Color(40, 40, 40));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
            g.drawString(s, x, y);
        }

        private void drawBars(Graphics2D g, int x, int y, int w, int h) {
            int l = x + 60, r = x + w - 30, t = y + 45, b = y + h - 40;
            title(g, "Habit completion - " + monthLabel.getText()
                    + "   (overall " + Math.round(overall()) + "%)", l, y + 25);
            grid(g, l, t, r, b);

            int n = names.size();
            double slot = (r - l) / (double) n;
            int bw = (int) Math.min(80, slot * 0.6);
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < n; i++) {
                String name = names.get(i);
                double p = habitPct(name);
                int bh = (int) ((b - t) * p / 100);
                int bx = (int) (l + slot * i + (slot - bw) / 2);
                g.setColor(p >= 70 ? new Color(46, 160, 67) : p >= 40 ? new Color(240, 160, 30)
                        : new Color(214, 69, 65));
                g.fillRect(bx, b - bh, bw, bh);
                g.setColor(Color.BLACK);
                String pct = Math.round(p) + "%";
                g.drawString(pct, bx + (bw - fm.stringWidth(pct)) / 2, b - bh - 4);
                String s = name;
                while (fm.stringWidth(s) > slot - 4 && s.length() > 3) s = s.substring(0, s.length() - 1);
                g.drawString(s, (int) (l + slot * i + (slot - fm.stringWidth(s)) / 2), b + 16);
            }
        }

        private void drawLine(Graphics2D g, int x, int y, int w, int h) {
            int l = x + 60, r = x + w - 30, t = y + 35, b = y + h - 35;
            title(g, "Daily progress (% of habits completed each day)", l, y + 20);
            grid(g, l, t, r, b);

            int days = current.lengthOfMonth();
            int dc = daysCounted();
            double step = (r - l) / (double) Math.max(days - 1, 1);
            int skip = step < 18 ? 2 : 1;
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
            for (int d = 1; d <= days; d += skip) {
                g.setColor(Color.GRAY);
                g.drawString(String.valueOf(d), (int) (l + step * (d - 1)) - 4, b + 15);
            }
            int px = -1, py = -1;
            for (int d = 1; d <= dc; d++) {
                int cx = (int) (l + step * (d - 1));
                int cy = b - (int) ((b - t) * dayPct(d) / 100);
                g.setColor(new Color(41, 98, 255));
                g.setStroke(new BasicStroke(2f));
                if (px >= 0) g.drawLine(px, py, cx, cy);
                g.fillOval(cx - 4, cy - 4, 8, 8);
                px = cx; py = cy;
            }
            g.setStroke(new BasicStroke(1f));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HabitBuilder().setVisible(true));
    }
}
