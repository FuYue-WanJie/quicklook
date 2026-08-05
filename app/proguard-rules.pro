# Add project specific ProGuard rules here.
# Compose / Material3 / Navigation / Coil / Media3 / DataStore 均自带 consumer rules，
# 无需额外 keep。以下仅为应用自身的必要保留。

# 保留 Application 子类（Coil ImageLoaderFactory 反射实例化）
-keep class wanjie.quicklook.QuickLookApp { *; }

# 保留 FileProvider authority 与路径配置（由 manifest 合并处理，此处仅保险）
-keepclassmembers class androidx.core.content.FileProvider { *; }

# Media3 ExoPlayer 内部使用少量反射，库已附带规则；如遇裁剪问题可按需放开
# -keep class androidx.media3.** { *; }

# zip4j：AES 加密/解密使用少量反射，库已附带 consumer rules；如遇裁剪问题可放开
# -keep class net.lingala.zip4j.** { *; }

# SevenZipJBinding：JNI 桥接层，需保留 native 方法与回调接口
-keep class net.sf.sevenzipjbinding.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Amplituda：波形提取库，保留入口类
-keep class linc.com.amplituda.** { *; }
