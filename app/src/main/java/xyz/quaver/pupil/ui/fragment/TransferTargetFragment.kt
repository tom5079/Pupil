package xyz.quaver.pupil.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import xyz.quaver.pupil.R
import xyz.quaver.pupil.adapters.TransferPeersAdapter
import xyz.quaver.pupil.databinding.TransferTargetFragmentBinding
import xyz.quaver.pupil.ui.TransferStep
import xyz.quaver.pupil.ui.TransferViewModel

class TransferTargetFragment : Fragment() {

    private var _binding: TransferTargetFragmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TransferTargetFragmentBinding.inflate(inflater, container, false)

        viewModel.thisDevice.observe(viewLifecycleOwner) { device ->
            if (device == null) {
                return@observe
            }

            if (device.status == 3) {
                binding.ripple.startRippleAnimation()
                binding.retryButton.visibility = View.INVISIBLE
            } else {
                binding.ripple.stopRippleAnimation()
                binding.retryButton.visibility = View.VISIBLE
            }
        }

        viewModel.peers.observe(viewLifecycleOwner) { peers ->
            if (peers == null) {
                return@observe
            }

            binding.deviceList.adapter = TransferPeersAdapter(peers.deviceList) {
                viewModel.connect(it)
            }
        }

        binding.ripple.startRippleAnimation()

        binding.retryButton.setOnClickListener {
            viewModel.setStep(TransferStep.TARGET)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}