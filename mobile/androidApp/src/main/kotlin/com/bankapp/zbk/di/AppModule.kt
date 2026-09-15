package com.bankapp.zbk.di

import com.bankapp.zbk.ui.cards.CardsViewModel
import com.bankapp.zbk.ui.credit.CreditViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule =
    module {
        viewModelOf(::CardsViewModel)
        viewModelOf(::CreditViewModel)
    }
