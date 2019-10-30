package coder.utils;

public class Const {
    public static final int RULE_SIZE_INIT_SHRINK = 8;
    public static final int MAX_RULE_ARRAYS = RULE_SIZE_INIT_SHRINK * 512;

    public static final int WORK_ARRAY_BASIC_LENGTH = 100;

    public static final int ALLOW_ACCURACY = 20;
    public static final int ALLOW_MASK = 0xfffff000;

    public static int DENY_ACCURACY = 25;
    public static int DENY_MASK = 0xffffff80;

    public static double FALSE_POSITIVE_RATE = 0.001;

    public static final int BUFFERED_LINES = 2000;
}
