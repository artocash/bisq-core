/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.statistics;

import bisq.core.app.AppOptionKeys;
import bisq.core.locale.CurrencyTuple;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.Trade;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.storage.JsonFileManager;
import bisq.common.storage.Storage;
import bisq.common.util.Utilities;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.io.File;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeStatisticsManager {

    static TradeStatistics2 ConvertToTradeStatistics2(TradeStatistics tradeStatistics) {
        return new TradeStatistics2(tradeStatistics.getDirection(),
                tradeStatistics.getBaseCurrency(),
                tradeStatistics.getCounterCurrency(),
                tradeStatistics.getOfferPaymentMethod(),
                tradeStatistics.getOfferDate(),
                tradeStatistics.isOfferUseMarketBasedPrice(),
                tradeStatistics.getOfferMarketPriceMargin(),
                tradeStatistics.getOfferAmount(),
                tradeStatistics.getOfferMinAmount(),
                tradeStatistics.getOfferId(),
                tradeStatistics.getTradePrice().getValue(),
                tradeStatistics.getTradeAmount().getValue(),
                tradeStatistics.getTradeDate().getTime(),
                tradeStatistics.getDepositTxId(),
                null,
                tradeStatistics.getExtraDataMap());
    }

    private final JsonFileManager jsonFileManager;
    private final P2PService p2PService;
    private final PriceFeedService priceFeedService;
    private final boolean dumpStatistics;
    private final ObservableSet<TradeStatistics2> observableTradeStatisticsSet = FXCollections.observableSet();
    private final HashSet<TradeStatistics2> tradeStatisticsSet = new HashSet<>();

    @Inject
    public TradeStatisticsManager(P2PService p2PService,
                                  PriceFeedService priceFeedService,
                                  @Named(Storage.STORAGE_DIR) File storageDir,
                                  @Named(AppOptionKeys.DUMP_STATISTICS) boolean dumpStatistics) {
        this.p2PService = p2PService;
        this.priceFeedService = priceFeedService;
        this.dumpStatistics = dumpStatistics;
        jsonFileManager = new JsonFileManager(storageDir);
    }

    public void onAllServicesInitialized() {
        if (dumpStatistics) {
            ArrayList<CurrencyTuple> fiatCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            jsonFileManager.writeToDisc(Utilities.objectToJson(fiatCurrencyList), "fiat_currency_list");

            ArrayList<CurrencyTuple> cryptoCurrencyList = new ArrayList<>(CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(e -> new CurrencyTuple(e.getCode(), e.getName(), 8))
                    .collect(Collectors.toList()));
            cryptoCurrencyList.add(0, new CurrencyTuple(Res.getBaseCurrencyCode(), Res.getBaseCurrencyName(), 8));
            jsonFileManager.writeToDisc(Utilities.objectToJson(cryptoCurrencyList), "crypto_currency_list");
        }

        p2PService.getP2PDataStorage().addPersistableNetworkPayloadMapListener(payload -> {
            if (payload instanceof TradeStatistics2)
                addToMap((TradeStatistics2) payload, true);
        });

        p2PService.getP2PDataStorage().getPersistableNetworkPayloadCollection().getMap().values().forEach(e -> {
            if (e instanceof TradeStatistics2)
                addToMap((TradeStatistics2) e, false);
        });

        //TODO can be removed after version older than v0.6.0 are not used anymore
        // We listen to TradeStatistics objects from old clients as well and convert them into TradeStatistics2 objects
        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final ProtectedStoragePayload protectedStoragePayload = data.getProtectedStoragePayload();
                if (protectedStoragePayload instanceof TradeStatistics)
                    p2PService.getP2PDataStorage().addPersistableNetworkPayload(ConvertToTradeStatistics2((TradeStatistics) protectedStoragePayload),
                            p2PService.getNetworkNode().getNodeAddress(), true, false, false, false);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });

        priceFeedService.applyLatestBisqMarketPrice(tradeStatisticsSet);
        dump();

        // print all currencies sorted by nr. of trades
        // printAllCurrencyStats();
    }

    public void publishTradeStatistics(List<Trade> trades) {
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            TradeStatistics2 tradeStatistics = new TradeStatistics2(trade.getOffer().getOfferPayload(),
                    trade.getTradePrice(),
                    trade.getTradeAmount(),
                    trade.getDate(),
                    (trade.getDepositTx() != null ? trade.getDepositTx().getHashAsString() : ""));
            addToMap(tradeStatistics, true);

            // We only republish trades from last 10 days
            if ((new Date().getTime() - trade.getDate().getTime()) < TimeUnit.DAYS.toMillis(10)) {
                long delay = 5000;
                long minDelay = (i + 1) * delay;
                long maxDelay = (i + 2) * delay;
                UserThread.runAfterRandomDelay(() -> {
                    p2PService.addPersistableNetworkPayload(tradeStatistics, true);
                }, minDelay, maxDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void addToMap(TradeStatistics2 tradeStatistics, boolean storeLocally) {
        if (!tradeStatisticsSet.contains(tradeStatistics)) {
            boolean itemAlreadyAdded = tradeStatisticsSet.stream().filter(e -> (e.getOfferId().equals(tradeStatistics.getOfferId()))).findAny().isPresent();
            if (!itemAlreadyAdded) {
                tradeStatisticsSet.add(tradeStatistics);
                observableTradeStatisticsSet.add(tradeStatistics);

                tradeStatistics.getTradePrice().getValue();

                if (storeLocally) {
                    priceFeedService.applyLatestBisqMarketPrice(tradeStatisticsSet);
                    dump();
                }
            } else {
                log.debug("We have already an item with the same offer ID. That might happen if both the maker and the taker published the tradeStatistics");
            }
        }
    }

    public ObservableSet<TradeStatistics2> getObservableTradeStatisticsSet() {
        return observableTradeStatisticsSet;
    }

    private void dump() {
        if (dumpStatistics) {
            // We store the statistics as json so it is easy for further processing (e.g. for web based services)
            // TODO This is just a quick solution for storing to one file.
            // 1 statistic entry has 500 bytes as json.
            // Need a more scalable solution later when we get more volume.
            // The flag will only be activated by dedicated nodes, so it should not be too critical for the moment, but needs to
            // get improved. Maybe a LevelDB like DB...? Could be impl. in a headless version only.
            List<TradeStatisticsForJson> list = tradeStatisticsSet.stream().map(TradeStatisticsForJson::new).collect(Collectors.toList());
            list.sort((o1, o2) -> (o1.tradeDate < o2.tradeDate ? 1 : (o1.tradeDate == o2.tradeDate ? 0 : -1)));
            TradeStatisticsForJson[] array = new TradeStatisticsForJson[list.size()];
            list.toArray(array);
            jsonFileManager.writeToDisc(Utilities.objectToJson(array), "trade_statistics");
        }
    }

    private void printAllCurrencyStats() {
        Map<String, Set<TradeStatistics2>> map1 = new HashMap<>();
        for (TradeStatistics2 tradeStatistics : tradeStatisticsSet) {
            if (CurrencyUtil.isFiatCurrency(tradeStatistics.getCounterCurrency())) {
                final String counterCurrency = CurrencyUtil.getNameAndCode(tradeStatistics.getCounterCurrency());
                if (!map1.containsKey(counterCurrency))
                    map1.put(counterCurrency, new HashSet<>());

                map1.get(counterCurrency).add(tradeStatistics);
            }
        }

        StringBuilder sb1 = new StringBuilder("\nAll traded Fiat currencies:\n");
        map1.entrySet().stream()
                .sorted((o1, o2) -> Integer.valueOf(o2.getValue().size()).compareTo(o1.getValue().size()))
                .forEach(e -> sb1.append(e.getKey()).append(": ").append(e.getValue().size()).append("\n"));
        log.error(sb1.toString());

        Map<String, Set<TradeStatistics2>> map2 = new HashMap<>();
        for (TradeStatistics2 tradeStatistics : tradeStatisticsSet) {
            if (CurrencyUtil.isCryptoCurrency(tradeStatistics.getBaseCurrency())) {
                final String code = CurrencyUtil.getNameAndCode(tradeStatistics.getBaseCurrency());
                if (!map2.containsKey(code))
                    map2.put(code, new HashSet<>());

                map2.get(code).add(tradeStatistics);
            }
        }

        List<String> allCryptoCurrencies = new ArrayList<>();
        Set<String> coinsWithValidator = new HashSet<>();

        // List of coins with validator before 0.6.0 hard requirements for address validator
        coinsWithValidator.add("BTC");
        coinsWithValidator.add("BSQ");
        coinsWithValidator.add("LTC");
        coinsWithValidator.add("DOGE");
        coinsWithValidator.add("DASH");
        coinsWithValidator.add("ETH");
        coinsWithValidator.add("PIVX");
        coinsWithValidator.add("IOP");
        coinsWithValidator.add("888");
        coinsWithValidator.add("ZEC");
        coinsWithValidator.add("GBYTE");
        coinsWithValidator.add("NXT");

        // All those need to have a address validator
        Set<String> newlyAdded = new HashSet<>();
        // v0.6.0
        newlyAdded.add("DCT");
        newlyAdded.add("PNC");
        newlyAdded.add("WAC");
        newlyAdded.add("ZEN");
        newlyAdded.add("ELLA");
        newlyAdded.add("XCN");
        newlyAdded.add("TRC");
        newlyAdded.add("INXT");
        newlyAdded.add("PART");
        // v0.6.1
        newlyAdded.add("MAD");
        newlyAdded.add("BCH");
        newlyAdded.add("BCHC");
        newlyAdded.add("BTG");
        // v0.6.2
        newlyAdded.add("CAGE");
        newlyAdded.add("CRED");
        newlyAdded.add("XSPEC");
        // v0.6.3
        newlyAdded.add("WILD");
        newlyAdded.add("ONION");
        // v0.6.4
        newlyAdded.add("CREA");
        newlyAdded.add("XIN");
        // v0.6.5
        newlyAdded.add("BETR");
        newlyAdded.add("MVT");
        newlyAdded.add("REF");
        // v0.6.6
        newlyAdded.add("STL");
        newlyAdded.add("DAI");
        newlyAdded.add("YTN");
        newlyAdded.add("DARX");
        newlyAdded.add("ODN");
        newlyAdded.add("CDT");
        newlyAdded.add("DGM");
        newlyAdded.add("SCS");
        newlyAdded.add("SOS");
        newlyAdded.add("ACH");
        newlyAdded.add("VDN");
        // v0.7.0
        newlyAdded.add("ALC");
        newlyAdded.add("DIN");
        newlyAdded.add("NAH");
        newlyAdded.add("ROI");
        newlyAdded.add("WMCC");
        newlyAdded.add("RTO");

        coinsWithValidator.addAll(newlyAdded);

        CurrencyUtil.getAllSortedCryptoCurrencies()
                .forEach(e -> allCryptoCurrencies.add(e.getNameAndCode()));
        StringBuilder sb2 = new StringBuilder("\nAll traded Crypto currencies:\n");
        StringBuilder sb3 = new StringBuilder("\nNever traded Crypto currencies:\n");
        map2.entrySet().stream()
                .sorted((o1, o2) -> Integer.compare(o2.getValue().size(), o1.getValue().size()))
                .forEach(e -> {
                    final String key = e.getKey();
                    sb2.append(key).append(": ").append(e.getValue().size()).append("\n");
                    // key is: USD Tether (USDT)
                    String code = key.substring(key.indexOf("(") + 1, key.length() - 1);
                    if (!coinsWithValidator.contains(code) && !newlyAdded.contains(code))
                        allCryptoCurrencies.remove(key);
                });
        log.error(sb2.toString());

        // Not considered age of newly added coins, so take care with removal if coin was added recently.
        allCryptoCurrencies.sort(String::compareTo);
        allCryptoCurrencies
                .forEach(e -> {
                    // key is: USD Tether (USDT)
                    String code = e.substring(e.indexOf("(") + 1, e.length() - 1);
                    if (!coinsWithValidator.contains(code) && !newlyAdded.contains(code))
                        sb3.append(e).append("\n");
                });
        log.error(sb3.toString());
    }
}
