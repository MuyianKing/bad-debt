package com.kyant.backdrop

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

// Vendored from Kyant0/AndroidLiquidGlass 2.0.0 (Apache-2.0), adapted to Compose 1.7 / single-module Android.

@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
fun isRenderEffectSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@ChecksSdkIntAtLeast(Build.VERSION_CODES.TIRAMISU)
fun isRuntimeShaderSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
