package com.github.lechandonga.pluginextensionruntimejava.ext.model;

/**
 * Maven 风格的版本区间。
 * <ul>
 *   <li>{@code [1.0,2.0)}：左闭右开</li>
 *   <li>{@code [1.0,2.0]} / {@code (1.0,2.0)}：边界开闭可控</li>
 *   <li>{@code [1.0,)}：单侧无界</li>
 *   <li>裸版本 {@code 1.2} 等价于 {@code [1.2,2.0.0)}，即同一主版本且不低于该版本</li>
 * </ul>
 */
public final class VersionRange {

    private final Version lower;
    private final Version upper;
    private final boolean lowerInclusive;
    private final boolean upperInclusive;
    private final String raw;

    private VersionRange(Version lower, Version upper, boolean lowerInclusive,
                         boolean upperInclusive, String raw) {
        this.lower = lower;
        this.upper = upper;
        this.lowerInclusive = lowerInclusive;
        this.upperInclusive = upperInclusive;
        this.raw = raw;
    }

    public static VersionRange parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("版本区间不能为空");
        }
        String value = text.trim();
        if (!value.contains(",") && (value.startsWith("[") || value.startsWith("("))) {
            throw new IllegalArgumentException("非法版本区间: " + text);
        }
        if (value.startsWith("[") || value.startsWith("(")) {
            boolean lowerInclusive = value.charAt(0) == '[';
            if (!value.endsWith("]") && !value.endsWith(")")) {
                throw new IllegalArgumentException("非法版本区间: " + text);
            }
            boolean upperInclusive = value.charAt(value.length() - 1) == ']';
            String body = value.substring(1, value.length() - 1);
            String[] sides = body.split(",", -1);
            if (sides.length != 2) {
                throw new IllegalArgumentException("非法版本区间: " + text);
            }
            Version lower = sides[0].isBlank() ? null : Version.parse(sides[0].trim());
            Version upper = sides[1].isBlank() ? null : Version.parse(sides[1].trim());
            if (lower == null && !lowerInclusive) {
                throw new IllegalArgumentException("非法版本区间: " + text);
            }
            return new VersionRange(lower, upper, lowerInclusive, upperInclusive, value);
        }
        Version version = Version.parse(value);
        Version ceiling = Version.of(version.major() + 1, 0, 0, null, (version.major() + 1) + ".0.0");
        return new VersionRange(version, ceiling, true, false, value);
    }

    public boolean contains(Version version) {
        if (lower != null) {
            int cmp = version.compareTo(lower);
            if (cmp < 0 || (cmp == 0 && !lowerInclusive)) {
                return false;
            }
        }
        if (upper != null) {
            int cmp = version.compareTo(upper);
            return cmp < 0 || (cmp == 0 && upperInclusive);
        }
        return true;
    }

    @Override
    public String toString() {
        return raw;
    }
}
