# Ignore third-party library warnings that do not affect execution
-dontwarn kotlinx.serialization.**
-dontwarn kotlinx.datetime.**
-dontwarn org.jetbrains.exposed.**
-dontwarn org.slf4j.**
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.Unsafe

# Retain database and reflection related classes to prevent obfuscation execution errors
-keep class org.jetbrains.exposed.** { *; }
-keep class org.xerial.sqlite.** { *; }
-keep class org.sqlite.** { *; }

# Retain project main logic classes
-keep class com.visuallog.** { *; }
-keep class com.visuallog.MainKt { *; }

# Retain coroutine ServiceLoader configuration to prevent Main dispatcher initialization failure
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
