package com.odago.travollio

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.odago.travollio.state.TripViewModel

class TripViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        if (modelClass.isAssignableFrom(TripViewModel::class.java)) {
            TripViewModel(app) as T
        } else {
            super.create(modelClass)
        }
}
