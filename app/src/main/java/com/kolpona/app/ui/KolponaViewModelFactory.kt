package com.kolpona.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kolpona.app.KolponaApp
import com.kolpona.app.di.AppContainer

class KolponaViewModelFactory(
    private val app: KolponaApp,
    private val creator: (AppContainer) -> ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator(app.container) as T
}
