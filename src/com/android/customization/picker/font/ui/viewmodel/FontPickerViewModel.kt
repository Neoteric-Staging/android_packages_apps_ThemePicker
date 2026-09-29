/*
 * SPDX-FileCopyrightText: Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.font.ui.viewmodel

import com.android.customization.picker.font.data.repository.FontOptionModel
import com.android.customization.picker.font.data.repository.FontRepository
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

class FontPickerViewModel
@AssistedInject
constructor(
    private val repository: FontRepository,
    @Assisted private val viewModelScope: CoroutineScope,
) {
    private val fontOptions =
        repository.fontOptions.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val isFontCustomizationAvailable: Flow<Boolean> = fontOptions.map { it.size > 1 }

    private val selectedFontKey =
        repository.selectedFontKey.shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    /** The currently applied font, falling back to the default when its overlay is gone. */
    val selectedFontOption: Flow<FontOptionModel> =
        combine(fontOptions, selectedFontKey) { options, key ->
            options.firstOrNull { it.key == key } ?: options.first()
        }

    private val overridingFontKey = MutableStateFlow<String?>(null)
    private val previewingFontKey =
        combine(overridingFontKey, selectedFontOption) { overriding, selected ->
                overriding ?: selected.key
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val fontOptionListItems: Flow<List<OptionItemViewModel2<FontOptionModel>>> =
        fontOptions
            .map { options -> options.map { toOptionItemViewModel(it) } }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val onApply: Flow<(suspend () -> Unit)?> =
        combine(overridingFontKey, selectedFontOption) { overriding, selected ->
            if (overriding != null && overriding != selected.key) {
                { repository.applyFont(overriding) }
            } else {
                null
            }
        }

    fun resetPreview() {
        overridingFontKey.value = null
    }

    private fun toOptionItemViewModel(
        option: FontOptionModel
    ): OptionItemViewModel2<FontOptionModel> {
        val isSelected =
            previewingFontKey
                .map { it == option.key }
                .stateIn(viewModelScope, SharingStarted.Lazily, false)
        return OptionItemViewModel2(
            key = MutableStateFlow(option.key),
            payload = option,
            text = Text.Loaded(option.title),
            isSelected = isSelected,
            onClicked =
                isSelected.map { selected ->
                    if (!selected) {
                        { overridingFontKey.value = option.key }
                    } else {
                        null
                    }
                },
        )
    }

    @ViewModelScoped
    @AssistedFactory
    interface Factory {
        fun create(viewModelScope: CoroutineScope): FontPickerViewModel
    }
}
