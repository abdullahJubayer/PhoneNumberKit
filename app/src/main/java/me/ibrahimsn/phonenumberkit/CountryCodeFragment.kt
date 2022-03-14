package me.ibrahimsn.phonenumberkit

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import me.ibrahimsn.lib.PhoneNumberKit


class CountryCodeFragment : Fragment() {

    private lateinit var activity: AppCompatActivity


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val textView=view.findViewById<TextInputLayout>(R.id.textField)

        if (getActivity() is FragmentContainer){
            activity=getActivity() as FragmentContainer
        }

        val phoneNumberKit = PhoneNumberKit.Builder(activity)
            .setIconEnabled(true)
            .setFullScreen(true)
            .build()

        // To attach an editTextLayout
        phoneNumberKit.attachToInput(textView, "tr")

        // Setup country code picker optionally
        phoneNumberKit.setupCountryPicker(
            activity = activity,
            searchEnabled = true
        )

        // Provides example phone number for given country iso2 code
        val exampleNumber = phoneNumberKit.getExampleNumber("tr")
        Log.d(TAG, "Example Number: $exampleNumber")

        // Parses raw phone number to phone object
        val parsedNumber = phoneNumberKit.parsePhoneNumber(
            number = "05066120000",
            defaultRegion = "us"
        )
        Log.d(TAG, "Parsed Number: $parsedNumber")

        // Converts raw phone number to international formatted phone number
        // Ex: +90 506 606 00 00
        val formattedNumber = phoneNumberKit.formatPhoneNumber(
            number = "05066120000",
            defaultRegion = "tr"
        )
        Log.d(TAG, "Formatted Number: $formattedNumber")
    }

    companion object {
        private const val TAG = "###"
        @JvmStatic
        fun newInstance() = CountryCodeFragment()
    }
}