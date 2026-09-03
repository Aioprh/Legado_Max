package io.legado.app.ui.book.info

import android.view.View
import io.legado.app.databinding.ActivityBookInfoBinding

/**
 * Compatibility shim for the old book-info layout API.
 * The redesigned layout no longer needs the legacy bottom spacer, so this
 * property intentionally returns null while keeping the existing activity
 * source compatible during the UI migration.
 */
val ActivityBookInfoBinding.vSpacerBottom: View?
    get() = null
