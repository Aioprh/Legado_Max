package io.legado.app.ui.book.read.page.entities.column

/**
 * 文字基列
 */
interface TextBaseColumn : BaseColumn {
    override var start: Float
    override var end: Float
    val charData: String
    val textColor: Int?
    val underlineMode: Int
    val underlineColor: Int?
    var selected: Boolean
    var isSearchResult: Boolean
    var isCurrentSearchResult: Boolean
}
