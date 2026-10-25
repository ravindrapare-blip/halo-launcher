package com.halo.launcher.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** HDMI / composite / tuner inputs, read straight from the TV's input framework. */
class InputsRepository(
    private val context: Context,
) {
    private val tim: TvInputManager? = runCatching { context.getSystemService(TvInputManager::class.java) }.getOrNull()

    fun inputs(): List<TvInputItem> =
        runCatching {
            tim
                ?.tvInputList
                .orEmpty()
                .map { info ->
                    val custom = runCatching { info.loadCustomLabel(context)?.toString() }.getOrNull()
                    val label = custom?.takeIf { it.isNotBlank() } ?: info.loadLabel(context).toString()
                    TvInputItem(
                        id = info.id,
                        label = label,
                        typeLabel = typeLabel(info.type),
                        deviceName =
                            info.parentId?.let { pid ->
                                runCatching { tim?.getTvInputInfo(pid)?.loadLabel(context)?.toString() }.getOrNull()
                            },
                        isPassthrough = info.isPassthroughInput,
                    )
                }.sortedWith(compareBy({ !it.isPassthrough }, { it.label }))
        }.getOrDefault(emptyList())

    fun switchTo(input: TvInputItem): Boolean {
        val uri =
            if (input.isPassthrough) {
                TvContract.buildChannelUriForPassthroughInput(input.id)
            } else {
                TvContract.buildChannelsUriForInput(input.id)
            }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun typeLabel(t: Int) =
        when (t) {
            TvInputInfo.TYPE_HDMI -> "HDMI"
            TvInputInfo.TYPE_COMPONENT -> "Component"
            TvInputInfo.TYPE_COMPOSITE -> "AV"
            TvInputInfo.TYPE_TUNER -> "Antenna / Cable"
            TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
            TvInputInfo.TYPE_VGA -> "VGA"
            TvInputInfo.TYPE_SCART -> "SCART"
            TvInputInfo.TYPE_SVIDEO -> "S-Video"
            else -> "Input"
        }
}
