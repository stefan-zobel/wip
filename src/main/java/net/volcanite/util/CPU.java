package net.volcanite.util;

public final class CPU {

    public static native int detectInstructionSet();

    static {
        System.loadLibrary("instructionset_detect");
    }

    public static void main(String[] args) {
        System.out.println("Instructionset: " + detectInstructionSet());
    }

    private CPU() {
        throw new AssertionError();
    }
}
