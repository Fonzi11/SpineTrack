package com.example.spinetrack.ui.lecciones

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.spinetrack.data.model.CategoriaLeccion

class LeccionesPagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val categorias = listOf(
        CategoriaLeccion.LECCIONES,
        CategoriaLeccion.EJERCICIOS,
        CategoriaLeccion.ERGONOMIA,
        CategoriaLeccion.HABITOS
    )

    val tabTitles = listOf("Lecciones", "Ejercicios", "Ergonomía", "Hábitos")

    override fun getItemCount(): Int = categorias.size

    override fun createFragment(position: Int): Fragment {
        return LeccionesTabFragment.newInstance(categorias[position])
    }
}