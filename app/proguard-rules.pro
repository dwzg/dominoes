# kotlinx.serialization ships its own keep rules; navigation routes are @Serializable
# classes and are covered by them. Add app-specific rules here if needed.

# Same-Wi-Fi hosting runs a Ktor server in the app. Ktor's debugger check refers to JVM
# management classes that Android doesn't have; it's never used on Android.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
