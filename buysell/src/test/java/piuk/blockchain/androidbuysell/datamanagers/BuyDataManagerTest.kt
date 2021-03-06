package piuk.blockchain.androidbuysell.datamanagers

import com.blockchain.remoteconfig.FeatureFlag
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.api.data.WalletOptions
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Single
import io.reactivex.subjects.ReplaySubject
import org.amshove.kluent.`it returns`
import org.junit.Before
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import piuk.blockchain.android.testutils.RxTest
import piuk.blockchain.androidbuysell.models.ExchangeData
import piuk.blockchain.androidbuysell.services.BuyConditions
import piuk.blockchain.androidbuysell.services.ExchangeService
import piuk.blockchain.androidcore.data.auth.AuthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.data.settings.SettingsDataManager
import kotlin.test.Test

class BuyDataManagerTest : RxTest() {

    private lateinit var subject: BuyDataManager
    private val mockSettingsDataManager: SettingsDataManager =
        mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val mockAuthDataManager: AuthDataManager = mock()
    private val mockPayloadDataManager: PayloadDataManager =
        mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val featureFlag: FeatureFlag = mock {
        on { enabled } `it returns` Single.just(true)
    }
    private val mockExchangeService: ExchangeService = mock()

    private val mockWalletOptions: WalletOptions = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val mockSettings: Settings = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val mockExchangeData: ExchangeData = mock(defaultAnswer = RETURNS_DEEP_STUBS)
    private val buyConditions: BuyConditions = mock()

    @Before
    fun setUp() {
        val walletOptionsSource = mockWalletOptionsReplay()
        val exchangeDataSource = mockExchangeDataReplay()
        val walletSettingsSource = mockWalletSettingsReplay()

        whenever(buyConditions.walletOptionsSource).thenReturn(walletOptionsSource)
        whenever(buyConditions.walletSettingsSource).thenReturn(walletSettingsSource)
        whenever(buyConditions.exchangeDataSource).thenReturn(exchangeDataSource)

        subject = BuyDataManager(
            mockSettingsDataManager,
            mockAuthDataManager,
            mockPayloadDataManager,
            buyConditions,
            featureFlag,
            mockExchangeService
        )
    }

    private fun mockWalletOptionsReplay(): ReplaySubject<WalletOptions> {
        val source = ReplaySubject.create<WalletOptions>()
        val o1: Observer<WalletOptions> = mock()
        source.subscribe(o1)

        source.onNext(mockWalletOptions)
        source.onComplete()

        whenever(mockAuthDataManager.getWalletOptions()).thenReturn(
            Observable.just(
                mockWalletOptions
            )
        )

        return source
    }

    private fun mockWalletSettingsReplay(): ReplaySubject<Settings> {
        val source = ReplaySubject.create<Settings>()
        val o1: Observer<Settings> = mock()
        source.subscribe(o1)

        source.onNext(mockSettings)
        source.onComplete()

        whenever(mockSettingsDataManager.getSettings()).thenReturn(Observable.just(mockSettings))

        return source
    }

    private fun mockExchangeDataReplay(): ReplaySubject<ExchangeData> {
        val source = ReplaySubject.create<ExchangeData>()
        val o1: Observer<ExchangeData> = mock()
        source.subscribe(o1)

        source.onNext(mockExchangeData)
        source.onComplete()

        whenever(mockExchangeService.getExchangeMetaData()).thenReturn(
            Observable.just(
                mockExchangeData
            )
        )

        return source
    }

    @Test
    fun isBuyRolledOut() {
        isBuyRolledOut(0.0, false)
        isBuyRolledOut(0.3, false)
        isBuyRolledOut(0.5, true)
        isBuyRolledOut(1.0, true)
    }

    private fun isBuyRolledOut(percentage: Double, expectedResult: Boolean) {
        // Arrange
        whenever(mockPayloadDataManager.wallet!!.guid).thenReturn("7279615c-23eb-4a1c-92df-2440acea8e1a")
        whenever(mockWalletOptions.rolloutPercentage).thenReturn(percentage)

        // Act
        val testObserver = subject.isBuyRolledOut.test()

        // Assert
        verify(mockPayloadDataManager, atLeastOnce()).wallet
        verify(mockWalletOptions, atLeastOnce()).rolloutPercentage
        verifyNoMoreInteractions(mockPayloadDataManager)
        verifyNoMoreInteractions(mockWalletOptions)
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(expectedResult)
    }

    @Test
    fun `isCoinifyAllowed is sepa country, no account`() {
        // Arrange
        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("GB"))
        whenever(mockSettings.countryCode).thenReturn("GB")
        whenever(mockExchangeData.coinify!!.user).thenReturn(0)

        // Act
        val testObserver = subject.isCoinifyAllowed.test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(true)
    }

    @Test
    fun `isCoinifyAllowed is sepa country, with account`() {
        // Arrange
        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("GB"))
        whenever(mockSettings.countryCode).thenReturn("GB")
        whenever(mockExchangeData.coinify!!.user).thenReturn(100)

        // Act
        val testObserver = subject.isCoinifyAllowed.test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(true)
    }

    @Test
    fun `isCoinifyAllowed not sepa country, with account but feature flag disabled`() {
        // Arrange
        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("GB"))
        whenever(mockSettings.countryCode).thenReturn("GB")
        whenever(mockExchangeData.coinify!!.user).thenReturn(100)
        whenever(featureFlag.enabled).thenReturn(Single.just(false))

        // Act
        val testObserver = subject.isCoinifyAllowed.test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    fun `isCoinifyAllowed not sepa country, no account`() {
        // Arrange
        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("RSA"))
        whenever(mockSettings.countryCode).thenReturn("GB")
        whenever(mockExchangeData.coinify!!.user).thenReturn(0)

        // Act
        val testObserver = subject.isCoinifyAllowed.test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    fun `isCoinifyAllowed not sepa country, with account`() {
        // Arrange
        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("RSA"))
        whenever(mockSettings.countryCode).thenReturn("GB")
        whenever(mockExchangeData.coinify!!.user).thenReturn(100)

        // Act
        val testObserver = subject.isCoinifyAllowed.test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(true)
    }

    @Test
    fun `canBuy isAllowed but not rolled out`() {

        // individual cases have been tested above

        // Arrange
        whenever(mockPayloadDataManager.wallet!!.guid).thenReturn("7279615c-23eb-4a1c-92df-2440acea8e1a")
        whenever(mockWalletOptions.rolloutPercentage).thenReturn(0.0)

        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("GB"))
        whenever(mockSettings.countryCode).thenReturn("GB")

        whenever(mockExchangeData.coinify!!.user).thenReturn(100)

        // Act
        val testObserver = subject.canBuy.test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    fun `canBuy notAllowed and androidEnabled`() {

        // individual cases have been tested above

        // Arrange
        whenever(mockPayloadDataManager.wallet!!.guid).thenReturn("7279615c-23eb-4a1c-92df-2440acea8e1a")
        whenever(mockWalletOptions.rolloutPercentage).thenReturn(1.0)
        whenever(mockSettings.countryCode).thenReturn("USA")
        whenever(mockSettings.state).thenReturn("TX")
        whenever(mockWalletOptions.androidFlags.containsKey("showSfox")).thenReturn(true)
        whenever(mockWalletOptions.androidFlags["showSfox"]).thenReturn(true)

        // Act
        val testObserver = subject.canBuy.test()

        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }

    @Test
    fun getCountryCode() {
        // Arrange
        whenever(mockSettings.countryCode).thenReturn("GB")
        // Act
        val testObserver = subject.countryCode.test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue("GB")
    }

    @Test
    fun `isInCoinifyCountry true`() {
        // Arrange
        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("GB"))
        // Act
        val testObserver = subject.isInCoinifyCountry("GB").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(true)
    }

    @Test
    fun `isInCoinifyCountry false`() {
        // Arrange
        whenever(mockWalletOptions.partners.coinify.countries).thenReturn(listOf("GB"))
        // Act
        val testObserver = subject.isInCoinifyCountry("ZA").test()
        // Assert
        testObserver.assertComplete()
        testObserver.assertNoErrors()
        testObserver.assertValue(false)
    }
}