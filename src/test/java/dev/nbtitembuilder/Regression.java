package dev.nbtitembuilder;

/** Runs the whole regression set; every build and CI execution runs this. */
public final class Regression {
    private Regression() {}

    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        if (!IdsSmoke.run(out)) {
            System.out.println(out);
            System.exit(1);
        }
        System.out.println(out);
        LogicSuite.main(new String[0]);
    }
}
