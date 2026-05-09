package com.pocketpets.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketpets.app.LocalDeepLinkPetId
import com.pocketpets.app.PocketPetsApp
import com.pocketpets.app.domain.speech.CatSpeech
import com.pocketpets.app.ui.adopt.AdoptPetScreen
import com.pocketpets.app.ui.adopt.AdoptViewModel
import com.pocketpets.app.ui.pet.PetScreen
import com.pocketpets.app.ui.pet.PetViewModel
import com.pocketpets.app.ui.select.PetSelectorSheet
import com.pocketpets.app.ui.select.PetSelectorViewModel
import com.pocketpets.app.ui.settings.SettingsScreen
import com.pocketpets.app.ui.settings.SettingsViewModel
import kotlinx.datetime.TimeZone

@Composable
fun AppNav() {
    val context = LocalContext.current
    val container = (context.applicationContext as PocketPetsApp).container
    val nav = rememberNavController()

    val petsAtStart =
        container.petRepository
            .observeAll()
            .collectAsState(initial = emptyList())
            .value
    val start = if (petsAtStart.isEmpty()) "adopt" else "pet"

    NavHost(navController = nav, startDestination = start) {
        composable("adopt") {
            val vm: AdoptViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { AdoptViewModel(container.petRepository) }
                        },
                )
            AdoptPetScreen(
                vm = vm,
                onAdopt = { _ ->
                    nav.navigate("pet") { popUpTo("adopt") { inclusive = true } }
                },
            )
        }
        composable("pet") {
            var sheetOpen by remember { mutableStateOf(false) }
            val deepLinkId = LocalDeepLinkPetId.current
            LaunchedEffect(deepLinkId) {
                if (deepLinkId != null) container.petRepository.setActive(deepLinkId)
            }
            val petVm: PetViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer {
                                PetViewModel(
                                    repo = container.petRepository,
                                    clock = container.clock,
                                    zone = TimeZone.currentSystemDefault(),
                                    speech = CatSpeech,
                                )
                            }
                        },
                )
            val selVm: PetSelectorViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { PetSelectorViewModel(container.petRepository) }
                        },
                )
            PetScreen(
                vm = petVm,
                onOpenSettings = { nav.navigate("settings") },
                onOpenSelector = { sheetOpen = true },
            )
            if (sheetOpen) {
                PetSelectorSheet(
                    vm = selVm,
                    onSelect = { sheetOpen = false },
                    onAdopt = {
                        sheetOpen = false
                        nav.navigate("adopt")
                    },
                    onDismiss = { sheetOpen = false },
                )
            }
        }
        composable("settings") {
            val vm: SettingsViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { SettingsViewModel(container.settings) }
                        },
                )
            SettingsScreen(vm)
        }
    }
}
