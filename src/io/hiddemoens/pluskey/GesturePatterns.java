package io.hiddemoens.pluskey;

final class GesturePatterns {
    static final int MAX_LENGTH = 8;

    private GesturePatterns() { }

    static int id(String pattern) {
        if (pattern == null || pattern.isEmpty() || pattern.length() > MAX_LENGTH)
            throw new IllegalArgumentException("Expected 1 to 8 dots or dashes");
        int bits = 1;
        for (int index = 0; index < pattern.length(); index++) {
            char symbol = pattern.charAt(index);
            if (symbol != '.' && symbol != '-') throw new IllegalArgumentException("Expected ASCII dot or dash");
            bits = (bits << 1) | (symbol == '-' ? 1 : 0);
        }
        switch (bits) {
            case 2: return KeyPress.SHORT;
            case 3: return KeyPress.LONG;
            case 4: return KeyPress.DOUBLE;
            case 8: return KeyPress.TRIPLE;
            default: return bits;
        }
    }

    static String text(int id) {
        switch (id) {
            case KeyPress.SHORT: return ".";
            case KeyPress.LONG: return "-";
            case KeyPress.DOUBLE: return "..";
            case KeyPress.TRIPLE: return "...";
            default:
                if (id < 5 || id == 8 || id >= (2 << MAX_LENGTH)) throw new IllegalArgumentException("Unknown gesture ID: " + id);
                return Integer.toBinaryString(id).substring(1).replace('0', '.').replace('1', '-');
        }
    }

    static String label(int id) {
        String pattern = text(id);
        StringBuilder label = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            if (index != 0) label.append(' ');
            label.append(pattern.charAt(index) == '.' ? '●' : '—');
        }
        return label.toString();
    }
}
