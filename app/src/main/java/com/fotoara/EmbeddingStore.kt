package com.fotoara

import java.io.File

/** Fotoğraf id -> vektör. Basit metin dosyası; her açılışta yeniden hesaplamayı önler. */
class EmbeddingStore(private val file: File) {
    private val map = LinkedHashMap<Long, FloatArray>()

    init {
        if (file.exists()) file.forEachLine { line ->
            val p = line.split("|")
            if (p.size == 2) {
                runCatching {
                    map[p[0].toLong()] = p[1].split(",").map { it.toFloat() }.toFloatArray()
                }
            }
        }
    }

    @Synchronized fun has(id: Long) = map.containsKey(id)
    @Synchronized fun put(id: Long, v: FloatArray) { map[id] = v }
    @Synchronized fun size() = map.size
    @Synchronized fun all(): List<Pair<Long, FloatArray>> = map.entries.map { it.key to it.value }

    @Synchronized fun save() {
        file.writeText(map.entries.joinToString("\n") { "${it.key}|${it.value.joinToString(",")}" })
    }
}
