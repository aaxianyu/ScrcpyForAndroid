package io.github.miuzarte.scrcpyforandroid.constants

import androidx.compose.ui.unit.dp

object UiSpacing {
    val Tiny = 2.dp
    val Small = 4.dp
    val Medium = 8.dp
    val MediumLarge = 12.dp
    val PageItem = 12.dp
    val Large = 16.dp
    val PopupHorizontal = 20.dp
    val PageHorizontal = 12.dp
    val PageVertical = 12.dp
    val SectionTitleLeadingGap = 8.dp
    val SectionTitleStart = 12.dp
    val SectionTitleTop = 12.dp
    val SectionTitleBottom = 6.dp
    val FieldLabelBottom = 4.dp
    val Content = 12.dp
    val ContentVertical = 12.dp
    val ContentHorizontal = 12.dp
    val CardTitle = 16.dp
    val PageBottom = 64.dp
    val SheetBottom = 16.dp

    /**
     * 底部弹窗标题栏左侧图标钮的光学下沉量。
     *
     * `IconButton` 与标题都按几何中心居中，但标题是 18sp 汉字、墨迹上下留白不对称，
     * 墨迹中心比几何中心低约 1dp；图标是纯几何图形。实测两处 bottom sheet 的墨迹
     * 中心差 2.5~3.0px（@440dpi），肉眼可见箭头偏高。
     */
    val SheetHeaderIconOpticalOffset = 1.dp
}
