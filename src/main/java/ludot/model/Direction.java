// Defines clockwise and counter-clockwise movement steps and display names.
package ludot.model;

public enum Direction {
    CLOCKWISE(1, "clockwise"),
    COUNTERCLOCKWISE(-1, "counter-clockwise");

    private final int step;
    private final String name;

    Direction(int step, String name) {
        this.step = step;
        this.name = name;
    }

    public int getStep() {
        return step;
    }

    public String getName() {
        return name;
    }
}
