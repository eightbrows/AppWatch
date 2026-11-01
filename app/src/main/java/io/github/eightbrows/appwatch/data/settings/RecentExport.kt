package io.github.eightbrows.appwatch.data.settings

/** 最近の書き出し1件（日時・ファイル名・大きさ）。DataStore に文字でまとめて保存する。 */
data class RecentExport(val exportedAt: Long, val fileName: String, val sizeBytes: Long) {
    companion object {
        /** 1件1行、「日時 TAB ファイル名 TAB 大きさ」。読めない行は飛ばす。 */
        fun decode(text: String): List<RecentExport> = text.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val size = parts[2].toLongOrNull() ?: return@mapNotNull null
            RecentExport(time, parts[1], size)
        }

        fun encode(list: List<RecentExport>): String = list.joinToString("\n") { export ->
            val name = export.fileName.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
            "${export.exportedAt}\t$name\t${export.sizeBytes}"
        }
    }
}
