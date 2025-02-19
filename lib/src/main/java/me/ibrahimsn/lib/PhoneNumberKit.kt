package me.ibrahimsn.lib

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.redmadrobot.inputmask.MaskedTextChangedListener
import com.redmadrobot.inputmask.MaskedTextChangedListener.Companion.installOn
import com.redmadrobot.inputmask.helper.AffinityCalculationStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import me.ibrahimsn.lib.api.Country
import me.ibrahimsn.lib.api.Phone
import me.ibrahimsn.lib.internal.core.Proxy
import me.ibrahimsn.lib.internal.ext.*
import me.ibrahimsn.lib.internal.io.FileReader
import me.ibrahimsn.lib.internal.model.State
import me.ibrahimsn.lib.internal.pattern.CountryPattern
import me.ibrahimsn.lib.internal.ui.CountryPickerArguments
import me.ibrahimsn.lib.internal.ui.CountryPickerBottomSheet
import java.lang.ref.WeakReference
import java.util.*

class PhoneNumberKit private constructor(
    private val context: Context,
    private val isIconEnabled: Boolean,
    private val isFullScreen: Boolean,
    private val excludedCountries: List<String>,
    private val admittedCountries: List<String>,
) {

    private val supervisorJob = SupervisorJob()

    private val scope = CoroutineScope(supervisorJob + Dispatchers.Main)

    private val proxy: Proxy by lazy { Proxy(context) }

    private val state: MutableStateFlow<State> = MutableStateFlow(State.Ready)

    private var input: WeakReference<TextInputLayout> = WeakReference(null)

    private var inputEt: WeakReference<EditText> = WeakReference(null)

    private val countriesCache = mutableListOf<Country>()

    private var inputValue: CharSequence?
        get() = input.get()?.editText?.text
        set(value) {
            input.get()?.editText?.setText(value)
        }

    private var inputValueEt: CharSequence?
        get() = inputEt.get()?.text
        set(value) {
            inputEt.get()?.setText(value)
        }

    val isValid: Boolean get() = validate(inputValue)

    val isValidEditText: Boolean get() = validate(inputValueEt)

    init {
        scope.launch(Dispatchers.IO) {
            countriesCache.addAll(getCountries())
        }
    }

    private var textChangedListener: MaskedTextChangedListener? = null

    private fun setupListener(editText: EditText, pattern: String) {
        editText.removeTextChangedListener(textChangedListener)
        textChangedListener = installOn(
            editText,
            pattern,
            emptyList(),
            AffinityCalculationStrategy.WHOLE_STRING,
            object : MaskedTextChangedListener.ValueListener {
                override fun onTextChanged(
                    maskFilled: Boolean,
                    extractedValue: String,
                    formattedValue: String
                ) {
                    val state = this@PhoneNumberKit.state.value
                    if (state is State.Attached) {
                        val parsedNumber = proxy.parsePhoneNumber(
                            extractedValue.clearSpaces(),
                            state.country.iso2
                        )

                        if (state.country.code != parsedNumber?.countryCode) {
                            val country = countriesCache.findCountry(parsedNumber?.countryCode)
                            if (country != null) {
                                setCountry(country)
                            }
                        }
                    }
                }
            }
        )
    }

    private fun setCountry(countryIso2: String) = scope.launch {
        val country = default {
            getCountries().findCountry(
                countryIso2.trim().lowercase(Locale.ENGLISH)
            )
        } ?: return@launch
        setCountry(country)
    }

    private fun setCountry(country: Country) {
        val formattedNumber = proxy.formatPhoneNumber(
            proxy.getExampleNumber(country.iso2)
        )
        val pattern = CountryPattern.create(
            formattedNumber.orEmpty()
        )
        state.value = State.Attached(
            country = country,
            pattern = pattern
        )
    }

    fun attachToInput(
        input: TextInputLayout,
        defaultCountry: Int,
    ) {
        this.input = WeakReference(input)
        scope.launch {
            val country = default {
                getCountries().findCountry(defaultCountry)
            }
            if (country != null) {
                attachToInput(input, country)
            }
        }
    }

    fun attachToInput(
        input: TextInputLayout,
        countryIso2: String,
    ) {
        this.input = WeakReference(input)
        scope.launch {
            val country = default {
                getCountries().findCountry(
                    countryIso2.trim().lowercase(Locale.ENGLISH)
                )
            }
            if (country != null) {
                attachToInput(input, country)
            }
        }
    }

    fun attachToInputWithEditText(
        inputEt: EditText,
        countryIso2: String,
    ) {
        this.inputEt = WeakReference(inputEt)
        scope.launch {
            val country = default {
                getCountries().findCountry(
                    countryIso2.trim().lowercase(Locale.ENGLISH)
                )
            }
            if (country != null) {
                attachToInputWithEditText(inputEt, country)
            }
        }
    }

    private fun collectState() = scope.launch {
        state.collect { state ->
            when (state) {
                is State.Ready -> {}
                is State.Attached -> {
                    if (isIconEnabled) {
                        getFlagIcon(state.country.iso2)?.let { icon ->
                            input.get()?.startIconDrawable = icon
                            inputEt.get()?.setCompoundDrawablesWithIntrinsicBounds(icon,null,null,null)
                            inputEt.get()?.compoundDrawablePadding=20
                        }
                    }
                    input.get()?.editText?.let { editText ->
                        setupListener(editText, state.pattern)
                    }
                    inputEt.get()?.let { editText ->
                        setupListener(editText, state.pattern)
                    }
                    if (inputValue.isNullOrEmpty()) {
                        inputValue = state.country.code.toString()
                    }
                    if (inputValueEt.isNullOrEmpty()) {
                        inputValueEt = state.country.code.toString()
                    }
                }
            }
        }
    }

    private suspend fun getCountries() = io {
        if (countriesCache.isEmpty()) {
            FileReader.readAssetFile(context, ASSET_FILE_NAME).toCountryList()
        } else {
            countriesCache
        }
    }

    private fun clearInputValue() {
        inputValue = ""
    }

    private fun clearInputValueForEditText() {
        inputValueEt = ""
    }

    private fun attachToInput(
        input: TextInputLayout,
        country: Country,
    ) {
        input.editText?.inputType = InputType.TYPE_CLASS_PHONE

        input.isStartIconVisible = isIconEnabled
        input.setStartIconTintList(null)

        collectState()
        setCountry(country.iso2)
    }

    private fun attachToInputWithEditText(
        input: EditText,
        country: Country,
    ) {
        input.inputType = InputType.TYPE_CLASS_PHONE

        collectState()
        setCountry(country.iso2)
    }

    /**
     * Sets up country code picker bottomSheet
     */
    fun setupCountryPicker(
        activity: AppCompatActivity,
        itemLayout: Int = R.layout.item_country_picker,
        searchEnabled: Boolean = false,
    ) {
        input.get()?.isStartIconCheckable = true
        input.get()?.setStartIconOnClickListener {
            CountryPickerBottomSheet.newInstance(
                CountryPickerArguments(
                    itemLayout,
                    searchEnabled,
                    isFullScreen,
                    excludedCountries,
                    admittedCountries
                )
            ).apply {
                onCountrySelectedListener = { country ->
                    clearInputValue()
                    setCountry(country)
                }
                show(
                    activity.supportFragmentManager,
                    CountryPickerBottomSheet.TAG
                )
            }
        }
        inputEt.get()?.setOnClickListener {
            CountryPickerBottomSheet.newInstance(
                CountryPickerArguments(
                    itemLayout,
                    searchEnabled,
                    isFullScreen,
                    excludedCountries,
                    admittedCountries
                )
            ).apply {
                onCountrySelectedListener = { country ->
                    clearInputValueForEditText()
                    setCountry(country)
                }
                show(
                    activity.supportFragmentManager,
                    CountryPickerBottomSheet.TAG
                )
            }
        }
    }

    /**
     * Parses raw phone number into phone object
     */
    fun parsePhoneNumber(number: String?, defaultRegion: String?): Phone? {
        proxy.parsePhoneNumber(number, defaultRegion)?.let { phone ->
            return Phone(
                nationalNumber = phone.nationalNumber,
                countryCode = phone.countryCode,
                rawInput = phone.rawInput,
                numberOfLeadingZeros = phone.numberOfLeadingZeros
            )
        }
        return null
    }

    /**
     * Formats raw phone number into international phone
     */
    fun formatPhoneNumber(number: String?, defaultRegion: String?): String? {
        return proxy.formatPhoneNumber(proxy.parsePhoneNumber(number, defaultRegion))
    }

    /**
     * Provides an example phone number according to country iso2 code
     */
    fun getExampleNumber(iso2: String?): Phone? {
        proxy.getExampleNumber(iso2)?.let { phone ->
            return Phone(
                nationalNumber = phone.nationalNumber,
                countryCode = phone.countryCode,
                rawInput = phone.rawInput,
                numberOfLeadingZeros = phone.numberOfLeadingZeros
            )
        }
        return null
    }

    /**
     * Provides country flag icon for given country iso2 code
     */
    fun getFlagIcon(iso2: String?): Drawable? {
        return try {
            ContextCompat.getDrawable(
                context, context.resources.getIdentifier(
                    "country_flag_$iso2",
                    "drawable",
                    context.packageName
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun List<Country>.findCountry(
        countryCode: Int?
    ) = this.filter {
        admittedCountries.isEmpty() || admittedCountries.contains(it.iso2)
    }.filterNot {
        excludedCountries.contains(it.iso2)
    }.firstOrNull {
        it.code == countryCode
    }

    private fun List<Country>.findCountry(
        countryIso2: String?
    ) = this.filter {
        admittedCountries.isEmpty() || admittedCountries.contains(it.iso2)
    }.filterNot {
        excludedCountries.contains(it.iso2)
    }.firstOrNull {
        it.iso2 == countryIso2
    }

    private fun validate(number: CharSequence?): Boolean {
        if (number == null) return false
        return state.value.doIfAttached {
            proxy.validateNumber(number.toString(), country.iso2)
        } ?: false
    }

    companion object {
        const val ASSET_FILE_NAME = "countries.json"
    }

    class Builder(private val context: Context) {

        private var isIconEnabled: Boolean = true
        private var isFullScreen: Boolean = false

        private var excludedCountries: List<String>? = null

        private var admittedCountries: List<String>? = null

        fun setIconEnabled(isEnabled: Boolean): Builder {
            this.isIconEnabled = isEnabled
            return this
        }

        fun excludeCountries(countries: List<String>): Builder {
            this.excludedCountries = countries
            return this
        }

        fun admitCountries(countries: List<String>): Builder {
            this.admittedCountries = countries
            return this
        }

        fun setFullScreen(isFullScreen: Boolean): Builder {
            this.isFullScreen = isFullScreen
            return this
        }

        fun build(): PhoneNumberKit {
            return PhoneNumberKit(
                context,
                isIconEnabled,
                isFullScreen,
                excludedCountries.orEmpty(),
                admittedCountries.orEmpty()
            )
        }
    }
}
