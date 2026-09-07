package app.pwhs.tv.di

import org.koin.dsl.module

val flavorModule = module {
    // Play flavor: no updaterModule or background update checks to comply with Google Play Developer Policies
}
