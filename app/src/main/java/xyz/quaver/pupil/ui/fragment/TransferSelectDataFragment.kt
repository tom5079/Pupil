package xyz.quaver.pupil.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import xyz.quaver.pupil.databinding.TransferSelectDataFragmentBinding
import xyz.quaver.pupil.ui.TransferViewModel

class TransferSelectDataFragment: Fragment() {

    private var _binding: TransferSelectDataFragmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = TransferSelectDataFragmentBinding.inflate(inflater, container, false)

        binding.checkAll.setOnCheckedChangeListener {  _, isChecked ->
            viewModel.list()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}