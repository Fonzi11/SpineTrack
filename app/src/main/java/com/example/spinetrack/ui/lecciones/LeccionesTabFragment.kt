package com.example.spinetrack.ui.lecciones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// ...existing code...
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.R
import com.example.spinetrack.data.model.CategoriaLeccion
import com.example.spinetrack.data.repository.LeccionesRepository

class LeccionesTabFragment : Fragment() {

    companion object {
        private const val ARG_CATEGORIA = "arg_categoria"

        // Cambiado a CategoriaLeccion (el objeto Enum)
        fun newInstance(categoria: CategoriaLeccion): LeccionesTabFragment {
            return LeccionesTabFragment().apply {
                arguments = Bundle().apply {
                    // Aquí ya no dará error porque 'categoria' es el Enum y tiene .name
                    putString(ARG_CATEGORIA, categoria.name)
                }
            }
        }
    }
    private lateinit var adapter: LeccionesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_lecciones_tab, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rv_lecciones)

        adapter = LeccionesAdapter { leccion ->
            // Navegar al detalle de la lección pasando el id
            val bundle = android.os.Bundle().apply { putInt("leccion_id", leccion.id) }
            findNavController().navigate(R.id.nav_leccion_detail, bundle)
        }

        rv.adapter = adapter

        val categoriaName = arguments?.getString(ARG_CATEGORIA) ?: CategoriaLeccion.LECCIONES.name
        val categoria = CategoriaLeccion.valueOf(categoriaName)
        // Usar la versión que marca completadas según el almacenamiento local
        adapter.submitList(LeccionesRepository.getLeccionesWithCompletion(requireContext(), categoria))
    }
}
