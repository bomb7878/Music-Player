import javax.swing.*;
import java.awt.*;

public enum Colors {
    DEFAULT (new JPanel().getBackground()),
    MAIN_BACKGROUND_DARK_THEME (new Color(30,30,30)),
    PANELS_DARK_THEME (new Color(45, 45, 45)),
    MUSIC_LIST_DARK_THEME (new Color(37, 37, 37)),
    BASIC_TEXT_DARK_THEME (new Color(200,200,200)),
    ACCENT_DARK_THEME (new Color(30,144,255)),
    HIGHLIGHTED_CELL_DARK_THEME(new Color(30, 58, 95)),
    ACTIVE_MUSIC_TEXT_DARK_THEME (Color.WHITE),
    MUSIC_TIME_BACKGROUND_DARK_THEME (new Color(68, 68, 68)),
    BUTTONS_DARK_THEME (new Color(60, 60, 60));

    private Color color;
    Colors(Color c) {
        this.color = c;
    }
    public Color getColor() {
        return this.color;
    }
    /**
     * Сброс компонента к стандартным (светлым) цветам LookAndFeel.
     */
    public static void toDefault(JComponent component) {
        component.setBackground(UIManager.getColor("Panel.background"));
        component.setForeground(UIManager.getColor("Panel.foreground"));

        if (component instanceof JList<?>) {
            JList<?> list = (JList<?>) component;
            list.setSelectionBackground(UIManager.getColor("List.selectionBackground"));
            list.setSelectionForeground(UIManager.getColor("List.selectionForeground"));
        } else if (component instanceof JProgressBar) {
            JProgressBar bar = (JProgressBar) component;
            bar.setForeground(UIManager.getColor("ProgressBar.foreground"));
            bar.setBackground(UIManager.getColor("ProgressBar.background"));
        } else if (component instanceof JSlider) {
            JSlider slider = (JSlider) component;
            slider.setForeground(UIManager.getColor("Slider.foreground"));
            slider.setBackground(UIManager.getColor("Slider.background"));
        } else if (component instanceof JButton) {
            component.setBackground(UIManager.getColor("Button.background"));
            component.setForeground(UIManager.getColor("Button.foreground"));
        }
    }
}
