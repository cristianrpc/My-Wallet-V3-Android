package piuk.blockchain.android.ui.transactions.mapping

import com.blockchain.sunriver.XlmDataManager
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.format
import info.blockchain.wallet.multiaddress.MultiAddressFactory
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.Single
import piuk.blockchain.android.R
import piuk.blockchain.android.coincore.model.ActivitySummaryItem
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.bitcoincash.BchDataManager
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import java.math.BigInteger

class TransactionInOutMapper(
    private val transactionHelper: TransactionHelper,
    private val payloadDataManager: PayloadDataManager,
    private val stringUtils: StringUtils,
    private val ethDataManager: EthDataManager,
    private val bchDataManager: BchDataManager,
    private val xlmDataManager: XlmDataManager,
    private val environmentSettings: EnvironmentConfig
) {

    fun transformInputAndOutputs(item: ActivitySummaryItem): Single<TransactionInOutDetails> =
        when (item.cryptoCurrency) {
            CryptoCurrency.BTC -> handleBtcToAndFrom(item)
            CryptoCurrency.ETHER -> handleEthToAndFrom(item)
            CryptoCurrency.BCH -> handleBchToAndFrom(item)
            CryptoCurrency.XLM -> handleXlmToAndFrom(item)
            CryptoCurrency.PAX -> handlePaxToAndFrom(item)
            else -> throw IllegalArgumentException("${item.cryptoCurrency} is not currently supported")
        }

    private fun handleXlmToAndFrom(activitySummaryItem: ActivitySummaryItem) =
        xlmDataManager.defaultAccount()
            .map { account ->
                var fromAddress = activitySummaryItem.inputsMap.keys.first()
                var toAddress = activitySummaryItem.outputsMap.keys.first()
                if (fromAddress == account.accountId) {
                    fromAddress = account.label
                }
                if (toAddress == account.accountId) {
                    toAddress = account.label
                }

                TransactionInOutDetails(
                    inputs = listOf(
                        TransactionDetailModel(
                            fromAddress
                        )
                    ),
                    outputs = listOf(
                        TransactionDetailModel(
                            toAddress
                        )
                    )
                )
            }

    private fun handleEthToAndFrom(activitySummaryItem: ActivitySummaryItem) =
        Single.fromCallable {
            var fromAddress = activitySummaryItem.inputsMap.keys.first()
            var toAddress = activitySummaryItem.outputsMap.keys.first()

            val ethAddress = ethDataManager.getEthResponseModel()!!.getAddressResponse()!!.account
            if (fromAddress == ethAddress) {
                fromAddress = stringUtils.getString(R.string.eth_default_account_label)
            }
            if (toAddress == ethAddress) {
                toAddress = stringUtils.getString(R.string.eth_default_account_label)
            }

            TransactionInOutDetails(
                inputs = listOf(
                    TransactionDetailModel(
                        fromAddress
                    )
                ),
                outputs = listOf(
                    TransactionDetailModel(
                        toAddress
                    )
                )
            )
        }

    private fun handlePaxToAndFrom(activitySummaryItem: ActivitySummaryItem) =
        Single.fromCallable {
            var fromAddress = activitySummaryItem.inputsMap.keys.first()
            var toAddress = activitySummaryItem.outputsMap.keys.first()

            val ethAddress = ethDataManager.getEthResponseModel()!!.getAddressResponse()!!.account
            if (fromAddress == ethAddress) {
                fromAddress = stringUtils.getString(R.string.pax_default_account_label)
            }
            if (toAddress == ethAddress) {
                toAddress = stringUtils.getString(R.string.pax_default_account_label)
            }

            TransactionInOutDetails(
                inputs = listOf(
                    TransactionDetailModel(
                        fromAddress
                    )
                ),
                outputs = listOf(
                    TransactionDetailModel(
                        toAddress
                    )
                )
            )
        }

    // ----------------------------------------------------------------------------------------------------------------

    private fun handleBtcToAndFrom(activitySummaryItem: ActivitySummaryItem) =
        Single.fromCallable {
            val (inputs, outputs) = transactionHelper.filterNonChangeBtcAddresses(activitySummaryItem)
            setToAndFrom(CryptoCurrency.BTC, inputs, outputs)
        }

    private fun handleBchToAndFrom(activitySummaryItem: ActivitySummaryItem) =
        Single.fromCallable {
            val (inputs, outputs) = transactionHelper.filterNonChangeBchAddresses(activitySummaryItem)
            setToAndFrom(CryptoCurrency.BCH, inputs, outputs)
        }

    private fun setToAndFrom(
        cryptoCurrency: CryptoCurrency,
        inputs: Map<String, BigInteger?>,
        outputs: Map<String, BigInteger?>
    ) = TransactionInOutDetails(
        inputs = getFromList(cryptoCurrency, inputs),
        outputs = getToList(cryptoCurrency, outputs)
    )

    private fun getFromList(
        currency: CryptoCurrency,
        inputMap: Map<String, BigInteger?>
    ): List<TransactionDetailModel> {
        val inputs = handleTransactionMap(inputMap, currency)
        // No inputs = coinbase transaction
        if (inputs.isEmpty()) {
            val coinbase =
                TransactionDetailModel(
                    address = stringUtils.getString(R.string.transaction_detail_coinbase),
                    displayUnits = currency.symbol
                )
            inputs.add(coinbase)
        }
        return inputs.toList()
    }

    private fun getToList(
        currency: CryptoCurrency,
        outputMap: Map<String, BigInteger?>
    ): List<TransactionDetailModel> = handleTransactionMap(outputMap, currency)

    private fun handleTransactionMap(
        inputMap: Map<String, BigInteger?>,
        currency: CryptoCurrency
    ): MutableList<TransactionDetailModel> {
        val inputs = mutableListOf<TransactionDetailModel>()
        for ((key, value) in inputMap) {
            val label = if (currency == CryptoCurrency.BTC) {
                payloadDataManager.addressToLabel(key)
            } else {
                bchDataManager.getLabelFromBchAddress(key)
                    ?: FormatsUtil.toShortCashAddress(environmentSettings.bitcoinCashNetworkParameters, key)
            }

            val transactionDetailModel = buildTransactionDetailModel(label, currency, value, currency)
            inputs.add(transactionDetailModel)
        }
        return inputs
    }

    private fun buildTransactionDetailModel(
        label: String,
        currency: CryptoCurrency,
        value: BigInteger?,
        cryptoCurrency: CryptoCurrency
    ): TransactionDetailModel =
        TransactionDetailModel(
            label,
            if (currency == CryptoCurrency.BTC) {
                CryptoValue.bitcoinFromSatoshis(value ?: BigInteger.ZERO).format()
            } else {
                CryptoValue.bitcoinCashFromSatoshis(value ?: BigInteger.ZERO).format()
            },
            cryptoCurrency.symbol
        ).apply {
            if (address == MultiAddressFactory.ADDRESS_DECODE_ERROR) {
                address = stringUtils.getString(R.string.tx_decode_error)
                addressDecodeError = true
            }
        }
}
