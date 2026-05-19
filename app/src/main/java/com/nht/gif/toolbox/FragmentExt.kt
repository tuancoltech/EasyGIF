package com.nht.gif.toolbox

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun Fragment.launchOnStarted(block: suspend CoroutineScope.() -> Unit) {
  viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
  }
}

fun <T> Fragment.collectOnStarted(flow: Flow<T>, collector: suspend (T) -> Unit) {
  viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      flow.collect { collector(it) }
    }
  }
}
