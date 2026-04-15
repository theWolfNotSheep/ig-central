package co.uk.wolfnotsheep.document.util;

public final class SlugGenerator {

    private SlugGenerator() {}

    public static String generate(String name, String id) {
        String base = slugify(name);
        String shortId = id.length() >= 6 ? id.substring(id.length() - 6) : id;
        return base + "-" + shortId;
    }

    private static String slugify(String input) {
        if (input == null || input.isBlank()) return "untitled";
        String name = input.contains(".") ? input.substring(0, input.lastIndexOf('.')) : input;
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
