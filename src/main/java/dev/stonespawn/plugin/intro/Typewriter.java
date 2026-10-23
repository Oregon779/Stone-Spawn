package dev.stonespawn.plugin.intro;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** Reveals a formatted text letter by letter; the letters already shown keep their colors and styles. */
final class Typewriter {

    private Typewriter() {
    }

    static int length(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text).length();
    }

    /** The first {@code letters} letters of {@code text}, with their formatting. */
    static Component cut(Component text, int letters) {
        return cut(text, new int[]{letters});
    }

    private static Component cut(Component component, int[] left) {
        Component own;
        if (component instanceof TextComponent text) {
            String content = text.content();
            int shown = Math.min(content.length(), Math.max(0, left[0]));
            // Never split an emoji or other character made of two chars.
            if (shown > 0 && shown < content.length() && Character.isHighSurrogate(content.charAt(shown - 1))) {
                shown--;
            }
            left[0] -= shown;
            own = text.content(content.substring(0, shown)).children(List.of());
        } else {
            // Translations, key names and the like appear in one go.
            own = component.children(List.of());
            left[0] -= length(own);
        }
        List<Component> children = new ArrayList<>();
        for (Component child : component.children()) {
            if (left[0] <= 0) {
                break;
            }
            children.add(cut(child, left));
        }
        return own.children(children);
    }
}
