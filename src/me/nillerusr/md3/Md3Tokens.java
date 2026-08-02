package me.nillerusr.md3;

/** Plain holder of MD3 color tokens */
public final class Md3Tokens {
    public static final class Role {
        public int color;
        public int onColor;
        public int container;
        public int onContainer;
    }
    public Role primary   = new Role();
    public Role secondary = new Role();
    public Role tertiary  = new Role();
    public Role error     = new Role();

    public int surfaceDim;
    public int surface;
    public int surfaceBright;
    public int surfaceContainerLowest;
    public int surfaceContainerLow;
    public int surfaceContainer;
    public int surfaceContainerHigh;
    public int surfaceContainerHighest;

    public int onSurface;
    public int onSurfaceVariant;
    public int outline;
    public int outlineVariant;

    public int inverseSurface;
    public int inverseOnSurface;
    public int inversePrimary;

    public int statusBar;
    public int navBar;

    public boolean dark;
}
