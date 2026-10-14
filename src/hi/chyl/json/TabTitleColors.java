package hi.chyl.json;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.netbeans.swing.tabcontrol.TabData;
import org.netbeans.swing.tabcontrol.TabDisplayer;
import org.netbeans.swing.tabcontrol.plaf.BasicTabDisplayerUI;
import org.netbeans.swing.tabcontrol.plaf.TabCellRenderer;
import org.netbeans.swing.tabcontrol.plaf.TabState;

/** Changes title colors without replacing the platform's tab layout or painters. */
final class TabTitleColors {
    static void install(Component component) {
        if (component instanceof TabDisplayer displayer) {
            apply(displayer);
            displayer.addPropertyChangeListener("UI", event -> apply(displayer));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) install(child);
        }
    }

    private static void apply(TabDisplayer displayer) {
        if (!(displayer.getUI() instanceof BasicTabDisplayerUI ui)) return;
        TabCellRenderer original = ui.getTabCellRenderer(-1);
        if (Proxy.isProxyClass(original.getClass())
                && Proxy.getInvocationHandler(original) instanceof TitleRenderer) return;
        try {
            // RELEASE310 exposes the renderer getter but no setter. Keep this
            // compatibility access isolated rather than replacing the whole UI.
            var rendererField = BasicTabDisplayerUI.class.getDeclaredField("defaultRenderer");
            rendererField.setAccessible(true);
            rendererField.set(ui, wrap(original));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("无法配置标签标题颜色", ex);
        }
    }

    static TabCellRenderer wrap(TabCellRenderer original) {
        return (TabCellRenderer) Proxy.newProxyInstance(TabCellRenderer.class.getClassLoader(),
            new Class<?>[] {TabCellRenderer.class}, new TitleRenderer(original));
    }

    private record TitleRenderer(TabCellRenderer original) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getName().equals("getRendererComponent")) {
                TabData tab = (TabData) arguments[0];
                int state = (Integer) arguments[2];
                String color = (state & TabState.SELECTED) != 0 ? "#000000" : "#808080";
                // Only the renderer sees markup; model titles remain original text.
                // Escape filenames so they cannot introduce their own HTML tags.
                String text = tab.getText().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                arguments = arguments.clone();
                arguments[0] = new TabData(tab.getUserObject(), tab.getIcon(),
                    "<html><font color='" + color + "'>" + text + "</font></html>", tab.getTooltip());
            }
            try {
                return method.invoke(original, arguments);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        }
    }
}
