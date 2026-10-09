package com.ynks.burndown.chart

import androidx.annotation.ColorInt

data class ChartColors(
    @ColorInt val primary: Int,
    @ColorInt val onPrimary: Int,
    @ColorInt val secondary: Int,
    @ColorInt val onSecondary: Int,
    @ColorInt val trend: Int,
    @ColorInt val grid: Int,
    @ColorInt val label: Int,
    @ColorInt val plate: Int,
)
