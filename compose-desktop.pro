# 忽略第三方库中不影响运行的警告
-dontwarn kotlinx.serialization.**
-dontwarn kotlinx.datetime.**
-dontwarn org.jetbrains.exposed.**
-dontwarn org.slf4j.**
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.Unsafe

# 保留数据库和反射相关的类，防止被混淆导致运行报错
-keep class org.jetbrains.exposed.** { *; }
-keep class org.xerial.sqlite.** { *; }
-keep class org.sqlite.** { *; }

# 保留项目主逻辑类
-keep class com.visuallog.** { *; }
-keep class MainKt { *; }

# 保持协程的 ServiceLoader 配置，防止 Main 调度器初始化失败
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
