/*
 * SPDX-FileCopyrightText: Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.picker.font.ui.binder

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.common.ui.view.SingleRowListItemSpacing
import com.android.customization.picker.font.data.repository.FontOptionModel
import com.android.themepicker.R
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.FONT
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.option.ui.adapter.OptionItemAdapter2
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

object FontFloatingSheetBinder {

    fun bind(
        view: View,
        optionsViewModel: ThemePickerCustomizationOptionsViewModel,
        colorUpdateViewModel: ColorUpdateViewModel,
        lifecycleOwner: LifecycleOwner,
        backgroundDispatcher: CoroutineDispatcher,
    ) {
        val viewModel = optionsViewModel.fontPickerViewModel
        val isFloatingSheetActive = { optionsViewModel.selectedOption.value == FONT }

        val floatingSheetContainer =
            view.requireViewById<ViewGroup>(R.id.floating_sheet_content_container)
        ColorUpdateBinder.bind(
            setColor = { color ->
                DrawableCompat.setTint(
                    DrawableCompat.wrap(floatingSheetContainer.background),
                    color,
                )
            },
            color = colorUpdateViewModel.colorSurfaceBright,
            shouldAnimate = isFloatingSheetActive,
            lifecycleOwner = lifecycleOwner,
        )

        val adapter =
            OptionItemAdapter2<FontOptionModel>(
                layoutResourceId = R.layout.font_option,
                lifecycleOwner = lifecycleOwner,
                backgroundDispatcher = backgroundDispatcher,
                bindPayload = { itemView, option ->
                    itemView.requireViewById<TextView>(R.id.font_preview).typeface =
                        option.headlineFont
                    null
                },
                colorUpdateViewModel = WeakReference(colorUpdateViewModel),
                shouldAnimateColor = isFloatingSheetActive,
            )
        val optionList =
            view.requireViewById<RecyclerView>(R.id.font_options).apply {
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                addItemDecoration(
                    SingleRowListItemSpacing(
                        edgeItemSpacePx =
                            resources.getDimensionPixelSize(
                                R.dimen.floating_sheet_content_horizontal_padding
                            ),
                        itemHorizontalSpacePx =
                            resources.getDimensionPixelSize(
                                R.dimen.floating_sheet_grid_list_item_horizontal_space
                            ),
                    )
                )
                this.adapter = adapter
            }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fontOptionListItems.collect { options ->
                    adapter.setItems(options) {
                        val indexToFocus =
                            options.indexOfFirst { it.isSelected.value }.coerceAtLeast(0)
                        (optionList.layoutManager as LinearLayoutManager).scrollToPosition(
                            indexToFocus
                        )
                    }
                }
            }
        }
    }
}
