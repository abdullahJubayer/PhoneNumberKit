package me.ibrahimsn.phonenumberkit

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class FragmentContainer : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)

        supportFragmentManager.beginTransaction()
         .replace(R.id.root_layout,CountryCodeFragment.newInstance())
            .commit()
    }

}