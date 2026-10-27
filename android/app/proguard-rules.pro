# 原生核心由清单直接引用，R8 会保留；这里只兜底 JSON 字段与反射入口。
-keep class muyian.bill.listener.** { *; }
-keepattributes *Annotation*
