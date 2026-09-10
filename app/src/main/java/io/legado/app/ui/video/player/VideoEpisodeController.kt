package io.legado.app.ui.video.player

/**
 * Pure episode navigation state. The controller is deliberately independent of
 * Android views so automatic-next logic can be tested without a player instance.
 */
class VideoEpisodeController<T>(items: List<T> = emptyList()) {
    private var items: List<T> = items.toList()
    var index: Int = 0
        private set

    val current: T?
        get() = items.getOrNull(index)

    val hasNext: Boolean
        get() = index + 1 < items.size

    val hasPrevious: Boolean
        get() = index > 0 && items.isNotEmpty()

    val size: Int
        get() = items.size

    fun submit(newItems: List<T>, selectedIndex: Int = index) {
        items = newItems.toList()
        index = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    fun select(position: Int): T? {
        if (position !in items.indices) return null
        index = position
        return items[index]
    }

    fun next(): T? {
        if (!hasNext) return null
        index += 1
        return items[index]
    }

    fun previous(): T? {
        if (!hasPrevious) return null
        index -= 1
        return items[index]
    }

    fun snapshot(): List<T> = items
}
