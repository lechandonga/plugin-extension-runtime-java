package com.github.lechandonga.pluginextensionruntimejava.ext.model;

import java.util.Objects;

/**
 * 语义化版本号：major.minor.patch[.qualifier]，例如 1、1.2、1.2.3、1.2.3-SNAPSHOT。
 * 比较规则：先按 major/minor/patch 数值比较，再按限定符比较（无限定符的正式版大于带限定符版本）。
 */
public final class Version implements Comparable<Version> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String qualifier;
    private final String raw;

    private Version(int major, int minor, int patch, String qualifier, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier;
        this.raw = raw;
    }

    static Version of(int major, int minor, int patch, String qualifier, String raw) {
        return new Version(major, minor, patch, qualifier, raw);
    }

    public static Version parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("版本号不能为空");
        }
        String value = text.trim();
        String qualifier = null;
        int dash = value.indexOf('-');
        if (dash >= 0) {
            qualifier = value.substring(dash + 1);
            if (qualifier.isBlank()) {
                throw new IllegalArgumentException("非法版本号: " + text);
            }
            value = value.substring(0, dash);
        }
        String[] parts = value.split("\\.");
        if (parts.length < 1 || parts.length > 3) {
            throw new IllegalArgumentException("非法版本号: " + text);
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
            if (major < 0 || minor < 0 || patch < 0) {
                throw new IllegalArgumentException("非法版本号: " + text);
            }
            return new Version(major, minor, patch, qualifier, text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("非法版本号: " + text, ex);
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String qualifier() {
        return qualifier;
    }

    @Override
    public int compareTo(Version other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        if (patch != other.patch) {
            return Integer.compare(patch, other.patch);
        }
        if (Objects.equals(qualifier, other.qualifier)) {
            return 0;
        }
        if (qualifier == null) {
            return 1;
        }
        if (other.qualifier == null) {
            return -1;
        }
        return qualifier.compareTo(other.qualifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Version other)) {
            return false;
        }
        return compareTo(other) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, qualifier);
    }

    @Override
    public String toString() {
        return raw;
    }
}
