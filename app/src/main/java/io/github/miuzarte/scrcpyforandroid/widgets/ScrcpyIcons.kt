package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

// Sysbar 导航栏图标 — 从 miuix 子模块移植到项目内
// 他人克隆后无需自定义 miuix 子模块即可编译

object ScrcpyIcons {
    // 返回键：左箭头
    val SysbarBack: ImageVector
        get() {
            if (_sysbarBack != null) return _sysbarBack!!
            _sysbarBack = ImageVector.Builder(
                name = "SysbarBack",
                defaultWidth = 24.0f.dp,
                defaultHeight = 24.0f.dp,
                viewportWidth = 1200.0f,
                viewportHeight = 1200.0f,
            ).apply {
                group(scaleY = -1.0f, translationY = 1200.0f) {
                    addPath(
                        pathData = listOf(
                            PathNode.MoveTo(888.8f, 152.0f),
                            PathNode.CurveTo(909.0f, 178.6f, 903.7f, 216.6f, 877.0f, 236.7f),
                            PathNode.LineTo(460.2f, 551.7f),
                            PathNode.CurveTo(428.1f, 575.9f, 428.1f, 624.1f, 460.2f, 648.3f),
                            PathNode.LineTo(877.0f, 963.3f),
                            PathNode.CurveTo(903.7f, 983.4f, 909.0f, 1021.4f, 888.8f, 1048.0f),
                            PathNode.CurveTo(868.7f, 1074.7f, 830.7f, 1080.0f, 804.0f, 1059.8f),
                            PathNode.LineTo(387.2f, 744.9f),
                            PathNode.CurveTo(291.0f, 672.3f, 291.0f, 527.8f, 387.2f, 455.2f),
                            PathNode.LineTo(804.0f, 140.2f),
                            PathNode.CurveTo(830.7f, 120.0f, 868.7f, 125.3f, 888.8f, 152.0f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build()
            return _sysbarBack!!
        }

    // 主页键：外 Squircle + 内矩形
    val SysbarHome: ImageVector
        get() {
            if (_sysbarHome != null) return _sysbarHome!!
            _sysbarHome = ImageVector.Builder(
                name = "SysbarHome",
                defaultWidth = 24.0f.dp,
                defaultHeight = 24.0f.dp,
                viewportWidth = 1200.0f,
                viewportHeight = 1200.0f,
            ).apply {
                group(scaleY = -1.0f, translationY = 1200.0f) {
                    addPath(
                        pathData = listOf(
                            PathNode.MoveTo(187.9f, 1005.4f),
                            PathNode.CurveTo(120.0f, 934.8f, 120.0f, 823.2f, 120.0f, 600.0f),
                            PathNode.CurveTo(120.0f, 376.8f, 120.0f, 265.2f, 187.9f, 194.6f),
                            PathNode.CurveTo(190.1f, 192.3f, 192.3f, 190.1f, 194.6f, 187.9f),
                            PathNode.CurveTo(265.2f, 120.0f, 376.8f, 120.0f, 600.0f, 120.0f),
                            PathNode.CurveTo(823.2f, 120.0f, 934.8f, 120.0f, 1005.4f, 187.9f),
                            PathNode.CurveTo(1007.7f, 190.1f, 1009.9f, 192.3f, 1012.1f, 194.6f),
                            PathNode.CurveTo(1080.0f, 265.2f, 1080.0f, 376.8f, 1080.0f, 600.0f),
                            PathNode.CurveTo(1080.0f, 823.2f, 1080.0f, 934.8f, 1012.1f, 1005.4f),
                            PathNode.CurveTo(1009.9f, 1007.7f, 1007.7f, 1009.9f, 1005.4f, 1012.1f),
                            PathNode.CurveTo(934.8f, 1080.0f, 823.2f, 1080.0f, 600.0f, 1080.0f),
                            PathNode.CurveTo(376.8f, 1080.0f, 265.2f, 1080.0f, 194.6f, 1012.1f),
                            PathNode.CurveTo(192.3f, 1009.9f, 190.1f, 1007.7f, 187.9f, 1005.4f),
                            PathNode.Close,
                            PathNode.MoveTo(277.1f, 922.9f),
                            PathNode.CurveTo(241.5f, 887.3f, 241.5f, 830.0f, 241.5f, 715.4f),
                            PathNode.VerticalTo(484.6f),
                            PathNode.CurveTo(241.5f, 370.0f, 241.5f, 312.7f, 277.1f, 277.1f),
                            PathNode.CurveTo(312.7f, 241.5f, 370.0f, 241.5f, 484.6f, 241.5f),
                            PathNode.HorizontalTo(715.4f),
                            PathNode.CurveTo(830.0f, 241.5f, 887.3f, 241.5f, 922.9f, 277.1f),
                            PathNode.CurveTo(958.5f, 312.7f, 958.5f, 370.0f, 958.5f, 484.6f),
                            PathNode.VerticalTo(715.4f),
                            PathNode.CurveTo(958.5f, 830.0f, 958.5f, 887.3f, 922.9f, 922.9f),
                            PathNode.CurveTo(887.3f, 958.5f, 830.0f, 958.5f, 715.4f, 958.5f),
                            PathNode.HorizontalTo(484.6f),
                            PathNode.CurveTo(370.0f, 958.5f, 312.7f, 958.5f, 277.1f, 922.9f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        pathFillType = PathFillType.EvenOdd,
                    )
                }
            }.build()
            return _sysbarHome!!
        }

    // 多任务键：三条水平线
    val SysbarRecent: ImageVector
        get() {
            if (_sysbarRecent != null) return _sysbarRecent!!
            _sysbarRecent = ImageVector.Builder(
                name = "SysbarRecent",
                defaultWidth = 24.0f.dp,
                defaultHeight = 24.0f.dp,
                viewportWidth = 1200.0f,
                viewportHeight = 1200.0f,
            ).apply {
                group(scaleY = -1.0f, translationY = 1200.0f) {
                    addPath(
                        pathData = listOf(
                            PathNode.MoveTo(120.0f, 984.0f),
                            PathNode.CurveTo(120.0f, 1019.3f, 148.7f, 1048.0f, 184.0f, 1048.0f),
                            PathNode.HorizontalTo(1016.0f),
                            PathNode.CurveTo(1051.3f, 1048.0f, 1080.0f, 1019.3f, 1080.0f, 984.0f),
                            PathNode.CurveTo(1080.0f, 948.7f, 1051.3f, 920.0f, 1016.0f, 920.0f),
                            PathNode.HorizontalTo(184.0f),
                            PathNode.CurveTo(148.7f, 920.0f, 120.0f, 948.7f, 120.0f, 984.0f),
                            PathNode.Close,
                            PathNode.MoveTo(120.0f, 600.0f),
                            PathNode.CurveTo(120.0f, 635.3f, 148.7f, 664.0f, 184.0f, 664.0f),
                            PathNode.HorizontalTo(1016.0f),
                            PathNode.CurveTo(1051.3f, 664.0f, 1080.0f, 635.3f, 1080.0f, 600.0f),
                            PathNode.CurveTo(1080.0f, 564.7f, 1051.3f, 536.0f, 1016.0f, 536.0f),
                            PathNode.HorizontalTo(184.0f),
                            PathNode.CurveTo(148.7f, 536.0f, 120.0f, 564.7f, 120.0f, 600.0f),
                            PathNode.Close,
                            PathNode.MoveTo(184.0f, 280.0f),
                            PathNode.CurveTo(148.7f, 280.0f, 120.0f, 251.3f, 120.0f, 216.0f),
                            PathNode.CurveTo(120.0f, 180.7f, 148.7f, 152.0f, 184.0f, 152.0f),
                            PathNode.HorizontalTo(1016.0f),
                            PathNode.CurveTo(1051.3f, 152.0f, 1080.0f, 180.7f, 1080.0f, 216.0f),
                            PathNode.CurveTo(1080.0f, 251.3f, 1051.3f, 280.0f, 1016.0f, 280.0f),
                            PathNode.HorizontalTo(184.0f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        pathFillType = PathFillType.EvenOdd,
                    )
                }
            }.build()
            return _sysbarRecent!!
        }

    // 导航栏指示器：外框 + 三个圆点
    val SysbarNavigation: ImageVector
        get() {
            if (_sysbarNavigation != null) return _sysbarNavigation!!
            _sysbarNavigation = ImageVector.Builder(
                name = "SysbarNavigation",
                defaultWidth = 24.0f.dp,
                defaultHeight = 24.0f.dp,
                viewportWidth = 1200.0f,
                viewportHeight = 1200.0f,
            ).apply {
                group(scaleY = -1.0f, translationY = 1200.0f) {
                    addPath(
                        pathData = listOf(
                            PathNode.MoveTo(360.0f, 1080.0f),
                            PathNode.CurveTo(315.8f, 1080.0f, 280.0f, 1044.2f, 280.0f, 1000.0f),
                            PathNode.VerticalTo(200.0f),
                            PathNode.CurveTo(280.0f, 155.8f, 315.8f, 120.0f, 360.0f, 120.0f),
                            PathNode.HorizontalTo(840.0f),
                            PathNode.CurveTo(884.2f, 120.0f, 920.0f, 155.8f, 920.0f, 200.0f),
                            PathNode.VerticalTo(1000.0f),
                            PathNode.CurveTo(920.0f, 1044.2f, 884.2f, 1080.0f, 840.0f, 1080.0f),
                            PathNode.Close,
                            PathNode.MoveTo(333.3f, 1026.7f),
                            PathNode.HorizontalTo(866.7f),
                            PathNode.VerticalTo(173.3f),
                            PathNode.HorizontalTo(333.3f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        pathFillType = PathFillType.EvenOdd,
                    )
                    addPath(
                        pathData = listOf(
                            PathNode.MoveTo(426.7f, 280.0f),
                            PathNode.CurveTo(426.7f, 302.1f, 444.6f, 320.0f, 466.7f, 320.0f),
                            PathNode.CurveTo(488.8f, 320.0f, 506.7f, 302.1f, 506.7f, 280.0f),
                            PathNode.CurveTo(506.7f, 257.9f, 488.8f, 240.0f, 466.7f, 240.0f),
                            PathNode.CurveTo(444.6f, 240.0f, 426.7f, 257.9f, 426.7f, 280.0f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        pathFillType = PathFillType.NonZero,
                    )
                    addPath(
                        pathData = listOf(
                            PathNode.MoveTo(560.0f, 280.0f),
                            PathNode.CurveTo(560.0f, 302.1f, 577.9f, 320.0f, 600.0f, 320.0f),
                            PathNode.CurveTo(622.1f, 320.0f, 640.0f, 302.1f, 640.0f, 280.0f),
                            PathNode.CurveTo(640.0f, 257.9f, 622.1f, 240.0f, 600.0f, 240.0f),
                            PathNode.CurveTo(577.9f, 240.0f, 560.0f, 257.9f, 560.0f, 280.0f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        pathFillType = PathFillType.NonZero,
                    )
                    addPath(
                        pathData = listOf(
                            PathNode.MoveTo(693.3f, 280.0f),
                            PathNode.CurveTo(693.3f, 302.1f, 711.2f, 320.0f, 733.3f, 320.0f),
                            PathNode.CurveTo(755.4f, 320.0f, 773.3f, 302.1f, 773.3f, 280.0f),
                            PathNode.CurveTo(773.3f, 257.9f, 755.4f, 240.0f, 733.3f, 240.0f),
                            PathNode.CurveTo(711.2f, 240.0f, 693.3f, 257.9f, 693.3f, 280.0f),
                            PathNode.Close,
                        ),
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        pathFillType = PathFillType.NonZero,
                    )
                }
            }.build()
            return _sysbarNavigation!!
        }

    private var _sysbarBack: ImageVector? = null
    private var _sysbarHome: ImageVector? = null
    private var _sysbarRecent: ImageVector? = null
    private var _sysbarNavigation: ImageVector? = null
}
