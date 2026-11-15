// Defines player colours and their display names and piece initials.
package ludot.model;

public enum Colour {
    RED("red", "R"),
    GREEN("green", "G"),
    YELLOW("yellow", "Y"),
    BLUE("blue", "B");

    private final String name;
    private final String initial;

    Colour(String name, String initial) {
        this.name = name;
        this.initial = initial;
    }

    public String getName() {
        return name;
    }

    public String getInitial() {
        return initial;
    }
}
