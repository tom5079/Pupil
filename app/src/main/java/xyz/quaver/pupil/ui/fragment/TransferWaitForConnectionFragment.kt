package xyz.quaver.pupil.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import xyz.quaver.pupil.databinding.TransferWaitForConnectionFragmentBinding

class TransferWaitForConnectionFragment : Fragment() {

    private var _binding: TransferWaitForConnectionFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = TransferWaitForConnectionFragmentBinding.inflate(layoutInflater)

        binding.ripple.startRippleAnimation()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}