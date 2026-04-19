package com.example.spinetrack.ui.lecciones

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.spinetrack.data.model.CategoriaLeccion

class LeccionesViewPageAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    override fun getItemCount(): Int = 4 // Tienes 4 categorías en el Enum

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LeccionesTabFragment.newInstance(CategoriaLeccion.LECCIONES)
            1 -> LeccionesTabFragment.newInstance(CategoriaLeccion.EJERCICIOS)
            2 -> LeccionesTabFragment.newInstance(CategoriaLeccion.ERGONOMIA)
            else -> LeccionesTabFragment.newInstance(CategoriaLeccion.HABITOS)
        }
    }
}