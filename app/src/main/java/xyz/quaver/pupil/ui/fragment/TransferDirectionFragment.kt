package xyz.quaver.pupil.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import xyz.quaver.pupil.R
import xyz.quaver.pupil.databinding.TransferDirectionFragmentBinding
import xyz.quaver.pupil.ui.TransferStep
import xyz.quaver.pupil.ui.TransferViewModel

class TransferDirectionFragment : Fragment(R.layout.transfer_direction_fragment) {

    private var _binding: TransferDirectionFragmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TransferDirectionFragmentBinding.inflate(inflater, container, false)

        binding.inButton.setOnClickListener {
            viewModel.setStep(TransferStep.TARGET)
        }

        binding.outButton.setOnClickListener {
            viewModel.setStep(TransferStep.WAIT_FOR_CONNECTION)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}