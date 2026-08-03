package me.nillerusr.md3

/** Plain holder of MD3 color tokens. */
class Md3Tokens {
    class Role {
        @JvmField var color = 0
        @JvmField var onColor = 0
        @JvmField var container = 0
        @JvmField var onContainer = 0
    }

    @JvmField var primary = Role()
    @JvmField var secondary = Role()
    @JvmField var tertiary = Role()
    @JvmField var error = Role()

    @JvmField var surfaceDim = 0
    @JvmField var surface = 0
    @JvmField var surfaceBright = 0
    @JvmField var surfaceContainerLowest = 0
    @JvmField var surfaceContainerLow = 0
    @JvmField var surfaceContainer = 0
    @JvmField var surfaceContainerHigh = 0
    @JvmField var surfaceContainerHighest = 0
    @JvmField var onSurface = 0
    @JvmField var onSurfaceVariant = 0
    @JvmField var outline = 0
    @JvmField var outlineVariant = 0
    @JvmField var inverseSurface = 0
    @JvmField var inverseOnSurface = 0
    @JvmField var inversePrimary = 0
    @JvmField var statusBar = 0
    @JvmField var navBar = 0
    @JvmField var dark = false
}
