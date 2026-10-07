package com.github.lechandonga.pluginextensionruntimejava.extension;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本与版本区间。区间语法示例：
 * <ul>
 *   <li>{@code 1.2.0} 精确版本</li>
 *   <li>{@code [1.0,2.0)} 左闭右开</li>
 *   <li>{@code [1.0,]} 下限闭区间，无上限</li>
 * </ul>
 */
public final class VersionRange {

    private static final Pattern RANGE_PATTERN =
            Pattern.compile("^([\\[(])\\s*([^,]*)\\s*,\\s*([^])]*)\\s*([\\])])$");

    private final Version lower;
    private final boolean lowerInclusive;
    private final Version upper;
    private final boolean upperInclusive;

    private VersionRange(Version lower, boolean lowerInclusive, Version upper, boolean upperInclusive) {
        this.lower = lower;
        this.lowerInclusive = lowerInclusive;
        this.upper = upper;
        this.upperInclusive = upperInclusive;
    }

    public static VersionRange parse(String text) {
        Objects.requireNonNull(text, "version range text");
        String trimmed = text.trim();
        Matcher matcher = RANGE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            Version lower = matcher.group(2).isBlank() ? null : Version.parse(matcher.group(2).trim());
            Version upper = matcher.group(3).isBlank() ? null : Version.parse(matcher.group(3).trim());
            return new VersionRange(lower, "[".equals(matcher.group(1)),
                    upper, "]".equals(matcher.group(4)));
        }
        Version exact = Version.parse(trimmed);
        return new VersionRange(exact, true, exact, true);
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
            return cmp <= 0 && (cmp != 0 || upperInclusive);
        }
        return true;
    }

    public record Version(int major, int minor, int patch) implements Comparable<Version> {

        public static Version parse(String text) {
            String[] parts = text.trim().split("\\.");
            if (parts.length == 0 || parts.length > 3) {
                throw new IllegalArgumentException("非法版本号: " + text);
            }
            try {
                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                return new Version(major, minor, patch);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("非法版本号: " + text, e);
            }
        }

        @Override
        public int compareTo(Version other) {
            int cmp = Integer.compare(major, other.major);
            if (cmp != 0) {
                return cmp;
            }
            cmp = Integer.compare(minor, other.minor);
            return cmp != 0 ? cmp : Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}
