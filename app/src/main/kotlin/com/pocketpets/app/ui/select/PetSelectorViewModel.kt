package com.pocketpets.app.ui.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.Pet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PetSelectorViewModel(
    private val repo: PetRepo,
) : ViewModel() {
    val pets: Flow<List<Pet>> = repo.observeAll()

    fun select(id: Long) {
        viewModelScope.launch { repo.setActive(id) }
    }
}
