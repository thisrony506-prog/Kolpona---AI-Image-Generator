package com.kolpona.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kolpona.ai.KolponaApp
import com.kolpona.ai.di.AppContainer

class KolponaViewModelFactory(
    private val app: KolponaApp,
    private val creator: (AppContainer) -> ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator(app.container) as T
}
