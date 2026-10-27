package com.muyian.debt.data

/** 角色（吉祥物）唯一来源：主题 -> 角色 -> 表情。页面不写死角色名或图片路径。 */
object Mascots {
    const val STYLE_KEY_PREFIX = "muyian_mascot_style_v1:"
    const val AUTO_MOOD_KEY = "muyian_mascot_auto_mood_v1"
    const val LEGACY_NEZHA_KEY = "muyian_nezha_v1"

    val NEZHA = Mascot(
        id = "nezha",
        name = "哪吒",
        defaultStyleId = "grin",
        moodAliases = mapOf("warn" to "grumpy"),
        styles = listOf(
            MascotStyle("grin", "idle", "坏笑", "牙尖嘴利", "mascots/grin.webp", "mascots/head-grin.webp"),
            MascotStyle("cheer", "success", "大笑", "举拳大笑", "mascots/cheer.webp", "mascots/head-cheer.webp"),
            MascotStyle("grumpy", "empty", "不服", "撇嘴斜眼", "mascots/grumpy.webp", "mascots/head-grumpy.webp")
        )
    )

    val WUKONG = Mascot(
        id = "wukong",
        name = "悟空",
        defaultStyleId = "grin",
        moodAliases = mapOf("warn" to "grumpy"),
        ready = false,
        styles = listOf(
            MascotStyle("grin", "idle", "坏笑", "扛棒挑眉", "mascots/wukong-grin.webp", "mascots/wukong-head-grin.webp"),
            MascotStyle("cheer", "success", "大笑", "举棒大笑", "mascots/wukong-cheer.webp", "mascots/wukong-head-cheer.webp"),
            MascotStyle("grumpy", "empty", "不服", "抱臂撇嘴", "mascots/wukong-grumpy.webp", "mascots/wukong-head-grumpy.webp")
        )
    )

    val MASCOTS = mapOf("nezha" to NEZHA, "wukong" to WUKONG)

    /** 主题 -> 角色 id */
    private val THEME_MASCOT = mapOf("wukong" to "wukong", "nezha" to "nezha", "shaseng" to "nezha")

    fun active(themeId: String): Mascot {
        val id = THEME_MASCOT[themeId] ?: "nezha"
        val candidate = MASCOTS[id]
        return if (candidate != null && candidate.ready) candidate else NEZHA
    }

    fun readStyleId(prefs: Prefs, mascot: Mascot): String {
        val own = prefs.str(STYLE_KEY_PREFIX + mascot.id, "")
        if (mascot.styles.any { it.id == own }) return own
        if (mascot.id == "nezha") {
            val legacy = prefs.str(LEGACY_NEZHA_KEY, "")
            if (mascot.styles.any { it.id == legacy }) {
                prefs.putStr(STYLE_KEY_PREFIX + mascot.id, legacy)
                return legacy
            }
        }
        return mascot.defaultStyleId
    }

    fun readAllStyleIds(prefs: Prefs): Map<String, String> =
        MASCOTS.mapValues { readStyleId(prefs, it.value) }

    fun readAutoMood(prefs: Prefs): Boolean = prefs.bool(AUTO_MOOD_KEY, true)

    fun styleOf(mascot: Mascot, styleId: String): MascotStyle =
        mascot.styles.firstOrNull { it.id == styleId }
            ?: mascot.styles.firstOrNull { it.id == mascot.defaultStyleId }
            ?: mascot.styles.first()

    /**
     * 按场景解析表情：mode=base 或关闭跟随场景时固定基础表情；
     * 其余按 mood 找表情，支持 moodAliases（warn -> grumpy）。
     */
    fun resolve(mascot: Mascot, styleId: String, autoMood: Boolean, mood: String): MascotStyle {
        val base = styleOf(mascot, styleId)
        if (!autoMood || mood.isEmpty() || mood == "idle") return base
        val alias = mascot.moodAliases[mood]
        if (alias != null) return mascot.styles.firstOrNull { it.id == alias } ?: base
        return mascot.styles.firstOrNull { it.mood == mood } ?: base
    }

    fun setStyle(prefs: Prefs, mascot: Mascot, styleId: String): MascotStyle {
        val target = mascot.styles.firstOrNull { it.id == styleId } ?: mascot.styles.first()
        prefs.putStr(STYLE_KEY_PREFIX + mascot.id, target.id)
        if (mascot.id == "nezha") prefs.putStr(LEGACY_NEZHA_KEY, target.id)
        return target
    }

    fun setAutoMood(prefs: Prefs, enabled: Boolean) {
        prefs.putBool(AUTO_MOOD_KEY, enabled)
    }
}
