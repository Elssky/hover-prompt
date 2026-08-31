package com.example.hoverprompt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF090D12)
private val Panel = Color(0xFF111821)
private val PanelRaised = Color(0xFF17212A)
private val Mint = Color(0xFFC4F76A)
private val MintDim = Color(0xFF7EAD40)
private val Paper = Color(0xFFF0EEE5)
private val Muted = Color(0xFF8C9699)
private val Rust = Color(0xFFD67551)

private const val SAMPLE_SCRIPT = """大家好，今天用两分钟介绍一下这款提词器。

它适用于直播带货、录课、在线会议、视频拍摄、演讲发言等多种场景。

你读到哪里，它就跟随到哪里；也可以切换匀速滚动，用手指控制进度。

准备好了吗？看向镜头，开始表达。"""

enum class PromptMode(val label: String) {
    PACE("匀速滚动"),
    FOLLOW("AI 跟随")
}

data class PromptSettings(
    val mode: PromptMode = PromptMode.PACE,
    val speed: Float = 24f,
    val fontSize: Float = 23f,
    val lineSpacing: Float = 1.28f,
    val textColor: Color = Paper,
    val backgroundColor: Color = Panel,
    val opacity: Float = 0.92f,
    val loop: Boolean = true,
    val countdown: Boolean = true
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HoverPromptTheme {
                PromptHome(
                    onLaunchOverlay = { script, settings -> launchOverlay(script, settings) },
                    onRequestPermission = { requestOverlayPermission() }
                )
            }
        }
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun launchOverlay(script: String, settings: PromptSettings) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先允许“悬浮提词”显示在其他应用上层", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TEXT, script)
            putExtra(OverlayService.EXTRA_MODE, settings.mode.name)
            putExtra(OverlayService.EXTRA_SPEED, settings.speed)
            putExtra(OverlayService.EXTRA_FONT_SIZE, settings.fontSize)
            putExtra(OverlayService.EXTRA_LINE_SPACING, settings.lineSpacing)
            putExtra(OverlayService.EXTRA_TEXT_COLOR, settings.textColor.toArgb())
            putExtra(OverlayService.EXTRA_BACKGROUND_COLOR, settings.backgroundColor.toArgb())
            putExtra(OverlayService.EXTRA_OPACITY, settings.opacity)
            putExtra(OverlayService.EXTRA_LOOP, settings.loop)
        }
        startForegroundService(intent)
        Toast.makeText(this, "悬浮提词已打开，可切换到相机或抖音", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun HoverPromptTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Mint,
            onPrimary = Ink,
            background = Ink,
            surface = Panel,
            onSurface = Paper,
            onBackground = Paper
        ),
        typography = androidx.compose.material3.Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp)
        ),
        content = content
    )
}

@Composable
private fun PromptHome(
    onLaunchOverlay: (String, PromptSettings) -> Unit,
    onRequestPermission: () -> Unit
) {
    var script by remember { mutableStateOf(TextFieldValue(SAMPLE_SCRIPT)) }
    var settings by remember { mutableStateOf(PromptSettings()) }
    var showSettings by remember { mutableStateOf(true) }
    var previewPlaying by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(onRequestPermission = onRequestPermission)
            HeroIntro()
            PreviewStage(
                script = script.text,
                settings = settings,
                isPlaying = previewPlaying,
                onTogglePlaying = { previewPlaying = !previewPlaying }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onLaunchOverlay(script.text, settings) },
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mint,
                        contentColor = Ink
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开悬浮提词", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier.size(width = 54.dp, height = 54.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF33414A)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        if (showSettings) Icons.Filled.Close else Icons.Filled.Tune,
                        contentDescription = "提词器设置",
                        tint = Paper
                    )
                }
            }
            ScriptEditor(
                value = script,
                onValueChange = { script = it },
                onClear = { script = TextFieldValue("") }
            )
            AnimatedVisibility(visible = showSettings) {
                PromptSettingsPanel(
                    settings = settings,
                    onSettingsChange = { settings = it }
                )
            }
            TipCard()
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Header(onRequestPermission: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = null, tint = Ink)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("HOVER PROMPT", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp)
                Text("悬浮提词", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        TextButton(onClick = onRequestPermission) {
            Text("权限设置", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HeroIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "让眼睛留在镜头上。",
            color = Paper,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp
        )
        Text(
            "台词悬浮在任何应用之上，跟随你的表达节奏。",
            color = Muted,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun PreviewStage(
    script: String,
    settings: PromptSettings,
    isPlaying: Boolean,
    onTogglePlaying: () -> Unit
) {
    val playScale by animateFloatAsState(if (isPlaying) 1.04f else 1f, label = "play-scale")
    Card(
        modifier = Modifier.fillMaxWidth().height(252.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10151B))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF152629), Color(0xFF10151B), Color(0xFF272016))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(settings.backgroundColor.copy(alpha = .78f))
                    .border(1.dp, Color(0x332F3E42), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Rust))
                            Spacer(Modifier.width(7.dp))
                            Text("FLOATING PREVIEW", color = Muted, fontSize = 10.sp, letterSpacing = 1.3.sp)
                        }
                        Text("${settings.mode.label}  ·  ${settings.speed.toInt()} dp/s", color = Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 56.dp)
                                .height(1.dp).background(Mint.copy(alpha = .75f))
                        )
                        Text(
                            text = script.ifBlank { "输入一段台词，开始你的表达。" },
                            color = settings.textColor,
                            fontSize = 17.sp,
                            lineHeight = 25.sp,
                            maxLines = 4,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("参考线  52%", color = Mint, fontSize = 11.sp)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Mint)
                                .rotate(if (isPlaying) 0f else 0f)
                                .clickable(onClick = onTogglePlaying),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停预览" else "播放预览",
                                tint = Ink,
                                modifier = Modifier.size((20 * playScale).dp)
                            )
                        }
                    }
                }
            }
            Text(
                "LIVE",
                modifier = Modifier.align(Alignment.TopEnd).padding(32.dp)
                    .clip(RoundedCornerShape(8.dp)).background(Mint)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                color = Ink,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun ScriptEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("当前台词", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("录制前先把节奏写下来", color = Muted, fontSize = 12.sp)
            }
            Row {
                IconButton(onClick = { }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制台词", tint = Muted, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "清空台词", tint = Muted, modifier = Modifier.size(19.dp))
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp))
                .background(Panel).border(1.dp, Color(0xFF26323A), RoundedCornerShape(20.dp))
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxSize().padding(17.dp),
                textStyle = TextStyle(color = Paper, fontSize = 16.sp, lineHeight = 27.sp),
                cursorBrush = SolidColor(Mint),
                decorationBox = { innerTextField ->
                    if (value.text.isBlank()) {
                        Text("粘贴或输入你的台词…", color = Color(0xFF58646A), fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )
            Text(
                "${value.text.length} 字",
                modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
                color = Color(0xFF58646A),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PromptSettingsPanel(
    settings: PromptSettings,
    onSettingsChange: (PromptSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("提词器设置", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("实时预览", color = MintDim, fontSize = 11.sp)
            }
            SettingLabel("提词模式")
            ModeSelector(settings.mode) { onSettingsChange(settings.copy(mode = it)) }
            SliderSetting(
                label = "滚动速度",
                valueText = "${settings.speed.toInt()} px/s",
                value = settings.speed,
                range = 8f..60f,
                onValueChange = { onSettingsChange(settings.copy(speed = it)) }
            )
            SliderSetting(
                label = "字号大小",
                valueText = "${settings.fontSize.toInt()} sp",
                value = settings.fontSize,
                range = 16f..36f,
                onValueChange = { onSettingsChange(settings.copy(fontSize = it)) }
            )
            SliderSetting(
                label = "背景透明度",
                valueText = "${(settings.opacity * 100).toInt()}%",
                value = settings.opacity,
                range = .35f..1f,
                onValueChange = { onSettingsChange(settings.copy(opacity = it)) }
            )
            ColorSetting(
                label = "文字颜色",
                colors = listOf(Paper, Color(0xFFFF6B5C), Color(0xFFFFB11B), Mint, Color(0xFF63D8FF)),
                selected = settings.textColor,
                onSelected = { onSettingsChange(settings.copy(textColor = it)) }
            )
            ColorSetting(
                label = "背景颜色",
                colors = listOf(Panel, Color(0xFF0B0B0B), Color(0xFF20202B), Color(0xFF1C2A26)),
                selected = settings.backgroundColor,
                onSelected = { onSettingsChange(settings.copy(backgroundColor = it)) }
            )
            SwitchSetting(
                icon = Icons.Filled.Replay,
                label = "循环播放",
                description = "读完后自动回到开头",
                checked = settings.loop,
                onCheckedChange = { onSettingsChange(settings.copy(loop = it)) }
            )
            SwitchSetting(
                icon = Icons.Filled.Speed,
                label = "3 秒倒计时",
                description = "进入相机后再开始滚动",
                checked = settings.countdown,
                onCheckedChange = { onSettingsChange(settings.copy(countdown = it)) }
            )
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun ModeSelector(selected: PromptMode, onSelected: (PromptMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PanelRaised).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PromptMode.entries.forEach { mode ->
            val isSelected = selected == mode
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF2B3940) else Color.Transparent)
                    .clickable { onSelected(mode) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(mode.label, color = if (isSelected) Paper else Muted, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Paper, fontSize = 14.sp)
            Text(valueText, color = Mint, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Mint,
                activeTrackColor = Mint,
                inactiveTrackColor = Color(0xFF354047)
            )
        )
    }
}

@Composable
private fun ColorSetting(
    label: String,
    colors: List<Color>,
    selected: Color,
    onSelected: (Color) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(72.dp), color = Paper, fontSize = 14.sp)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { color ->
                val active = color == selected
                Box(
                    modifier = Modifier.size(27.dp).clip(CircleShape).background(color)
                        .border(if (active) 3.dp else 1.dp, if (active) Mint else Color(0xFF596269), CircleShape)
                        .clickable { onSelected(color) }
                )
            }
        }
    }
}

@Composable
private fun SwitchSetting(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Paper, fontSize = 14.sp)
            Text(description, color = Color(0xFF68747A), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Mint,
                uncheckedThumbColor = Muted,
                uncheckedTrackColor = Color(0xFF354047),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun TipCard() {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF17201B)).padding(15.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.FitScreen, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("第一次使用？", color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("打开悬浮窗后，拖动顶部六个点移动位置，拖动右下角调整大小。切换到相机或抖音即可开始录制。", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
        }
    }
}
