package com.pocketpets.app.ui.adopt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.domain.Species
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdoptState(
    val name: String = "",
    val species: Species = Species.CAT,
    val canSubmit: Boolean = false,
)

class AdoptViewModel(
    private val repo: PetRepo,
) : ViewModel() {
    private val _state = MutableStateFlow(AdoptState())
    val state: StateFlow<AdoptState> = _state

    fun setName(s: String) {
        _state.update { it.copy(name = s, canSubmit = s.trim().length in 1..20) }
    }

    fun adopt(onDone: (Long) -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            val id = repo.adopt(s.name, s.species)
            onDone(id)
        }
    }
}
