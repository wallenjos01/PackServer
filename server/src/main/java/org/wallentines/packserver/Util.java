package org.wallentines.packserver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import org.wallentines.mdcfg.Tuples.T2;

public class Util {

    public static final Pattern HEXADECIMAL_PATTERN =
        Pattern.compile("\\p{XDigit}+");

    public static final Pattern TAG_PATTERN =
        Pattern.compile("([a-z][0-9A-Za-z-_]*):?([0-9A-Za-z-_]*)");

    public static boolean isHexadecimal(String input) {
        final Matcher matcher = HEXADECIMAL_PATTERN.matcher(input);
        return matcher.matches();
    }

    public static boolean isValidTag(String input) {
        final Matcher matcher = TAG_PATTERN.matcher(input);
        return matcher.matches();
    }

    @Nullable
    public static T2<String, String> parseTag(String input) {

        final Matcher matcher = TAG_PATTERN.matcher(input);
        if (!matcher.matches())
            return null;

        String tag = matcher.group(1);
        String version = matcher.groupCount() < 2 ? "latest" : matcher.group(2);

        return new T2<>(tag, version);
    }
}
