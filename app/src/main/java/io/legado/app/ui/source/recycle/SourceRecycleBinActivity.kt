package io.legado.app.ui.source.recycle

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch

class SourceRecycleBinActivity : AppCompatActivity() {

    private val viewModel: SourceRecycleBinViewModel by viewModels {
        SourceRecycleBinViewModel.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        collectToasts()
        setLegadoContent {
            SourceRecycleBinScreen(viewModel = viewModel, onBackClick = { finish() })
        }
    }

    /** 收集 Toast 事件并在主线程执行（ViewModel 只抛事件，§4.1/§4.4） */
    private fun collectToasts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.toasts.collect { toastOnUi(it.msgRes) }
            }
        }
    }
}

