package xyz.quaver.pupil.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import xyz.quaver.pupil.databinding.TransferPermissionFragmentBinding
import xyz.quaver.pupil.ui.TransferStep
import xyz.quaver.pupil.ui.TransferViewModel

class TransferPermissionFragment: Fragment() {

    private var _binding: TransferPermissionFragmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TransferPermissionFragmentBinding.inflate(inflater, container, false)

        binding.permissionsButton.setOnClickListener {
            viewModel.setStep(TransferStep.TARGET_FORCE)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}