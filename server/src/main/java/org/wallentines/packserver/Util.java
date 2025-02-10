package org.wallentines.packserver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    public static final Pattern HEXADECIMAL_PATTERN = Pattern.compile("\\p{XDigit}+");

    public static final Pattern TAG_PATTERN = Pattern.compile("[0-9A-Za-z-_]+");

    public static boolean isHexadecimal(String input) {
        final Matcher matcher = HEXADECIMAL_PATTERN.matcher(input);
        return matcher.matches();
    }

    public static boolean isValidTag(String input) {
        final Matcher matcher = TAG_PATTERN.matcher(input);
        return matcher.matches();
    }


}
